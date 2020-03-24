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
	private int startRow;

	/**
	 * 截止行
	 */
	private int endRow;

	public int getStartRow() {
		return startRow;
	}

	public void setStartRow(int startRow) {
		this.startRow = startRow;
	}

	public int getEndRow() {
		return endRow;
	}

	public void setEndRow(int endRow) {
		this.endRow = endRow;
	}

}
