/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.HashMap;

/**
 * @project sqltoy-orm
 * @description sqltoy 查询条件参数值过滤加工配制模型
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ParamFilterModel.java,Revision:v1.0,Date:2013-3-22
 * @modify Date:2019-1-15 {增加缓存条件过滤}
 */
public class ParamFilterModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2608369719903008282L;

	public ParamFilterModel() {
	}

	/**
	 * 
	 * @param filterType
	 * @param params
	 */
	public ParamFilterModel(String filterType, String[] params) {
		this.filterType = filterType;
		this.params = params;
	}

	/**
	 * 参数名称（exclusive和primary 两个filter使用）
	 */
	private String param;

	/**
	 * 参数名称数组
	 */
	private String[] params;

	/**
	 * 参数值
	 */
	private String[] values;

	/**
	 * 过滤加工的类型:blank、equals、any、moreThan、moreEquals、lessThan、lessEquals、between、
	 * not-any、to-date、to-number、primary、to-array、replace,exclusive(排斥性参数),cache-arg
	 */
	private String filterType;

	/**
	 * 数据类型
	 */
	private String dataType;

	/**
	 * numberType
	 */
	private String numberType;

	/**
	 * 日期或数字格式
	 */
	private String format;

	/**
	 * replace的表达式
	 */
	private String regex;

	/**
	 * 分割符
	 */
	private String split = ",";

	/**
	 * 是否替换第一个参数
	 */
	private boolean isFirst = false;

	/**
	 * exclusive 排他性参数
	 */
	private String[] updateParams;

	/**
	 * 设置的值(exclusive filter)
	 */
	private String updateValue = null;

	/**
	 * 互斥型filter 对比类型
	 */
	private String compareType = "==";

	/**
	 * 缓存名称
	 */
	private String cacheName;

	/**
	 * 缓存类型
	 */
	private String cacheType;

	/**
	 * 转化成新的笔名
	 */
	private String aliasName;

	/**
	 * 缓存匹配列(默认为第二列)
	 */
	private int[] cacheMappingIndexes = { 1 };

	/**
	 * 互斥型filter 对比的值
	 */
	private String[] compareValues = null;

	/**
	 * 未被缓存转换匹配上赋予的默认值
	 */
	private String cacheNotMatchedValue;

	/**
	 * 排除的参数
	 */
	private HashMap<String, String> excludesMap;

	/**
	 * 增加的天数
	 */
	private Double incrementDays = 0d;

	/**
	 * 最大匹配数量为500
	 */
	private int cacheMappingMax = 500;

	/**
	 * 缓存条件过滤配置
	 */
	private CacheFilterModel[] cacheFilters;

	/**
	 * to-date 的日期类型
	 */
	private String type;

	/**
	 * to-in-arg 是否增加单引号
	 */
	private boolean singleQuote = true;

	public String getFilterType() {
		return filterType;
	}

	public void setFilterType(String filterType) {
		this.filterType = filterType;
	}

	public String[] getValues() {
		return values;
	}

	public void setValues(String[] values) {
		this.values = values;
	}

	/**
	 * @return the params
	 */
	public String[] getParams() {
		return params;
	}

	/**
	 * @param params
	 *            the params to set
	 */
	public void setParams(String[] params) {
		this.params = params;
	}

	/**
	 * @return the numberType
	 */
	public String getNumberType() {
		return numberType;
	}

	/**
	 * @param numberType
	 *            the numberType to set
	 */
	public void setNumberType(String numberType) {
		this.numberType = numberType;
	}

	/**
	 * @return the excludesMap
	 */
	public HashMap<String, String> getExcludesMap() {
		return excludesMap;
	}

	/**
	 * @param excludesMap
	 *            the excludesMap to set
	 */
	public void setExcludesMap(HashMap<String, String> excludesMap) {
		this.excludesMap = excludesMap;
	}

	/**
	 * @return the incrementDays
	 */
	public Double getIncrementDays() {
		return incrementDays;
	}

	/**
	 * @param incrementDays
	 *            the incrementDays to set
	 */
	public void setIncrementDays(Double incrementDays) {
		this.incrementDays = incrementDays;
	}

	/**
	 * @return the dataType
	 */
	public String getDataType() {
		return dataType;
	}

	/**
	 * @param dataType
	 *            the dataType to set
	 */
	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	/**
	 * @return the format
	 */
	public String getFormat() {
		return format;
	}

	/**
	 * @param format
	 *            the format to set
	 */
	public void setFormat(String format) {
		this.format = format;
	}

	/**
	 * @return the split
	 */
	public String getSplit() {
		return split;
	}

	/**
	 * @param split
	 *            the split to set
	 */
	public void setSplit(String split) {
		this.split = split;
	}

	/**
	 * @return the regex
	 */
	public String getRegex() {
		return regex;
	}

	/**
	 * @param regex
	 *            the regex to set
	 */
	public void setRegex(String regex) {
		this.regex = regex;
	}

	/**
	 * @return the isFirst
	 */
	public boolean isFirst() {
		return isFirst;
	}

	/**
	 * @param isFirst
	 *            the isFirst to set
	 */
	public void setFirst(boolean isFirst) {
		this.isFirst = isFirst;
	}

	/**
	 * @return the param
	 */
	public String getParam() {
		return param;
	}

	/**
	 * @param param
	 *            the param to set
	 */
	public void setParam(String param) {
		this.param = param;
	}

	/**
	 * @return the updateParams
	 */
	public String[] getUpdateParams() {
		return updateParams;
	}

	/**
	 * @param updateParams
	 *            the updateParams to set
	 */
	public void setUpdateParams(String[] updateParams) {
		this.updateParams = updateParams;
	}

	/**
	 * @return the updateValue
	 */
	public String getUpdateValue() {
		return updateValue;
	}

	/**
	 * @param updateValue
	 *            the updateValue to set
	 */
	public void setUpdateValue(String updateValue) {
		this.updateValue = updateValue;
	}

	/**
	 * @return the compareType
	 */
	public String getCompareType() {
		return compareType;
	}

	/**
	 * @param compareType
	 *            the compareType to set
	 */
	public void setCompareType(String compareType) {
		this.compareType = compareType;
	}

	/**
	 * @return the compareValues
	 */
	public String[] getCompareValues() {
		return compareValues;
	}

	/**
	 * @param compareValues
	 *            the compareValues to set
	 */
	public void setCompareValues(String[] compareValues) {
		this.compareValues = compareValues;
	}

	/**
	 * @return the cacheName
	 */
	public String getCacheName() {
		return cacheName;
	}

	/**
	 * @param cacheName
	 *            the cacheName to set
	 */
	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}

	/**
	 * @return the cacheType
	 */
	public String getCacheType() {
		return cacheType;
	}

	/**
	 * @param cacheType
	 *            the cacheType to set
	 */
	public void setCacheType(String cacheType) {
		this.cacheType = cacheType;
	}

	/**
	 * @return the aliasName
	 */
	public String getAliasName() {
		return aliasName;
	}

	/**
	 * @param aliasName
	 *            the aliasName to set
	 */
	public void setAliasName(String aliasName) {
		this.aliasName = aliasName;
	}

	/**
	 * @return the cacheMappingIndexes
	 */
	public int[] getCacheMappingIndexes() {
		return cacheMappingIndexes;
	}

	/**
	 * @param cacheMappingIndexes
	 *            the cacheMappingIndexes to set
	 */
	public void setCacheMappingIndexes(int[] cacheMappingIndexes) {
		this.cacheMappingIndexes = cacheMappingIndexes;
	}

	/**
	 * @return the cacheMappingMax
	 */
	public int getCacheMappingMax() {
		return cacheMappingMax;
	}

	/**
	 * @param cacheMappingMax
	 *            the cacheMappingMax to set
	 */
	public void setCacheMappingMax(int cacheMappingMax) {
		this.cacheMappingMax = cacheMappingMax;
	}

	/**
	 * @return the cacheFilters
	 */
	public CacheFilterModel[] getCacheFilters() {
		return cacheFilters;
	}

	/**
	 * @param cacheFilters
	 *            the cacheFilters to set
	 */
	public void setCacheFilters(CacheFilterModel[] cacheFilters) {
		this.cacheFilters = cacheFilters;
	}

	/**
	 * @return the singleQuote
	 */
	public boolean isSingleQuote() {
		return singleQuote;
	}

	/**
	 * @param singleQuote
	 *            the singleQuote to set
	 */
	public void setSingleQuote(boolean singleQuote) {
		this.singleQuote = singleQuote;
	}

	/**
	 * @return the cacheNotMatchedValue
	 */
	public String getCacheNotMatchedValue() {
		return cacheNotMatchedValue;
	}

	/**
	 * @param cacheNotMatchedValue
	 *            the cacheNotMatchedValue to set
	 */
	public void setCacheNotMatchedValue(String cacheNotMatchedValue) {
		this.cacheNotMatchedValue = cacheNotMatchedValue;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}

}
