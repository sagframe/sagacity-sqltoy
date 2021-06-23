package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 用于存放map转换后的named和values数据模型
 * @author zhongxuchen
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
	private String[] names;
	
	/**
	 * 参数值
	 */
	private Object[] values;

	public String[] getNames() {
		return names;
	}

	public void setNames(String[] names) {
		this.names = names;
	}

	public Object[] getValues() {
		return values;
	}

	public void setValues(Object[] values) {
		this.values = values;
	}	

}
