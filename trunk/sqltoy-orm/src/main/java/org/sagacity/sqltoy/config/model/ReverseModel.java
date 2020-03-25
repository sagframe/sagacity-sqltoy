/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 集合反转
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ReverseModel.java,Revision:v1.0,Date:2020年3月24日
 */
public class ReverseModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2770052243575868466L;

	/**
	 * 开始行
	 */
	private Integer startRow;

	/**
	 * 截止行
	 */
	private Integer endRow;

	public Integer getStartRow() {
		return startRow;
	}

	public void setStartRow(Integer startRow) {
		this.startRow = startRow;
	}

	public Integer getEndRow() {
		return endRow;
	}

	public void setEndRow(Integer endRow) {
		this.endRow = endRow;
	}

}
