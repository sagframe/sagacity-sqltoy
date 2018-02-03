/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;
import java.util.HashMap;

/**
 * @project  sagacity-quickvo
 * @description 数据库字段对应字段数据类型映射模型
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:ColumnTypeMapping.java,Revision:v1.0,Date:Apr 19, 2009 9:46:49
 *          AM $
 */
public class ColumnTypeMapping implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5178780217838061638L;

	/**
	 * jdbc中的数据类型
	 */
	private String jdbcType;

	/**
	 * 数据总长度
	 */
	private int precisionMin = -1;

	private int precisionMax = -1;

	/**
	 * 小数位
	 */
	private int scaleMin = -1;

	private int scaleMax = -1;

	/**
	 * 数据库本身的类型
	 */
	private HashMap<String, String> nativeTypes = new HashMap<String, String>();

	/**
	 * 字段对应java的数据类型
	 */
	private String javaType;

	/**
	 * 代码中的类型
	 */
	private String resultType;

	/**
	 * @return the jdbcType
	 */
	public String getJdbcType() {
		return jdbcType;
	}

	/**
	 * @param jdbcType
	 *            the jdbcType to set
	 */
	public void setJdbcType(String jdbcType) {
		this.jdbcType = jdbcType;
	}

	/**
	 * @return the precisionMin
	 */
	public int getPrecisionMin() {
		return precisionMin;
	}

	/**
	 * @param precisionMin
	 *            the precisionMin to set
	 */
	public void setPrecisionMin(int precisionMin) {
		this.precisionMin = precisionMin;
	}

	/**
	 * @return the precisionMax
	 */
	public int getPrecisionMax() {
		return precisionMax;
	}

	/**
	 * @param precisionMax
	 *            the precisionMax to set
	 */
	public void setPrecisionMax(int precisionMax) {
		this.precisionMax = precisionMax;
	}

	/**
	 * @return the scaleMin
	 */
	public int getScaleMin() {
		return scaleMin;
	}

	/**
	 * @param scaleMin
	 *            the scaleMin to set
	 */
	public void setScaleMin(int scaleMin) {
		this.scaleMin = scaleMin;
	}

	/**
	 * @return the scaleMax
	 */
	public int getScaleMax() {
		return scaleMax;
	}

	/**
	 * @param scaleMax
	 *            the scaleMax to set
	 */
	public void setScaleMax(int scaleMax) {
		this.scaleMax = scaleMax;
	}

	/**
	 * @return the resultType
	 */
	public String getResultType() {
		return resultType;
	}

	/**
	 * @param resultType
	 *            the resultType to set
	 */
	public void setResultType(String resultType) {
		this.resultType = resultType;
	}

	/**
	 * @return the javaType
	 */
	public String getJavaType() {
		return javaType;
	}

	/**
	 * @param javaType
	 *            the javaType to set
	 */
	public void setJavaType(String javaType) {
		this.javaType = javaType;
	}

	public HashMap<String, String> getNativeTypes() {
		return nativeTypes;
	}

	public void putNativeTypes(String[] nativeTypes) {
		for (String nativeType : nativeTypes)
			this.nativeTypes.put(nativeType.trim().toLowerCase(), "1");
	}

}
