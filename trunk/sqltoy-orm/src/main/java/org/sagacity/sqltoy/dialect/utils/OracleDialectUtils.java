/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.CallableStatementResultHandler;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;

import oracle.jdbc.OracleTypes;

/**
 * @project sqltoy-orm
 * @description 提供基于oracle广泛应用的数据库的一些通用的逻辑处理,避免大量重复代码
 * @author zhongxuchen
 * @version v1.0,Date:2014年12月26日
 */
@SuppressWarnings("rawtypes")
public class OracleDialectUtils {

	/**
	 * @todo 加载单个对象
	 * @param sqlToyContext
	 * @param entity
	 * @param cascadeTypes
	 * @param lockMode
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Serializable load(final SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, final Integer dbType, final String dialect,
			String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search,
				dialect, null);
		String loadSql = sqlToyConfig.getSql(dialect);
		loadSql = loadSql.concat(getLockSql(loadSql, dbType, lockMode));
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, onlySubTables,
				cascadeTypes, conn, dbType);
	}

	/**
	 * @todo oracle loadAll 实现
	 * @param sqlToyContext
	 * @param entities
	 * @param onlySubTables
	 * @param cascadeTypes
	 * @param lockMode
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static List<?> loadAll(final SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, Connection conn, final Integer dbType, String tableName,
			final int fetchSize, final int maxRows) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, lockMode, conn, dbType,
				tableName, (sql, dbTypeValue, lockedMode) -> {
					return getLockSql(sql, dbTypeValue, lockedMode);
				}, fetchSize, maxRows);
	}

	/**
	 * @TODO 分页查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param pageNo
	 * @param pageSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		StringBuilder sql = new StringBuilder();
		boolean isNamed = sqlToyConfig.isNamedParam();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// 是否有order by,update 2017-5-22
		boolean hasOrderBy = SqlUtil.hasOrderBy(innerSql, true);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		// order by 外包裹一层,确保查询结果是按排序
		if (hasOrderBy) {
			sql.append(" select " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
		}
		sql.append(innerSql);
		if (hasOrderBy) {
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		}
		sql.append(" offset ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		sql.append(" rows fetch next ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		sql.append(" rows only ");
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), (pageNo - 1) * pageSize, Long.valueOf(pageSize), dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.page : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * @todo 实现top记录查询
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param topSize
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Integer topSize, Connection conn,
			final Integer dbType, final String dialect, final int fetchSize, final int maxRows) throws Exception {
		StringBuilder sql = new StringBuilder();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// 是否有order by
		boolean hasOrderBy = SqlUtil.hasOrderBy(innerSql, true);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		// order by 外包裹一层,确保查询结果是按排序
		if (hasOrderBy) {
			sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
		}
		sql.append(innerSql);
		if (hasOrderBy) {
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		}
		sql.append(" fetch first ");
		sql.append(topSize);
		sql.append(" rows only");
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.top : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * @todo 取随机记录
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param totalCount
	 * @param randomCount
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param fetchSize
	 * @param maxRows
	 * @return
	 * @throws Exception
	 */
	public static QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
		// 注：dbms_random包需要手工安装，位于$ORACLE_HOME/rdbms/admin/dbmsrand.sql
		StringBuilder sql = new StringBuilder();
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		// 存在order 或union 则在sql外包裹一层
		if (hasOrderOrUnion) {
			sql.append("select * from (");
			sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from ( ");
			sql.append(innerSql);
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" order by dbms_random.random )");
		} else {
			sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from ( ");
			sql.append(innerSql);
			sql.append(" order by dbms_random.random) ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		}
		sql.append(" where rownum<=");
		sql.append(randomCount);

		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null, dialect);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		// 增加sql执行拦截器 update 2022-9-10
		queryParam = DialectUtils.doInterceptors(sqlToyContext, sqlToyConfig,
				(extend.entityClass == null) ? OperateType.random : OperateType.singleTable, queryParam,
				extend.entityClass, dbType);
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend, decryptHandler, conn, dbType, 0, fetchSize, maxRows);
	}

