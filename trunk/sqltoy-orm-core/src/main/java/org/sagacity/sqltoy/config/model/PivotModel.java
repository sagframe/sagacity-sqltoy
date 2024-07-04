/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 旋转定义模型
 * @author zhongxuchen
 * @version v1.0,Date:2013-5-17
 * @modify Date:2013-5-17 {填写修改说明}
 */
public class PivotModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6583560999494073747L;

	/**
	 * 旋转时参照的列
	 */
	private String[] groupCols;

	/**
	 * 旋转依据的分类列
	 */
	private String[] categoryCols;

	/**
	 * 通过sql获取旋转分类参照数据
	 */
	private String categorySql;

	/**
	 * 需要旋转的开始和截止列(可以是列名，也可以是对应的index数字)
	 */
	private String[] startEndCols;

	/**
	 * 默认值
	 */
	private Object defaultValue;

	/**
	 * @return the groupCols
	 */
	public String[] getGroupCols() {
		return groupCols;
	}

	/**
	 * @param groupCols the groupCols to set
	 */
	public PivotModel setGroupCols(String... groupCols) {
		if (groupCols != null && groupCols.length > 0) {
			if (groupCols.length > 1) {
				this.groupCols = groupCols;
			} else {
				this.groupCols = groupCols[0].split("\\,");
			}
		}
		return this;
	}

	/**
	 * @return the categoryCols
	 */
	public String[] getCategoryCols() {
		return categoryCols;
	}

	/**
	 * @param categoryCols the categoryCols to set
	 */
	public PivotModel setCategoryCols(String... categoryCols) {
		if (categoryCols != null && categoryCols.length > 0) {
			if (categoryCols.length > 1) {
				this.categoryCols = categoryCols;
			} else {
				this.categoryCols = categoryCols[0].split("\\,");
			}
		}
		return this;
	}

	/**
	 * @return the pivotCols
	 */
	public String[] getStartEndCols() {
		return startEndCols;
	}

	/**
	 * @param startEndCols the pivotCols to set
	 */
	public PivotModel setStartEndCols(String... startEndCols) {
		if (startEndCols != null && startEndCols.length > 0) {
			if (startEndCols.length > 1) {
				this.startEndCols = startEndCols;
			} else {
				this.startEndCols = startEndCols[0].split("\\,");
			}
		}
		return this;
	}

	public Object getDefaultValue() {
		return defaultValue;
	}

	public PivotModel setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
		return this;
	}

	/**
	 * @return the categorySql
	 */
	public String getCategorySql() {
		return categorySql;
	}

	/**
	 * @param categorySql the categorySql to set
	 */
	public PivotModel setCategorySql(String categorySql) {
		this.categorySql = categorySql;
		return this;
	}

}
