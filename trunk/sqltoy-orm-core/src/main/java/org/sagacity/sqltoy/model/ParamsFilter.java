/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

import org.sagacity.sqltoy.utils.StringUtil;

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
	 * 默认值类型
	 */
	private String dataType;

	/**
	 * 对比类型
	 */
	private String compareType;

	/**
	 * 对比值
	 */
	private String[] compareValues;

	/**
	 * 参数名称
	 */
	private String[] params;

	/**
	 * 参数值
	 */
	private Object[] value;

	private String[] assignParams;
	private String assignValue;

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

	/**
	 * 别名
	 */
	private String asName;

	private String addQueto;

	private CacheArg cacheArg;

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

	/**
	 * 将字符类型转数字
	 * 
	 * @param dataType integer、long、decimal、bigdecimal，biginteger等
	 * @return
	 */
	public ParamsFilter toNumber(String dataType) {
		this.type = "to-number";
		this.dataType = dataType.toLowerCase();
		return this;
	}

	/**
	 * @TODO 将参数值转为字符传
	 * @param addQuote 是否加引号，none、double、single
	 * @return
	 */
	public ParamsFilter toString(String addQuote) {
		this.type = "to-string";
		if (StringUtil.isBlank(addQuote) || "none".equalsIgnoreCase(addQuote)) {
			this.addQueto = "none";
		} else if ("\"".equals(addQuote) || "double".equalsIgnoreCase(addQuote)) {
			this.addQueto = "double";
		} else if ("\'".equals(addQuote) || "single".equalsIgnoreCase(addQuote)) {
			this.addQueto = "single";
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
	 * @TODO 复制一个值作为另外一个属性的值
	 * @param aliasName
	 * @return
	 */
	public ParamsFilter clone(String aliasName) {
		this.type = "clone";
		this.asName = aliasName;
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
	 * @TODO 设置默认值，如:sysdate()-3
	 * @param defaultValue
	 * @param dataType     localDateTime\localDate\Integer
	 * @return
	 */
	public ParamsFilter defaultValue(Object defaultValue, String dataType) {
		this.type = "default";
		this.dataType = (dataType == null) ? "string" : dataType.toLowerCase();
		this.value = new Object[] { defaultValue };
		return this;
	}

	/**
	 * @TODO 排斥法:当某个属性值为xx时，设置其他几个属性值为xxx
	 * @param exclusive
	 * @return
	 */
	public ParamsFilter exclusive(Exclusive exclusive) {
		if (exclusive.getCompareType() == null || exclusive.getCompareValues() == null
				|| exclusive.getUpdateParams() == null) {
			throw new IllegalArgumentException("filter exclusive 必须要设置compareType、compareValues、updateParams属性值!");
		}
		this.type = "exclusive";
		// >,>=,<,<=,!=,in,between
		this.compareType = (exclusive.getCompareType() == null) ? "==" : exclusive.getCompareType();
		if (compareType == "gte") {
			compareType = ">=";
		} else if (compareType == "gt") {
			compareType = ">";
		} else if (compareType == "lt") {
			compareType = "<";
		} else if (compareType == "lte") {
			compareType = "<=";
		} else if (compareType == "neq") {
			compareType = "!=";
		}
		this.compareValues = exclusive.getCompareValues();
		this.assignParams = exclusive.getUpdateParams();
		this.assignValue = exclusive.getUpdateValue();
		return this;
	}

	/**
	 * @TODO 反向缓存，通过名称利用缓存匹配到key集合，作为sql中in的条件，代替like关联表进行模糊查询
	 * @param cacheArg
	 * @return
	 */
	public ParamsFilter cacheArg(CacheArg cacheArg) {
		if (cacheArg.getCacheName() == null) {
			throw new IllegalArgumentException("cacheArg反向缓存必须要设置cacheName属性!");
		}
		// 只支持1个属性
		if (this.params.length > 1) {
			this.params = new String[] { this.params[0] };
		}
		this.type = "cache-arg";
		this.cacheArg = cacheArg;
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

	public String getDataType() {
		return dataType;
	}

	public String getCompareType() {
		return compareType;
	}

	public String[] getCompareValues() {
		return compareValues;
	}

	public String getAssignValue() {
		return assignValue;
	}

	public String getAsName() {
		return asName;
	}

	public String[] getAssignParams() {
		return assignParams;
	}

	public String getAddQueto() {
		return addQueto;
	}

	public CacheArg getCacheArg() {
		return cacheArg;
	}

}
