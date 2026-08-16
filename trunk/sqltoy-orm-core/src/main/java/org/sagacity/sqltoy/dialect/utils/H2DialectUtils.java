/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供h2数据库相关的特殊逻辑处理封装
 * @author zhongxuchen
 * @version v1.0, Date:2023年6月8日
 * @modify 2023年6月8日,修改说明
 */
public class H2DialectUtils {
	/**
	 * @TODO 主键策略是identity或sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}

	/**
	 * @todo 组织merge into 语句中select 的字段，进行类型转换
	 * @param sql
	 * @param columnName
	 * @param fieldMeta
	 */
	public static void wrapSelectFields(StringBuilder sql, String columnName, FieldMeta fieldMeta) {
		int jdbcType = fieldMeta.getType();
		int length = fieldMeta.getLength();
		if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
				|| jdbcType == java.sql.Types.LONGVARCHAR || jdbcType == java.sql.Types.LONGNVARCHAR) {
			if (length > 0) {
				sql.append("cast(? as VARCHAR(" + length + "))");
			} else {
				sql.append("cast(? as VARCHAR)");
			}
		} else if (jdbcType == java.sql.Types.CHAR || jdbcType == java.sql.Types.NCHAR) {
			if (length > 0) {
				sql.append("cast(? as CHAR(" + length + "))");
			} else {
				sql.append("cast(? as CHAR)");
			}
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
			sql.append("cast(? as INT)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP) {
			sql.append("cast(? as TIMESTAMP)");
		} else if (jdbcType == java.sql.Types.DOUBLE) {
			sql.append("cast(? as DOUBLE PRECISION)");
		} else if (jdbcType == java.sql.Types.FLOAT) {
			sql.append("cast(? as DOUBLE PRECISION)");
		} else if (jdbcType == java.sql.Types.REAL) {
			sql.append("cast(? as REAL)");
		} else if (jdbcType == java.sql.Types.TIME) {
			sql.append("cast(? as TIME)");
		} else if (jdbcType == java.sql.Types.CLOB) {
			sql.append("cast(? as CLOB)");
		} else if (jdbcType == java.sql.Types.BOOLEAN) {
			sql.append("cast(? as BOOLEAN)");
		} else if (jdbcType == java.sql.Types.BINARY) {
			sql.append("cast(? as BINARY)");
		} else if (jdbcType == java.sql.Types.BLOB) {
			sql.append("cast(? as BLOB)");
		} else if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			sql.append("cast(? as JSON)");
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
}
