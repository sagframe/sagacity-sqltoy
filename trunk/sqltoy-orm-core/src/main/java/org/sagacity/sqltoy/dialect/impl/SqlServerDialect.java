package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowCallback;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.SavePKStrategy;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description sqlserver2012以及更新版本的数据库操作实现
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 * @modify Date:2020-02-05 废弃对sqlserver2008 的支持,最低版本为2012版
 */
@SuppressWarnings({ "rawtypes" })
public class SqlServerDialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(SqlServerDialect.class);

	// order by 匹配
	private static final Pattern ORDER_BY = Pattern.compile("(?i)\\Worder\\s*by\\W");

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			DBProfile profile, String tableName, final Integer queryTimeout) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, profile, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, profile, table);
					return queryStr.replaceFirst("(?i)select ", "select top " + topSize + " ");
				}, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org. sagacity
	 * .sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		String dialect = profile.getDialect();
		// sqlserver 不支持内部order by
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		StringBuilder sql = DialectUtils.openFastWrap(sqlToyConfig, dialect);
		String partSql = " select top " + randomCount + " ";
		if (sqlToyConfig.isHasWith()) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(innerSql);
			sql.append(sqlWith.getWithSql());
			innerSql = sqlWith.getRejectWithSql();
		}
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		// 存在order 或union 则在sql外包裹一层
		if (hasOrderOrUnion) {
			sql.append(partSql);
			sql.append(" " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
			// update 2026-9-14 mssql真库实测:派生表内的order by必须伴随top/offset方能合法
			// (否则报"The ORDER BY clause is invalid in views, inline functions, derived
			// tables, subqueries..."),内层sql末尾追加offset 0 rows使内层排序合法;
			// 随机取样的最终行序由外层order by NEWID()决定,内层offset 0不增删行不改变行集;
			// 内层已含offset的不追加(派生表内带offset的排序本已合法,重复追加反而语法错误)
			// update 2026-9-17 追加判定收编至SqlServerDialectUtils.legalizeDerivedInnerSql:
			// contains("offset")子串匹配会被row_offset/offset_id等标识符误判(跳过追加致
			// 内层order by报语法错误),改word边界正则;追加条件精确到top级order by(字面量
			// 掩码+括号剔除后判定),union无order by时OFFSET语法必须跟随order by不可追加
			sql.append(SqlServerDialectUtils.legalizeDerivedInnerSql(innerSql));
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		} else {
			sql.append(innerSql.replaceFirst("(?i)select ", partSql));
		}
		sql.append(" order by NEWID() ");
		DialectUtils.closeFastWrap(sqlToyConfig, dialect, sql);
		return DialectUtils.executeWrappedQuery(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, conn,
				profile, sql.toString(), null, null,
				(queryExecutor.getInnerModel().entityClass == null) ? OperateType.random : OperateType.singleTable,
				fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity
	 * .sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor,java.lang.Long, java.lang.Integer,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		String dialect = profile.getDialect();
		boolean isNamed = sqlToyConfig.isNamedParam();
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// update 2021-10-20 提前试算一下实际sql,便于判断最终sql中是否包含order by
		String judgeOrderSql = innerSql;
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		// 存在@fast() 快速分页
		StringBuilder sql = DialectUtils.openFastWrap(sqlToyConfig, dialect);
		sql.append(innerSql);
		// 避免条件用?模式,导致实际参数位置不匹配,因此只针对:name模式进行处理
		if (isNamed) {
			SqlToyResult tmpResult = SqlConfigParseUtils.processSql(judgeOrderSql, extend.getParamsName(),
					extend.getParamsValue(sqlToyContext, sqlToyConfig), dialect);
			judgeOrderSql = tmpResult.getSql();
		}
		// order by位置
		int orderByIndex = StringUtil.matchIndex(judgeOrderSql, ORDER_BY);
		// 存在order by，继续判断order by 是否在子查询内
		if (orderByIndex > 0) {
			// 剔除select 和from 之间内容，剔除sql中所有()之间的内容,即剔除所有子查询，再判断是否有order by
			orderByIndex = StringUtil.matchIndex(DialectUtils.clearDisturbSql(judgeOrderSql), ORDER_BY);
		}
		// 不存在order by或order by存在于子查询中
		if (orderByIndex < 0) {
			// update 2026-9-5 真实库验证:sqlserver对union语句直接追加order by会报
			// "ORDER BY items must appear in the select list...",需包一层派生表后再挂分页参数
			if (!sqlToyConfig.isHasFast() && SqlUtil.hasUnion(judgeOrderSql, true)) {
				sql.insert(0, "select * from (");
				sql.append(") sag_union_tmp order by (select 1) ");
			} else {
				// offset fetch语法要求必须有order by,用固定排序占位符,避免NEWID()导致每次分页顺序随机出现重复或丢行
				sql.append(" order by (select 1) ");
			}
		}
		// 增加分页语句
		sql.append(" offset ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		sql.append(" rows fetch next ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		sql.append(" rows only");
		DialectUtils.closeFastWrap(sqlToyConfig, dialect, sql);
		// 锁hint无需经实例findBySql:分页包装sql的lockMode恒为null,lockSql原样返回
		return DialectUtils.executeWrappedQuery(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, conn,
				profile, sql.toString(), (pageNo - 1) * pageSize, Long.valueOf(pageSize),
				(extend.entityClass == null) ? OperateType.page : OperateType.singleTable, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor, double, java.sql.Connection)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, DBProfile profile,
			final int fetchSize, final int maxRows) throws Exception {
		String dialect = profile.getDialect();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		StringBuilder sql = DialectUtils.openFastWrap(sqlToyConfig, dialect);
		String partSql = " select top " + topSize + " ";
		if (sqlToyConfig.isHasWith()) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(innerSql);
			sql.append(sqlWith.getWithSql());
			innerSql = sqlWith.getRejectWithSql();
		}
		boolean hasUnion = false;
		if (sqlToyConfig.isHasUnion()) {
			hasUnion = SqlUtil.hasUnion(innerSql, false);
		}
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		if (hasUnion) {
			sql.append(partSql);
			sql.append(" " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
			// update 2026-9-17 与getRandomResult同源修复:派生表内的order by必须伴随top/offset
			// (否则报"The ORDER BY clause is invalid in views, inline functions, derived
			// tables..."),此前带尾部order by的union包派生表直接报语法错误;经legalizeDerivedInnerSql
			// 判定后追加offset 0 rows(不增删行;无order by的裸union派生表本已合法不可追加)
			sql.append(SqlServerDialectUtils.legalizeDerivedInnerSql(innerSql));
			sql.append(") as " + SqlToyConstants.INTERMEDIATE_TABLE + " ");
		} else {
			sql.append(innerSql.replaceFirst("(?i)select ", partSql));
		}
		DialectUtils.closeFastWrap(sqlToyConfig, dialect, sql);
		return DialectUtils.executeWrappedQuery(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, conn,
				profile, sql.toString(), null, null,
				(queryExecutor.getInnerModel().entityClass == null) ? OperateType.top : OperateType.singleTable,
				fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.
	 * sqltoy.config.model.SqlToyConfig, java.lang.String[], java.lang.Object[],
	 * java.lang.reflect.Type, org.sagacity.sqltoy.callback.RowCallbackHandler,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final QueryExecutorExtend queryExecutorExtend,
			final DecryptHandler decryptHandler, final Connection conn, final LockMode lockMode, DBProfile profile,
			final int fetchSize, final int maxRows) throws Exception {
		String realSql = SqlServerDialectUtils.lockSql(sql, null, lockMode);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, queryExecutorExtend,
				decryptHandler, conn, profile, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(java.lang .String,
	 * java.lang.String[], java.lang.Object[], java.sql.Connection)
	 */
	@Override
	public Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final boolean isLastSql, final QueryExecutorExtend extend,
			final Connection conn, DBProfile profile) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, extend, conn,
				profile);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, final String[] forceUpdateFields,
			Connection conn, DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				profile, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy
	 * .SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		// update 2026-9-7
		// merge语句统一由DialectUtils.getSaveOrUpdateSql生成(sqlserver的rowversion列
		// 排除等特性在其内部按dbType门控);rowversion判据按目标库元数据校准(每实体首次,幂等)
		SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, profile, tableName, entities.get(0).getClass());
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// sqlserver merge into must end with ";" charater
		// 返回记录变更量
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						// update 2026-9-7 merge语句统一由DialectUtils.getSaveOrUpdateSql生成
						// (rowversion排除/vector/json/decimal等sqlserver特性在其内部按dbType门控)
						String sql = DialectUtils.getSaveOrUpdateSql(sqlToyContext,
								sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta, entityMeta.getIdStrategy(),
								forceUpdateFields, null, "@mySeqVariable",
								SqlServerDialectUtils.allowAssignPKValue(pkStrategy), tableName);
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE)) {
							sql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + entityMeta.getSequence()
									+ " " + sql;
						}
						return sql.concat(";");
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAllNotExist(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection,
	 * java.lang.Boolean)
	 */
	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// update 2026-9-5 rowversion判据按目标库元数据校准(每实体首次,幂等)
		SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, profile, tableName, entities.get(0).getClass());
		// sqlserver merge into must end with ";" charater
		// 返回变更的记录数量
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						// update 2026-9-7 统一由DialectExtUtils.mergeIgnore生成
						// (rowversion排除等sqlserver特性在其内部按dbType门控)
						String sql = DialectUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), profile,
								entityMeta, pkStrategy, null, "@mySeqVariable",
								SqlServerDialectUtils.allowAssignPKValue(pkStrategy), tableName);
						// 2012 版本
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE)) {
							sql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + entityMeta.getSequence()
									+ " " + sql;
						}
						return sql.concat(";");
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#load(java.io.Serializable,
	 * java.util.List, java.sql.Connection)
	 */
	@Override
	public Serializable load(final SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, int lockWaitTimeout, Connection conn, DBProfile profile,
			final String tableName, final Integer queryTimeout) throws Exception {
		Integer dbType = profile.getDbType();
		String dialect = profile.getDialect();
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect, null);
		String loadSql = sqlToyConfig.getSql(dialect);
		loadSql = SqlServerDialectUtils.lockSql(loadSql, entityMeta.getSchemaTable(tableName, dbType), lockMode);
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, onlySubTables,
				cascadeTypes, conn, profile, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(java.util.List,
	 * java.util.List, java.sql.Connection)
	 */
	@Override
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, final int lockWaitTimeout, Connection conn, DBProfile profile,
			final String tableName, final int fetchSize, final int maxRows, final Integer queryTimeout)
			throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, lockMode, conn, profile,
				tableName, null, fetchSize, maxRows, queryTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.util.List, java.sql.Connection)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		// update 2026-9-5 rowversion判据按目标库元数据校准(每实体首次,幂等)
		SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, profile, tableName, entity.getClass());
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// save行为根据主键是否赋值情况调整最终的主键策略
		PKStrategy pkStrategy = DialectUtils.getSavePKStrategy(entityMeta, entity, profile);
		boolean isAssignPK = SqlServerDialectUtils.allowAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, "@mySeqVariable", isAssignPK, tableName);
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE)) {
			// sqlserver的sequence主键通过select
			// @mySeqVariable结果集回填(DialectUtils.save按dbType分支处理)
			insertSql = "set nocount on DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR "
					+ entityMeta.getSequence() + " " + insertSql + " select @mySeqVariable ";
		}
		// update 2026-9-7 save执行逻辑统一由DialectUtils.save处理(rowversion参数剔除/sqlserver
		// sequence
		// 回填在其内部按dbType门控;级联子表通过回调注入sqlserver的insert语句)
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta subEntityMeta, String[] forceUpdateField) {
						PKStrategy subPkStrategy = subEntityMeta.getIdStrategy();
						String subInsertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(),
								profile, subEntityMeta, subPkStrategy, "@mySeqVariable",
								SqlServerDialectUtils.allowAssignPKValue(subPkStrategy), null);
						if (subPkStrategy != null && subPkStrategy.equals(PKStrategy.SEQUENCE)) {
							subInsertSql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR "
									+ subEntityMeta.getSequence() + " " + subInsertSql;
						}
						return subInsertSql;
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta subEntityMeta) {
						PKStrategy subPkStrategy = subEntityMeta.getIdStrategy();
						return new SavePKStrategy(subPkStrategy,
								SqlServerDialectUtils.allowAssignPKValue(subPkStrategy));
					}
				}, conn, profile);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAll(org.sagacity.sqltoy.
	 * SqlToyContext , java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection)
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		// update 2026-9-5 rowversion判据按目标库元数据校准(每实体首次,幂等)
		SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, profile, tableName, entities.get(0).getClass());
		// update 2026-9-7 批量保存统一由DialectUtils.saveAll处理(rowversion参数剔除在其内部按dbType门控)
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = SqlServerDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				entityMeta.getIdStrategy(), "@mySeqVariable", isAssignPK, tableName);
		if (entityMeta.getIdStrategy() != null && entityMeta.getIdStrategy().equals(PKStrategy.SEQUENCE)) {
			insertSql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + entityMeta.getSequence() + " "
					+ insertSql;
		}
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, profile, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#update(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.lang.String[],
	 * java.sql.Connection)
	 */
	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		// update 2026-9-5 rowversion判据按目标库元数据校准(每实体首次,幂等)
		SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, profile, tableName, entity.getClass());
		// update 2026-9-7 级联修改统一由DialectUtils.update级联重载处理(子表saveOrUpdateAll通过回调注入
		// sqlserver merge语句;子表rowversion校准在其内部按dbType门控)
		return DialectUtils.update(sqlToyContext, entity, forceUpdateFields, cascade, new GenerateSqlHandler() {
			@Override
			public String generateSql(EntityMeta subEntityMeta, String[] forceUpdateField) {
				PKStrategy subPkStrategy = subEntityMeta.getIdStrategy();
				String sql = DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
						profile, subEntityMeta, subPkStrategy, forceUpdateField, null, "@mySeqVariable",
						SqlServerDialectUtils.allowAssignPKValue(subPkStrategy), null);
				if (subPkStrategy != null && subPkStrategy.equals(PKStrategy.SEQUENCE)) {
					sql = "DECLARE @mySeqVariable as numeric(20)=NEXT VALUE FOR " + subEntityMeta.getSequence() + " "
							+ sql;
				}
				return sql.concat(";");
			}
		}, forceCascadeClasses, subTableForceUpdateProps, conn, profile, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropsHandler, java.sql.Connection)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] uniqueFields, final String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler,
			Connection conn, DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		// update 2026-9-5 rowversion判据按目标库元数据校准(每实体首次,幂等)
		SqlServerDialectUtils.ensureRowVersionMeta(sqlToyContext, conn, profile, tableName, entities.get(0).getClass());
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler, conn,
				profile, autoCommit, tableName, false);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, int lockWaitTimeout, String[] uniqueProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		return DefaultDialectUtils.updateSaveFetch(sqlToyContext, entity, updateRowHandler, uniqueProps, conn, profile,
				tableName, lockWaitTimeout);
	}

	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowCallback updateRowCallback, int lockWaitTimeout, String[] uniqueProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		return DefaultDialectUtils.updateSaveFetch(sqlToyContext, entity, updateRowCallback, uniqueProps, conn, profile,
				tableName, lockWaitTimeout);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		return DialectUtils.delete(sqlToyContext, entity, conn, profile, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#deleteAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		return DialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, profile, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetch(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.model.QueryExecutor,
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, DBProfile profile,
			final LockMode lockMode, int lockWaitTimeout, final int fetchSize, final int maxRows) throws Exception {
		String realSql = SqlServerDialectUtils.lockSql(sql, null, (lockMode == null) ? LockMode.UPGRADE : lockMode);
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				profile, 0, fetchSize, maxRows);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final boolean moreResult,
			final Connection conn, DBProfile profile, final int fetchSize, final Integer timeout) throws Exception {
		// 2012版本存储过程查询需要增加set nocount on 否则不返回结果集
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, moreResult,
				conn, profile, fetchSize, timeout);
	}

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		List<ColumnMeta> tableColumns = SqlServerDialectUtils.getTableColumns(catalog, schema, tableName, conn,
				profile);
		// 获取主键信息
		Map<String, ColumnMeta> pkMap = DefaultDialectUtils.getTablePrimaryKeys(catalog, schema, tableName, conn,
				profile);
		if (pkMap == null || pkMap.isEmpty()) {
			return tableColumns;
		}
		ColumnMeta mapMeta;
		for (ColumnMeta colMeta : tableColumns) {
			mapMeta = pkMap.get(colMeta.getColName());
			if (mapMeta != null) {
				colMeta.setPK(true);
			}
		}
		return tableColumns;
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return SqlServerDialectUtils.getTables(catalog, schema,
				(tableName != null && "%".equals(tableName)) ? null : tableName, conn, profile);
	}

}
