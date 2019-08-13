/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.math.RoundingMode;

/**
 * @project sagacity-sqltoy4.2
 * @description 格式化参数模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:FormatModel.java,Revision:v1.0,Date:2018年6月26日
 */
public class FormatModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8483990404112803642L;
	
	/**
	 * 列名
	 */
	private String column;
	
	/**
	 * 0:date,1:number
	 */
	private int type=0;
	
	/**
	 * 格式
	 */
	private String format;
	
	/**
	 * 
	 */
	private RoundingMode roundingMode=null;

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
	 * @return the format
	 */
	public String getFormat() {
		return format;
	}

	/**
	 * @param format the format to set
	 */
	public void setFormat(String format) {
		this.format = format;
	}

	/**
	 * @return the type
	 */
	public int getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(int type) {
		this.type = type;
	}

	/**
	 * @return the roundingMode
	 */
	public RoundingMode getRoundingMode() {
		return roundingMode;
	}

	/**
	 * @param roundingMode the roundingMode to set
	 */
	public void setRoundingMode(RoundingMode roundingMode) {
		this.roundingMode = roundingMode;
	}

}