	/**
	 * @todo <b>oracle 存储过程调用，inParam需放在outParam前面(oracle存储过程返回结果必须用out
	 *       参数返回，返回结果集则out 参数类型必须是OracleTypes.CURSOR,相对其他数据库比较特殊 )</b>
	 * @param sqlToyConfig
	 * @param sqlToyContext
	 * @param storeSql
	 * @param inParamValues
	 * @param outParamTypes
	 * @param conn
	 * @param dbType
	 * @param fetchSize
	 * @return
	 * @throws Exception
	 */
	public static StoreResult executeStore(final SqlToyConfig sqlToyConfig, final SqlToyContext sqlToyContext,
			final String storeSql, final Object[] inParamValues, final Integer[] outParamTypes,
			final boolean moreResult, final Connection conn, final Integer dbType, final int fetchSize)
			throws Exception {
		CallableStatement callStat = null;
		ResultSet rs = null;
		return (StoreResult) SqlUtil.callableStatementProcess(null, callStat, rs, new CallableStatementResultHandler() {
			@Override
			public void execute(Object obj, CallableStatement callStat, ResultSet rs) throws Exception {
				callStat = conn.prepareCall(storeSql);
				if (fetchSize > 0) {
					callStat.setFetchSize(fetchSize);
				}
				SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, callStat, inParamValues, null, 0);
				int cursorIndex = -1;
				int cursorCnt = 0;
				int inCount = (inParamValues == null) ? 0 : inParamValues.length;
				int outCount = (outParamTypes == null) ? 0 : outParamTypes.length;
				// 记录输出集合的index
				List<Integer> cursorIndexes = new ArrayList<Integer>();
				// 注册输出参数
				if (outCount != 0) {
					for (int i = 0; i < outCount; i++) {
						callStat.registerOutParameter(i + inCount + 1, outParamTypes[i]);
						if (OracleTypes.CURSOR == outParamTypes[i].intValue()) {
							cursorCnt++;
							cursorIndex = i;
							cursorIndexes.add(i);
						}
					}
				}
				callStat.execute();
				StoreResult storeResult = new StoreResult();
				// 只返回最后一个CURSOR 类型的数据集
				if (cursorIndex != -1) {
					if (moreResult) {
						List<String[]> labelsList = new ArrayList<String[]>();
						List<String[]> labelTypesList = new ArrayList<String[]>();
						List<List> dataSets = new ArrayList<List>();
						int meter = 0;
						SqlToyConfig notFirstConfig = new SqlToyConfig(sqlToyConfig.getId(), sqlToyConfig.getSql());
						for (int outIndex : cursorIndexes) {
							rs = (ResultSet) callStat.getObject(inCount + outIndex + 1);
							if (rs != null) {
								QueryResult tempResult = ResultUtils.processResultSet(sqlToyContext,
										(meter == 0) ? sqlToyConfig : notFirstConfig, conn, rs, null, null, null, 0);
								labelsList.add(tempResult.getLabelNames());
								labelTypesList.add(tempResult.getLabelTypes());
								dataSets.add(tempResult.getRows());
								meter++;
							}
						}
						storeResult.setLabelsList(labelsList);
						storeResult.setLabelTypesList(labelTypesList);
						List[] moreResults = new List[dataSets.size()];
						dataSets.toArray(moreResults);
						storeResult.setMoreResults(moreResults);
						// 默认第一个集合作为后续sql 配置处理的对象(如缓存翻译、格式化等)
						if (dataSets.size() > 0) {
							storeResult.setLabelNames(labelsList.get(0));
							storeResult.setLabelTypes(labelTypesList.get(0));
							storeResult.setRows(dataSets.get(0));
						}
					} else {
						rs = (ResultSet) callStat.getObject(inCount + cursorIndex + 1);
						if (rs != null) {
							QueryResult tempResult = ResultUtils.processResultSet(sqlToyContext, sqlToyConfig, conn, rs,
									null, null, null, 0);
							storeResult.setLabelNames(tempResult.getLabelNames());
							storeResult.setLabelTypes(tempResult.getLabelTypes());
							storeResult.setRows(tempResult.getRows());
						}
					}
				}

				// 有返回参数(CURSOR 的类型不包含在内)
				if (outCount != 0) {
					Object[] outParams = new Object[outCount - cursorCnt];
					int index = 0;
					for (int i = 0; i < outCount; i++) {
						if (OracleTypes.CURSOR != outParamTypes[i].intValue()) {
							// 存储过程自动分页第一个返回参数是总记录数
							outParams[index] = callStat.getObject(i + inCount + 1);
							index++;
						}
					}
					storeResult.setOutResult(outParams);
				}
				this.setResult(storeResult);
				if (rs != null) {
					rs.close();
					rs = null;
				}
				if (callStat != null) {
					callStat.close();
					callStat = null;
				}
			}
		});
	}

	public static String getLockSql(String sql, Integer dbType, LockMode lockMode) {
		// 判断是否已经包含for update
		if (lockMode == null || SqlUtil.hasLock(sql, dbType)) {
			return "";
		}
		if (lockMode == LockMode.UPGRADE_NOWAIT) {
			return " for update nowait ";
		}
		if (lockMode == LockMode.UPGRADE_SKIPLOCK) {
			return " for update skip locked";
		}
		return " for update ";
	}

	@SuppressWarnings("unchecked")
	public static List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		List<ColumnMeta> tableColumns = DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, dbType,
				dialect);
		String sql = "SELECT COLUMN_NAME,COMMENTS FROM USER_COL_COMMENTS WHERE TABLE_NAME=?";
		PreparedStatement pst = conn.prepareStatement(sql);
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		Map<String, String> colMap = (Map<String, String>) SqlUtil.preparedStatementProcess(null, pst, rs,
				new PreparedStatementResultHandler() {
					@Override
					public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
						pst.setString(1, tableName);
						rs = pst.executeQuery();
						Map<String, String> colComments = new HashMap<String, String>();
						String comment;
						while (rs.next()) {
							comment = rs.getString("COMMENTS");
							if (comment != null) {
								colComments.put(rs.getString("COLUMN_NAME").toUpperCase(), comment);
							}
						}
						this.setResult(colComments);
						if (rs != null) {
							rs.close();
							rs = null;
						}
					}
				});
		for (ColumnMeta col : tableColumns) {
			col.setComments(colMap.get(col.getColName().toUpperCase()));
		}
		return tableColumns;
	}

	@SuppressWarnings("unchecked")
	public static List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			Integer dbType, String dialect) throws Exception {
		String sql = "select * from user_tab_comments";
		if (StringUtil.isNotBlank(tableName)) {
			sql = sql.concat(" where TABLE_NAME like ?");
		}
		PreparedStatement pst = conn.prepareStatement(sql);
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		return (List<TableMeta>) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
				if (StringUtil.isNotBlank(tableName)) {
					if (tableName.contains("%")) {
						pst.setString(1, tableName);
					} else {
						pst.setString(1, "%" + tableName + "%");
					}
				}
				rs = pst.executeQuery();
				List<TableMeta> tables = new ArrayList<TableMeta>();
				while (rs.next()) {
					TableMeta tableMeta = new TableMeta();
					tableMeta.setTableName(rs.getString("TABLE_NAME"));
					tableMeta.setType(rs.getString("TABLE_TYPE"));
					tableMeta.setRemarks(rs.getString("COMMENTS"));
					tables.add(tableMeta);
				}
				this.setResult(tables);
				if (rs != null) {
					rs.close();
					rs = null;
				}
			}
		});
	}

	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		return true;
	}
}
