/**
 * 
 */
package org.sagacity.sqltoy.criteria;

/**
 * @project sqltoy-orm
 * @description sql语句中的比较操作
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Compare.java,Revision:v1.0,Date:2012-8-31
 */
public enum Compare {
	/**
	 * 相等
	 */
	equal("="),

	/**
	 * 大于
	 */
	more(">"),

	/**
	 * 大于等于
	 */
	moreEqual(">="),

	/**
	 * like
	 */
	like(" like "),

	/**
	 * 小于
	 */
	less("<"),
	
	/**
	 * 小于等于
	 */
	lessEqual("<="),
	
	/**
	 * is 判断
	 */
	is(" is ");

	/**
	 * 枚举的值
	 */
	private final String compare;

	private Compare(String compare) {
		this.compare = compare;
	}

	/**
	 * 获取枚举的值
	 * @return
	 */
	public String getValue() {
		return this.compare;
	}

	public String toString() {
		return this.compare;
	}
}
