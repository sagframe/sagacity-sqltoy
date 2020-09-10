package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 用于存放map转换后的named和values数据模型
 * @author zhongxuchen@hotmail.com
 * @version v1.0, Date:2020-9-10
 * @modify 2020-9-10,修改说明
 */
public class NamedValuesModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4776060558293466261L;
	
	/**
	 * 参数名称
	 */
	private String[] paramNames;
	
	/**
	 * 参数值
	 */
	private Object[] paramValues;

	public String[] getParamNames() {
		return paramNames;
	}

	public void setParamNames(String[] paramNames) {
		this.paramNames = paramNames;
	}

	public Object[] getParamValues() {
		return paramValues;
	}

	public void setParamValues(Object[] paramValues) {
		this.paramValues = paramValues;
	}
	
	

}
