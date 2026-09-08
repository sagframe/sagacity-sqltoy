package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供db2数据库通用的操作功能实现,为不同版本提供支持
 * @author zhongxuchen
 * @version v1.0,Date:2015-02-28
 */
public class DB2DialectUtils {

	// 新的驱动级别无需转换(目前保留2023-06-09)
	/**
	 * 组织merge into 语句中select 的字段，进行类型转换
	 * 
	 * @param sql
	 * @param columnName
	 * @param fieldMeta
	 */
	public static void wrapSelectFields(StringBuilder sql, String columnName, FieldMeta fieldMeta) {
		int jdbcType = fieldMeta.getType();
		int length = fieldMeta.getLength();
		if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
				|| jdbcType == java.sql.Types.LONGVARCHAR || jdbcType == java.sql.Types.LONGNVARCHAR) {
			sql.append("?");
		} else if (jdbcType == java.sql.Types.CHAR || jdbcType == java.sql.Types.NCHAR) {
			sql.append("?");
		} else if (jdbcType == java.sql.Types.DATE) {
			sql.append("cast(? as DATE)");
		} else if (jdbcType == java.sql.Types.NUMERIC) {
			sql.append("cast(? as DECIMAL)");
		} else if (jdbcType == java.sql.Types.DECIMAL) {
			sql.append("cast(? as DECIMAL)");
		} else if (jdbcType == java.sql.Types.BIGINT) {
			sql.append("cast(? as BIGINT)");
		} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.TINYINT
				|| jdbcType == java.sql.Types.SMALLINT) {
			sql.append("cast(? as INTEGER)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP) {
			sql.append("cast(? as TIMESTAMP)");
		} else if (jdbcType == java.sql.Types.DOUBLE) {
			sql.append("cast(? as DOUBLE)");
		} else if (jdbcType == java.sql.Types.FLOAT) {
			sql.append("cast(? as DOUBLE)");
		} else if (jdbcType == java.sql.Types.REAL) {
			sql.append("cast(? as REAL)");
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
		} else if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			// update 2026-9-6 实测db2 11.5无JSON类型(cast as JSON报SQLCODE=-204 JSON未定义),
			// JSON以字符列承载直接绑定参数(db2 12的JSON列亦接受字符串隐式转换)
			sql.append("?");
		} else if (jdbcType == JdbcTypes.GEOMETRY) {
			// update 2026-9-6 实测db2(db2gse扩展,db2se enable_db启用)的ST_Geometry列
			// 裸?+setString报类型错误,SQL层以db2gse.ST_GeomFromText(wkt,srid)包装,
			// 参数按VARCHAR绑定(含null实测通过)
			sql.append("db2gse.ST_GeomFromText(?,0)");
		} else if (jdbcType == JdbcTypes.VECTOR) {
			// db2 12.1.2+支持vector类型,与应用交互采用'[1,2,3]'字符串形式,cast确保using select子查询列类型正确
			sql.append("cast(? as VECTOR)");
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
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}
}
