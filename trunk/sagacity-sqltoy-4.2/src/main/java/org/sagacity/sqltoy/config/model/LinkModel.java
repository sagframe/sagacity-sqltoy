/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 根据分组字段，将其它字段值进行连接的配置模型
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-4-8
 * @Modification Date:2013-4-8 {填写修改说明}
 */
public class LinkModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6336313366096191304L;

	/**
	 * 需要link的字段
	 */
	private String column;

	/**
	 * link字段之间的分割符
	 */
	private String sign = ",";

	/**
	 * 分组字段(即以哪个字段为参照)
	 */
	private String idColumn;

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
	 * @return the column
	 */
	public String getColumn() {
		return column;
	}

	/**
	 * @param column the column to set
	 */
	public void setColumn(String column) {
		this.column = column;
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


	/**
	 * @return the idColumn
	 */
	public String getIdColumn() {
		return idColumn;
	}

	/**
	 * @param idColumn the idColumn to set
	 */
	public void setIdColumn(String idColumn) {
		this.idColumn = idColumn;
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
	
	

}
