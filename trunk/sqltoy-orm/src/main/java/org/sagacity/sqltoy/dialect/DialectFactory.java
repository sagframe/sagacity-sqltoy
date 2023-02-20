package org.sagacity.sqltoy.dialect;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.ShardingModel;
import org.sagacity.sqltoy.config.model.SqlParamsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.dialect.impl.ClickHouseDialect;
import org.sagacity.sqltoy.dialect.impl.DB2Dialect;
import org.sagacity.sqltoy.dialect.impl.DMDialect;
import org.sagacity.sqltoy.dialect.impl.DefaultDialect;
import org.sagacity.sqltoy.dialect.impl.GuassDBDialect;
import org.sagacity.sqltoy.dialect.impl.ImpalaDialect;
import org.sagacity.sqltoy.dialect.impl.KingbaseDialect;
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
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;
import org.sagacity.sqltoy.plugins.sharding.ShardingUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.ParallelUtils;
import org.sagacity.sqltoy.utils.QueryExecutorBuilder;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 为不同类型数据库提供不同方言实现类的factory,避免各个数据库发展变更形成相互影响
 * @author zhongxuchen
 * @version v1.0,Date:2014年12月11日
 * @update data:2020-06-05 增加dm(达梦)数据库支持
 * @update data:2020-06-10 增加tidb、guassdb、oceanbase支持,规整sqlserver的版本(默认仅支持2012+)
 * @update data:2021-01-25 分页支持并行查询
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
		// 5.6+(mysql 的缺陷主要集中在不支持with as以及临时表不能在一个查询中多次引用)
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
		// postgresql/greenplum
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
		case DBType.IMPALA: {
			dialectSqlWrapper = new ImpalaDialect();
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
		} // 北大金仓
		case DBType.KINGBASE: {
			dialectSqlWrapper = new KingbaseDialect();
			break;
		} // sybase iq基本淘汰
			// 15.4+(必须采用15.4,最好采用16.0 并打上最新的补丁),15.4 之后的分页支持limit模式
		case DBType.SYBASE_IQ: {
			dialectSqlWrapper = new SybaseIQDialect();
		}
		// 如果匹配不上使用默认dialect
		default:
			dialectSqlWrapper = new DefaultDialect();
		}
		dialects.put(dbType, dialectSqlWrapper);
		return dialectSqlWrapper;
	}

	/**
	 * @todo 批量执行sql修改或删除操作
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param dataSet             支持List<List>、List<Object[]>(sql中?传参)
	 *                            ;List<VO>、List<Map> 形式(sql中:paramName传参)
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param insertCallhandler   使用反调方式自己对rs进行处理
	 * @param autoCommit
	 * @param dataSource
	 * @return
	 */
	public Long batchUpdate(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final List dataSet,
			final int batchSize, final ReflectPropsHandler reflectPropsHandler,
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
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							String realSql = sqlToyConfig.getSql(dialect);
							Integer[] fieldTypes = null;
							List values = dataSet;
							// sql中存在:named参数模式，通过sql提取参数名称
							if (sqlToyConfig.getParamsName() != null) {
								// 替换sql中:name为?并提取参数名称归集成数组
								SqlParamsModel sqlParamsModel = SqlConfigParseUtils.processNamedParamsQuery(realSql);
								realSql = sqlParamsModel.getSql();
								// update 2021-10-28 增加统一授权传参、根据insert、update 判断自动补充创建人、创建时间、修改人、修改时间等属性值的填充
								ReflectPropsHandler realPropsHandler = DialectUtils.wrapReflectWithUnifyFields(realSql,
										reflectPropsHandler, sqlToyContext.getUnifyFieldsHandler());
								values = BeanUtil.reflectBeansToList(dataSet, sqlParamsModel.getParamsName(),
										realPropsHandler);
								fieldTypes = BeanUtil.matchMethodsType(dataSet.get(0).getClass(),
										sqlParamsModel.getParamsName());
							}
							// 做sql签名
							realSql = SqlUtilsExt.signSql(realSql, dbType, sqlToyConfig);
							SqlExecuteStat.showSql("批量sql执行", realSql, null);
							this.setResult(SqlUtil.batchUpdateByJdbc(sqlToyContext.getTypeHandler(), realSql, values,
									batchSize, insertCallhandler, fieldTypes, autoCommit, conn, dbType));
						}
					});
			// 输出执行结果更新记录量日志
			if (updateTotalCnt > sqlToyContext.getUpdateTipCount()) {
				SqlExecuteStat.debug("执行结果", "batchUpdate影响记录数量:{} 条,大于数据修改提示阈值:{}条!", updateTotalCnt,
						sqlToyContext.getUpdateTipCount());
			} else {
				SqlExecuteStat.debug("执行结果", "batchUpdate影响记录数量:{} 条!", updateTotalCnt);
			}
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
	 * @param queryExecutor
	 * @param paramsTypes
	 * @param autoCommit
	 * @param dataSource
	 * @return
	 */
	public Long executeSql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
			final QueryExecutor queryExecutor, final Integer[] paramsTypes, final Boolean autoCommit,
			final DataSource dataSource) {
		try {
			// 将修改语句当做特殊的查询，其处理过程在交jdbc执行前完全一致
			final QueryExecutorExtend extend = queryExecutor.getInnerModel();
			// 组织参数和参数校验，但忽视数据权限数据的传参和校验
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
			SqlExecuteStat.start(sqlToyConfig.getId(), "executeSql",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							// 进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(), extend.getParamsValue(sqlToyContext, realSqlToyConfig),
									dialect);
							// 增加sql执行拦截器 update 2022-9-10
							queryParam = DialectUtils.doInterceptors(sqlToyContext, realSqlToyConfig,
									(extend.entityClass == null) ? OperateType.execute : OperateType.singleTable,
									queryParam, extend.entityClass, dbType);
							String sql = queryParam.getSql();
							// clickhouse 删除和修改语法存在特殊性
							if (dbType == DBType.CLICKHOUSE && extend.entityClass != null) {
								EntityMeta entityMeta = sqlToyContext.getEntityMeta(extend.entityClass);
								String startSql = "alter table ".concat(entityMeta.getSchemaTable(null, dbType));
								// 删除操作
								if (sqlToyConfig.getSqlType() == SqlType.delete) {
									sql = startSql.concat(" delete ")
											.concat(sql.substring(StringUtil.matchIndex(sql, "(?i)\\swhere\\s")));
								} else if (sqlToyConfig.getSqlType() == SqlType.update) {
									sql = startSql.concat(" update ")
											.concat(sql.substring(StringUtil.matchIndex(sql, "(?i)\\sset\\s") + 4));
								}
							}
							// 做sql签名
							String executeSql = SqlUtilsExt.signSql(sql, dbType, realSqlToyConfig);
							// 2022-3-21 存在类似in (?) ?对应参数为数组，将参数和类型长度变得不一致则去除类型约束
							if (paramsTypes != null && queryParam.getParamsValue() != null
									&& queryParam.getParamsValue().length != paramsTypes.length) {
								this.setResult(SqlUtil.executeSql(sqlToyContext.getTypeHandler(), executeSql,
										queryParam.getParamsValue(), null, conn, dbType, autoCommit, false));
							} else {
								this.setResult(SqlUtil.executeSql(sqlToyContext.getTypeHandler(), executeSql,
										queryParam.getParamsValue(), paramsTypes, conn, dbType, autoCommit, false));
							}
						}
					});
			if (updateTotalCnt > sqlToyContext.getUpdateTipCount()) {
				SqlExecuteStat.debug("执行结果", "executeSql影响记录数量:{} 条,大于数据修改提示阈值:{}条!", updateTotalCnt,
						sqlToyContext.getUpdateTipCount());
			} else {
				SqlExecuteStat.debug("执行结果", "executeSql影响记录数量:{} 条!", updateTotalCnt);
			}
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
			SqlExecuteStat.start(BeanUtil.getEntityClass(uniqueExecutor.getEntity().getClass()).getName(), "isUnique",
					sqlToyContext.isDebug());
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, uniqueExecutor.getEntity(),
					false, dataSource);
			Boolean isUnique = (Boolean) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
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
			Long startTime = System.currentTimeMillis();
			// 规整查询参数名称和参数名称对应的值
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
			SqlExecuteStat.start(sqlToyConfig.getId(), "getRandomResult",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			QueryResult result = (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
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
								long countRunTime = 0;
								// 提取总记录数
								if (totalCount == null) {
									long preTime = System.currentTimeMillis();
									totalCount = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn,
											dbType, dialect);
									countRunTime = System.currentTimeMillis() - preTime;
								}
								randomCnt = Double.valueOf(totalCount * randomCount.doubleValue()).longValue();
								if (countRunTime == 0) {
									SqlExecuteStat.debug("过程提示", "按比例提取总记录数:{}条,需取随机记录:{}条!", totalCount, randomCnt);
								} else {
									SqlExecuteStat.debug("过程提示", "按比例提取总记录数:{}条,需取随机记录:{}条,执行count查询耗时:{}毫秒!",
											totalCount, randomCnt, countRunTime);
								}
								// 如果总记录数不为零，randomCnt最小为1
								if (totalCount >= 1 && randomCnt < 1) {
									randomCnt = 1L;
								}
							}
							QueryResult queryResult;
							// 总记录数为零
							if (totalCount != null && totalCount == 0) {
								queryResult = new QueryResult();
								queryResult.setRows(new ArrayList());
								this.setResult(queryResult);
								logger.warn("getRandom,total Records is zero,please check sql!sqlId={}",
										sqlToyConfig.getIdOrSql());
								return;
							}
							queryResult = getDialectSqlWrapper(dbType).getRandomResult(sqlToyContext, realSqlToyConfig,
									queryExecutor, wrapDecryptHandler(sqlToyContext, extend.resultType), totalCount,
									randomCnt, conn, dbType, dialect, getFetchSize(extend.fetchSize), extend.maxRows);
							if (queryResult.getRows() != null && !queryResult.getRows().isEmpty()) {
								// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
								List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
										queryExecutor, conn, dbType, dialect);
								// 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
								boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(),
										realSqlToyConfig, queryResult, pivotCategorySet, extend);
								// 结果映射成对象(含Map),为什么不放在rs循环过程中?因为rs循环里面有link、缓存翻译等很多处理
								// 将结果映射对象单独出来为了解耦，性能影响其实可以忽略，上万条也是1毫秒级
								if (extend.resultType != null) {
									queryResult.setRows(ResultUtils.wrapQueryResult(sqlToyContext,
											queryResult.getRows(), queryResult.getLabelNames(),
											(Class) extend.resultType, changedCols, extend.humpMapLabel,
											extend.hiberarchy, extend.hiberarchyClasses, extend.fieldsMap));
								}
							}
							SqlExecuteStat.debug("查询结果", "取得随机记录数:{}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
			result.setExecuteTime(System.currentTimeMillis() - startTime);
			return result;
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
				if (StringUtil.isBlank(treeModel.getTableName())) {
					treeModel.table(entityMeta.getSchemaTable(null, null));
				}
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
				// update 2022-5-6，boolean类型转出Boolean,在未赋值情况下通过主键类型进行自动补全设置
				if (treeModel.isChar() == null) {
					// id字段非主键
					if (!treeModel.getIdField().equalsIgnoreCase(idMeta.getColumnName())) {
						idMeta = (FieldMeta) entityMeta
								.getFieldMeta(entityMeta.getColumnFieldMap().get(treeModel.getIdField().toLowerCase()));
					}
					// 类型,默认值为false
					if (idMeta.getType() == java.sql.Types.INTEGER || idMeta.getType() == java.sql.Types.DECIMAL
							|| idMeta.getType() == java.sql.Types.DOUBLE || idMeta.getType() == java.sql.Types.FLOAT
							|| idMeta.getType() == java.sql.Types.NUMERIC
							|| idMeta.getType() == java.sql.Types.BIGINT) {
						treeModel.idTypeIsChar(false);
						// update 2016-12-05 节点路径默认采取主键值直接拼接,更加直观科学
						// treeModel.setAppendZero(true);
					} else if (idMeta.getType() == java.sql.Types.VARCHAR || idMeta.getType() == java.sql.Types.NVARCHAR
							|| idMeta.getType() == java.sql.Types.CHAR || idMeta.getType() == java.sql.Types.NCHAR
							|| idMeta.getType() == java.sql.Types.LONGVARCHAR) {
						treeModel.idTypeIsChar(true);
					}
				}
			}
			SqlExecuteStat.start(treeModel.getTableName(), "wrapTreeTableRoute", sqlToyContext.isDebug());
			return (Boolean) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							this.setResult(SqlUtil.wrapTreeTableRoute(sqlToyContext.getTypeHandler(), treeModel, conn,
									dbType));
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
			Long startTime = System.currentTimeMillis();
			// 规整查询参数名称和参数名称对应的值
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, true);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findSkipTotalCountPage",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			QueryResult result = (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, true);
							QueryResult queryResult = getDialectSqlWrapper(dbType).findPageBySql(sqlToyContext,
									realSqlToyConfig, queryExecutor,
									wrapDecryptHandler(sqlToyContext, extend.resultType), pageNo, pageSize, conn,
									dbType, dialect, getFetchSize(extend.fetchSize), extend.maxRows);
							queryResult.setPageNo(pageNo);
							queryResult.setPageSize(pageSize);
							if (queryResult.getRows() != null && !queryResult.getRows().isEmpty()) {
								// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
								List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
										queryExecutor, conn, dbType, dialect);
								// 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
								boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(),
										realSqlToyConfig, queryResult, pivotCategorySet, extend);
								// 结果映射成对象(含Map),为什么不放在rs循环过程中?因为rs循环里面有link、缓存翻译等很多处理
								// 将结果映射对象单独出来为了解耦，性能影响其实可以忽略，上万条也是1毫秒级
								if (extend.resultType != null) {
									queryResult.setRows(ResultUtils.wrapQueryResult(sqlToyContext,
											queryResult.getRows(), queryResult.getLabelNames(),
											(Class) extend.resultType, changedCols, extend.humpMapLabel,
											extend.hiberarchy, extend.hiberarchyClasses, extend.fieldsMap));
								}
							}
							queryResult.setSkipQueryCount(true);
							SqlExecuteStat.debug("查询结果", "分页查询出记录数量:{}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
			result.setExecuteTime(System.currentTimeMillis() - startTime);
			return result;
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
	 * @param overPageToFirst
	 * @param dataSource
	 * @return
	 */
	public QueryResult findPage(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final long pageNo, final Integer pageSize, final Boolean overPageToFirst,
			final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		if (StringUtil.isBlank(extend.sql)) {
			throw new IllegalArgumentException("findPage operate sql is null!");
		}
		try {
			Long startTime = System.currentTimeMillis();
			// 规整查询参数名称和参数名称对应的值
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, true);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findPage",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			final DataSource realDataSource = ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig,
					queryExecutor, dataSource);
			QueryResult result = (QueryResult) DataSourceUtils.processDataSource(sqlToyContext, realDataSource,
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							boolean isOverPageToFirst = false;
							// 使用全局默认值
							if (sqlToyContext.getOverPageToFirst() != null) {
								isOverPageToFirst = sqlToyContext.getOverPageToFirst();
							}
							// 以pageModel中指定的为准
							if (overPageToFirst != null) {
								isOverPageToFirst = overPageToFirst;
							}
							SqlExecuteStat.setDialect(dialect);
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
								recordCnt = PageOptimizeUtils.getPageTotalCount(realSqlToyConfig, pageOptimize,
										pageQueryKey);
								if (recordCnt != null) {
									SqlExecuteStat.debug("过程提示", "分页优化条件命中,从缓存中获得总记录数:{}!!", recordCnt);
								}
							}
							// 并行且缓存中无总记录数量，执行并行处理
							if (pageOptimize != null && pageOptimize.isParallel() && pageNo != -1
									&& recordCnt == null) {
								queryResult = parallelPage(sqlToyContext, queryExecutor, realSqlToyConfig, extend,
										pageNo, pageSize, isOverPageToFirst, pageOptimize, conn, dbType, dialect,
										realDataSource);
								recordCnt = queryResult.getRecordCount();
								// 将并行后得到的总记录数登记到缓存
								if (null != pageQueryKey) {
									PageOptimizeUtils.registPageTotalCount(realSqlToyConfig, pageOptimize, pageQueryKey,
											recordCnt);
								}
							} else {
								// 非并行且分页缓存未命中，执行count查询
								if (recordCnt == null) {
									long preTime = System.currentTimeMillis();
									recordCnt = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn,
											dbType, dialect);
									SqlExecuteStat.debug("查询count执行耗时", (System.currentTimeMillis() - preTime) + "毫秒!");
								}
								// 将总记录数登记到缓存
								if (null != pageQueryKey) {
									PageOptimizeUtils.registPageTotalCount(realSqlToyConfig, pageOptimize, pageQueryKey,
											recordCnt);
								}
								// pageNo=-1时的提取数据量限制
								int limitSize = sqlToyContext.getPageFetchSizeLimit();
								// pageNo=-1时,总记录数超出限制则返回空集合
								boolean illegal = (pageNo == -1 && (limitSize != -1 && recordCnt > limitSize));
								if (recordCnt == 0 || illegal) {
									queryResult = new QueryResult();
									if (recordCnt == 0 && isOverPageToFirst) {
										queryResult.setPageNo(1L);
									} else {
										queryResult.setPageNo(pageNo);
									}
									queryResult.setPageSize(pageSize);
									queryResult.setRecordCount(0L);
									if (illegal) {
										SqlExecuteStat.debug("过程提示",
												"非法分页查询,提取记录总数为:{}>{}上限,可设置参数:spring.sqltoy.pageFetchSizeLimit进行调整(-1表示不限制)",
												recordCnt, limitSize);
										logger.warn(
												"非法分页查询,提取记录总数为:{}>{}上限可设置参数:spring.sqltoy.pageFetchSizeLimit进行调整(-1表示不限制),sql={}",
												recordCnt, limitSize, sqlToyConfig.getIdOrSql());
									} else {
										SqlExecuteStat.debug("过程提示", "分页查询提取count数为:0");
									}
								} else {
									long preTime = System.currentTimeMillis();
									// 合法的全记录提取,设置页号为1按记录数
									if (pageNo == -1) {
										SqlExecuteStat.debug("过程提示", "pageNo=-1,页面可能在做下载操作!");
										// 通过参数处理最终的sql和参数值
										SqlToyResult queryParam = SqlConfigParseUtils.processSql(
												realSqlToyConfig.getSql(dialect), extend.getParamsName(),
												extend.getParamsValue(sqlToyContext, realSqlToyConfig), dialect);
										// 增加sql执行拦截器 update 2022-9-10
										queryParam = DialectUtils.doInterceptors(sqlToyContext, realSqlToyConfig,
												(extend.entityClass == null) ? OperateType.search
														: OperateType.singleTable,
												queryParam, extend.entityClass, dbType);
										queryResult = getDialectSqlWrapper(dbType).findBySql(sqlToyContext,
												realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
												extend.rowCallbackHandler,
												wrapDecryptHandler(sqlToyContext, extend.resultType), conn, null,
												dbType, dialect, getFetchSize(extend.fetchSize), extend.maxRows);
										long totalRecord = (queryResult.getRows() == null) ? 0
												: queryResult.getRows().size();
										queryResult.setPageNo(1L);
										queryResult.setPageSize(Long.valueOf(totalRecord).intValue());
										queryResult.setRecordCount(totalRecord);
									} else {
										// 实际开始页(页数据超出总记录,则从第一页重新开始,相反如继续按指定的页查询则记录为空,且实际页号也不存在)
										boolean isOverPage = (pageNo * pageSize >= (recordCnt + pageSize));
										// 允许页号超出总页数，结果返回空集合
										if (isOverPage && !isOverPageToFirst) {
											queryResult = new QueryResult();
											queryResult.setPageNo(pageNo);
										} else {
											long realStartPage = isOverPage ? 1 : pageNo;
											queryResult = getDialectSqlWrapper(dbType).findPageBySql(sqlToyContext,
													realSqlToyConfig, queryExecutor,
													wrapDecryptHandler(sqlToyContext, extend.resultType), realStartPage,
													pageSize, conn, dbType, dialect, getFetchSize(extend.fetchSize),
													extend.maxRows);
											queryResult.setPageNo(realStartPage);
										}
										queryResult.setPageSize(pageSize);
										queryResult.setRecordCount(recordCnt);
									}
									SqlExecuteStat.debug("查询分页记录耗时", (System.currentTimeMillis() - preTime) + "毫秒!");
								}
							}
							if (queryResult.getRows() != null && !queryResult.getRows().isEmpty()) {
								// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
								List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
										queryExecutor, conn, dbType, dialect);
								// 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
								boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(),
										realSqlToyConfig, queryResult, pivotCategorySet, extend);
								// 结果映射成对象(含Map),为什么不放在rs循环过程中?因为rs循环里面有link、缓存翻译等很多处理
								// 将结果映射对象单独出来为了解耦，性能影响其实可以忽略，上万条也是1毫秒级
								if (extend.resultType != null) {
									queryResult.setRows(ResultUtils.wrapQueryResult(sqlToyContext,
											queryResult.getRows(), queryResult.getLabelNames(),
											(Class) extend.resultType, changedCols, extend.humpMapLabel,
											extend.hiberarchy, extend.hiberarchyClasses, extend.fieldsMap));
								}
							}
							SqlExecuteStat.debug("查询结果", "分页总记录数:{}条,取得本页记录数:{}条!",
									((QueryResult) queryResult).getRecordCount(),
									((QueryResult) queryResult).getRows().size());
							this.setResult(queryResult);
						}
					});
			result.setExecuteTime(System.currentTimeMillis() - startTime);
			return result;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @update data:2022-12-09 增加传DataSource以两个conn进行并发，解决单个conn竞争问题
	 * @update data:2021-01-25 分页支持并行查询
	 * @TODO 并行分页查询，同时执行count和rows记录查询
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param extend
	 * @param pageNo
	 * @param pageSize
	 * @param overPageToFirst
	 * @param pageOptimize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param dataSource
	 * @return
	 * @throws Exception
	 */
	private QueryResult parallelPage(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final QueryExecutorExtend extend, final long pageNo,
			final Integer pageSize, final boolean overPageToFirst, PageOptimize pageOptimize, Connection conn,
			Integer dbType, String dialect, final DataSource dataSource) throws Exception {
		final QueryResult queryResult = new QueryResult();
		queryResult.setPageNo(pageNo);
		queryResult.setPageSize(pageSize);
		ExecutorService pool = null;
		try {
			SqlExecuteStat.debug("开始并行查询count总记录数和单页记录数据!", null);
			pool = Executors.newFixedThreadPool(1);
			// 查询总记录数量
			pool.submit(new Runnable() {
				@Override
				public void run() {
					try {
						Long startTime = System.currentTimeMillis();
						// 重新通过dataSource获取conn，避免conn竞争
						DataSourceUtils.processDataSource(sqlToyContext, dataSource, new DataSourceCallbackHandler() {
							@Override
							public void doConnection(Connection countConn, Integer countDbType, String countDialect)
									throws Exception {
								queryResult.setRecordCount(getCountBySql(sqlToyContext, sqlToyConfig, queryExecutor,
										countConn, countDbType, countDialect));
							}
						});
						SqlExecuteStat.debug("查询count执行耗时", (System.currentTimeMillis() - startTime) + "毫秒!");
					} catch (Exception e) {
						e.printStackTrace();
						queryResult.setSuccess(false);
						queryResult.setMessage("查询总记录数异常:" + e.getMessage());
					}
				}
			});
			pool.shutdown();
			try {
				Long startTime = System.currentTimeMillis();
				QueryResult result = getDialectSqlWrapper(dbType).findPageBySql(sqlToyContext, sqlToyConfig,
						queryExecutor, wrapDecryptHandler(sqlToyContext, extend.resultType), pageNo, pageSize, conn,
						dbType, dialect, getFetchSize(extend.fetchSize), extend.maxRows);
				queryResult.setRows(result.getRows());
				queryResult.setLabelNames(result.getLabelNames());
				queryResult.setLabelTypes(result.getLabelTypes());
				SqlExecuteStat.debug("查询分页记录耗时", (System.currentTimeMillis() - startTime) + "毫秒!");
			} catch (Exception e) {
				e.printStackTrace();
				queryResult.setSuccess(false);
				queryResult.setMessage("查询单页记录数据异常:" + e.getMessage());
			}
			// 设置最大等待时长(秒)
			if (pageOptimize.getParallelMaxWaitSeconds() > 0) {
				pool.awaitTermination(pageOptimize.getParallelMaxWaitSeconds(), TimeUnit.SECONDS);
			} else {
				pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
			}
			// 发生异常
			if (!queryResult.isSuccess()) {
				throw new DataAccessException("并行查询执行错误:" + queryResult.getMessage());
			}
			int rowSize = (queryResult.getRows() == null) ? 0 : queryResult.getRows().size();
			// 修正实际结果跟count的差异,比如:pageNo=3,rows=9,count=27,则需要将count调整为29
			long minCount = (queryResult.getPageNo() - 1) * queryResult.getPageSize() + rowSize;
			// 总记录数小于实际查询记录数量(rowSize <= queryResult.getPageSize() 防止单页数据关联扩大了记录量的场景)
			if (queryResult.getRecordCount() < minCount && minCount >= 0 && rowSize <= queryResult.getPageSize()) {
				queryResult.setRecordCount(minCount);
			}
			// 总记录数量大于实际记录数量
			if (rowSize < queryResult.getPageSize() && (queryResult.getRecordCount() > minCount) && minCount >= 0) {
				queryResult.setRecordCount(minCount);
			}
			if (queryResult.getRecordCount() == 0 && overPageToFirst) {
				queryResult.setPageNo(1L);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("并行查询执行错误:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}
		return queryResult;
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
			Long startTime = System.currentTimeMillis();
			// 规整查询参数名称和参数名称对应的值
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findTop",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			QueryResult result = (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							Integer realTopSize;
							// 小于1表示按比例提取
							if (topSize < 1) {
								long preTime = System.currentTimeMillis();
								Long totalCount = getCountBySql(sqlToyContext, realSqlToyConfig, queryExecutor, conn,
										dbType, dialect);
								realTopSize = Double.valueOf(topSize * totalCount.longValue()).intValue();
								SqlExecuteStat.debug("过程提示", "按比例提取,总记录数:{}条,按比例top记录要取:{} 条,执行count记录数耗时:{}毫秒!",
										totalCount, realTopSize, System.currentTimeMillis() - preTime);
							} else {
								realTopSize = Double.valueOf(topSize).intValue();
							}
							if (realTopSize == 0) {
								this.setResult(new QueryResult());
								SqlExecuteStat.debug("查询结果", "实际取得top记录数:0 条!");
								return;
							}
							// 调用数据库方言查询结果
							QueryResult queryResult = getDialectSqlWrapper(dbType).findTopBySql(sqlToyContext,
									realSqlToyConfig, queryExecutor,
									wrapDecryptHandler(sqlToyContext, extend.resultType), realTopSize, conn, dbType,
									dialect, getFetchSize(extend.fetchSize), extend.maxRows);
							if (queryResult.getRows() != null && !queryResult.getRows().isEmpty()) {
								// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
								List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
										queryExecutor, conn, dbType, dialect);
								// 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
								boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(),
										realSqlToyConfig, queryResult, pivotCategorySet, extend);
								// 结果映射成对象(含Map),为什么不放在rs循环过程中?因为rs循环里面有link、缓存翻译等很多处理,后续可能还有旋转、汇总等计算
								// 将结果映射对象单独出来为了解耦，性能影响其实可以忽略，上万条也是1毫秒级
								if (extend.resultType != null) {
									queryResult.setRows(ResultUtils.wrapQueryResult(sqlToyContext,
											queryResult.getRows(), queryResult.getLabelNames(),
											(Class) extend.resultType, changedCols, extend.humpMapLabel,
											extend.hiberarchy, extend.hiberarchyClasses, extend.fieldsMap));
								}
							}
							SqlExecuteStat.debug("查询结果", "实际取得top记录数: {}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
			result.setExecuteTime(System.currentTimeMillis() - startTime);
			return result;
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
			Long startTime = System.currentTimeMillis();
			// 规整查询参数名称和参数名称对应的值
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
			SqlExecuteStat.start(sqlToyConfig.getId(), "findByQuery",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			QueryResult result = (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							// 通过参数处理最终的sql和参数值
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(), extend.getParamsValue(sqlToyContext, realSqlToyConfig),
									dialect);
							// 增加sql执行拦截器 update 2022-9-10
							queryParam = DialectUtils.doInterceptors(sqlToyContext, realSqlToyConfig,
									(extend.entityClass == null) ? OperateType.search : OperateType.singleTable,
									queryParam, extend.entityClass, dbType);
							QueryResult queryResult = getDialectSqlWrapper(dbType).findBySql(sqlToyContext,
									realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
									extend.rowCallbackHandler, wrapDecryptHandler(sqlToyContext, extend.resultType),
									conn, lockMode, dbType, dialect, getFetchSize(extend.fetchSize), extend.maxRows);
							if (queryResult.getRows() != null && !queryResult.getRows().isEmpty()) {
								// 存在计算和旋转的数据不能映射到对象(数据类型不一致，如汇总平均以及数据旋转)
								List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, realSqlToyConfig,
										queryExecutor, conn, dbType, dialect);
								// 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
								boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(),
										realSqlToyConfig, queryResult, pivotCategorySet, extend);
								// 结果映射成对象(含Map),为什么不放在rs循环过程中?因为rs循环里面有link、缓存翻译等很多处理,后续可能还有旋转、汇总等计算
								// 将结果映射对象单独出来为了解耦，性能影响其实可以忽略，上万条也是1毫秒级
								if (extend.resultType != null) {
									queryResult.setRows(ResultUtils.wrapQueryResult(sqlToyContext,
											queryResult.getRows(), queryResult.getLabelNames(),
											(Class) extend.resultType, changedCols, extend.humpMapLabel,
											extend.hiberarchy, extend.hiberarchyClasses, extend.fieldsMap));
								}
							}
							SqlExecuteStat.debug("查询结果", "共查询出记录数={}条!", queryResult.getRecordCount());
							this.setResult(queryResult);
						}
					});
			result.setExecuteTime(System.currentTimeMillis() - startTime);
			return result;
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
			// 规整查询参数名称和参数名称对应的值
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
			SqlExecuteStat.start(sqlToyConfig.getId(), "getCountBySql",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			Long count = (Long) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
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
				String countPart = dbType.equals(DBType.ES) ? " count(*) " : " count(1) ";
				for (int i = 0; i < unionSqlSize; i++) {
					sql_from_index = StringUtil.getSymMarkMatchIndex("(?i)select\\s+", "(?i)\\s+from[\\(\\s+]",
							unionSqls[i], 0);
					countSql.append(" select ").append(countPart).append(" row_count ")
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
		SqlToyResult queryParam = SqlConfigParseUtils.processSql(sql, extend.getParamsName(),
				extend.getParamsValue(sqlToyContext, sqlToyConfig), dialect);
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.count : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
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
		// 主键值为空，直接调用save操作
		if (DialectUtils.isEmptyPK(sqlToyContext, entity)) {
			logger.debug("主键字段对应值存在null，因此saveOrUpdate转执行save操作!");
			save(sqlToyContext, entity, dataSource);
			return 1L;
		}
		try {
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, true, dataSource);
			SqlExecuteStat.start(entity.getClass().getName(), "saveOrUpdate", sqlToyContext.isDebug());
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							this.setResult(getDialectSqlWrapper(dbType).saveOrUpdate(sqlToyContext, entity,
									forceUpdateProps, conn, dbType, dialect, null, shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("执行结果", "单对象saveOrUpdate实际影响记录数量:{} 条!", updateTotalCnt);
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
	 * @param reflectPropsHandler
	 * @param dataSource
	 * @param autoCommit
	 */
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final String[] forceUpdateProps, final ReflectPropsHandler reflectPropsHandler, final DataSource dataSource,
			final Boolean autoCommit) {
		// 前置输入合法校验
		if (entities == null || entities.isEmpty()) {
			logger.warn("saveOrUpdateAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			// 启动执行日志
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"saveOrUpdateAll:[" + entities.size() + "]条记录!", sqlToyContext.isDebug());
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									@Override
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										SqlExecuteStat.setDialect(dialect);
										this.setResult(getDialectSqlWrapper(dbType).saveOrUpdateAll(context,
												batchModel.getEntities(), batchSize, reflectPropsHandler,
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
			if (updateTotalCnt > sqlToyContext.getUpdateTipCount()) {
				SqlExecuteStat.debug("执行结果", "saveOrUpdateAll影响记录数量:{} 条,大于数据修改提示阈值:{}条!", updateTotalCnt,
						sqlToyContext.getUpdateTipCount());
			} else {
				SqlExecuteStat.debug("执行结果", "saveOrUpdateAll影响记录数量:{} 条!", updateTotalCnt);
			}
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
	 * @param reflectPropsHandler
	 * @param dataSource
	 * @param autoCommit
	 */
	public Long saveAllIgnoreExist(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropsHandler reflectPropsHandler, final DataSource dataSource, final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("saveAllIgnoreExist entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"saveAllNotExist:[" + entities.size() + "]条记录!", sqlToyContext.isDebug());
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									@Override
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										SqlExecuteStat.setDialect(dialect);
										this.setResult(getDialectSqlWrapper(dbType).saveAllIgnoreExist(context,
												batchModel.getEntities(), batchSize, reflectPropsHandler, conn, dbType,
												dialect, autoCommit, shardingModel.getTableName()));
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
			if (updateTotalCnt > sqlToyContext.getUpdateTipCount()) {
				SqlExecuteStat.debug("执行结果", "saveAllIgnoreExist影响记录数量:{} 条,大于数据修改提示阈值:{}条!", updateTotalCnt,
						sqlToyContext.getUpdateTipCount());
			} else {
				SqlExecuteStat.debug("执行结果", "saveAllIgnoreExist影响记录数量:{} 条!", updateTotalCnt);
			}
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
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "load", sqlToyContext.isDebug());
			return (T) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
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
	 * @todo 批量加载集合(自4.13.1 版本已经自动将超大规模集合拆分执行)，规避了jpa等框架的缺陷
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
					"loadAll:[" + entities.size() + "]条记录!", sqlToyContext.isDebug());
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
										@Override
										public void doConnection(Connection conn, Integer dbType, String dialect)
												throws Exception {
											SqlExecuteStat.setDialect(dialect);
											this.setResult(getDialectSqlWrapper(dbType).loadAll(context,
													batchModel.getEntities(),
													(cascadeTypes == null) ? null
															: CollectionUtil.arrayToList(cascadeTypes),
													lockMode, conn, dbType, dialect, shardingModel.getTableName(),
													getFetchSize(-1), -1));
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
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "save", sqlToyContext.isDebug());
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, true, dataSource);
			Serializable result = (Serializable) DataSourceUtils.processDataSource(sqlToyContext,
					shardingModel.getDataSource(), new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
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
	 * @param reflectPropsHandler
	 * @param dataSource
	 * @param autoCommit
	 */
	public Long saveAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final ReflectPropsHandler reflectPropsHandler, final DataSource dataSource, final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("saveAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"saveAll:[" + entities.size() + "]条记录!", sqlToyContext.isDebug());
			// 分库分表并行执行
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, true, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									@Override
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										SqlExecuteStat.setDialect(dialect);
										this.setResult(getDialectSqlWrapper(dbType).saveAll(context,
												batchModel.getEntities(), batchSize, reflectPropsHandler, conn, dbType,
												dialect, autoCommit, shardingModel.getTableName()));
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
			// 便于日志检索
			if (updateTotalCnt > sqlToyContext.getUpdateTipCount()) {
				SqlExecuteStat.debug("执行结果", "saveAll影响记录量:{}条,大于数据修改提示阈值:{}条!", updateTotalCnt,
						sqlToyContext.getUpdateTipCount());
			} else {
				SqlExecuteStat.debug("执行结果", "saveAll影响记录量:{}条!", updateTotalCnt);
			}
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
	 * @param uniqueFields             唯一性索引字段
	 * @param forceUpdateFields
	 * @param cascade
	 * @param forceCascadeClass
	 * @param subTableForceUpdateProps
	 * @param dataSource
	 * @return
	 */
	public Long update(final SqlToyContext sqlToyContext, final Serializable entity, final String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClass,
			final HashMap<Class, String[]> subTableForceUpdateProps, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("update entity is null,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "update",
					sqlToyContext.isDebug());
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							this.setResult(getDialectSqlWrapper(dbType).update(sqlToyContext, entity, forceUpdateFields,
									cascade, forceCascadeClass, subTableForceUpdateProps, conn, dbType, dialect,
									shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("执行结果", "单对象修改操作影响记录量:{} 条!", updateTotalCnt);
			return updateTotalCnt;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @TODO 适用于库存台账、客户资金账强事务高并发场景，一次数据库交互实现：1、锁查询；2、记录存在则修改；3、记录不存在则执行insert；4、返回修改或插入的记录信息
	 * @param sqlToyContext
	 * @param entity
	 * @param updateRowHandler
	 * @param uniqueProps      空则表示根据主键查询
	 * @param dataSource
	 * @return
	 */
	public Serializable updateSaveFetch(final SqlToyContext sqlToyContext, final Serializable entity,
			final UpdateRowHandler updateRowHandler, final String[] uniqueProps, final DataSource dataSource) {
		if (entity == null || updateRowHandler == null) {
			logger.warn("updateSaveFetch entity or updateRowHandler is null,please check!");
			return null;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "updateSaveFetch",
					sqlToyContext.isDebug());
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
			Serializable result = (Serializable) DataSourceUtils.processDataSource(sqlToyContext,
					shardingModel.getDataSource(), new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							this.setResult(getDialectSqlWrapper(dbType).updateSaveFetch(sqlToyContext, entity,
									updateRowHandler, uniqueProps, conn, dbType, dialect,
									shardingModel.getTableName()));
						}
					});
			return result;
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
	 * @param uniqueFields        唯一性索引字段
	 * @param forceUpdateFields
	 * @param reflectPropsHandler
	 * @param dataSource
	 * @param autoCommit
	 * @return
	 */
	public Long updateAll(final SqlToyContext sqlToyContext, final List<?> entities, final int batchSize,
			final String[] uniqueFields, final String[] forceUpdateFields,
			final ReflectPropsHandler reflectPropsHandler, final DataSource dataSource, final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("updateAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"updateAll:[" + entities.size() + "]条记录!", sqlToyContext.isDebug());
			// 分库分表并行执行
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, false, dataSource,
					(context, batchModel) -> {
						ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									@Override
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										SqlExecuteStat.setDialect(dialect);
										this.setResult(getDialectSqlWrapper(dbType).updateAll(context,
												batchModel.getEntities(), batchSize, uniqueFields, forceUpdateFields,
												reflectPropsHandler, conn, dbType, dialect, autoCommit,
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
			if (updateTotalCnt > sqlToyContext.getUpdateTipCount()) {
				SqlExecuteStat.debug("执行结果", "updateAll影响记录数量:{} 条,大于数据修改提示阈值:{}条!", updateTotalCnt,
						sqlToyContext.getUpdateTipCount());
			} else {
				SqlExecuteStat.debug("执行结果", "updateAll影响记录数量:{} 条!", updateTotalCnt);
			}
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
	 * @return
	 */
	public Long delete(final SqlToyContext sqlToyContext, final Serializable entity, final DataSource dataSource) {
		if (entity == null) {
			logger.warn("delete entity is null,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entity.getClass()).getName(), "delete",
					sqlToyContext.isDebug());
			// 获取分库分表策略结果
			final ShardingModel shardingModel = ShardingUtils.getSharding(sqlToyContext, entity, false, dataSource);
			Long updateTotalCnt = (Long) DataSourceUtils.processDataSource(sqlToyContext, shardingModel.getDataSource(),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							this.setResult(getDialectSqlWrapper(dbType).delete(sqlToyContext, entity, conn, dbType,
									dialect, shardingModel.getTableName()));
						}
					});
			SqlExecuteStat.debug("执行结果", "单记录删除操作影响记录量:{} 条!", updateTotalCnt);
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
	 * @param <T>
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param dataSource
	 * @param autoCommit
	 * @return
	 */
	public <T extends Serializable> Long deleteAll(final SqlToyContext sqlToyContext, final List<T> entities,
			final int batchSize, final DataSource dataSource, final Boolean autoCommit) {
		if (entities == null || entities.isEmpty()) {
			logger.warn("deleteAll entities is null or empty,please check!");
			return 0L;
		}
		try {
			SqlExecuteStat.start(BeanUtil.getEntityClass(entities.get(0).getClass()).getName(),
					"deleteAll:[" + entities.size() + "]条记录!", sqlToyContext.isDebug());
			// 分库分表并行执行
			List<Long> result = ParallelUtils.execute(sqlToyContext, entities, false, dataSource,
					(context, batchModel) -> {
						final ShardingModel shardingModel = batchModel.getShardingModel();
						Long updateCnt = (Long) DataSourceUtils.processDataSource(context,
								shardingModel.getDataSource(), new DataSourceCallbackHandler() {
									@Override
									public void doConnection(Connection conn, Integer dbType, String dialect)
											throws Exception {
										SqlExecuteStat.setDialect(dialect);
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
			if (updateTotalCnt > sqlToyContext.getUpdateTipCount()) {
				SqlExecuteStat.debug("执行结果", "deleteAll影响记录量:{} 条,大于数据修改提示阈值:{}条!", updateTotalCnt,
						sqlToyContext.getUpdateTipCount());
			} else {
				SqlExecuteStat.debug("执行结果", "deleteAll影响记录量:{} 条!", updateTotalCnt);
			}
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
		try {
			Long startTime = System.currentTimeMillis();
			SqlExecuteStat.start(sqlToyConfig.getId(), "updateFetch", sqlToyConfig.isShowSql());
			// 组织参数和参数校验，但忽视数据权限数据的传参和校验
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
			QueryResult result = (QueryResult) DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							// 处理sql中的?为统一的:named形式
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(), extend.getParamsValue(sqlToyContext, realSqlToyConfig),
									dialect);
							// 增加sql执行拦截器 update 2022-9-10
							queryParam = DialectUtils.doInterceptors(sqlToyContext, realSqlToyConfig,
									OperateType.fetchUpdate, queryParam, null, dbType);
							QueryResult queryResult = getDialectSqlWrapper(dbType).updateFetch(sqlToyContext,
									realSqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
									updateRowHandler, conn, dbType, dialect,
									(extend.lockMode == null) ? LockMode.UPGRADE : extend.lockMode,
									getFetchSize(extend.fetchSize), extend.maxRows);
							if (extend.resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(sqlToyContext, queryResult.getRows(),
										queryResult.getLabelNames(), (Class) extend.resultType, false,
										extend.humpMapLabel, extend.hiberarchy, extend.hiberarchyClasses,
										extend.fieldsMap));
							}
							if (queryResult.getRecordCount() > sqlToyContext.getUpdateTipCount()) {
								SqlExecuteStat.debug("执行结果", "updateFetch操作影响记录量:{} 条,大于数据修改提示阈值:{}条!",
										queryResult.getRecordCount(), sqlToyContext.getUpdateTipCount());
							} else {
								SqlExecuteStat.debug("执行结果", "updateFetch操作影响记录量:{} 条!", queryResult.getRecordCount());
							}
							this.setResult(queryResult);
						}
					});
			result.setExecuteTime(System.currentTimeMillis() - startTime);
			return result;
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
			Long startTime = System.currentTimeMillis();
			SqlExecuteStat.start(sqlToyConfig.getId(), "executeStore", sqlToyConfig.isShowSql());
			StoreResult result = (StoreResult) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
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

							// 将call xxxStore(?,?) 后的条件参数判断是否为null，如果是null则改为call xxxStore(null,?,null)
							// 避免设置类型错误
							SqlConfigParseUtils.replaceNull(sqlToyResult, isFirstResult ? 1 : 0);
							// 针对不同数据库执行存储过程调用
							SqlExecuteStat.showSql("存储过程执行", sqlToyResult.getSql(), sqlToyResult.getParamsValue());
							StoreResult queryResult = getDialectSqlWrapper(dbType).executeStore(sqlToyContext,
									sqlToyConfig, sqlToyResult.getSql(), sqlToyResult.getParamsValue(), outParamsType,
									conn, dbType, dialect, -1);
							// 进行数据必要的数据处理(一般存储过程不会结合旋转sql进行数据旋转操作)
							// {此区域代码正常情况下不会使用
							QueryExecutor queryExecutor = new QueryExecutor(null, sqlToyConfig.getParamsName(),
									inParamsValue);
							List pivotCategorySet = ResultUtils.getPivotCategory(sqlToyContext, sqlToyConfig,
									queryExecutor, conn, dbType, dialect);
							boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(),
									sqlToyConfig, queryResult, pivotCategorySet, null);
							// }
							// 映射成对象
							if (resultType != null) {
								queryResult.setRows(ResultUtils.wrapQueryResult(sqlToyContext, queryResult.getRows(),
										queryResult.getLabelNames(), resultType, changedCols, null, false, null, null));
							}
							if (queryResult.getRecordCount() > sqlToyContext.getUpdateTipCount()) {
								SqlExecuteStat.debug("执行结果", "executeStore影响记录量:{} 条,大于数据修改提示阈值:{}条!",
										queryResult.getRecordCount(), sqlToyContext.getUpdateTipCount());
							} else {
								SqlExecuteStat.debug("执行结果", "executeStore影响记录量:{} 条!", queryResult.getRecordCount());
							}
							this.setResult(queryResult);
						}
					});
			result.setExecuteTime(System.currentTimeMillis() - startTime);
			return result;
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			throw new DataAccessException(e);
		} finally {
			SqlExecuteStat.destroy();
		}
	}

	/**
	 * @TODO 以流模式获取查询结果
	 * @param sqlToyContext
	 * @param queryExecutor
	 * @param sqlToyConfig
	 * @param streamResultHandler
	 * @param dataSource
	 */
	public void fetchStream(final SqlToyContext sqlToyContext, final QueryExecutor queryExecutor,
			final SqlToyConfig sqlToyConfig, final StreamResultHandler streamResultHandler,
			final DataSource dataSource) {
		final QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 合法校验
		if (StringUtil.isBlank(extend.sql)) {
			throw new IllegalArgumentException("fetchStream operate sql is null!");
		}
		try {
			// 规整查询参数名称和参数名称对应的值
			QueryExecutorBuilder.initQueryExecutor(sqlToyContext, extend, sqlToyConfig, false);
			SqlExecuteStat.start(sqlToyConfig.getId(), "fetchStream",
					(extend.showSql != null) ? extend.showSql : sqlToyConfig.isShowSql());
			DataSourceUtils.processDataSource(sqlToyContext,
					ShardingUtils.getShardingDataSource(sqlToyContext, sqlToyConfig, queryExecutor, dataSource),
					new DataSourceCallbackHandler() {
						@Override
						public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
							SqlExecuteStat.setDialect(dialect);
							// 处理sql中的?为统一的:named形式，并进行sharding table替换
							SqlToyConfig realSqlToyConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
									sqlToyConfig, queryExecutor, dialect, false);
							// 通过参数处理最终的sql和参数值
							SqlToyResult queryParam = SqlConfigParseUtils.processSql(realSqlToyConfig.getSql(dialect),
									extend.getParamsName(), extend.getParamsValue(sqlToyContext, realSqlToyConfig),
									dialect);
							// 增加sql执行拦截器 update 2022-9-10
							queryParam = DialectUtils.doInterceptors(sqlToyContext, realSqlToyConfig,
									OperateType.search, queryParam, null, dbType);
							// 做sql签名
							String lastSql = SqlUtilsExt.signSql(queryParam.getSql(), dbType, realSqlToyConfig);
							Object[] paramsValue = queryParam.getParamsValue();
							// 打印sql
							SqlExecuteStat.showSql("执行查询", lastSql, paramsValue);
							PreparedStatement pst = conn.prepareStatement(lastSql, ResultSet.TYPE_FORWARD_ONLY,
									ResultSet.CONCUR_READ_ONLY);
							if (extend.fetchSize != -1) {
								pst.setFetchSize(extend.fetchSize);
							} // mysql 有点特殊必须要设置为MIN_VALUE
							else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57) {
								pst.setFetchSize(Integer.MIN_VALUE);
							} // 默认为1000
							else {
								pst.setFetchSize(1000);
							}
							pst.setFetchDirection(ResultSet.FETCH_FORWARD);
							ResultSet rs = null;
							SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
								@Override
								public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws Exception {
									SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst,
											paramsValue, null, 0);
									rs = pst.executeQuery();
									ResultUtils.consumeResult(sqlToyContext, extend, sqlToyConfig, conn, rs,
											streamResultHandler, (Class) extend.resultType, extend.humpMapLabel,
											extend.fieldsMap);
								}
							});
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
	 * @TODO 获取数据库的表字段信息
	 * @param sqlToyContext
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param dataSource
	 * @return
	 */
	public List<ColumnMeta> getTableColumns(final SqlToyContext sqlToyContext, final String catalog,
			final String schema, String tableName, DataSource dataSource) {
		if (StringUtil.isBlank(tableName)) {
			throw new IllegalArgumentException("getTableColumns method tableName is null,please check!");
		}
		return (List<ColumnMeta>) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
				new DataSourceCallbackHandler() {
					@Override
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(getDialectSqlWrapper(dbType).getTableColumns(catalog, schema, tableName, conn,
								dbType, dialect));
					}
				});
	}

	/**
	 * @TODO 获取数据库的表信息
	 * @param sqlToyContext
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param dataSource
	 * @return
	 */
	public List<TableMeta> getTables(final SqlToyContext sqlToyContext, final String catalog, final String schema,
			String tableName, DataSource dataSource) {
		return (List<TableMeta>) DataSourceUtils.processDataSource(sqlToyContext, dataSource,
				new DataSourceCallbackHandler() {
					@Override
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						this.setResult(getDialectSqlWrapper(dbType).getTables(catalog, schema, tableName, conn, dbType,
								dialect));
					}
				});
	}

	private int getFetchSize(int fetchSize) {
		if (fetchSize > 0) {
			return fetchSize;
		}
		return SqlToyConstants.FETCH_SIZE;
	}

	/**
	 * @TODO 构造加解密处理器
	 * @param sqlToyContext
	 * @param resultType
	 * @return
	 */
	private DecryptHandler wrapDecryptHandler(final SqlToyContext sqlToyContext, Type resultType) {
		// 只针对POJO 实体类
		if (resultType == null || resultType.equals(Map.class) || resultType.equals(HashMap.class)
				|| resultType.equals(List.class)) {
			return null;
		}
		FieldsSecureProvider fieldsSecureProvider = sqlToyContext.getFieldsSecureProvider();
		if (fieldsSecureProvider == null) {
			return null;
		}
		EntityMeta entityMeta = null;
		if (sqlToyContext.isEntity((Class) resultType)) {
			entityMeta = sqlToyContext.getEntityMeta((Class) resultType);
		}
		if (entityMeta == null || entityMeta.getSecureColumns() == null) {
			return null;
		}
		return new DecryptHandler(fieldsSecureProvider, entityMeta.getSecureColumns());
	}
}
