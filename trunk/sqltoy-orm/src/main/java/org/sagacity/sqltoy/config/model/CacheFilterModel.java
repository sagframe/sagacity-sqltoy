/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 基于缓存条件筛选的配置模型
 * @author zhongxuchen
 * @version v1.0,Date:2019年1月13日
 */
public class CacheFilterModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6777628067078118834L;

	/**
	 * 对缓存哪列值进行对比过滤
	 */
	private int cacheIndex;

	/**
	 * 对比的参数名称或值
	 */
	private String compareParam;

	/**
	 * 对比类型
	 */
	private String compareType = "eq";
	
	private Object[] compareValues;

	/**
	 * @return the cacheIndex
	 */
	public int getCacheIndex() {
		return cacheIndex;
	}

	/**
	 * @param cacheIndex the cacheIndex to set
	 */
	public void setCacheIndex(int cacheIndex) {
		this.cacheIndex = cacheIndex;
	}

	/**
	 * @return the compareParam
	 */
	public String getCompareParam() {
		return compareParam;
	}

	/**
	 * @param compareParam the compareParam to set
	 */
	public void setCompareParam(String compareParam) {
		this.compareParam = compareParam;
	}

	/**
	 * @return the compareType
	 */
	public String getCompareType() {
		return compareType;
	}

	/**
	 * @param compareType the compareType to set
	 */
	public void setCompareType(String compareType) {
		this.compareType = compareType;
	}

	public Object[] getCompareValues() {
		return compareValues;
	}

	public void setCompareValues(Object[] compareValues) {
		this.compareValues = compareValues;
	}

	
}
