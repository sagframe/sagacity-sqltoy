package org.sagacity.sqltoy.config.model;

/**
 * @project sagacity-sqltoy
 * @description sql类型(区分查询、插入、修改、删除)
 * @author zhongxuchen
 * @version v1.0,Date:2015-03-21
 */
public enum SqlType {
	search("search"),

	insert("insert"),

	update("update"),

	delete("delete");

	private final String sqlType;

	private SqlType(String sqlType) {
		this.sqlType = sqlType;
	}

	public String getValue() {
		return this.sqlType;
	}

	@Override
	public String toString() {
		return this.sqlType;
	}

	/**
	 * 转换给定字符串为枚举主键策略
	 * 
	 * @param sqlType
	 * @return
	 */
	public static SqlType getSqlType(String sqlType) {
		if (sqlType.equalsIgnoreCase(search.getValue())) {
			return search;
		}
		if (sqlType.equalsIgnoreCase(insert.getValue())) {
			return insert;
		}
		if (sqlType.equalsIgnoreCase(update.getValue())) {
			return update;
		}
		if (sqlType.equalsIgnoreCase(delete.getValue())) {
			return delete;
		}
		return search;
	}
}
