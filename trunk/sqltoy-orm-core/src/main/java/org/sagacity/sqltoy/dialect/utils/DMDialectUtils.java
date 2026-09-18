package org.sagacity.sqltoy.dialect.utils;

import java.sql.Types;

import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 针对dm数据库提供通用工具类
 * @author zhongxuchen
 * @version v1.0,Date:2020-07-30
 * @modify Date:2020-07-30,修改说明
 */
public class DMDialectUtils {
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
		// identity字段不能手工赋值(2023-6-2 ,需:set IDENTITY_INSERT tableName on)
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}

	/**
	 * 针对merge into using(select ...)场景，对字段进行cast(? as type)类型转换
	 * 
	 * @param sql
	 * @param columnName
	 * @param fieldMeta
	 */
	public static void wrapSelectFields(StringBuilder sql, String columnName, FieldMeta fieldMeta) {
		int jdbcType = fieldMeta.getType();
		if (jdbcType == Types.VARCHAR || jdbcType == Types.NVARCHAR || jdbcType == Types.LONGVARCHAR
				|| jdbcType == Types.LONGNVARCHAR) {
			sql.append("?");
		} else if (jdbcType == Types.CHAR || jdbcType == Types.NCHAR) {
			sql.append("?");
		} else if (jdbcType == Types.DATE) {
			sql.append("cast(? as DATE)");
		} else if (jdbcType == Types.NUMERIC) {
			sql.append("cast(? as NUMERIC)");
		} else if (jdbcType == Types.DECIMAL) {
			sql.append("cast(? as DECIMAL)");
		} else if (jdbcType == Types.BIGINT) {
			sql.append("cast(? as BIGINT)");
		} else if (jdbcType == Types.INTEGER || jdbcType == Types.TINYINT || jdbcType == Types.SMALLINT) {
			sql.append("cast(? as INT)");
		} else if (jdbcType == Types.TIMESTAMP) {
			sql.append("cast(? as TIMESTAMP)");
		} else if (jdbcType == Types.DOUBLE) {
			sql.append("cast(? as DOUBLE)");
		} else if (jdbcType == Types.FLOAT) {
			sql.append("cast(? as FLOAT)");
		} else if (jdbcType == Types.REAL) {
			sql.append("cast(? as REAL)");
		} else if (jdbcType == Types.TIME) {
			sql.append("cast(? as TIME)");
		} else if (jdbcType == Types.CLOB) {
			sql.append("cast(? as CLOB)");
		} else if (jdbcType == Types.BOOLEAN) {
			sql.append("cast(? as BIT)");
		} else if (jdbcType == Types.BINARY || jdbcType == Types.BLOB) {
			sql.append("cast(? as BLOB)");
		} else if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			// dm数据库json底层以CLOB存储
			sql.append("cast(? as CLOB)");
		} else if (jdbcType == JdbcTypes.GEOMETRY) {
			// update 2026-9-6 实测DM(DMGEO扩展SP_INIT_GEO_SYS初始化后)的ST_Geometry列
			// 裸?+setString报"类型转换异常",须DMGEO.ST_GeomFromText(wkt,srid)包装
			sql.append("DMGEO.ST_GeomFromText(?,0)");
		} else {
			if (StringUtil.isNotBlank(fieldMeta.getNativeType())) {
				sql.append("cast(? as ").append(fieldMeta.getNativeType()).append(")");
			} else {
				sql.append("?");
			}
		}
		sql.append(" as ");
		sql.append(columnName);
	}
}
