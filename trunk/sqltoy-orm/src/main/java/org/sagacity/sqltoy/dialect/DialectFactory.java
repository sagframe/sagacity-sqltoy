/**
 * 
 */
package org.sagacity.sqltoy.dialect;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlParamsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.dialect.impl.ClickHouseDialect;
import org.sagacity.sqltoy.dialect.impl.DB2Dialect;
import org.sagacity.sqltoy.dialect.impl.DMDialect;
import org.sagacity.sqltoy.dialect.impl.DefaultDialect;
import org.sagacity.sqltoy.dialect.impl.GuassDBDialect;
import org.sagacity.sqltoy.dialect.impl.MySqlDialect;
import org.sagacity.sqltoy.dialect.impl.OceanBaseDialect;
import org.sagacity.sqltoy.dialect.impl.Oracle11gDialect;
import org.sagacity.sqltoy.dialect.impl.OracleDialect;
import org.sagacity.sqltoy.dialect.impl.PostgreSqlDialect;
import org.sagacity.sqltoy.dialect.impl.SqlServerDialect;
import org.sagacity.sqltoy.dialect.impl.SqliteDialect;
import org.sagacity.sqltoy.dialect.impl.SybaseIQDialect;
import org.sagacity.sqltoy.dialect.impl.TidbDialect;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.PageOptimizeUtils;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.executor.UniqueExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutorExtend;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.ShardingModel;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.plugins.sharding.ShardingUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.ParallelUtils;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 为不同类型数据库提供不同方言实现类的factory,避免各个数据库发展变更形成相互影响
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DialectFactory.java,Revision:v1.0,Date:2014年12月11日
 * @modify data:2020-06-05 增加dm(达梦)数据库支持
 * @modify data:2020-06-10 增加tidb、guassdb、oceanbase支持,规整sqlserver的版本(默认仅支持2012+)
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DialectFactory {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(DialectFactory.class);

	/**
	 * 不同数据库方言的处理器实例(为什么不采用并发map?因为这里只有取,几乎不存在放入)
	 */
	private static HashMap<Integer, Dialect> dialects = new HashMap<Integer, Dialect>();

	private static DialectFactory me = new DialectFactory();

	/**
	 * 断是否是{?=call xxStore()} 模式
	 */
	private static Pattern STORE_PATTERN = Pattern.compile("^(\\s*\\{)?\\s*\\?");

	/**
	 * 问号模式的参数匹配
	 */
	private static Pattern ARG_PATTERN = Pattern.compile("\\?");

	/**
	 * 私有化避免直接实例化
	 */
	private DialectFactory() {
	}

	/**
	 * 获取对象单例
	 * 
	 * @return
	 */
	public static DialectFactory getInstance() {
		return me;
	}

	/**
	 * @todo 根据数据库类型获取处理sql的handler
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	private Dialect getDialectSqlWrapper(Integer dbType) throws Exception {
		// 从map中直接获取实例，避免重复创建和判断
		if (dialects.containsKey(dbType)) {
			return dialects.get(dbType);
		}
		// 按照市场排名作为优先顺序
		Dialect dialectSqlWrapper = null;
		switch (dbType) {
		// oracle12c(分页方式有了改变,支持identity主键策略(内部其实还是sequence模式))
		case DBType.ORACLE: {
			dialectSqlWrapper = new OracleDialect();
			break;
		}
		// 5.6+(mysql 的缺陷主要集中在不支持with as以及临时表不同在一个查询中多次引用)
		// 8.x+(支持with as语法)
		// MariaDB 在检测的时候归并到mysql,采用跟mysql一样的语法
		case DBType.MYSQL:
		case DBType.MYSQL57: {
			dialectSqlWrapper = new MySqlDialect();
			break;
		}
		// sqlserver2012 以后分页方式更简单
		case DBType.SQLSERVER: {
			dialectSqlWrapper = new SqlServerDialect();
			break;
		}
		// 9.5+(9.5开始支持类似merge into形式的语法,参见具体实现)
		case DBType.POSTGRESQL: {
			dialectSqlWrapper = new PostgreSqlDialect();
			break;
		}
		// oceanbase 数据库支持
		case DBType.OCEANBASE: {
			dialectSqlWrapper = new OceanBaseDialect();
			break;
		}
		// db2 10.x版本分页支持offset模式
		case DBType.DB2: {
			dialectSqlWrapper = new DB2Dialect();
			break;
		}
		// clickhouse 19.x 版本开始支持
		case DBType.CLICKHOUSE: {
			dialectSqlWrapper = new ClickHouseDialect();
			break;
		}
		// Tidb方言支持
		case DBType.TIDB: {
			dialectSqlWrapper = new TidbDialect();
			break;
		}
		// 华为guassdb(postgresql 为蓝本的)
		case DBType.GAUSSDB: {
			dialectSqlWrapper = new GuassDBDialect();
			break;
		}
		// dm数据库支持(以oracle为蓝本)
		case DBType.DM: {
			dialectSqlWrapper = new DMDialect();
			break;
		}
		// 基本支持(sqlite 本身功能就相对简单)
		case DBType.SQLITE: {
			dialectSqlWrapper = new SqliteDialect();
			break;
		}
		// 10g,11g
		case DBType.ORACLE11: {
			dialectSqlWrapper = new Oracle11gDialect();
			break;
		}
		// sybase iq基本淘汰
		// 15.4+(必须采用15.4,最好采用16.0 并打上最新的补丁),15.4 之后的分页支持limit模式
		case DBType.SYBASE_IQ: {
			dialectSqlWrapper = new SybaseIQDialect();
			break;
		}
		// 如果匹配不上使用默认dialect
		default:
			dialectSqlWrapper = new DefaultDialect();
//			// 不支持
//			throw new UnsupportedOperationException(SqlToyConstants.UN_MATCH_DIALECT_MESSAGE);
		}
		dialects.put(dbType, dialectSqlWrapper);
		return dialectSqlWrapper;
	}

	/**
	 * @todo 批量执行sql修改或删除操作
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param dataSet
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param insertCallhandler
	 * @param autoCommit
	 * @param dataSource
	 * @return
	 */
	public Long batchUpdate(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final List dataSet,
			final int batchSize, final ReflectPropertyHandler reflectPropertyHandler,
			final InsertRowCallbackHandler insertCallhandler, final Boolean autoCommit, final DataSource dataSource) {
		// 首先合法性校验
		if (dataSet == null || dataSet.isEmpty()) {
			logger.warn("batchUpdate dataSet is null or empty,please check!");
			return 0L;
		}
		try {
			// 启动执行日志(会在threadlocal中创建一个当前执行信息,并建立一个唯一跟踪id)
			SqlExecuteStat.start(sqlToyConfig.getId(), "batchUpdate:[" + dataSet.size() + "]条记录!",
					sqlToyConfig.isShowSql());
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							String realSql = sqlToyConfig.getSql(dialect);
							Integer[] fieldTypes = null;
							List values = dataSet;
							// sql中存在:named参数模式，通过sql提取参数名称
							if (sqlToyConfig.getParamsName() != null) {
								// 替换sql中:name为?并提取参数名称归集成数组
								SqlParamsModel sqlParamsModel = SqlConfigParseUtils.processNamedParamsQuery(realSql);
								values = BeanUtil.reflectBeansToList(dataSet, sqlParamsModel.getParamsName(),
										reflectPropertyHandler, false, 0);
								fieldTypes = BeanUtil.matchMethodsType(dataSet.get(0).getClass(),
										sqlParamsModel.getParamsName());
								realSql = sqlParamsModel.getSql();
							}
							// 做sql签名
							realSql = SqlUtilsExt.signSql(realSql, dbType, sqlToyConfig);
							SqlExecuteStat.showSql("批量sql执行", realSql, null);
							this.setResult(SqlUtil.batchUpdateByJdbc(realSql, values, batchSize, insertCallhandler,
									fieldTypes, autoCommit, conn, dbType));
						}
					});
			// 输出执行结果更新记录量日志
			SqlExecuteStat.debug("执行结果", "批量更新记录数量:{} 条!", updateTotalCnt);
			return updateTotalCnt;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			// 输出执行效率和超时、错误日志
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 执行sql修改性质的操作语句
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param paramsNamed
	 * @param paramsValue
	 * @param autoCommit
	 * @param dataSource
	 * @return
	 */
	public Long executeSql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final String[] paramsNamed, final Object[] paramsValue, final Boolean autoCommit,
			final DataSource dataSource) {
		try {
			SqlExecuteStat.start(sqlToyConfig.getId(), "executeSql", sqlToyConfig.isShowSql());
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig,
							new QueryExecutor(sqlToyConfig.getSql(), paramsNamed, paramsValue), dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(sqlToyConfig.getSql(dialect),
									paramsNamed, paramsValue);
							// 替换sharding table
							String executeSql = ShardingUtils.replaceShardingTables(sqlToyContext, queryParam.getSql(),
									sqlToyConfig, paramsNamed, paramsValue);
							// 做sql签名
							executeSql = SqlUtilsExt.signSql(executeSql, dbType, sqlToyConfig);
							this.setResult(SqlUtil.executeSql(executeSql, queryParam.getParamsValue(), null, conn,
									dbType, autoCommit));
						}
					});
			SqlExecuteStat.debug("执行结果", "受影响记录数量:{} 条!", updateTotalCnt);
			return updateTotalCnt;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 判定数据是否重复 true 表示唯一不重复；false 表示不唯一，即数据库中已经存在
	 * @param sqlToyContext
	 * @param uniqueExecutor
	 * @param dataSource
	 * @return
	 */
	public boolean isUnique(final SqlToyContext sqlToyContext, final UniqueExecutor uniqueExecutor,
			final DataSource dataSource) {
		if (uniqueExecutor.getEntity() == null) {
			throw new IllegalArgumentException("unique judge entity object is null,please check!");
		}
		try {
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, uniqueExecutor.getEntity(),
					false, dataSource);
			SqlExecuteStat.start(BeanUtil.getEntityClass(uniqueExecutor.getEntity().getClass()).getName(), "isUnique",
					null);
			Boolean isUnique = (Boolean) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							this.setResult(getDialectSqlWrapper(dbType).isUnique(sqlToyContext,
									uniqueExecutor.getEntity(), uniqueExecutor.getUniqueFields(), conn, dbType,
									shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("查询结果", "唯一性验证返回结果={}!", isUnique);
			return isUnique;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 取随机记录
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param randomCount
	 * @param dataSource
	 * @return
	 */
	public QueryResult getRandomResult(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final Double randomCount, final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		if (extend.sql == null) {
			throw new IllegalArgumentException("getRandomResult operate sql is null!");
		}
		try {
			extend.optimizeArgs(sqlToyConfig);
			SqlExecuteStat.start(sqlToyConfig.getId(), "getRandomResult", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							// 最后一个参数应该为false,因为取随机记录的条数值是直接带入sql中的，而非变量(这里暂缓修改并无影响)
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, true);
							// 合理的写法如下
							// SqlToyConfig realSqlToyConfig
							// =DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,sqlToyConfig,
							// queryExecutor, dialect, false);
							// 判断数据库是否支持取随机记录(只有informix和sybase不支持)
							Long totalCount = SqlToyConstants.randomWithDialect(dbType) ? null
									: getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType,
											dialect);
							Long randomCnt;
							// 记录数量大于1表示取随机记录数量
							if (randomCount >= 1) {
								randomCnt = randomCount.longValue();
							}
							// 按比例提取
							else {
								// 提取总记录数
								if (totalCount == null) {
									totalCount = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn,
											dbType, dialect);
								}

								randomCnt = Double.valueOf(totalCount * randomCount.doubleValue()).longValue();
								SqlExecuteStat.debug("过程提示", "按比例提取总记录数:{}条,需取随机记录:{}条!", totalCount, randomCnt);
								// 如果总记录数不为零，randomCnt最小为1
								if (totalCount >= 1 && randomCnt < 1) {
									randomCnt = 1L;
								}
							}
							// 总记录数为零(主要针对sybase & informix 数据库)
							if (totalCount != null && totalCount == 0) {
								this.setResult(new QueryResult());
								logger.warn("getRandom,total Records is zero,please check sql!sqlId={}",
										sqlToyConfig.getIdOrSql());
								return;
							}
							QueryResult queryResult = getDialectSqlWrapper(dbType).getRandomResult(sqlToyContext,
									realSqlToyConfig, queryExecutor, totalCount, randomCnt, conn, dbType, dialect);

							// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
							List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
									queryExecutor, conn, dbType, dialect);
							ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, extend);
							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) extend.resultType));
							}
							SqlExecuteStat.debug("查询结果", "取得随机记录数:{}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 构造树形表的节点路径、节点层级、节点类别(是否叶子节点)
	 * @param sqlToyContext
	 * @param treeModel
	 * @param dataSource
	 * @return
	 */
	public boolean wrapTreeTableRoute(final SqlToyContext sqlToyContext, final TreeTableModel treeModel,
			final DataSource dataSource) {
		if (treeModel == null || StringUtil.isBlank(treeModel.getPidField())) {
			throw new IllegalArgumentException("请检查pidField赋值是否正确!");
		}
		if (StringUtil.isBlank(treeModel.getLeafField()) || StringUtil.isBlank(treeModel.getNodeRouteField())
				|| StringUtil.isBlank(treeModel.getNodeLevelField())) {
			throw new IllegalArgumentException("请检查isLeafField\nodeRouteField\nodeLevelField 赋值是否正确!");
		}
		try {
			if (null != treeModel.getEntity()) {
				EntityMeta entityMeta = null;
				if (treeModel.getEntity() instanceof Type) {
					entityMeta = sqlToyContext.getEntityMeta((Class) treeModel.getEntity());
				} else {
					entityMeta = sqlToyContext.getEntityMeta(treeModel.getEntity().getClass());
				}
				// 兼容填写fieldName,统一转化为columnName
				// pid
				String columnName = entityMeta.getColumnName(treeModel.getPidField());
				if (columnName != null) {
					treeModel.pidField(columnName);
				}
				// leafField
				columnName = entityMeta.getColumnName(treeModel.getLeafField());
				if (columnName != null) {
					treeModel.isLeafField(columnName);
				}
				// nodeLevel
				columnName = entityMeta.getColumnName(treeModel.getNodeLevelField());
				if (columnName != null) {
					treeModel.nodeLevelField(columnName);
				}
				// nodeRoute
				columnName = entityMeta.getColumnName(treeModel.getNodeRouteField());
				if (columnName != null) {
					treeModel.nodeRouteField(columnName);
				}
				HashMap<String, String> columnMap = new HashMap<String, String>();
				for (FieldMeta column : entityMeta.getFieldsMeta().values()) {
					columnMap.put(column.getColumnName().toUpperCase(), "");
				}
				if (!columnMap.containsKey(treeModel.getNodeRouteField().toUpperCase())) {
					throw new IllegalArgumentException("树形表:节点路径字段名称:" + treeModel.getNodeRouteField() + "不正确,请检查!");
				}
				if (!columnMap.containsKey(treeModel.getLeafField().toUpperCase())) {
					throw new IllegalArgumentException("树形表:是否叶子节点字段名称:" + treeModel.getLeafField() + "不正确,请检查!");
				}
				if (!columnMap.containsKey(treeModel.getNodeLevelField().toUpperCase())) {
					throw new IllegalArgumentException("树形表:节点等级字段名称:" + treeModel.getNodeLevelField() + "不正确,请检查!");
				}

				FieldMeta idMeta = (FieldMeta) entityMeta.getFieldMeta(entityMeta.getIdArray()[0]);
				// 如未定义则使用主键(update 2020-10-16)
				if (StringUtil.isBlank(treeModel.getIdField())) {
					treeModel.idField(idMeta.getColumnName());
				} else {
					// 别名转换
					columnName = entityMeta.getColumnName(treeModel.getIdField());
					if (columnName != null) {
						treeModel.idField(columnName);
					}
				}
				treeModel.table(entityMeta.getTableName());
				// 通过实体对象取值给rootId和idValue赋值
				if (!(treeModel.getEntity() instanceof Type)) {
					// update 2020-10-19 从手工设定的字段中取值(原本从主键中取值)
					if (null == treeModel.getRootId()) {
						Object pidValue = BeanUtil.getProperty(treeModel.getEntity(),
								StringUtil.toHumpStr(treeModel.getPidField(), false));
						treeModel.rootId(pidValue);
					}
					if (null == treeModel.getIdValue()) {
						Object idValue = BeanUtil.getProperty(treeModel.getEntity(),
								StringUtil.toHumpStr(treeModel.getIdField(), false));
						treeModel.setIdValue(idValue);
					}
				}
				// 类型,默认值为false
				if (idMeta.getType() == java.sql.Types.INTEGER || idMeta.getType() == java.sql.Types.DECIMAL
						|| idMeta.getType() == java.sql.Types.DOUBLE || idMeta.getType() == java.sql.Types.FLOAT
						|| idMeta.getType() == java.sql.Types.NUMERIC) {
					treeModel.idTypeIsChar(false);
					// update 2016-12-05 节点路径默认采取主键值直接拼接,更加直观科学
					// treeModel.setAppendZero(true);
				} else if (idMeta.getType() == java.sql.Types.VARCHAR || idMeta.getType() == java.sql.Types.CHAR) {
					treeModel.idTypeIsChar(true);
				}
			}
			SqlExecuteStat.start(treeModel.getTableName(), "wrapTreeTableRoute", true);
			return (Boolean) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							this.setResult(SqlUtil.wrapTreeTableRoute(treeModel, conn, dbType));
						}
					});
		} catch (Exception e) {
			logger.error("封装树形表节点路径操作:wrapTreeTableRoute发生错误,{}", e.getMessage());
			e.printStackTrace();
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @TODO 跳过查询总记录的分页查询,提供给特殊的场景，尤其是移动端滚屏模式
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param pageNo
	 * @param pageSize
	 * @param dataSource
	 * @return
	 */
	public QueryResult findSkipTotalCountPage(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final long pageNo, final Integer pageSize, final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		if (StringUtil.isBlank(extend.sql)) {
			throw new IllegalArgumentException("findSkipTotalCountPage operate sql is null!");
		}
		// 页数必须要大于等于1，pageSize必须要大于1
		if (pageNo < 1 || pageSize < 1) {
			throw new IllegalArgumentException(
					"findSkipTotalCountPage operate  pageSize:" + pageSize + "<1 or pageNo:" + pageNo + " < 1!");
		}
		int limitSize = sqlToyContext.getPageFetchSizeLimit();
		// 分页查询不允许单页数据超过上限，避免大规模数据提取
		if (pageSize >= limitSize) {
			throw new IllegalArgumentException("findSkipTotalCountPage operate args is Illegal,pageSize={" + pageSize
					+ "}>= limit:{" + limitSize + "}!");
		}
		try {
			extend.optimizeArgs(sqlToyConfig);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findSkipTotalCountPage", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, true);
							QueryResult queryResult = getDialectSqlWrapper(dbType).findPageBySql(sqlToyContext,
									realSqlToyConfig, queryExecutor, pageNo, pageSize, conn, dbType, dialect);
							queryResult.setPageNo(pageNo);
							queryResult.setPageSize(pageSize);
							// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
							List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
									queryExecutor, conn, dbType, dialect);
							ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, extend);
							// 结果映射成对象
							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) extend.resultType));
							}
							queryResult.setSkipQueryCount(true);
							SqlExecuteStat.debug("查询结果", "分页查询出记录数量:{}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 分页查询, pageNo为负一表示取全部记录
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param pageNo
	 * @param pageSize
	 * @param dataSource
	 * @return
	 */
	public QueryResult findPage(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final long pageNo, final Integer pageSize, final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		if (StringUtil.isBlank(extend.sql)) {
			throw new IllegalArgumentException("findPage operate sql is null!");
		}
		try {
			extend.optimizeArgs(sqlToyConfig);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findPage", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, true);
							QueryResult queryResult = null;
							PageOptimize pageOptimize = extend.pageOptimize;
							if (pageOptimize == null) {
								pageOptimize = realSqlToyConfig.getPageOptimize();
							}
							Long recordCnt = null;
							// 通过查询条件构造唯一的key
							String pageQueryKey = PageOptimizeUtils.generateOptimizeKey(sqlToyContext, sqlToyConfig,
									queryExecutor, pageOptimize);
							// 需要进行分页查询优化
							if (null != pageQueryKey) {
								// 从缓存中提取总记录数
								recordCnt = PageOptimizeUtils.getPageTotalCount(sqlToyConfig, pageOptimize,
										pageQueryKey);
								// 缓存中没有则重新查询
								if (null == recordCnt) {
									recordCnt = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn,
											dbType, dialect);
									// 将总记录数登记到缓存
									PageOptimizeUtils.registPageTotalCount(sqlToyConfig, pageOptimize, pageQueryKey,
											recordCnt);
								} else {
									SqlExecuteStat.debug("过程提示", "分页优化条件命中,从缓存中获得总记录数:{}!!", recordCnt);
								}
							} else {
								recordCnt = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType,
										dialect);
							}
							// pageNo=-1时的提取数据量限制
							int limitSize = sqlToyContext.getPageFetchSizeLimit();
							// pageNo=-1时,总记录数超出限制则返回空集合
							boolean illegal = (pageNo == -1 && (limitSize != -1 && recordCnt > limitSize));
							if (recordCnt == 0 || illegal) {
								queryResult = new QueryResult();
								queryResult.setPageNo(pageNo);
								queryResult.setPageSize(pageSize);
								queryResult.setRecordCount(0L);
								if (illegal) {
									logger.warn(
											"非法分页查询,提取记录总数为:{}>{}上限(可设置sqlToyContext中的pageFetchSizeLimit进行调整),sql={}",
											recordCnt, limitSize, sqlToyConfig.getIdOrSql());
								} else {
									SqlExecuteStat.debug("过程提示", "提取count数为:0,sql={}", sqlToyConfig.getIdOrSql());
								}
							} else {
								// 合法的全记录提取,设置页号为1按记录数
								if (pageNo == -1) {
									// 通过参数处理最终的sql和参数值
									SqlToyResult queryParam = SqlConfigParseUtils.processSql(
											realSqlToyConfig.getSql(dialect), extend.getParamsName(realSqlToyConfig),
											extend.getParamsValue(sqlToyContext, realSqlToyConfig));
									queryResult = getDialectSqlWrapper(dbType).findBySql(sqlToyContext,
											realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
											extend.rowCallbackHandler, conn, null, dbType, dialect, extend.fetchSize,
											extend.maxRows);
									long totalRecord = (queryResult.getRows() == null) ? 0
											: queryResult.getRows().size();
									queryResult.setPageNo(1L);
									queryResult.setPageSize(Long.valueOf(totalRecord).intValue());
									queryResult.setRecordCount(totalRecord);
								} else {
									// 实际开始页(页数据超出总记录,则从第一页重新开始,相反如继续按指定的页查询则记录为空,且实际页号也不存在)
									long realStartPage = (pageNo * pageSize >= (recordCnt + pageSize)) ? 1 : pageNo;
									queryResult = getDialectSqlWrapper(dbType).findPageBySql(sqlToyContext,
											realSqlToyConfig, queryExecutor, realStartPage, pageSize, conn, dbType,
											dialect);
									queryResult.setPageNo(realStartPage);
									queryResult.setPageSize(pageSize);
									queryResult.setRecordCount(recordCnt);
								}
								// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
								List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
										queryExecutor, conn, dbType, dialect);
								ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, extend);
								// 结果映射成对象
								if (extend.resultType != null) {
									queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
											ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
											(Class) extend.resultType));
								}
							}
							SqlExecuteStat.debug("查询结果", "分页总记录数:{}条,取得本页记录数:{}条!",
									((QueryResult) queryResult).getRecordCount(),
									((QueryResult) queryResult).getRows().size());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 取符合条件的前多少条记录
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param topSize
	 * @param dataSource
	 * @return
	 */
	public QueryResult findTop(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final double topSize, final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 合法校验
		if (StringUtil.isBlank(extend.sql)) {
			throw new IllegalArgumentException("findTop operate sql is null!");
		}
		try {
			extend.optimizeArgs(sqlToyConfig);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findTop", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							Integer realTopSize;
							if (topSize < 1) {
								Long totalCount = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn,
										dbType, dialect);

								realTopSize = Double.valueOf(topSize * totalCount.longValue()).intValue();
								SqlExecuteStat.debug("过程提示", "按比例提取,总记录数:{}条,按比例top记录要取:{} 条!", totalCount,
										realTopSize);
							} else {
								realTopSize = Double.valueOf(topSize).intValue();
							}
							if (realTopSize == 0) {
								this.setResult(new QueryResult());
								SqlExecuteStat.debug("查询结果", "实际取得top记录数:0 条!");
								return;
							}

							QueryResult queryResult = getDialectSqlWrapper(dbType).findTopBySql(sqlToyContext,
									realSqlToyConfig, queryExecutor, realTopSize, conn, dbType, dialect);
							// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
							List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
									queryExecutor, conn, dbType, dialect);
							ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, extend);
							// 结果映射成对象
							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) extend.resultType));
							}
							SqlExecuteStat.debug("查询结果", "实际取得top记录数: {}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 查询符合条件的数据集合
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param lockMode
	 * @param dataSource
	 * @return
	 */
	public QueryResult findByQuery(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final LockMode lockMode, final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 合法校验
		if (StringUtil.isBlank(extend.sql)) {
			throw new IllegalArgumentException("findByQuery operate sql is null!");
		}
		try {
			extend.optimizeArgs(sqlToyConfig);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findByQuery", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							// 通过参数处理最终的sql和参数值
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(realSqlToyConfig),
									extend.getParamsValue(sqlToyContext, realSqlToyConfig));
							QueryResult queryResult = getDialectSqlWrapper(dbType).findBySql(sqlToyContext,
									realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
									extend.rowCallbackHandler, conn, lockMode, dbType, dialect, extend.fetchSize,
									extend.maxRows);
							// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
							List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
									queryExecutor, conn, dbType, dialect);
							ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, extend);
							// 结果映射成对象、map等
							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) extend.resultType));
							}
							SqlExecuteStat.debug("查询结果", "共查询出记录数={}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 查询符合条件的记录数量
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param dataSource
	 * @return
	 */
	public Long getCountBySql(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		if (StringUtil.isBlank(extend.sql)) {
			throw new IllegalArgumentException("getCountBySql operate sql is null!");
		}
		try {
			extend.optimizeArgs(sqlToyConfig);
			SqlExecuteStat.start(sqlToyConfig.getId(), "getCountBySql", sqlToyConfig.isShowSql());
			Long count = (Long) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							this.setResult(getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType,
									dialect));
						}
					});
			SqlExecuteStat.debug("查询结果", "count查询结果={}!", count);
			return count;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 获取记录总数
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	private Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final QueryExecutor queryExecutor, final Connection conn, Integer dbType, String dialect) throws Exception {
		String sql;
		boolean isLastSql = false;
		// 是否自定义了count sql语句(直接定义了则跳过各种优化处理)
		String tmp = sqlToyConfig.getCountSql(dialect);
		if (tmp != null) {
			sql = tmp;
			isLastSql = true;
		} else {
			// 是否是select * from @fast(select * from xxx where xxx) t1 left join xx 模式
			if (!sqlToyConfig.isHasFast()) {
				sql = sqlToyConfig.getSql(dialect);
			} else {
				String fastWithSql = sqlToyConfig.getFastWithSql(dialect);
				sql = (fastWithSql == null ? "" : fastWithSql).concat(" ").concat(sqlToyConfig.getFastSql(dialect));
			}
			String rejectWithSql = sql;
			String withSql = "";
			boolean hasUnion = false;
			// update 2019-07-23 进行先判定然后再通过逻辑解析with as 相关语法,提升效率
			// 存在可以简化的 union all 模式(sql xml 文件通过union-all-count 属性由开发者指定)
			if (sqlToyConfig.isHasUnion() && sqlToyConfig.isUnionAllCount()) {
				if (sqlToyConfig.isHasWith()) {
					SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
					rejectWithSql = sqlWith.getRejectWithSql();
					withSql = sqlWith.getWithSql();
				}
				hasUnion = SqlUtil.hasUnion(rejectWithSql, false);
			}
			// 判定union all并且可以进行union all简化处理(sql文件中进行配置)
			if (hasUnion && StringUtil.matches(rejectWithSql, SqlToyConstants.UNION_ALL_REGEX)) {
				isLastSql = true;
				String[] unionSqls = rejectWithSql.split(SqlToyConstants.UNION_ALL_REGEX);
				StringBuilder countSql = new StringBuilder();
				countSql.append(withSql);
				countSql.append(" select sum(row_count) from (");
				int sql_from_index;
				int unionSqlSize = unionSqls.length;
				for (int i = 0; i < unionSqlSize; i++) {
					sql_from_index = StringUtil.getSymMarkMatchIndex("(?i)select\\s+", "(?i)\\s+from[\\(\\s+]",
							unionSqls[i], 0);
					countSql.append(" select count(1) row_count ")
							.append((sql_from_index != -1 ? unionSqls[i].substring(sql_from_index) : unionSqls[i]));
					if (i < unionSqlSize - 1) {
						countSql.append(" union all ");
					}
				}
				countSql.append(" ) ");
				sql = countSql.toString();
			}
		}
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 通过参数处理最终的sql和参数值
		SqlToyResult queryParam = SqlConfigParseUtils.processSql(sql, extend.getParamsName(sqlToyConfig),
				extend.getParamsValue(sqlToyContext, sqlToyConfig));
		return getDialectSqlWrapper(dbType).getCountBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(),
				queryParam.getParamsValue(), isLastSql, conn, dbType, dialect);
	}

	// mysql、postgresql、sqlite等类似的on duplicate key update
	// 存在缺陷,所以改为先update后save;但oracle、mssql、db2等可以用merge实现一次交互完成新增和修改
	/**
	 * @todo 保存或修改单个对象(数据库单条记录)
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateProps
	 * @param dataSource
	 * @return
	 */
	public Long saveOrUpdate(final SqlToyContext sqlToyContext, final Serializable entity,
			final String[] forceUpdateProps, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("saveOrUpdate entity is null,please check!");
			return 0L;
		}
		try {
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, true, dataSource);
			SqlExecuteStat.start(entity.getClass().getName(), "saveOrUpdate", null);
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							this.setResult(getDialectSqlWrapper(dbType).saveOrUpdate(sqlToyContext, entity,
									forceUpdateProps, conn, dbType, dialect, null, shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("执行结果", "实际影响记录数量:{} 条!", updateTotalCnt);
			return updateTotalCnt;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 批量保存或修改数据
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @param dataSource
	 * @param autoCommit
	 */
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final String[] forceUpdateProps, final ReflectPropertyHandler reflectPropertyHandler,
			final DataSource dataSource, final Boolean autoCommit) {
		// 前置输入合法校验
		if (entities == null || entities.isEmpty()) {
			logger.warn("saveOrUpdateAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			// 启动执行日志
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"saveOrUpdateAll:[" + entities.size() + "]条记录!", null);
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).saveOrUpdateAll(context,
												batchModel.getEntities(), batchSize, reflectPropertyHandler,
												forceUpdateProps, conn, dbType, dialect, autoCommit,
												shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					});
			long updateTotalCnt = 0;
			if (result != null) {
				for (Long cnt : result) {
					updateTotalCnt = updateTotalCnt + cnt.longValue();
				}
			}
			// 输出修改记录量日志
			SqlExecuteStat.debug("执行结果", "实际影响记录数量:{} 条!", updateTotalCnt);
			return Long.valueOf(updateTotalCnt);
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			// 最终输出执行失效和错误日志
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 批量保存数据，当已经存在的时候忽视掉
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param dataSource
	 * @param autoCommit
	 */
	public Long saveAllIgnoreExist(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource,
			final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("saveAllIgnoreExist entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"saveAllNotExist:[" + entities.size() + "]条记录!", null);
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).saveAllIgnoreExist(context,
												batchModel.getEntities(), batchSize, reflectPropertyHandler, conn,
												dbType, dialect, autoCommit, shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					});
			long updateTotalCnt = 0;
			if (result != null) {
				for (Long cnt : result) {
					updateTotalCnt = updateTotalCnt + cnt.longValue();
				}
			}
			SqlExecuteStat.debug("执行结果", "实际影响记录数量:{} 条!", updateTotalCnt);
			return Long.valueOf(updateTotalCnt);
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 加载单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param cascadeTypes
	 * @param lockMode
	 * @param dataSource
	 * @return
	 */
	public <T extends Serializable> T load(final SqlToyContext sqlToyContext, final T entity,
			final Class[] cascadeTypes, final LockMode lockMode, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("load entity is null,please check!");
			return null;
		}
		try {
			// 单记录操作返回对应的库和表配置
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "load", null);
			return (T) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							this.setResult(getDialectSqlWrapper(dbType).load(sqlToyContext, entity,
									(cascadeTypes == null) ? null : CollectionUtil.arrayToList(cascadeTypes), lockMode,
									conn, dbType, dialect, shardingModel.getTableName()));
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 批量加载集合(自4.13.1 版本已经自动将超大规模集合拆分执行)
	 * @param sqlToyContext
	 * @param entities
	 * @param cascadeTypes
	 * @param lockMode
	 * @param dataSource
	 * @return
	 */
	public <T extends Serializable> List<T> loadAll(final SqlToyContext sqlToyContext, final List<T> entities,
			final Class[] cascadeTypes, final LockMode lockMode, final DataSource dataSource) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("loadAll entities is null or empty,please check!");
			return entities;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"loadAll:[" + entities.size() + "]条记录!", null);
			// 一般in的最大数量是1000
			int batchSize = SqlToyConstants.getLoadAllBatchSize();
			// 对可能存在的配置参数定义错误进行校正,最大控制在1000内
			if (batchSize > 1000 || batchSize < 1) {
				batchSize = 1000;
			}
			int totalSize = entities.size();
			int batch = (totalSize + batchSize - 1) / batchSize;
			List result = new ArrayList();
			List batchEntities;
			for (int i = 0; i < batch; i++) {
				// 切取单个批次的记录
				batchEntities = entities.subList(i * batchSize, (i == batch - 1) ? totalSize : (i + 1) * batchSize);
				// 分库分表并行执行,并返回结果
				result.addAll(ParallelUtils.execute(sqlToyContext, batchEntities, false, dataSource,
						(context, batchModel) -> {
							ShardingModel shardingModel = batchModel.getShardingModel();
							return (List) DataSourceUtils.processDataSource(context, shardingModel.getDataSource(),
									new DataSourceCallbackHandler() {
										public void doConnection(Connection conn, Integer dbType, String dialect)
												throws Exception {
											this.setResult(getDialectSqlWrapper(dbType).loadAll(context,
													batchModel.getEntities(),
													(cascadeTypes == null) ? null
															: CollectionUtil.arrayToList(cascadeTypes),
													lockMode, conn, dbType, dialect, shardingModel.getTableName()));
										}
									});
						}));
			}
			SqlExecuteStat.debug("执行结果", "查询结果记录:{} 条!", result.size());
			return result;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 保存单个对象记录
	 * @param sqlToyContext
	 * @param entity
	 * @param dataSource
	 * @return
	 */
	public Serializable save(final SqlToyContext sqlToyContext, final Serializable entity,
			final DataSource dataSource) {
		if (entity == null) {
			logger.warn("save entity is null,please check!");
			return null;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "save", null);
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, true, dataSource);
			Serializable result = (Serializable) DataSourceUtils.processDataSource(sqlToyContext,
					shardingModel.getDataSource(), new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							this.setResult(getDialectSqlWrapper(dbType).save(sqlToyContext, entity, conn, dbType,
									dialect, shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("执行结果", "单对象保存返回主键值:{}", result);
			return result;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 批量保存
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param dataSource
	 * @param autoCommit
	 */
	public Long saveAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource,
			final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("saveAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"saveAll:[" + entities.size() + "]条记录!", null);
			// 分库分表并行执行
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).saveAll(context,
												batchModel.getEntities(), batchSize, reflectPropertyHandler, conn,
												dbType, dialect, autoCommit, shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					});
			long updateTotalCnt = 0;
			if (result != null) {
				for (Long cnt : result) {
					updateTotalCnt = updateTotalCnt + cnt.longValue();
				}
			}
			SqlExecuteStat.debug("执行结果", "批量保存记录量:{}条!", updateTotalCnt);
			return Long.valueOf(updateTotalCnt);
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 修改单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateFields
	 * @param cascade
	 * @param forceCascadeClass
	 * @param subTableForceUpdateProps
	 * @param dataSource
	 */
	public Long update(final SqlToyContext sqlToyContext, final Serializable entity, final String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClass,
			final HashMap<Class, String[]> subTableForceUpdateProps, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("update entity is null,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "update", null);
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							this.setResult(getDialectSqlWrapper(dbType).update(sqlToyContext, entity, forceUpdateFields,
									cascade, forceCascadeClass, subTableForceUpdateProps, conn, dbType, dialect,
									shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("执行结果", "update操作影响记录量:{} 条!", updateTotalCnt);
			return updateTotalCnt;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 批量修改对象
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param forceUpdateFields
	 * @param reflectPropertyHandler
	 * @param dataSource
	 * @param autoCommit
	 */
	public Long updateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final String[] forceUpdateFields, final ReflectPropertyHandler reflectPropertyHandler,
			final DataSource dataSource, final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("updateAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"updateAll:[" + entities.size() + "]条记录!", null);
			// 分库分表并行执行
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, false, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).updateAll(context,
												batchModel.getEntities(), batchSize, forceUpdateFields,
												reflectPropertyHandler, conn, dbType, dialect, autoCommit,
												shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					});
			long updateTotalCnt = 0;
			if (result != null) {
				for (Long cnt : result) {
					updateTotalCnt = updateTotalCnt + cnt.longValue();
				}
			}
			SqlExecuteStat.debug("执行结果", "批量更新影响记录量:{} 条!", updateTotalCnt);
			return Long.valueOf(updateTotalCnt);
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 删除单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param dataSource
	 */
	public Long delete(final SqlToyContext sqlToyContext, final Serializable entity, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("delete entity is null,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "delete", null);
			// 获取分库分表策略结果
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							this.setResult(getDialectSqlWrapper(dbType).delete(sqlToyContext, entity, conn, dbType,
									dialect, shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("执行结果", "删除操作影响记录量:{} 条!", updateTotalCnt);
			return updateTotalCnt;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 批量删除对象
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param dataSource
	 * @param autoCommit
	 */
	public <T extends Serializable> Long deleteAll(final SqlToyContext sqlToyContext, final List<T> entities,
			final int batchSize, final DataSource dataSource, final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("deleteAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"deleteAll:[" + entities.size() + "]条记录!", null);
			// 分库分表并行执行
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, false, dataSource,
					(context, batchModel) -> {
						final ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).deleteAll(context,
												batchModel.getEntities(), batchSize, conn, dbType, dialect, autoCommit,
												shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					});
			long updateTotalCnt = 0;
			if (result != null) {
				for (Long cnt : result) {
					updateTotalCnt = updateTotalCnt + cnt.longValue();
				}
			}
			SqlExecuteStat.debug("执行结果", "批量删除操作影响记录量:{} 条!", updateTotalCnt);
			return Long.valueOf(updateTotalCnt);
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 查询锁定记录,并进行修改
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param updateRowHandler
	 * @param dataSource
	 * @return
	 */
	public QueryResult updateFetch(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final UpdateRowHandler updateRowHandler, final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		extend.optimizeArgs(sqlToyConfig);
		try {
			SqlExecuteStat.start(sqlToyConfig.getId(), "updateFetch", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(realSqlToyConfig),
									extend.getParamsValue(sqlToyContext, realSqlToyConfig));
							QueryResult queryResult = getDialectSqlWrapper(dbType).updateFetch(sqlToyContext,
									realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
									updateRowHandler, conn, dbType, dialect);

							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) extend.resultType));
							}
							SqlExecuteStat.debug("执行结果", "修改并返回记录操作影响记录:{} 条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	@Deprecated
	public QueryResult updateFetchTop(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final Integer topSize, final UpdateRowHandler updateRowHandler,
			final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		extend.optimizeArgs(sqlToyConfig);
		try {
			SqlExecuteStat.start(sqlToyConfig.getId(), "updateFetchTop", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);

							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(realSqlToyConfig),
									extend.getParamsValue(sqlToyContext, realSqlToyConfig));
							QueryResult queryResult = getDialectSqlWrapper(dbType).updateFetchTop(sqlToyContext,
									sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(), topSize,
									updateRowHandler, conn, dbType, dialect);

							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) extend.resultType));
							}
							SqlExecuteStat.debug("执行结果", "修改并返回记录操作影响记录:{} 条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	@Deprecated
	public QueryResult updateFetchRandom(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final Integer random, final UpdateRowHandler updateRowHandler,
			final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		extend.optimizeArgs(sqlToyConfig);
		try {
			SqlExecuteStat.start(sqlToyConfig.getId(), "updateFetchRandom", sqlToyConfig.isShowSql());
			return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							// 处理sql中的?为统一的:named形式
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(realSqlToyConfig),
									extend.getParamsValue(sqlToyContext, realSqlToyConfig));

							QueryResult queryResult = getDialectSqlWrapper(dbType).updateFetchRandom(sqlToyContext,
									realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(), random,
									updateRowHandler, conn, dbType, dialect);
							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) extend.resultType));
							}
							SqlExecuteStat.debug("执行结果", "修改并返回记录操作影响记录:{} 条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @todo 存储过程调用
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param inParamsValue
	 * @param outParamsType
	 * @param resultType
	 * @param dataSource
	 * @return
	 */
	public StoreResult executeStore(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final Object[] inParamsValue, final Integer[] outParamsType, final Class resultType,
			final DataSource dataSource) {
		try {
			SqlExecuteStat.start(sqlToyConfig.getId(), "executeStore", sqlToyConfig.isShowSql());
			return (StoreResult) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
					new DataSourceCallbackHandler() {
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							String dialectSql = sqlToyConfig.getSql(dialect);
							int inCount = (inParamsValue == null) ? 0 : inParamsValue.length;
							int outCount = (outParamsType == null) ? 0 : outParamsType.length;
							// sql中问号数量
							int paramCnt = StringUtil.matchCnt(dialectSql, ARG_PATTERN);
							// 处理参数注入
							if (paramCnt != inCount + outCount) {
								throw new IllegalArgumentException("存储过程语句中的输入和输出参数跟实际调用传递的数量不等!");
							}

							SqlToyResult sqlToyResult = new SqlToyResult(dialectSql, inParamsValue);
							// 判断是否是{?=call xxStore()} 模式(oracle 不支持此模式)
							boolean isFirstResult = StringUtil.matches(dialectSql, STORE_PATTERN);
							/*
							 * 将call xxxStore(?,?) 后的条件参数判断是否为null，如果是null则改为call xxxStore(null,?,null)
							 * 避免设置类型错误
							 */
							SqlConfigParseUtils.replaceNull(sqlToyResult, isFirstResult ? 1 : 0);
							// 针对不同数据库执行存储过程调用
							SqlExecuteStat.showSql("存储过程执行", sqlToyResult.getSql(), sqlToyResult.getParamsValue());
							StoreResult queryResult = getDialectSqlWrapper(dbType).executeStore(sqlToyContext,
									sqlToyConfig, sqlToyResult.getSql(), sqlToyResult.getParamsValue(), outParamsType,
									conn, dbType, dialect);
							// 进行数据必要的数据处理(一般存储过程不会结合旋转sql进行数据旋转操作)
							// {此区域代码正常情况下不会使用
							QueryExecutor queryExecutor = new QueryExecutor(null, sqlToyConfig.getParamsName(),
									inParamsValue);
							List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, sqlToyConfig,
									queryExecutor, conn, dbType, dialect);
							ResultUtils.calculate(sqlToyConfig, queryResult, pivotCategorySet, null);
							// }
							// 映射成对象
							if (resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										resultType));
							}
							SqlExecuteStat.debug("执行结果", "存储过程影响记录:{} 条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}
}
