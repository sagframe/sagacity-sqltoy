/**
 * 
 */
package org.sagacity.sqltoy.demo.vo;

import java.io.Serializable;

/**
 * @author zhong
 *
 */
public class StaffInfoVO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5297979866915758170L;

	private String staffName;

	private Integer[] statusAry;

	/**
	 * @return the staffName
	 */
	public String getStaffName() {
		return staffName;
	}

	/**
	 * @param staffName the staffName to set
	 */
	public void setStaffName(String staffName) {
		this.staffName = staffName;
	}

	/**
	 * @return the statusAry
	 */
	public Integer[] getStatusAry() {
		return statusAry;
	}

	/**
	 * @param statusAry the statusAry to set
	 */
	public void setStatusAry(Integer... statusAry) {
		this.statusAry = statusAry;
	}

}
