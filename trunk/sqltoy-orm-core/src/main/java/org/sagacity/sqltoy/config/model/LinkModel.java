/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 根据分组字段，将其它字段值进行连接的配置模型
 * @author zhongxuchen
 * @version v1.0,Date:2013-4-8
 * @modify Date:2013-4-8 {填写修改说明}
 */
public class LinkModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6336313366096191304L;

	/**
	 * 需要link的字段
	 */
	private String[] columns;

	/**
	 * link字段之间的分割符
	 */
	private String sign = ",";

	/**
	 * 分组字段(即以哪个字段为参照)
	 */
	private String[] groupColumns;

	/**
	 * 修饰符位置(补长修饰，如：link的字符长度为5,link后要统一成8位的长度)
	 */
	private String decorateAlign;

	/**
	 * 补充字符
	 */
	private String decorateAppendChar;

	/**
	 * 补充到的长度
	 */
	private int decorateSize;

	/**
	 * 是否去除重复
	 */
	private boolean distinct = false;

	/**
	 * 结果类型(-1 表示用特定符号拼接成一个完整字符串) 1:List集合 2:Array数组
	 */
	private int resultType = -1;

	/**
	 * @return the columns
	 */
	public String[] getColumns() {
		return columns;
	}

	/**
	 * @param columns the columns to set
	 */
	public void setColumns(String[] columns) {
		this.columns = columns;
	}

	/**
	 * @return the sign
	 */
	public String getSign() {
		return sign;
	}

	/**
	 * @param sign the sign to set
	 */
	public void setSign(String sign) {
		this.sign = sign;
	}

	public String[] getGroupColumns() {
		return groupColumns;
	}

	public void setGroupColumns(String[] groupColumns) {
		this.groupColumns = groupColumns;
	}

	/**
	 * @return the decorateAlign
	 */
	public String getDecorateAlign() {
		return decorateAlign;
	}

	/**
	 * @param decorateAlign the decorateAlign to set
	 */
	public void setDecorateAlign(String decorateAlign) {
		this.decorateAlign = decorateAlign;
	}

	/**
	 * @return the decorateAppendChar
	 */
	public String getDecorateAppendChar() {
		return decorateAppendChar;
	}

	/**
	 * @param decorateAppendChar the decorateAppendChar to set
	 */
	public void setDecorateAppendChar(String decorateAppendChar) {
		this.decorateAppendChar = decorateAppendChar;
	}

	/**
	 * @return the decorateSize
	 */
	public int getDecorateSize() {
		return decorateSize;
	}

	/**
	 * @param decorateSize the decorateSize to set
	 */
	public void setDecorateSize(int decorateSize) {
		this.decorateSize = decorateSize;
	}

	public boolean isDistinct() {
		return distinct;
	}

	public void setDistinct(boolean distinct) {
		this.distinct = distinct;
	}

	/**
	 * @return the resultType
	 */
	public int getResultType() {
		return resultType;
	}

	/**
	 * @param resultType the resultType to set
	 */
	public void setResultType(int resultType) {
		this.resultType = resultType;
	}

}
