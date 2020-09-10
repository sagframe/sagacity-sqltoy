package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * 
 * @author zhongxuchen
 *
 */
public class NamedValuesModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4776060558293466261L;
	
	private String[] paramNames;
	
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
