/**
 * 
 */
package org.sagacity.sqltoy.model;

/**
 * @project sagacity-sqltoy
 * @description 日期处理
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020年7月30日
 * @modify 2020年7月30日,修改说明
 */
public enum DateType {
	// 年的最后一天
	FIRST_OF_YEAR("FIRST_OF_YEAR"), LAST_OF_YEAR("LAST_OF_YEAR"),
	// 月的第一天
	FIRST_OF_MONTH("FIRST_OF_MONTH"),

	LAST_OF_MONTH("LAST_OF_MONTH"),
	// 月的第一天
	FIRST_OF_WEEK("FIRST_OF_WEEK"),

	LAST_OF_WEEK("LAST_OF_WEEK"),

	LocalDate("LocalDate"), LocalDateTime("LocalDateTime");

	private final String dateType;

	private DateType(String dateType) {
		this.dateType = dateType;
	}

	public String getValue() {
		return this.dateType;
	}

}
