/**
 * 
 */
package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.config.model.TableCascadeModel;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.SapIQDialectUtils;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description Sybase IQ数据库blob 长度过长的需要设置(set option
 *              public.ENABLE_LOB_VARIABLES='ON') url上需要指定参数：&textsize=204801024
 * @author zhongxuchen
 * @version v1.0,Date:2013-8-29
 * @modify Date:2013-8-29 {填写修改说明}
 */
@SuppressWarnings({ "rawtypes" })
public class SybaseIQDialect implements Dialect {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(SybaseIQDialect.class);

	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "isnull";

	@Override
	public boolean isUnique(final SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed,
			Connection conn, final Integer dbType, final String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table);
					return queryStr.replaceFirst("(?i)select ", "select top " + topSize + " ");
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org. sagacity
	 * .sqltoy.SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		SqlWithAnalysis sqlWith = new SqlWithAnalysis(sqlToyConfig.getSql(dialect));
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		QueryResult queryResult = null;
		boolean isNamed = sqlToyConfig.isNamedParam();
		String tmpTable = "#SAG_TMP_" + System.nanoTime();
		// 组合需要被插入的sql
		String pageSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlWith.getRejectWithSql();
		StringBuilder insertTempSql = new StringBuilder(pageSql.length() + 100);
		/** ===========组织分页sql语句============== */
		// 判断sql中是否有distinct或top
		if (DialectUtils.isComplexPageQuery(pageSql)) {
			insertTempSql.append("select ");
			insertTempSql.append(" sag_tmp_table.* ");
			insertTempSql.append(" into  ");
			insertTempSql.append(tmpTable);
			insertTempSql.append(" from ( ");
			insertTempSql.append(pageSql);
			insertTempSql.append(") sag_tmp_table");
		} else {
			String lowerSql = pageSql.toLowerCase();
			int fastPageFromIndex = StringUtil.getSymMarkIndex("select ", " from", lowerSql, 0);
			String columns = pageSql.substring(lowerSql.indexOf("select ") + 6, fastPageFromIndex);
			insertTempSql.append("select ");
			insertTempSql.append(columns);
			insertTempSql.append(" into ");
			insertTempSql.append(tmpTable).append(" ");
			insertTempSql.append(pageSql.substring(fastPageFromIndex));
		}
		if (sqlToyConfig.isHasWith()) {
			insertTempSql.insert(0, " ");
			insertTempSql.insert(0,
					sqlToyConfig.isHasFast() ? sqlToyConfig.getFastWithSql(dialect) : sqlWith.getWithSql());
		}
		boolean hasCreateTmp = false;
		try {
			// 通过参数处理最终的sql和参数值
			SqlToyResult queryParam = SqlConfigParseUtils.processSql(insertTempSql.toString(),
					extend.getParamsName(sqlToyConfig), extend.getParamsValue(sqlToyContext, sqlToyConfig), dialect);

			// 执行sql将记录插入临时表
			SqlUtil.executeSql(sqlToyContext.getTypeHandler(), queryParam.getSql(), queryParam.getParamsValue(), null,
					conn, dbType, true, false);
			hasCreateTmp = true;
			StringBuilder sql = new StringBuilder();
			sql.append("select ");
			sql.append(" rowid(").append(tmpTable).append(") as page_row_id,");
			sql.append(" * from ");
			sql.append(tmpTable).append(" where page_row_id in (");
			sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
			sql.append(")");
			if (sqlToyConfig.isHasFast()) {
				sql.insert(0, sqlToyConfig.getFastPreSql(dialect) + " ( ");
				sql.append(" ) ");
				sql.append(sqlToyConfig.getFastTailSql(dialect));
				if (sqlToyConfig.getFastWithIndex() != -1) {
					sqlWith = new SqlWithAnalysis(sql.toString());
					sql.delete(0, sql.length() - 1);
					String[] aliasTableAs;
					int index = 0;
					for (int i = sqlToyConfig.getFastWithIndex() + 1; i < sqlWith.getWithSqlSet().size(); i++) {
						aliasTableAs = sqlWith.getWithSqlSet().get(i);
						if (index == 0) {
							sql.append("with ").append(aliasTableAs[3]);
						} else {
							sql.append(",").append(aliasTableAs[3]);
						}
						sql.append(aliasTableAs[0]);
						sql.append(" as ").append(aliasTableAs[1]);
						sql.append(" (");
						sql.append(aliasTableAs[2]);
						sql.append(")");
						index++;
					}
					sql.append(" ").append(sqlWith.getRejectWithSql());
				}
			}
			queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor, sql.toString(),
					NumberUtil.randomArray(totalCount.intValue(), randomCount.intValue()), null, dialect);
			queryResult = findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
					extend.rowCallbackHandler, decryptHandler, conn, null, dbType, dialect, fetchSize, maxRows);
		} catch (Exception e) {
			throw e;
		} finally {
			// 删除临时表
			if (hasCreateTmp) {
				SqlUtil.executeSql(sqlToyContext.getTypeHandler(), "drop table ".concat(tmpTable), null, null, conn,
						dbType, true, false);
			}
		}
		return queryResult;
	}

	/**
	 * @todo sybase iq15.4之后通过set option public.reserved_keywords='limit'支持limit分页
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageNo
	 * @param pageSize
	 * @param conn
	 * @return
	 * @throws Exception
	 */
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		StringBuilder sql = new StringBuilder();
		boolean isNamed = sqlToyConfig.isNamedParam();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
			sql.append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		sql.append(" offset ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), Long.valueOf(pageSize), (pageNo - 1) * pageSize, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		return findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend.rowCallbackHandler, decryptHandler, conn, null, dbType, dialect, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, double, java.sql.Connection)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
		StringBuilder sql = new StringBuilder();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		String minSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		String partSql = " select top " + topSize + " ";
		if (sqlToyConfig.isHasWith()) {
			SqlWithAnalysis sqlWith = new SqlWithAnalysis(minSql);
			sql.append(sqlWith.getWithSql());
			minSql = sqlWith.getRejectWithSql();
		}
		boolean hasUnion = false;
		if (sqlToyConfig.isHasUnion()) {
			hasUnion = SqlUtil.hasUnion(minSql, false);
		}
		if (hasUnion) {
			sql.append(partSql);
			sql.append(" SAG_Paginationtable.* from (");
			sql.append(minSql);
			sql.append(") as SAG_Paginationtable ");
		} else {
			sql.append(minSql.replaceFirst("(?i)select ", partSql));
		}
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		return findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend.rowCallbackHandler, decryptHandler, conn, null, dbType, dialect, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.
	 * sqltoy.config.model.SqlToyConfig, java.lang.String[], java.lang.Object[],
	 * java.lang.reflect.Type, org.sagacity.sqltoy.callback.RowCallbackHandler,
	 * java.sql.Connection)
	 */
	public QueryResult findBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final RowCallbackHandler rowCallbackHandler,
			final DecryptHandler decryptHandler, final Connection conn, final LockMode lockMode, final Integer dbType,
			final String dialect, final int fetchSize, final int maxRows) throws Exception {
		if (null != lockMode) {
			throw new UnsupportedOperationException("sybase iq lock search," + SqlToyConstants.UN_SUPPORT_MESSAGE);
		}
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, rowCallbackHandler, decryptHandler,
				conn, dbType, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(java.lang .String,
	 * java.lang.String[], java.lang.Object[], java.sql.Connection)
	 */
	@Override
	public Long getCountBySql(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] paramsValue, final boolean isLastSql, final Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, final String[] forceUpdateFields,
			Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				dbType, dialect, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy
	 * .SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long saveOrUpdateAll(final SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// sybase iq 只支持identity模式
		boolean isIdentity = (entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.IDENTITY));
		boolean isOpenIdentity = (isIdentity && SqlToyConstants.sybaseIQIdentityOpen());
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (isOpenIdentity) {
			SqlUtil.executeSql(sqlToyContext.getTypeHandler(),
					"SET TEMPORARY OPTION IDENTITY_INSERT='" + realTable + "'", null, null, conn, dbType, true, false);
		}
		Long updateCount = DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta,
				forceUpdateFields, new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext.getUnifyFieldsHandler(), dbType,
								entityMeta, entityMeta.getIdStrategy(), forceUpdateFields, null, NVL_FUNCTION,
								entityMeta.getSequence() + ".NEXTVAL", isAssignPKValue(entityMeta.getIdStrategy()),
								tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
		if (isOpenIdentity) {
			SqlUtil.executeSql(sqlToyContext.getTypeHandler(), "SET TEMPORARY OPTION IDENTITY_INSERT=''", null, null,
					conn, dbType, true, false);
		}
		return updateCount;
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
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// sybase iq 只支持identity模式
		boolean isIdentity = (entityMeta.getIdStrategy() != null
				&& entityMeta.getIdStrategy().equals(PKStrategy.IDENTITY));
		boolean isOpenIdentity = (isIdentity && SqlToyConstants.sybaseIQIdentityOpen());
		if (isOpenIdentity) {
			SqlUtil.executeSql(sqlToyContext.getTypeHandler(),
					"SET TEMPORARY OPTION IDENTITY_INSERT='" + entityMeta.getSchemaTable(tableName, dbType) + "'", null,
					null, conn, dbType, true, false);
		}
		Long updateCount = DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						return DialectExtUtils.mergeIgnore(dbType, entityMeta, entityMeta.getIdStrategy(), null,
								NVL_FUNCTION, entityMeta.getSequence() + ".NEXTVAL",
								isAssignPKValue(entityMeta.getIdStrategy()), tableName);
					}
				}, reflectPropsHandler, conn, dbType, autoCommit);
		if (isOpenIdentity) {
			SqlUtil.executeSql(sqlToyContext.getTypeHandler(), "SET TEMPORARY OPTION IDENTITY_INSERT=''", null, null,
					conn, dbType, true, false);
		}
		return updateCount;
	}

	/*
	 * sybase iq 没有所谓的行锁
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#load(java.io.Serializable,
	 * java.util.List, java.sql.Connection)
	 */
	@Override
	public Serializable load(final SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect);
		String loadSql = sqlToyConfig.getSql(dialect);
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, cascadeTypes,
				conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(java.util.List,
	 * java.util.List, java.sql.Connection)
	 */
	@Override
	public List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName,
			final int fetchSize, final int maxRows) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, cascadeTypes, lockMode, conn, dbType, tableName, null,
				fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.util.List, java.sql.Connection)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return SapIQDialectUtils.save(sqlToyContext, entity, SqlToyConstants.sybaseIQIdentityOpen(), conn, dbType,
				tableName);
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
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		return SapIQDialectUtils.saveAll(sqlToyContext, entities, batchSize, reflectPropsHandler,
				SqlToyConstants.sybaseIQIdentityOpen(), conn, dbType, tableName);
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
			final boolean cascade, final Class[] emptyCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		Long updateCount = DialectUtils.update(sqlToyContext, entity, entityMeta, NVL_FUNCTION, forceUpdateFields, conn,
				dbType, realTable);
		// 级联保存
		if (cascade && !entityMeta.getCascadeModels().isEmpty()) {
			HashMap<Type, String> typeMap = new HashMap<Type, String>();
			if (emptyCascadeClasses != null)
				for (Type type : emptyCascadeClasses) {
					typeMap.put(type, "");
				}
			// 级联子表数据
			List subTableData = null;
			String[] forceUpdateProps = null;
			EntityMeta subEntityMeta;
			for (TableCascadeModel cascadeModel : entityMeta.getCascadeModels()) {
				subEntityMeta = sqlToyContext.getEntityMeta(cascadeModel.getMappedType());
				final String[] mappedFields = cascadeModel.getMappedFields();
				final Object[] mainFieldValues = BeanUtil.reflectBeanToAry(entity, cascadeModel.getFields());
				forceUpdateProps = (subTableForceUpdateProps == null) ? null
						: subTableForceUpdateProps.get(cascadeModel.getMappedType());
				if (cascadeModel.getCascadeType() == 1) {
					subTableData = (List) BeanUtil.getProperty(entity, cascadeModel.getProperty());
				} else {
					subTableData = new ArrayList();
					Object item = BeanUtil.getProperty(entity, cascadeModel.getProperty());
					if (item != null) {
						subTableData.add(item);
					}
				}

				// 针对子表存量数据,调用级联修改的语句，分delete 和update两种操作 1、删除存量数据;2、设置存量数据状态为停用
				if (cascadeModel.getCascadeUpdateSql() != null && ((subTableData != null && !subTableData.isEmpty())
						|| typeMap.containsKey(cascadeModel.getMappedType()))) {
					SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(cascadeModel.getCascadeUpdateSql(),
							mappedFields, mainFieldValues, dialect);
					SqlExecuteStat.debug("对存量数据进行级联修改", null);
					SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(),
							sqlToyResult.getParamsValue(), null, conn, dbType, null, true);
				}
				// 子表数据不为空,采取saveOrUpdateAll操作
				if (subTableData != null && !subTableData.isEmpty()) {
					SqlExecuteStat.debug("执行子表级联更新操作", null);
					logger.info("执行update主表:{} 对应级联子表: {} 更新操作!", tableName, subEntityMeta.getTableName());
					saveOrUpdateAll(sqlToyContext, subTableData, sqlToyContext.getBatchSize(),
							// 设置关联外键字段的属性值(来自主表的主键)
							new ReflectPropsHandler() {
								public void process() {
									for (int i = 0; i < mappedFields.length; i++) {
										this.setValue(mappedFields[i], mainFieldValues[i]);
									}
								}
							}, forceUpdateProps, conn, dbType, dialect, null, null);
				} else {
					logger.info("未执行update主表:{} 对应级联子表: {} 更新操作,子表数据为空!", tableName, subEntityMeta.getTableName());
				}
			}
		}
		return updateCount;
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
			Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception {
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler,
				NVL_FUNCTION, conn, dbType, autoCommit, tableName, false);
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, String[] uniqueProps, Connection conn, Integer dbType, String dialect,
			String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.
	 * SqlToyContext , java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.delete(sqlToyContext, entity, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#deleteAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		return DialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, dbType, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetch(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor,
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, final Integer dbType,
			final String dialect, final LockMode lockMode, final int fetchSize, final int maxRows) throws Exception {
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, updateRowHandler, conn,
				dbType, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findByStore(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.executor.StoreExecutor)
	 */
	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final Connection conn, final Integer dbType,
			final String dialect, final int fetchSize) throws Exception {
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, conn, dbType,
				fetchSize);
	}

	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType, dialect);
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn, Integer dbType,
			String dialect) throws Exception {
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, dbType, dialect);
	}

	private boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// 目前不支持sequence模式
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}

}
