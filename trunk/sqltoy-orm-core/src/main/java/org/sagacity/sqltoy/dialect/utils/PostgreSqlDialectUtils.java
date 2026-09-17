package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.PreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.SavePKStrategy;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供postgresql数据库共用的逻辑实现，便于今后postgresql不同版本之间共享共性部分的实现
 * @author zhongxuchen
 * @version v1.0,Date:2015-03-05
 * @modify Date:2020-06-12 修复10+版本对identity主键生成的策略
 */
public class PostgreSqlDialectUtils {
	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "COALESCE";

	/**
	 * 提供随机记录查询
	 * 
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param decryptHandler
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
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		String dialect = profile.getDialect();
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
			sql.append("select " + SqlToyConstants.INTERMEDIATE_TABLE + ".* from (");
		}
		sql.append(innerSql);
		if (hasOrderOrUnion) {
			sql.append(") ");
			sql.append(SqlToyConstants.INTERMEDIATE_TABLE);
			sql.append(" ");
		}
		sql.append(" order by random() limit ");
		sql.append(randomCount);

		DialectUtils.closeFastWrap(sqlToyConfig, dialect, sql);
		return DialectUtils.executeWrappedQuery(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, conn,
				profile, sql.toString(), null, null,
				(queryExecutor.getInnerModel().entityClass == null) ? OperateType.random : OperateType.singleTable,
				fetchSize, maxRows);
	}

	/**
	 * 保存单条对象记录
	 * 
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			String tableName) throws Exception {
		// 只支持sequence模式
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		String sequence = "nextval('" + entityMeta.getSequence() + "')";
		// save行为根据主键是否赋值情况调整最终的主键策略
		PKStrategy pkStrategy = DialectUtils.getSavePKStrategy(entityMeta, entity, profile);
		boolean isAssignPK = allowAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, sequence, isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile,
								entityMeta, pkStrategy, sequence, allowAssignPKValue(pkStrategy), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								allowAssignPKValue(entityMeta.getIdStrategy()));
					}
				}, conn, profile);
	}

	/**
	 * 批量保存对象入数据库
	 * 
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = "nextval('" + entityMeta.getSequence() + "')";
		boolean isAssignPK = allowAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, sequence, isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entities, batchSize,
				reflectPropsHandler, conn, profile, autoCommit);
	}

	/**
	 * postgresql15 开始支持merge into 语法
	 * 
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param forceUpdateFields
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, DBProfile profile,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, pkStrategy, forceUpdateFields, null, sequence,
								allowAssignPKValue(pkStrategy), tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/**
	 * 组织merge into 语句中select 的字段，进行类型转换
	 * 
	 * @param sql
	 * @param columnName
	 * @param fieldMeta
	 */
	public static void wrapSelectFields(StringBuilder sql, String columnName, FieldMeta fieldMeta) {
		int jdbcType = fieldMeta.getType();
		if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
				|| jdbcType == java.sql.Types.LONGVARCHAR || jdbcType == java.sql.Types.LONGNVARCHAR) {
			sql.append("?");
		} else if (jdbcType == java.sql.Types.CHAR || jdbcType == java.sql.Types.NCHAR) {
			sql.append("?");
		} else if (jdbcType == java.sql.Types.DATE) {
			sql.append("cast(? as date)");
		} else if (jdbcType == java.sql.Types.NUMERIC) {
			sql.append("cast(? as numeric)");
		} else if (jdbcType == java.sql.Types.DECIMAL) {
			sql.append("cast(? as decimal)");
		} else if (jdbcType == java.sql.Types.BIGINT) {
			sql.append("cast(? as bigint)");
		} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.TINYINT
				|| jdbcType == java.sql.Types.SMALLINT) {
			sql.append("cast(? as integer)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP) {
			sql.append("cast(? as timestamp)");
		} else if (jdbcType == java.sql.Types.DOUBLE) {
			sql.append("cast(? as double precision)");
		} else if (jdbcType == java.sql.Types.FLOAT) {
			sql.append("cast(? as double precision)");
		} else if (jdbcType == java.sql.Types.REAL) {
			sql.append("cast(? as real)");
		} else if (jdbcType == java.sql.Types.TIME) {
			sql.append("cast(? as time)");
		} else if (jdbcType == java.sql.Types.CLOB) {
			sql.append("cast(? as text)");
		} else if (jdbcType == java.sql.Types.BOOLEAN) {
			sql.append("cast(? as boolean)");
		} else if (jdbcType == java.sql.Types.BINARY) {
			sql.append("cast(? as bytea)");
		} else if (jdbcType == java.sql.Types.BLOB) {
			sql.append("cast(? as bytea)");
		} else if (jdbcType == JdbcTypes.JSON) {
			sql.append("cast(? as json)");
		} else if (jdbcType == JdbcTypes.JSONB) {
			sql.append("cast(? as jsonb)");
		} else if (jdbcType == JdbcTypes.VECTOR) {
			// vector类型参数通过PGobject包装后类型已正确,cast用于兜底setString等字符串参数场景
			sql.append("cast(? as vector)");
		} else if (jdbcType == JdbcTypes.GEOMETRY) {
			// geometry类型参数通过PGobject包装后类型已正确,cast用于兜底setString等字符串参数场景
			sql.append("cast(? as geometry)");
		} else {
			// 数组、json等特殊类型
			if (StringUtil.isNotBlank(fieldMeta.getNativeType())) {
				sql.append("cast(? as " + fieldMeta.getNativeType() + ")");
			} else {
				sql.append("?");
			}
		}
		sql.append(" as ");
		sql.append(columnName);
	}

	/**
	 * 主键策略是identity或sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * 
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// postgresql10+ 支持identity，但不能直接赋值
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}

	/**
	 * postgresql 获取表和view信息，表排除分区子表
	 * 
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		// v10 支持 AND c.relispartition = false
		// <v10 用 AND c.oid NOT IN (SELECT inhrelid FROM pg_inherits)
		String sql = """
					SELECT
						  c.relname AS TABLE_NAME,
						  CASE c.relkind
						    WHEN 'r' THEN 'TABLE'
						    WHEN 'p' THEN 'TABLE'
						    WHEN 'v' THEN 'VIEW'
						  END AS TABLE_TYPE,
						  d.description AS COMMENTS
					FROM pg_class c
						LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0
						JOIN pg_namespace n ON n.oid = c.relnamespace
					WHERE
					  c.relkind IN ('r', 'v', 'p')
					  AND c.oid NOT IN (SELECT inhrelid FROM pg_inherits)
				""";
		String realSchema = schema;
		if (StringUtil.isBlank(realSchema)) {
			realSchema = catalog;
		}
		// schema/表名统一用?绑定,避免拼接被单引号破坏或注入
		List<Object> paramValues = new ArrayList<Object>();
		if (StringUtil.isNotBlank(realSchema)) {
			sql = sql.concat(" AND n.nspname=? ");
			paramValues.add(realSchema);
		} else {
			sql = sql.concat(" AND c.relname NOT LIKE 'pg_%' AND n.nspname NOT LIKE 'pg_%' ");
		}
		if (StringUtil.isNotBlank(tableName)) {
			if (tableName.contains("%")) {
				sql = sql.concat(" AND c.relname like ?");
				paramValues.add(tableName);
			} else {
				sql = sql.concat(" AND c.relname like ?");
				paramValues.add("%" + tableName + "%");
			}
		}
		PreparedStatement pst = conn.prepareStatement(sql);
		for (int i = 0; i < paramValues.size(); i++) {
			pst.setObject(i + 1, paramValues.get(i));
		}
		// 设置全局statementTimeout，默认为null
		if (SqlToyConstants.defaultStatementTimeout != null && SqlToyConstants.defaultStatementTimeout > 0) {
			pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
		}
		ResultSet rs = null;
		// 通过preparedStatementProcess反调，第二个参数是pst
		return (List<TableMeta>) SqlUtil.preparedStatementProcess(null, pst, rs, new PreparedStatementResultHandler() {
			@Override
			public void execute(Object rowData, PreparedStatement pst, ResultSet rs) throws Exception {
				try {
					rs = pst.executeQuery();
					List<TableMeta> tables = new ArrayList<TableMeta>();
					while (rs.next()) {
						TableMeta tableMeta = new TableMeta();
						tableMeta.setTableName(rs.getString("TABLE_NAME"));
						tableMeta.setType(rs.getString("TABLE_TYPE"));
						tableMeta.setRemarks(StringUtil.escapeComment(rs.getString("COMMENTS")));
						tables.add(tableMeta);
					}
					this.setResult(tables);
				} catch (Exception e) {
					throw e;
				} finally {
					if (rs != null) {
						rs.close();
						rs = null;
					}
				}
			}
		});
	}

}
