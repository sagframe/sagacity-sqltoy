package org.sagacity.sqltoy.lambda.engine;

/**
 * @description sql常用聚合函数模板,多表join查询的聚合列使用
 * @author zhongxuchen
 */
public enum SqlFunction {

	AVG("AVG(%s)"), COUNT("COUNT(%s)"), SUM("SUM(%s)"), MIN("MIN(%s)"), MAX("MAX(%s)");

	private final String sql;

	SqlFunction(String sql) {
		this.sql = sql;
	}

	public String getSqlSegment(String column) {
		return String.format(sql, column);
	}
}
