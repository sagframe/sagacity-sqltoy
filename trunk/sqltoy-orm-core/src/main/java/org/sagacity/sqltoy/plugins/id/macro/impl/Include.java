package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.Map;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils.Dialect;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description 借助id宏功能提供一个sql引用另外一个sql构成一个大sql的功能
 *              <p>
 *              <li>@include(id="adb") where xxx=:xxx</li>
 *              <li>@include("abc") where xxx=:xxx</li>
 *              </p>
 * 
 * @author zhongxuchen
 * @version v1.0,Date:2023年2月1日
 */
public class Include extends AbstractMacro {

	@Override
	public String execute(String[] params, Map<String, Object> keyValues, Object paramValues, String preSql,
			String dialect) {
		if (params.length == 0) {
			return "";
		}
		String sqlId = params[0].replaceAll("\"|\'", "").trim();
		if ("".equals(sqlId)) {
			return "";
		}
		// id="xxx" 模式，切取xxxx
		if (sqlId.contains("=")) {
			sqlId = sqlId.substring(sqlId.indexOf("=") + 1).trim();
		}
		// @include(:paramName) 特殊模式
		if (sqlId.startsWith(":")) {
			// 取出参数名称
			String paramName = sqlId.substring(1).trim();
			if (paramValues == null) {
				return "@blank(:".concat(paramName).concat(")");
			}
			Object paramValue = BeanUtil.reflectBeanToAry(paramValues, new String[] { paramName })[0];
			if (StringUtil.isBlank(paramValue)) {
				return "@blank(:".concat(paramName).concat(")");
			}
			// 输出sql
			return paramValue.toString();
		}
		SqlToyConfig sqlToyConfig = getSqlToyConfig(keyValues, sqlId, dialect);
		if (sqlToyConfig == null) {
			return "(sqlId='" + sqlId + "' not exists!)";
		}
		// 不支持@include(id="abc") where xxx=:xx,"abc" 对应的sql中还包含@include
		// 容易造成死循环
		if (StringUtil.matches(sqlToyConfig.getSql(), SqlToyConstants.INCLUDE_PATTERN)) {
			return "(not support @include(sqlId) multinest!)";
		}
		return sqlToyConfig.getSql();
	}

	/**
	 * @TODO 增加对数据库方言的适配
	 * @param sqlCache
	 * @param sqlId
	 * @param dialect
	 * @return
	 */
	private SqlToyConfig getSqlToyConfig(Map<String, Object> sqlCache, String sqlId, String dialect) {
		SqlToyConfig result = null;
		if (StringUtil.isNotBlank(dialect)) {
			// sqlId_dialect
			result = (SqlToyConfig) sqlCache.get(sqlId.concat("_").concat(dialect));
			// dialect_sqlId
			if (result == null) {
				result = (SqlToyConfig) sqlCache.get(dialect.concat("_").concat(sqlId));
			}
			// 兼容一下sqlserver的命名
			if (result == null && dialect.equals(Dialect.SQLSERVER)) {
				result = (SqlToyConfig) sqlCache.get(sqlId.concat("_mssql"));
				if (result == null) {
					result = (SqlToyConfig) sqlCache.get("mssql_".concat(sqlId));
				}
			} // 兼容一下postgres的命名
			if (result == null && dialect.equals(Dialect.POSTGRESQL)) {
				result = (SqlToyConfig) sqlCache.get(sqlId.concat("_postgres"));
				if (result == null) {
					result = (SqlToyConfig) sqlCache.get("postgres_".concat(sqlId));
				}
			}
		}
		if (result == null) {
			result = (SqlToyConfig) sqlCache.get(sqlId);
		}
		return result;
	}
}
