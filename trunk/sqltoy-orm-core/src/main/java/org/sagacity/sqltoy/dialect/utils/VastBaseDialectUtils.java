package org.sagacity.sqltoy.dialect.utils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.sagacity.sqltoy.model.JdbcTypes;

import cn.com.vastbase.util.PGobject;

/**
 * @description 针对海量数据库自定义驱动改变了包路径场景，单独提供一个工具类处理差异
 * @date 2026-8-4
 */
public class VastBaseDialectUtils {

	/**
	 * 
	 * @param pst
	 * @param paramIndex
	 * @param jdbcType
	 * @param jsonStr
	 * @throws SQLException
	 */
	public static void setJSONValue(PreparedStatement pst, int paramIndex, int jdbcType, String jsonStr)
			throws SQLException {
		PGobject pgObject = new PGobject();
		pgObject.setType(jdbcType == JdbcTypes.JSONB ? "jsonb" : "json");
		pgObject.setValue(jsonStr);
		pst.setObject(paramIndex, pgObject);
	}

	public static void updateJSON(ResultSet rs, String columnName, int jdbcType, String jsonStr) throws SQLException {
		PGobject pgObject = new PGobject();
		pgObject.setType(jdbcType == JdbcTypes.JSONB ? "jsonb" : "json");
		pgObject.setValue(jsonStr);
		rs.updateObject(columnName, pgObject);
	}
}
