/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 便于在代码中为查询设置参数值过滤
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020年7月30日
 * @modify 2020年7月30日,修改说明
 */
public class ParamsFilter implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1976248512731692880L;

	/**
	 * 默认eq
	 */
	private String type;

	/**
	 * 参数名称
	 */
	private String[] params;

	/**
	 * 参数值
	 */
	private Object[] value;

	/**
	 * 首要条件参数设置时排除那些条件参数不自动设置为null
	 */
	private String[] excludes;

	/**
	 * 转日期时的加减
	 */
	private int reduce = 0;

	/**
	 * 日期格式
	 */
	private String dateType;

	public ParamsFilter(String... params) {
		this.params = params;
	}

	/**
	 * 等于
	 * 
	 * @return
	 */
	public ParamsFilter eq(Object... values) {
		this.type = "eq";
		this.value = values;
		return this;
	}

	/**
	 * 不等于
	 * 
	 * @return
	 */
	public ParamsFilter neq(Object... values) {
		this.type = "neq";
		this.value = values;
		return this;
	}

	/**
	 * left like
	 * 
	 * @return
	 */
	public ParamsFilter llike() {
		this.type = "l-like";
		return this;
	}

	/**
	 * right like
	 * 
	 * @return
	 */
	public ParamsFilter rlike() {
		this.type = "r-like";
		return this;
	}

	/**
	 * 参数转日期
	 * 
	 * @return
	 */
	public ParamsFilter toDate(DateType dateType, int reduce) {
		this.type = "to-date";
		this.dateType = dateType.getValue();
		this.reduce = reduce;
		return this;
	}

	public ParamsFilter primary(String... excludes) {
		this.type = "primary";
		this.excludes = excludes;
		return this;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the params
	 */
	public String[] getParams() {
		return params;
	}

	/**
	 * @return the value
	 */
	public Object[] getValue() {
		return value;
	}

	/**
	 * @return the excludes
	 */
	public String[] getExcludes() {
		return excludes;
	}

	/**
	 * @return the reduce
	 */
	public int getReduce() {
		return reduce;
	}

	/**
	 * @return the dateType
	 */
	public String getDateType() {
		return dateType;
	}

}
