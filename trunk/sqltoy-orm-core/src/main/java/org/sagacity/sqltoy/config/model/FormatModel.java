/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * @project sagacity-sqltoy
 * @description 格式化参数模型
 * @author zhongxuchen
 * @version v1.0,Date:2018年6月26日
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
	private int type = 0;

	/**
	 * 格式
	 */
	private String format;

	/**
	 * 区域
	 */
	private Locale locale;

	/**
	 * 币种单位(仅对capital-en等英文金额格式生效,支持ISO-4217代码如USD或直接写单位词,输出票据标准格式)
	 */
	private String currency;

	/**
	 *
	 */
	private RoundingMode roundingMode = null;

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

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
	}

	/**
	 * @return the currency
	 */
	public String getCurrency() {
		return currency;
	}

	/**
	 * @param currency the currency to set
	 */
	public void setCurrency(String currency) {
		this.currency = currency;
	}

}
