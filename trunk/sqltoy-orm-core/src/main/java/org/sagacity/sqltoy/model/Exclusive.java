/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 条件排斥参数模型，即当某个参数值是xx时，设置其他几个参数值为xxx
 * @author zhongxuchen
 * @version v1.0, Date:2023年6月22日
 * @modify 2023年6月22日,修改说明
 */
public class Exclusive implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3559134410854246004L;

	/**
	 * 对比类型
	 */
	private String compareType;

	/**
	 * 对比值
	 */
	private String[] compareValues;

	/**
	 * 修改属性
	 */
	private String[] updateParams;

	/**
	 * 修改值
	 */
	private String updateValue;

	public Exclusive(String... updateParams) {
		this.updateParams = updateParams;
	}

	public String getCompareType() {
		return compareType;
	}

	public Exclusive compareType(String compareType) {
		if (compareType != null) {
			this.compareType = compareType.toLowerCase();
		}
		return this;
	}

	public String[] getCompareValues() {
		return compareValues;
	}

	public Exclusive compareValues(String... compareValues) {
		this.compareValues = compareValues;
		return this;
	}

	public String[] getUpdateParams() {
		return updateParams;
	}

	public String getUpdateValue() {
		return updateValue;
	}

	public Exclusive updateValue(String updateValue) {
		this.updateValue = updateValue;
		return this;
	}

}
