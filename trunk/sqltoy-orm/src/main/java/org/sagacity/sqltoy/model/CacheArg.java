package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 提供基于QueryExecutor进行反向缓存匹配模型，代替like
 * @author zhongxuchen
 * @version v1.0, Date:2023年6月21日
 * @modify 2023年6月21日,修改说明
 */
public class CacheArg implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3657685200766661259L;

	/**
	 * 缓存中key所在列
	 */
	private int cacheKeyIndex = 0;

	// 最大缓存匹配数量，默认500，无需填
	private Integer matchMax;

	// 缓存名称
	private String cacheName;

	// 缓存分类，针对字典形式的缓存需要填写对应字典分类
	private String cacheType;

	// 缓存翻译出来的key另存为的别名
	private String aliasName;

	/**
	 * 是否优先匹配完全相等
	 */
	private boolean priorMatchEqual = true;

	/**
	 * 缓存匹配的列，如:名称、别名等
	 */
	private int[] matchIndexs;

	/**
	 * 未匹配到值返回自身
	 */
	private Boolean notMatchReturnSelf;

	/**
	 * 过滤的cache列
	 */
	private int filterIndex = -1;

	/**
	 * 缓存过滤对比的值
	 */
	private Object[] filterValues;

	// 等于和不等于（eq和neq）
	private String filterType = "eq";

	public CacheArg(String cacheName) {
		this.cacheName = cacheName;
	}

	public String getCacheName() {
		return cacheName;
	}

	public CacheArg cacheName(String cacheName) {
		this.cacheName = cacheName;
		return this;
	}

	public String getCacheType() {
		return cacheType;
	}

	public CacheArg cacheType(String cacheType) {
		this.cacheType = cacheType;
		return this;
	}

	public String getAliasName() {
		return aliasName;
	}

	public CacheArg aliasName(String aliasName) {
		this.aliasName = aliasName;
		return this;
	}

	public boolean isPriorMatchEqual() {
		return priorMatchEqual;
	}

	public CacheArg priorMatchEqual(boolean priorMatchEqual) {
		this.priorMatchEqual = priorMatchEqual;
		return this;
	}

	public CacheArg matchIndexs(int... matchIndexs) {
		this.matchIndexs = matchIndexs;
		return this;
	}

	public CacheArg cacheKeyIndex(int cacheKeyIndex) {
		this.cacheKeyIndex = cacheKeyIndex;
		return this;
	}

	public CacheArg matchMax(Integer matchMax) {
		this.matchMax = matchMax;
		return this;
	}

	public CacheArg notMatchReturnSelf(Boolean notMatchReturnSelf) {
		this.notMatchReturnSelf = notMatchReturnSelf;
		return this;
	}

	public CacheArg filterValues(String... filterValues) {
		this.filterValues = filterValues;
		return this;
	}

	public CacheArg filterType(String filterType) {
		if ("==".equals(filterType) || "eq".equalsIgnoreCase(filterType)) {
			this.filterType = "eq";
		} else if ("!=".equals(filterType) || "<>".equalsIgnoreCase(filterType) || "neq".equalsIgnoreCase(filterType)) {
			this.filterType = "neq";
		}
		return this;
	}

	public CacheArg filterIndex(int filterIndex) {
		this.filterIndex = filterIndex;
		return this;
	}

	public int getCacheKeyIndex() {
		return cacheKeyIndex;
	}

	public Integer getMatchMax() {
		return matchMax;
	}

	public int[] getMatchIndexs() {
		return matchIndexs;
	}

	public int getFilterIndex() {
		return filterIndex;
	}

	public Object[] getFilterValues() {
		return filterValues;
	}

	public String getFilterType() {
		return filterType;
	}

	public Boolean getNotMatchReturnSelf() {
		return notMatchReturnSelf;
	}

}
