/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;

import org.sagacity.sqltoy.model.inner.CacheMatchExtend;

/**
 * @project sagacity-sqltoy
 * @description 缓存名称匹配取key集合
 * @author zhongxuchen
 * @version v1.0, Date:2021-2-24
 * @modify 2021-2-24,修改说明
 */
public class CacheMatchFilter implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7916695239810406512L;

	private CacheMatchExtend cacheFilterArgs = new CacheMatchExtend();

	public static CacheMatchFilter create() {
		CacheMatchFilter filter = new CacheMatchFilter();
		return filter;
	}

	/**
	 * @TODO 设置缓存名称
	 * @param cacheName
	 * @return
	 */
	public CacheMatchFilter cacheName(String cacheName) {
		cacheFilterArgs.cacheName = cacheName;
		return this;
	}

	/**
	 * @TODO 设置缓存key对应的列，默认为0，可以不用设置
	 * @param cacheKeyIndex
	 * @return
	 */
	public CacheMatchFilter cacheKeyIndex(int cacheKeyIndex) {
		if (cacheKeyIndex >= 0) {
			cacheFilterArgs.cacheKeyIndex = cacheKeyIndex;
		}
		return this;
	}

	/**
	 * @TODO 设置缓存类别，一般针对数据字典类型的缓存才需要额外指定子分类
	 * @param cacheType
	 * @return
	 */
	public CacheMatchFilter cacheType(String cacheType) {
		cacheFilterArgs.cacheType = cacheType;
		return this;
	}

	/**
	 * @TODO 设置最大匹配量，比如用于sql中 in (keys) 有1000限制
	 * @param matchSize
	 * @return
	 */
	public CacheMatchFilter matchSize(int matchSize) {
		if (matchSize > 0) {
			cacheFilterArgs.matchSize = matchSize;
		}
		return this;
	}

	/**
	 * @TODO 设置匹配表达式跟缓存中哪几列数据进行匹配，默认为1,比如员工缓存，第0列为工号，第1列为员工名称，用名称匹配到工号
	 * @param matchIndexs
	 * @return
	 */
	public CacheMatchFilter matchIndexs(int... matchIndexs) {
		if (matchIndexs != null && matchIndexs.length > 0) {
			cacheFilterArgs.matchIndexs = matchIndexs;
		}
		return this;
	}

	/**
	 * @TODO 优先匹配完全相等的，并放于结果的第一行返回
	 * @param priorMatchEqual
	 * @return
	 */
	public CacheMatchFilter priorMatchEqual(boolean priorMatchEqual) {
		cacheFilterArgs.priorMatchEqual = priorMatchEqual;
		return this;
	}

	/**
	 * @return the cacheFilterArgs
	 */
	public CacheMatchExtend getCacheFilterArgs() {
		return cacheFilterArgs;
	}
}
