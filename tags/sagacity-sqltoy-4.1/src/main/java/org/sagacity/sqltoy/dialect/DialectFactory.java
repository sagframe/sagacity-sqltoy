/**
 * 
 */
package org.sagacity.sqltoy.dialect;

import static java.lang.System.out;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.ParallelCallbackHandler;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.SqlParamsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.dialect.impl.DB2Dialect;
import org.sagacity.sqltoy.dialect.impl.MySqlDialect;
import org.sagacity.sqltoy.dialect.impl.Oracle12Dialect;
import org.sagacity.sqltoy.dialect.impl.OracleDialect;
import org.sagacity.sqltoy.dialect.impl.PostgreSqlDialect;
import org.sagacity.sqltoy.dialect.impl.SqlServerDialect;
import org.sagacity.sqltoy.dialect.impl.SqliteDialect;
import org.sagacity.sqltoy.dialect.impl.SybaseIQDialect;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.exception.BaseException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.executor.UniqueExecutor;
import org.sagacity.sqltoy.lock.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.ShardingGroupModel;
import org.sagacity.sqltoy.model.ShardingModel;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.parallel.ParallelUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.ShardingUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 根据不同数据库类型提取不同数据库的处理handler,避免2.0之前版本:一个功能不同数据库写在一个方法中的弊端,
 *              每次修改容易导致产生连锁反应,分不同数据库方言虽然代码量多了一些，但可读性和可维护性变得更强
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DialectFactory.java,Revision:v1.0,Date:2014年12月11日
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DialectFactory {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LogManager.getLogger(getClass());

	/**
	 * 不同数据库方言的处理器实例
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
		if (dialects.containsKey(dbType))
			return dialects.get(dbType);
		else {
			// 按照市场排名作为优先顺序
			Dialect dialectSqlWrapper = null;
			switch (dbType) {
			// 10g,11g
			case DBType.ORACLE:
				dialectSqlWrapper = new OracleDialect();
				break;
			// oracle12c(分页方式有了改变,支持identity主键策略(内部其实还是sequence模式))
			case DBType.ORACLE12:
				dialectSqlWrapper = new Oracle12Dialect();
				break;
			// 5.6+(mysql 的缺陷主要集中在不支持with as以及临时表不同在一个查询中多次引用)
			// 8.x+(支持with as语法)
			// MariaDB 在检测的时候归并到mysql,采用跟mysql一样的语法
			case DBType.MYSQL:
			case DBType.MYSQL8:
				dialectSqlWrapper = new MySqlDialect();
				break;
			// sqlserver2012 以后分页方式更简单
			case DBType.SQLSERVER:
			case DBType.SQLSERVER2014:
			case DBType.SQLSERVER2016:
			case DBType.SQLSERVER2017:
				dialectSqlWrapper = new SqlServerDialect();
				break;
			// 9.5+(9.5开始支持类似merge into形式的语法,参见具体实现)
			case DBType.POSTGRESQL:
			case DBType.POSTGRESQL10:
				dialectSqlWrapper = new PostgreSqlDialect();
				break;
			// db2 10.x版本分页支持offset模式
			case DBType.DB2:
			case DBType.DB2_11:
				dialectSqlWrapper = new DB2Dialect();
				break;
			// 15.4+(必须采用15.4,最好采用16.0 并打上最新的补丁),15.4 之后的分页支持limit模式
			case DBType.SYBASE_IQ:
				dialectSqlWrapper = new SybaseIQDialect();
				break;
			// 基本支持(sqlite 本身功能就相对简单)
			case DBType.SQLITE:
				dialectSqlWrapper = new SqliteDialect();
				break;
			// 如果匹配不上使用mysql类型
			default:
				dialectSqlWrapper = new MySqlDialect();
				break;
			}
			dialects.put(dbType, dialectSqlWrapper);
			return dialectSqlWrapper;
		}
	}

	/**
	 * @todo 批量执行sql修改或删除操作
	 * @param sqlToyContext
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param insertCallhandler
	 * @param autoCommit
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public Long batchUpdate(final SqlToyContext sqlToyContext, final String sqlOrNamedSql, final List dataSet,
			final int batchSize, final ReflectPropertyHandler reflectPropertyHandler,
			final InsertRowCallbackHandler insertCallhandler, final Boolean autoCommit, final DataSource dataSource)
			throws Exception {
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrNamedSql, SqlType.update);
		return (Long) DataSourceUtils.processDataSource(sqlToyContext, dataSource, new DataSourceCallbackHandler() {
			public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
				String realSql = sqlToyContext.convertFunctions(sqlToyConfig.getSql(), dialect);
				Integer[] fieldTypes = null;
				List values = dataSet;
				// sql中存在:named参数模式，通过sql提取参数名称
				if (sqlToyConfig.getParamsName() != null) {
					// 替换sql中:name为?并提取参数名称归集成数组
					SqlParamsModel sqlParamsModel = SqlConfigParseUtils.processNamedParamsQuery(realSql);
					values = BeanUtil.reflectBeansToList(dataSet, sqlParamsModel.getParamsName(),
							reflectPropertyHandler, false, 0);
					fieldTypes = BeanUtil.matchMethodsType(dataSet.get(0).getClass(), sqlParamsModel.getParamsName());
					realSql = sqlParamsModel.getSql();
				}
				if (sqlToyContext.isDebug()) {
					out.println("=================batchUpdate执行的语句=================");
					out.println(" batchUpdate sql:" + realSql);
					out.println("======================================================");
				}
				this.setResult(SqlUtil.batchUpdateByJdbc(realSql, values, batchSize, insertCallhandler, fieldTypes,
						autoCommit, conn));
			}
		});
	}

	/**
	 * @todo 执行sql修改性质的操作语句
	 * @param sqlToyContext
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @param autoCommit
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public Long executeSql(final SqlToyContext sqlToyContext, final String sqlOrNamedSql, final String[] paramsNamed,
			final Object[] paramsValue, final Boolean autoCommit, final DataSource dataSource) throws Exception {
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrNamedSql, SqlType.update);
		return (Long) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig,
						new QueryExecutor(sqlOrNamedSql, paramsNamed, paramsValue), dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						SqlToyResult queryParam = SqlConfigParseUtils.processSql(
								sqlToyContext.convertFunctions(sqlToyConfig.getSql(), dialect), paramsNamed,
								paramsValue);
						String executeSql = queryParam.getSql();
						// 替换sharding table
						executeSql = ShardingUtils.replaceShardingTables(sqlToyContext, executeSql, sqlToyConfig,
								paramsNamed, paramsValue);
						if (sqlToyContext.isDebug()) {
							out.println("executeSql=" + executeSql);
							if (queryParam.getParamsValue() != null) {
								for (int i = 0; i < queryParam.getParamsValue().length; i++)
									out.println("paramValues[" + i + "]:" + queryParam.getParamsValue()[i] + ",");
							}
						}
						this.setResult(DialectUtils.executeSql(executeSql, queryParam.getParamsValue(), null, conn,
								autoCommit));
					}
				});
	}

	/**
	 * @todo 判定数据是否重复 true 表示唯一不重复；false 表示不唯一，即数据库中已经存在
	 * @param sqlToyContext
	 * @param uniqueExecutor
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public boolean isUnique(final SqlToyContext sqlToyContext, final UniqueExecutor uniqueExecutor,
			final DataSource dataSource) throws Exception {
		if (uniqueExecutor.getEntity() == null)
			throw new Exception("unique judge entity object is null,please check!");
		final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, uniqueExecutor.getEntity(), false,
				dataSource);
		return (Boolean) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(DialectUtils.isUnique(sqlToyContext, uniqueExecutor.getEntity(),
								uniqueExecutor.getUniqueFields(), conn, shardingModel.getTableName()));
					}
				});
	}

	/**
	 * @todo 取随机记录
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param randomCount
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public QueryResult getRandomResult(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final Double randomCount, final DataSource dataSource) throws Exception {
		if (queryExecutor.getSql() == null)
			throw new Exception("getRandomResult operate sql is null!");
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, true);
						// 判断数据库是否支持取随机记录(只有informix和sybase不支持)
						Long totalCount = SqlToyConstants.randomWithDialect(dbType) ? null
								: getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType, dialect);
						Long randomCnt;
						// 记录数量大于1表示取随机记录数量
						if (randomCount >= 1) {
							randomCnt = randomCount.longValue();
						}
						// 按比例提取
						else {
							// 提取总记录数
							if (totalCount == null) {
								totalCount = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType,
										dialect);
							}
							if (sqlToyContext.isDebug()) {
								out.println("getRandomResult按比例提取数据,总记录数=" + totalCount);
							}
							randomCnt = new Double(totalCount * randomCount.doubleValue()).longValue();
							// 如果总记录数不为零，randomCnt最小为1
							if (totalCount >= 1 && randomCnt < 1)
								randomCnt = 1L;
						}
						// 总记录数为零(主要针对sybase & informix 数据库)
						if (totalCount != null && totalCount == 0) {
							this.setResult(new QueryResult());
							logger.warn("getRandom,total Records is zero,please check sql!sqlId={}",
									sqlToyConfig.getId());
							return;
						}
						QueryResult queryResult = getDialectSqlWrapper(dbType).getRandomResult(sqlToyContext,
								realSqlToyConfig, queryExecutor, totalCount, randomCnt, conn);

						// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
						List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
								queryExecutor, conn, dialect);
						ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, sqlToyContext.isDebug());
						if (queryExecutor.getResultType() != null) {
							queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
									ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
									(Class) queryExecutor.getResultType()));
						}
						this.setResult(queryResult);
					}
				});
	}

	/**
	 * @todo 构造树形表的节点路径、节点层级、节点类别(是否叶子节点)
	 * @param sqlToyContext
	 * @param treeModel
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public boolean wrapTreeTableRoute(final SqlToyContext sqlToyContext, final TreeTableModel treeModel,
			final DataSource dataSource) throws Exception {
		if (null != treeModel.getEntity()) {
			EntityMeta entityMeta = sqlToyContext.getEntityMeta(treeModel.getEntity().getClass());
			HashMap<String, String> columnMap = new HashMap<String, String>();
			for (FieldMeta column : entityMeta.getFieldsMeta().values())
				columnMap.put(column.getColumnName().toUpperCase(), "");
			if (null == treeModel.getNodeRouteField()
					|| !columnMap.containsKey(treeModel.getNodeRouteField().toUpperCase()))
				throw new Exception("树形表的节点路径字段名称:" + treeModel.getNodeRouteField() + "不正确,请检查!");
			if (entityMeta.getIdArray() == null || entityMeta.getIdArray().length > 1)
				throw new Exception("对象对应的数据库表:" + entityMeta.getTableName() + "不存在唯一主键,不符合节点生成机制!");

			FieldMeta IdMeta = (FieldMeta) entityMeta.getFieldMeta(entityMeta.getIdArray()[0]);
			// 主键
			treeModel.idField(IdMeta.getColumnName());
			// 设置加工的节点路径
			if (!(treeModel.getEntity() instanceof Type)) {
				Object rootValue = PropertyUtils.getProperty(treeModel.getEntity(), entityMeta.getIdArray()[0]);
				Object pidValue = PropertyUtils.getProperty(treeModel.getEntity(),
						StringUtil.toHumpStr(treeModel.getPidField(), false));
				if (null == treeModel.getRootId())
					treeModel.rootId(pidValue);
				if (null == treeModel.getIdValue())
					treeModel.setIdValue(rootValue);
			}
			if (treeModel.getLeafField() != null && !columnMap.containsKey(treeModel.getLeafField().toUpperCase()))
				treeModel.isLeafField(null);
			if (treeModel.getNodeLevelField() != null
					&& !columnMap.containsKey(treeModel.getNodeLevelField().toUpperCase()))
				treeModel.nodeLevelField(null);
			// 类型,默认值为false
			if (IdMeta.getType() == java.sql.Types.INTEGER || IdMeta.getType() == java.sql.Types.DECIMAL
					|| IdMeta.getType() == java.sql.Types.DOUBLE || IdMeta.getType() == java.sql.Types.FLOAT
					|| IdMeta.getType() == java.sql.Types.NUMERIC) {
				treeModel.idTypeIsChar(false);
				// update 2016-12-05 节点路径默认采取主键值直接拼接,更加直观科学
				// treeModel.setAppendZero(true);
			} else if (IdMeta.getType() == java.sql.Types.VARCHAR || IdMeta.getType() == java.sql.Types.CHAR) {
				treeModel.idTypeIsChar(true);
			}
			treeModel.table(entityMeta.getTableName());
		}
		return (Boolean) DataSourceUtils.processDataSource(sqlToyContext, dataSource, new DataSourceCallbackHandler() {
			public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
				this.setResult(SqlUtil.wrapTreeTableRoute(treeModel, conn));
			}
		});
	}

	/**
	 * @todo 分页查询, pageNo为负一表示取全部记录
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param pageNo
	 * @param pageSize
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public QueryResult findPage(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor, final long pageNo,
			final Integer pageSize, final DataSource dataSource) throws Exception {
		if (queryExecutor.getSql() == null)
			throw new Exception("findPage operate sql is null!");
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, true);
						QueryResult queryResult = null;
						Long recordCnt = null;
						// 通过查询条件构造唯一的key
						String pageQueryKey = generatePageOptimizeCacheKey(sqlToyConfig, queryExecutor);
						// 需要进行分页查询优化
						if (null != pageQueryKey) {
							// 从缓存中提取总记录数
							recordCnt = sqlToyContext.getPageOptimizeCache().getPageTotalCount(sqlToyConfig,
									pageQueryKey);
							// 缓存中没有则重新查询
							if (null == recordCnt) {
								recordCnt = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType,
										dialect);
								// 将总记录数登记到缓存
								sqlToyContext.getPageOptimizeCache().registPageTotalCount(sqlToyConfig, pageQueryKey,
										recordCnt);
							}
						} else
							recordCnt = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType,
									dialect);

						// pageNo=-1时的提取数据量限制
						int limitSize = sqlToyContext.getPageFetchSizeLimit();
						// pageNo=-1时,总记录数超出限制则返回空集合
						boolean illegal = (pageNo == -1 && (limitSize != -1 && recordCnt > limitSize));
						if (recordCnt == 0 || illegal) {
							queryResult = new QueryResult();
							queryResult.setPageNo(pageNo);
							queryResult.setPageSize(pageSize);
							queryResult.setRecordCount(new Long(0));
							if (illegal)
								logger.warn("非法进行分页查询,提取记录总数为:{},sql={}", recordCnt, sqlToyConfig.getSql());
						} else {
							// 合法的全记录提取,设置页号为1按记录数
							if (pageNo == -1) {
								// 通过参数处理最终的sql和参数值
								SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(),
										queryExecutor.getParamsName(realSqlToyConfig),
										queryExecutor.getParamsValue(realSqlToyConfig));
								queryResult = getDialectSqlWrapper(dbType).findBySql(sqlToyContext, realSqlToyConfig,
										queryParam.getSql(), queryParam.getParamsValue(),
										queryExecutor.getRowCallbackHandler(), conn, queryExecutor.getFetchSize(),
										queryExecutor.getMaxRows());
								long totalRecord = (queryResult.getRows() == null) ? 0 : queryResult.getRows().size();
								queryResult.setPageNo(1L);
								queryResult.setPageSize(new Long(totalRecord).intValue());
								queryResult.setRecordCount(totalRecord);
							} else {
								// 实际开始页(页数据超出总记录,则从第一页重新开始)
								long realStartPage = (pageNo * pageSize >= (recordCnt + pageSize)) ? 1 : pageNo;
								queryResult = getDialectSqlWrapper(dbType).findPageBySql(sqlToyContext,
										realSqlToyConfig, queryExecutor, realStartPage, pageSize, conn);
								queryResult.setPageNo(realStartPage);
								queryResult.setPageSize(pageSize);
								queryResult.setRecordCount(recordCnt);
							}
							// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
							List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
									queryExecutor, conn, dialect);
							ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet,
									sqlToyContext.isDebug());
							// 结果映射成对象
							if (queryExecutor.getResultType() != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
										ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
										(Class) queryExecutor.getResultType()));
							}
						}
						this.setResult(queryResult);
					}
				});
	}

	/**
	 * @todo 根据查询条件组成key
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @return
	 * @throws Exception
	 */
	private String generatePageOptimizeCacheKey(final SqlToyConfig sqlToyConfig, final QueryExecutor queryExecutor)
			throws Exception {
		// 没有开放分页优化或sql id为null都不执行优化操作
		if (!sqlToyConfig.isPageOptimize() || null == sqlToyConfig.getId())
			return null;

		String[] paramNames = queryExecutor.getParamsName(sqlToyConfig);
		Object[] paramValues = queryExecutor.getParamsValue(sqlToyConfig);
		// sql中所有参数都为null,返回sqlId作为key
		if (paramValues == null || paramValues.length == 0)
			return sqlToyConfig.getId();
		StringBuilder cacheKey = new StringBuilder();
		boolean isParamsNamed = true;
		if (null == paramNames || paramNames.length == 0)
			isParamsNamed = false;
		int i = 0;
		// 循环查询条件的值构造key
		for (Object value : paramValues) {
			if (i > 0)
				cacheKey.append(",");
			if (isParamsNamed)
				cacheKey.append(paramNames[i]).append("=");
			else
				cacheKey.append("param").append(i).append("=");
			if (value == null)
				cacheKey.append("null");
			else if ((value instanceof Object[]) || value.getClass().isArray() || (value instanceof List)) {
				Object[] arrayValue = (value instanceof List) ? ((List) value).toArray()
						: CollectionUtil.convertArray(value);
				cacheKey.append("[");
				for (Object obj : arrayValue) {
					cacheKey.append((obj == null) ? "null" : obj.toString()).append(",");
				}
				cacheKey.append("]");
			} else
				cacheKey.append(value.toString());
			i++;
		}
		return cacheKey.toString();
	}

	/**
	 * @todo 取符合条件的前多少条记录
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param topSize
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public QueryResult findTop(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final double topSize, final DataSource dataSource) throws Exception {
		if (queryExecutor.getSql() == null)
			throw new Exception("findTop operate sql is null!");
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, false);
						Integer realTopSize;
						if (topSize < 1) {
							Long totalCount = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn,
									dbType, dialect);
							if (sqlToyContext.isDebug()) {
								out.println("findTopByQuery按比例提取数据,总记录数=" + totalCount);
							}
							realTopSize = new Double(topSize * totalCount.longValue()).intValue();
						} else
							realTopSize = new Double(topSize).intValue();
						if (realTopSize == 0) {
							this.setResult(new QueryResult());
							return;
						}

						QueryResult queryResult = getDialectSqlWrapper(dbType).findTopBySql(sqlToyContext,
								realSqlToyConfig, queryExecutor, realTopSize, conn);
						// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
						List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
								queryExecutor, conn, dialect);
						ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, sqlToyContext.isDebug());
						// 结果映射成对象
						if (queryExecutor.getResultType() != null) {
							queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
									ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
									(Class) queryExecutor.getResultType()));
						}
						this.setResult(queryResult);
					}
				});
	}

	/**
	 * @todo 查询符合条件的数据集合
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public QueryResult findByQuery(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final DataSource dataSource) throws Exception {
		if (queryExecutor.getSql() == null)
			throw new Exception("findByQuery operate sql is null!");
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, false);
						// 通过参数处理最终的sql和参数值
						SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(),
								queryExecutor.getParamsName(realSqlToyConfig),
								queryExecutor.getParamsValue(realSqlToyConfig));
						QueryResult queryResult = getDialectSqlWrapper(dbType).findBySql(sqlToyContext,
								realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
								queryExecutor.getRowCallbackHandler(), conn, queryExecutor.getFetchSize(),
								queryExecutor.getMaxRows());
						// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
						List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
								queryExecutor, conn, dialect);
						ResultUtils.calculate(realSqlToyConfig, queryResult, pivotCategorySet, sqlToyContext.isDebug());
						// 结果映射成对象
						if (queryExecutor.getResultType() != null) {
							queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
									ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
									(Class) queryExecutor.getResultType()));
						}
						this.setResult(queryResult);
					}
				});
	}

	/**
	 * @todo 查询符合条件的记录数量
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public Long getCountBySql(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final DataSource dataSource) throws Exception {
		if (queryExecutor.getSql() == null)
			throw new Exception("getCountBySql operate sql is null!");
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (Long) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, false);
						this.setResult(
								getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn, dbType, dialect));
					}
				});
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
		if (sqlToyConfig.getCountSql() != null) {
			sql = sqlToyConfig.getCountSql();
			isLastSql = true;
		} else {
			if (!sqlToyConfig.isHasFast())
				sql = sqlToyConfig.getSql();
			else
				sql = (sqlToyConfig.getFastWithSql() == null ? "" : sqlToyConfig.getFastWithSql()).concat(" ")
						.concat(sqlToyConfig.getFastSql());
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(sql);
			// 判定union all并且可以进行union all简化处理(sql文件中进行配置)
			if (DialectUtils.hasUnion(sqlWith.getRejectWithSql(), false)
					&& StringUtil.matches(sqlWith.getRejectWithSql(), SqlToyConstants.UNION_ALL_REGEX)
					&& sqlToyConfig.isUnionAllCount()) {
				isLastSql = true;
				String[] unionSqls = sqlWith.getRejectWithSql().split(SqlToyConstants.UNION_ALL_REGEX);
				StringBuilder countSql = new StringBuilder();
				countSql.append(sqlWith.getWithSql());
				countSql.append(" select sum(row_count) from (");
				int sql_from_index;
				int unionSqlSize = unionSqls.length;
				for (int i = 0; i < unionSqlSize; i++) {
					sql_from_index = StringUtil.getSymMarkIndexIgnoreCase("select ", " from", unionSqls[i], 0);
					countSql.append(" select count(1) row_count ")
							.append((sql_from_index != -1 ? unionSqls[i].substring(sql_from_index) : unionSqls[i]));
					if (i < unionSqlSize - 1)
						countSql.append(" union all ");
				}
				countSql.append(" ) ");
				sql = countSql.toString();
			}
		}
		// 通过参数处理最终的sql和参数值
		SqlToyResult queryParam = SqlConfigParseUtils.processSql(sql, queryExecutor.getParamsName(sqlToyConfig),
				queryExecutor.getParamsValue(sqlToyConfig));
		return getDialectSqlWrapper(dbType).getCountBySql(sqlToyContext, queryParam.getSql(),
				queryParam.getParamsValue(), isLastSql, conn);
	}

	/**
	 * @todo 保存或修改单个对象(数据库单条记录)
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateProps
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public Long saveOrUpdate(final SqlToyContext sqlToyContext, final Serializable entity,
			final String[] forceUpdateProps, final DataSource dataSource) throws Exception {
		if (entity == null)
			return new Long(0);
		final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, true, dataSource);
		return (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(getDialectSqlWrapper(dbType).saveOrUpdate(sqlToyContext, entity,
								forceUpdateProps, conn, null, shardingModel.getTableName()));
					}
				});
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
	 * @throws Exception
	 */
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final String[] forceUpdateProps, final ReflectPropertyHandler reflectPropertyHandler,
			final DataSource dataSource, final Boolean autoCommit) throws Exception {
		if (entities == null || entities.isEmpty())
			return new Long(0);
		List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
				new ParallelCallbackHandler() {
					public List execute(SqlToyContext sqlToyContext, ShardingGroupModel batchModel) throws Exception {
						final ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).saveOrUpdateAll(sqlToyContext,
												batchModel.getEntities(), batchSize, reflectPropertyHandler,
												forceUpdateProps, conn, autoCommit, shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					}
				});
		long updateTotalCnt = 0;
		if (result != null) {
			for (Long cnt : result) {
				updateTotalCnt = updateTotalCnt + cnt.longValue();
			}
		}
		return new Long(updateTotalCnt);
	}

	/**
	 * @todo 批量保存数据，当已经存在的时候忽视掉
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param dataSource
	 * @param autoCommit
	 * @throws Exception
	 */
	public Long saveAllNotExist(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource, final Boolean autoCommit)
			throws Exception {
		if (entities == null || entities.isEmpty())
			return new Long(0);
		List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
				new ParallelCallbackHandler() {
					public List execute(SqlToyContext sqlToyContext, ShardingGroupModel batchModel) throws Exception {
						final ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).saveAllIgnoreExist(sqlToyContext,
												batchModel.getEntities(), batchSize, reflectPropertyHandler, conn,
												autoCommit, shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					}
				});
		long updateTotalCnt = 0;
		if (result != null) {
			for (Long cnt : result) {
				updateTotalCnt = updateTotalCnt + cnt.longValue();
			}
		}
		return new Long(updateTotalCnt);
	}

	/**
	 * @todo 加载单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param cascadeTypes
	 * @param lockMode
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public Serializable load(final SqlToyContext sqlToyContext, final Serializable entity, final Class[] cascadeTypes,
			final LockMode lockMode, final DataSource dataSource) throws Exception {
		if (entity == null)
			return null;
		// 单记录操作返回对应的库和表配置
		final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
		return (Serializable) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(getDialectSqlWrapper(dbType).load(sqlToyContext, entity,
								(cascadeTypes == null) ? null : CollectionUtil.arrayToList(cascadeTypes), lockMode,
								conn, shardingModel.getTableName()));
					}
				});
	}

	/**
	 * @todo 批量加载集合
	 * @param sqlToyContext
	 * @param entities
	 * @param cascadeTypes
	 * @param lockMode
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public List<?> loadAll(final SqlToyContext sqlToyContext, final List<?> entities, final Class[] cascadeTypes,
			final LockMode lockMode, final DataSource dataSource) throws Exception {
		if (entities == null || entities.isEmpty())
			return entities;
		// 分库分表并行执行,并返回结果
		return ParallelUtils.execute(sqlToyContext, entities, false, dataSource, new ParallelCallbackHandler() {
			public List execute(SqlToyContext sqlToyContext, ShardingGroupModel batchModel) throws Exception {
				final ShardingModel shardingModel = batchModel.getShardingModel();
				return (List) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
						new DataSourceCallbackHandler() {
							public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
								this.setResult(
										getDialectSqlWrapper(dbType).loadAll(sqlToyContext, batchModel.getEntities(),
												(cascadeTypes == null) ? null
														: CollectionUtil.arrayToList(cascadeTypes),
												lockMode, conn, shardingModel.getTableName()));
							}
						});
			}
		});
	}

	/**
	 * @todo 保存单个对象记录
	 * @param sqlToyContext
	 * @param entity
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public Serializable save(final SqlToyContext sqlToyContext, final Serializable entity, final DataSource dataSource)
			throws Exception {
		if (entity == null)
			return null;
		final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, true, dataSource);
		return (Serializable) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(getDialectSqlWrapper(dbType).save(sqlToyContext, entity, conn,
								shardingModel.getTableName()));
					}
				});
	}

	/**
	 * @todo 批量保存
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param dataSource
	 * @param autoCommit
	 * @throws Exception
	 */
	public Long saveAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource, final Boolean autoCommit)
			throws Exception {
		if (entities == null || entities.isEmpty())
			return new Long(0);
		// 分库分表并行执行
		List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
				new ParallelCallbackHandler() {
					public List execute(SqlToyContext sqlToyContext, ShardingGroupModel batchModel) throws Exception {
						final ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).saveAll(sqlToyContext,
												batchModel.getEntities(), batchSize, reflectPropertyHandler, conn,
												autoCommit, shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					}
				});
		long updateTotalCnt = 0;
		if (result != null) {
			for (Long cnt : result) {
				updateTotalCnt = updateTotalCnt + cnt.longValue();
			}
		}
		return new Long(updateTotalCnt);
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
	 * @throws Exception
	 */
	public Long update(final SqlToyContext sqlToyContext, final Serializable entity, final String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClass,
			final HashMap<Class, String[]> subTableForceUpdateProps, final DataSource dataSource) throws Exception {
		if (entity == null)
			return new Long(0);
		final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
		return (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(getDialectSqlWrapper(dbType).update(sqlToyContext, entity, forceUpdateFields,
								cascade, forceCascadeClass, subTableForceUpdateProps, conn,
								shardingModel.getTableName()));
					}
				});
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
	 * @throws Exception
	 */
	public Long updateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final String[] forceUpdateFields, final ReflectPropertyHandler reflectPropertyHandler,
			final DataSource dataSource, final Boolean autoCommit) throws Exception {
		if (entities == null || entities.isEmpty())
			return new Long(0);
		// 分库分表并行执行
		List<Long> result = ParallelUtils.execute(sqlToyContext, entities, false, dataSource,
				new ParallelCallbackHandler() {
					public List execute(SqlToyContext sqlToyContext, ShardingGroupModel batchModel) throws Exception {
						final ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).updateAll(sqlToyContext,
												batchModel.getEntities(), batchSize, forceUpdateFields,
												reflectPropertyHandler, conn, autoCommit,
												shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					}
				});
		long updateTotalCnt = 0;
		if (result != null) {
			for (Long cnt : result) {
				updateTotalCnt = updateTotalCnt + cnt.longValue();
			}
		}
		return new Long(updateTotalCnt);
	}

	/**
	 * @todo 删除单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param dataSource
	 * @throws Exception
	 */
	public Long delete(final SqlToyContext sqlToyContext, final Serializable entity, final DataSource dataSource)
			throws Exception {
		if (entity == null)
			return new Long(0);
		// 获取分库分表策略结果
		final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
		return (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(getDialectSqlWrapper(dbType).delete(sqlToyContext, entity, conn,
								shardingModel.getTableName()));
					}
				});
	}

	/**
	 * @todo 批量删除对象
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param dataSource
	 * @param autoCommit
	 * @throws Exception
	 */
	public Long deleteAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final DataSource dataSource, final Boolean autoCommit) throws Exception {
		if (entities == null || entities.isEmpty())
			return new Long(0);
		// 分库分表并行执行
		List<Long> result = ParallelUtils.execute(sqlToyContext, entities, false, dataSource,
				new ParallelCallbackHandler() {
					public List execute(SqlToyContext sqlToyContext, ShardingGroupModel batchModel) throws Exception {
						final ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										this.setResult(getDialectSqlWrapper(dbType).deleteAll(sqlToyContext,
												batchModel.getEntities(), batchSize, conn, autoCommit,
												shardingModel.getTableName()));
									}
								});
						List<Long> tmp = new ArrayList();
						tmp.add(updateCnt);
						return tmp;
					}
				});
		long updateTotalCnt = 0;
		if (result != null) {
			for (Long cnt : result) {
				updateTotalCnt = updateTotalCnt + cnt.longValue();
			}
		}
		return new Long(updateTotalCnt);
	}

	/**
	 * @todo 查询锁定记录,并进行修改
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param updateRowHandler
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	public QueryResult updateFetch(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final UpdateRowHandler updateRowHandler, final DataSource dataSource) throws Exception {
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, false);
						SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(),
								queryExecutor.getParamsName(realSqlToyConfig),
								queryExecutor.getParamsValue(realSqlToyConfig));
						QueryResult queryResult = getDialectSqlWrapper(dbType).updateFetch(sqlToyContext,
								realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(), updateRowHandler,
								conn);

						if (queryExecutor.getResultType() != null) {
							queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
									ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
									(Class) queryExecutor.getResultType()));
						}
						this.setResult(queryResult);
					}
				});
	}

	@Deprecated
	public QueryResult updateFetchTop(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final Integer topSize, final UpdateRowHandler updateRowHandler, final DataSource dataSource)
			throws Exception {
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, false);

						SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(),
								queryExecutor.getParamsName(realSqlToyConfig),
								queryExecutor.getParamsValue(realSqlToyConfig));
						QueryResult queryResult = getDialectSqlWrapper(dbType).updateFetchTop(sqlToyContext,
								sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(), topSize,
								updateRowHandler, conn);

						if (queryExecutor.getResultType() != null) {
							queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
									ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
									(Class) queryExecutor.getResultType()));
						}
						this.setResult(queryResult);
					}
				});
	}

	@Deprecated
	public QueryResult updateFetchRandom(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final Integer random, final UpdateRowHandler updateRowHandler, final DataSource dataSource)
			throws Exception {
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
				ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// 处理sql中的?为统一的:named形式
						SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyConfig, queryExecutor, dialect, false);
						SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(),
								queryExecutor.getParamsName(realSqlToyConfig),
								queryExecutor.getParamsValue(realSqlToyConfig));

						QueryResult queryResult = getDialectSqlWrapper(dbType).updateFetchRandom(sqlToyContext,
								realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(), random,
								updateRowHandler, conn);
						if (queryExecutor.getResultType() != null) {
							queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
									ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
									(Class) queryExecutor.getResultType()));
						}
						this.setResult(queryResult);
					}
				});
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
	 * @throws Exception
	 */
	public StoreResult executeStore(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final Object[] inParamsValue, final Integer[] outParamsType, final Class resultType,
			final DataSource dataSource) throws Exception {
		return (StoreResult) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						int inCount = (inParamsValue == null) ? 0 : inParamsValue.length;
						int outCount = (outParamsType == null) ? 0 : outParamsType.length;
						// sql中问号数量
						int paramCnt = StringUtil.matchCnt(sqlToyConfig.getSql(), ARG_PATTERN);
						// 处理参数注入
						if (paramCnt != inCount + outCount)
							throw new BaseException("存储过程语句中的输入和输出参数跟实际调用传递的数量不等!");

						SqlToyResult sqlToyResult = new SqlToyResult(sqlToyConfig.getSql(), inParamsValue);
						// 判断是否是{?=call xxStore()} 模式(oracle 不支持此模式)
						boolean isFirstResult = StringUtil.matches(sqlToyConfig.getSql(), STORE_PATTERN);
						/*
						 * 将call xxxStore(?,?) 后的条件参数判断是否为null，如果是null则改为call xxxStore(null,?,null)
						 * 避免设置类型错误
						 */
						SqlConfigParseUtils.replaceNull(sqlToyResult, isFirstResult ? 1 : 0);
						// 针对不同数据库执行存储过程调用
						StoreResult queryResult = getDialectSqlWrapper(dbType).executeStore(sqlToyContext, sqlToyConfig,
								sqlToyResult.getSql(), inParamsValue, outParamsType, conn);
						// 进行数据必要的数据处理(一般存储过程不会结合旋转sql进行数据旋转操作)
						// {此区域代码正常情况下不会使用
						QueryExecutor queryExecutor = new QueryExecutor(null, sqlToyConfig.getParamsName(),
								inParamsValue);
						List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, sqlToyConfig, queryExecutor,
								conn, dialect);
						ResultUtils.calculate(sqlToyConfig, queryResult, pivotCategorySet, sqlToyContext.isDebug());
						// }
						// 映射成对象
						if (resultType != null) {
							queryResult.setRows(ResultUtils.wrapQueryResult(queryResult.getRows(),
									ResultUtils.humpFieldNames(queryExecutor, queryResult.getLabelNames()),
									resultType));
						}
						this.setResult(queryResult);
					}
				});
	}

}
