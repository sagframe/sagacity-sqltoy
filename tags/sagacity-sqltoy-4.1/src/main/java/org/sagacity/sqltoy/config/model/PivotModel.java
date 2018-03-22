/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sqltoy-orm
 * @description 旋转定义模型
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version Revision:v1.0,Date:2013-5-17
 * @Modification Date:2013-5-17 {填写修改说明}
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
	 * 需要旋转的列
	 */
	private String[] pivotCols;

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
	 * @param groupCols
	 *            the groupCols to set
	 */
	public void setGroupCols(String[] groupCols) {
		this.groupCols = groupCols;
	}

	/**
	 * @return the categoryCols
	 */
	public String[] getCategoryCols() {
		return categoryCols;
	}

	/**
	 * @param categoryCols
	 *            the categoryCols to set
	 */
	public void setCategoryCols(String[] categoryCols) {
		this.categoryCols = categoryCols;
	}

	/**
	 * @return the pivotCols
	 */
	public String[] getPivotCols() {
		return pivotCols;
	}

	/**
	 * @param pivotCols
	 *            the pivotCols to set
	 */
	public void setPivotCols(String[] pivotCols) {
		this.pivotCols = pivotCols;
	}

	public Object getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
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
	public void setCategorySql(String categorySql) {
		this.categorySql = categorySql;
	}

}
