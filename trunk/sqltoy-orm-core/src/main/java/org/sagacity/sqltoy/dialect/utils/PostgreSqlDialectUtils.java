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
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.NativeTypeUtils;
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
	 * @param profile
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
		StringBuilder sql = DialectUtils.openFastWrap(sqlToyConfig, dialect);
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// sql中是否存在排序或union
		boolean hasOrderOrUnion = DialectUtils.hasOrderByOrUnion(innerSql);
		// 给原始sql标记上特殊的开始和结尾，便于sql拦截器快速定位到原始sql并进行条件补充
		innerSql = SqlUtilsExt.markOriginalSql(innerSql);
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
	 * @param profile
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
	 * @param profile
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
	 * @param profile
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
		// update 2026-11-21 特殊族nativeType跨方言翻译前置:quickvo按源库生成(如pg的jsonb列生成'
		// String字段+nativeType=jsonb),按jdbcType分支裸?或原样cast在目标方言产生类型错配
		// (实测pg上COALESCE(varchar,jsonb)报错);族可翻译按目标cast,数组族null回落原链
		// (h2进组件链),族不可识别(null)保持原链nativeType精确控制
		String specialCast = (fieldMeta.getSpecialFamily() == null) ? null
				: NativeTypeUtils.specialCastType(DataSourceUtils.DBType.POSTGRESQL, fieldMeta.getSpecialFamily(),
						fieldMeta.getNativeType());
		if (StringUtil.isNotBlank(specialCast)) {
			sql.append("cast(? as " + specialCast + ")");
		} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
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
		} else if (jdbcType == JdbcTypes.UUID) {
			// update 2026-11-22 uuid列jdbcType分派(族parse期上调可达):pg内核系/h2原生列
			// cast定型;其余方言uuid为varchar(36)承载列裸?即正确(else的nativeType原样cast
			// 在dm/db2等目标非法,须在此拦截)
			sql.append("cast(? as uuid)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
			// update 2026-11-22 2014此前落else以nativeType原样cast(? as timestamptz),pg合法但
			// 属侥幸路径,显式定型(与DDL case对齐)
			sql.append("cast(? as timestamptz)");
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
			// update 2026-11-21 到达此处族必为null(可翻译族已在前置分支处理),nativeType原样
			// 透传仅限族不可识别的精确控制场景(如dm具名VARRAY——虽不会经本pg系工具,语义统一)
			if (StringUtil.isNotBlank(fieldMeta.getNativeType())) {
				sql.append("cast(? as " + fieldMeta.getNativeType() + ")");
			} else {
				// update 2026-11-21 数组列按组件类型cast定型(本else分支服务postgresql/kingbase/highgo):
				// pg系裸?靠绑定期定型(createArrayOf/驱动推断携带oid)本可工作,显式cast令using-select
				// 列型与绑定产出(varchar[]/integer[]等)一致并兜底弱类型绑定路径;cast元素类型名与
				// SqlUtil.setArray的createArrayOf口径对齐(varchar/integer/bigint/numeric/float/float8/
				// bool/int2);未识别组件(日期时间等)维持裸?走@Column(nativeType=..)口子
				String pgElementType = pgArrayElementTypeName(fieldMeta.getArrayComponentType());
				if (pgElementType != null) {
					sql.append("cast(? as " + pgElementType + "[])");
				} else {
					sql.append("?");
				}
			}
		}
		sql.append(" as ");
		sql.append(columnName);
	}

	/**
	 * update 2026-11-21 数组/集合字段组件类型对应的pg系数组元素类型名(与SqlUtil.setArray的
	 * createArrayOf类型名口径对齐,供postgresql/kingbase/highgo/openGauss系merge using-select
	 * 的数组列cast定型),未识别(含null)返回null维持裸?现状
	 *
	 * @param componentType 组件类型全限定名(含原始类型简名如int)
	 * @return pg元素类型名(varchar/integer/bigint/numeric/float/float8/bool/int2),未识别null
	 */
	public static String pgArrayElementTypeName(String componentType) {
		if (componentType == null) {
			return null;
		}
		switch (componentType) {
		case "java.lang.String":
		case "char":
			// update 2026-11-22 java.lang.Character 移出元素映射:Character[]/List<Character> 既无
			// 驱动绑定能力(pgjdbc setObject(Character[],ARRAY)报Cannot cast,真库实证;List<Character>
			// 实际以Object[]到达setObject同样报错),读回也无法还原成Character型集合,自动推断数组列
			// 只会造出建得出、绑不上、读不回的三不列;需要该形态时用String[]/List<String>,或以
			// @Column(nativeType="varchar[]")显式声明后自行保证参数为String[]
			return "varchar";
		case "java.lang.Integer":
		case "int":
			return "integer";
		case "java.lang.Long":
		case "long":
		case "java.math.BigInteger":
			return "bigint";
		case "java.math.BigDecimal":
			return "numeric";
		case "java.lang.Float":
		case "float":
			// update 2026-11-22 显式float8:pg的float即double precision(仅float(1..24)才是real),
			// 原名"float"语义易误读且与h2的REAL ARRAY口径看似冲突;保持既有8字节绑定语义
			// (SqlUtil.setArray的createArrayOf("float8"))不变,仅将DDL/merge cast类型名写明
			return "float8";
		case "java.lang.Double":
		case "double":
			return "float8";
		case "java.lang.Boolean":
		case "boolean":
			return "bool";
		case "java.lang.Short":
		case "short":
		case "java.lang.Byte":
		case "byte":
			return "int2";
		default:
			return null;
		}
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
	 * @param profile
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
