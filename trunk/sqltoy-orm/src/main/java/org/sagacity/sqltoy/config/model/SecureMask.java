/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 字段安全掩码配置模型,比如手机号、银行卡号等信息脱敏
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SecureMask.java,Revision:v1.0,Date:2017年9月8日
 */
public class SecureMask implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7967285640261486118L;
	// <secure-mask column="" type="name" head-size="" tail-size=""
	// mask-code="*****" mask-rate="50%"/>

	/**
	 * 需要脱敏的列
	 */
	private String column;

	/**
	 * 脱敏类型:tel、name、address、id-card、bank-card等几种
	 */
	private String type;

	/**
	 * 头部保留长度
	 */
	private int headSize = 0;

	/**
	 * 尾部保留长度
	 */
	private int tailSize = 0;

	/**
	 * 掩码
	 */
	private String maskCode;

	/**
	 * 脱敏比例
	 */
	private int maskRate;

	/**
	 * @return the column
	 */
	public String getColumn() {
		return column;
	}

	/**
	 * @param column
	 *            the column to set
	 */
	public void setColumn(String column) {
		this.column = column;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * @return the headSize
	 */
	public int getHeadSize() {
		return headSize;
	}

	/**
	 * @param headSize
	 *            the headSize to set
	 */
	public void setHeadSize(int headSize) {
		this.headSize = headSize;
	}

	/**
	 * @return the tailSize
	 */
	public int getTailSize() {
		return tailSize;
	}

	/**
	 * @param tailSize
	 *            the tailSize to set
	 */
	public void setTailSize(int tailSize) {
		this.tailSize = tailSize;
	}

	/**
	 * @return the maskCode
	 */
	public String getMaskCode() {
		return maskCode;
	}

	/**
	 * @param maskCode
	 *            the maskCode to set
	 */
	public void setMaskCode(String maskCode) {
		this.maskCode = maskCode;
	}

	/**
	 * @return the maskRate
	 */
	public int getMaskRate() {
		return maskRate;
	}

	/**
	 * @param maskRate
	 *            the maskRate to set
	 */
	public void setMaskRate(int maskRate) {
		this.maskRate = maskRate;
	}

}
