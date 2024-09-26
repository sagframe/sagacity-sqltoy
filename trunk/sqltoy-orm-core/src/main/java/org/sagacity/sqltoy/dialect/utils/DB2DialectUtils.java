package org.sagacity.sqltoy.dialect.utils;

import java.sql.Connection;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 提供db2数据库通用的操作功能实现,为不同版本提供支持
 * @author zhongxuchen
 * @version v1.0,Date:2015年2月28日
 */
public class DB2DialectUtils {

	/**
	 * @todo 提供随机记录查询
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
			Connection conn, final Integer dbType, final String dialect, final int fetchSize, final int maxRows)
			throws Exception {
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
		sql.append(" order by rand() fetch first ");
		sql.append(randomCount);
		sql.append(" rows only ");
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

	// 新的驱动级别无需转换(目前保留2023-06-09)
	/**
	 * @todo 组织merge into 语句中select 的字段，进行类型转换
	 * @param sql
	 * @param columnName
	 * @param fieldMeta
	 */
	public static void wrapSelectFields(StringBuilder sql, String columnName, FieldMeta fieldMeta) {
		int jdbcType = fieldMeta.getType();
		int length = fieldMeta.getLength();
		if (jdbcType == java.sql.Types.VARCHAR) {
			sql.append("?");
			// sql.append("cast(? as VARCHAR(" + length + "))");
		} else if (jdbcType == java.sql.Types.CHAR) {
			sql.append("?");
			// sql.append("cast(? as CHAR(" + length + "))");
		} else if (jdbcType == java.sql.Types.DATE) {
			sql.append("cast(? as DATE)");
		} else if (jdbcType == java.sql.Types.NUMERIC) {
			sql.append("cast(? as DECIMAL)");
		} else if (jdbcType == java.sql.Types.DECIMAL) {
			sql.append("cast(? as DECIMAL)");
		} else if (jdbcType == java.sql.Types.BIGINT) {
			sql.append("cast(? as BIGINT)");
		} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.TINYINT) {
			sql.append("cast(? as INTEGER)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP) {
			sql.append("cast(? as TIMESTAMP)");
		} else if (jdbcType == java.sql.Types.DOUBLE) {
			sql.append("cast(? as DOUBLE)");
		} else if (jdbcType == java.sql.Types.FLOAT) {
			sql.append("cast(? as DOUBLE)");
		} else if (jdbcType == java.sql.Types.TIME) {
			sql.append("cast(? as TIME)");
		} else if (jdbcType == java.sql.Types.CLOB) {
			sql.append("?");
			// sql.append("cast(? as CLOB(" + length + "))");
		} else if (jdbcType == java.sql.Types.BOOLEAN) {
			sql.append("cast(? as BOOLEAN)");
		} else if (jdbcType == java.sql.Types.BINARY) {
			sql.append("cast(? as BINARY LARGE OBJECT(" + length + "))");
		} else if (jdbcType == java.sql.Types.BLOB) {
			sql.append("?");
			// sql.append("cast(? as BLOB(" + length + "))");
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

	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}
}
