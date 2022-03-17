/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 便于在代码中为查询设置参数值过滤
 * @author zhongxuchen
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
	private int increase = 0;

	/**
	 * 时间单位类型
	 */
	private TimeUnit timeUnit = TimeUnit.DAYS;

	/**
	 * 日期格式
	 */
	private String dateType;

	public ParamsFilter(String... params) {
		this.params = params;
	}

	/**
	 * @TODO blank
	 * @return
	 */
	public ParamsFilter blank() {
		this.type = "blank";
		return this;
	}

	/**
	 * @TODO 等于
	 * @return
	 */
	public ParamsFilter eq(Object... values) {
		this.type = "eq";
		this.value = values;
		return this;
	}

	/**
	 * @TODO 不等于
	 * @return
	 */
	public ParamsFilter neq(Object... values) {
		this.type = "neq";
		this.value = values;
		return this;
	}

	/**
	 * @TODO 大于
	 * @param values
	 * @return
	 */
	public ParamsFilter gt(Object values) {
		this.type = "gt";
		this.value = new Object[] { values };
		return this;
	}

	/**
	 * @TODO 大于等于
	 * @param values
	 * @return
	 */
	public ParamsFilter gte(Object values) {
		this.type = "gte";
		this.value = new Object[] { values };
		return this;
	}

	/**
	 * @TODO 小于
	 * @param values
	 * @return
	 */
	public ParamsFilter lt(Object values) {
		this.type = "lt";
		this.value = new Object[] { values };
		return this;
	}

	/**
	 * @TODO 小于等于
	 * @param values
	 * @return
	 */
	public ParamsFilter lte(Object values) {
		this.type = "lte";
		this.value = new Object[] { values };
		return this;
	}

	/**
	 * @TODO left like
	 * @return
	 */
	public ParamsFilter llike() {
		this.type = "l-like";
		return this;
	}

	/**
	 * @TODO right like
	 * @return
	 */
	public ParamsFilter rlike() {
		this.type = "r-like";
		return this;
	}

	/**
	 * @TODO 参数转日期
	 * @param dateType
	 * @param increase
	 * @return
	 */
	public ParamsFilter toDate(DateType dateType, int increase) {
		this.type = "to-date";
		this.dateType = dateType.getValue();
		this.increase = increase;
		this.timeUnit = TimeUnit.DAYS;
		return this;
	}

	public ParamsFilter toDate(DateType dateType, TimeUnit timeUnit, int increase) {
		this.type = "to-date";
		this.dateType = dateType.getValue();
		this.increase = increase;
		if (timeUnit != null) {
			this.timeUnit = timeUnit;
		}
		return this;
	}

	// primary 这里用法上确实容易存在歧义(请注意)
	/**
	 * @TODO 决定性参数过滤(注意:new ParamsFilter(params) 指定了首要参数,primary(excludes)
	 *       指定的是排除哪些属性不直接设置为null)
	 * @param excludes
	 * @return
	 */
	public ParamsFilter primary(String... excludes) {
		this.type = "primary";
		this.excludes = excludes;
		return this;
	}

	/**
	 * @TODO between
	 * @param startValue
	 * @param endValue
	 * @return
	 */
	public ParamsFilter between(Object startValue, Object endValue) {
		this.type = "between";
		this.value = new Object[] { startValue, endValue };
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
	public int getIncrease() {
		return increase;
	}

	/**
	 * @return the dateType
	 */
	public String getDateType() {
		return dateType;
	}

	/**
	 * @return the timeUnit
	 */
	public TimeUnit getTimeUnit() {
		return timeUnit;
	}

}
