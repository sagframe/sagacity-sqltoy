/**
 * 
 */
package org.sagacity.sqltoy.config.model;

/**
 * @project sqltoy-orm
 * @description sql类型(区分查询、插入、修改、删除)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlType.java,Revision:v1.0,Date:2015年3月21日
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

	public String toString() {
		return this.sqlType;
	}

	/**
	 * @todo 转换给定字符串为枚举主键策略
	 * @param strategy
	 * @return
	 */
	public static SqlType getSqlType(String sqlType) {
		if (sqlType.equalsIgnoreCase(search.getValue()))
			return search;
		else if (sqlType.equalsIgnoreCase(insert.getValue()))
			return insert;
		else if (sqlType.equalsIgnoreCase(update.getValue()))
			return update;
		else if (sqlType.equalsIgnoreCase(delete.getValue()))
			return delete;
		return search;
	}
}
