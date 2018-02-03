/**
 * 
 */
package org.sagacity.sqltoy.cache.impl;

import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.cache.PageOptimizeCache;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供分页优化缓存实现
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:PageOptimizeCacheImpl.java,Revision:v1.0,Date:2016年11月24日
 */
public class PageOptimizeCacheImpl implements PageOptimizeCache {
	private static final int INITIAL_CAPACITY = 128;
	private static final float LOAD_FACTOR = 0.75f;

	// 定义sql对应的分页查询请求(不同查询条件构成的key)对应的总记录数Object[] as
	// {expireTime(失效时间),recordCount(分页查询总记录数)}
	private static ConcurrentHashMap<String, LinkedHashMap<String, Object[]>> pageOptimizeCache = new ConcurrentHashMap<String, LinkedHashMap<String, Object[]>>(
			INITIAL_CAPACITY, LOAD_FACTOR);

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.cache.PageOptimizeCache#getPageTotalCount(java.lang.
	 * String, java.lang.String)
	 */
	@Override
	public Long getPageTotalCount(final SqlToyConfig sqlToyConfig, String conditionsKey) {
		LinkedHashMap<String, Object[]> map = pageOptimizeCache.get(sqlToyConfig.getId());
		// sql初次执行查询
		if (null == map) {
			return null;
		} else {
			Object[] values = map.get(conditionsKey);
			// 为null表示条件初次查询
			if (null == values)
				return null;
			// 总记录数
			Long totalCount = (Long) values[1];
			// 失效时间
			long expireTime = (Long) values[0];
			long nowTime = System.currentTimeMillis();

			// 先移除(为了调整排列顺序)
			map.remove(conditionsKey);
			// 超时,返回null表示需要重新查询，并不需要定时检测
			// 1、控制总记录数量,最早的始终会排在最前面，会最先排挤出去
			// 2、每次查询时相同的条件会自动检测是否过期，过期则会重新执行
			if (nowTime >= expireTime) {
				return null;
			}
			// 重置过期时间
			values[0] = nowTime + sqlToyConfig.getPageAliveSeconds() * 1000;
			// 重新置于linkedHashMap的最后位置
			map.put(conditionsKey, values);
			return totalCount;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.cache.PageOptimizeCache#put(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public void registPageTotalCount(final SqlToyConfig sqlToyConfig, String pageQueryKey, Long totalCount) {
		long nowTime = System.currentTimeMillis();
		// 当前时间
		long expireTime = nowTime + sqlToyConfig.getPageAliveSeconds() * 1000;
		// 同一个分页查询sql保留的不同查询条件记录数量
		int aliveMax = sqlToyConfig.getPageAliveMax();
		LinkedHashMap<String, Object[]> map = pageOptimizeCache.get(sqlToyConfig.getId());
		if (null == map) {
			map = new LinkedHashMap<String, Object[]>(sqlToyConfig.getPageAliveMax());
			map.put(pageQueryKey, new Object[] { expireTime, totalCount });
			pageOptimizeCache.put(sqlToyConfig.getId(), map);
		} else {
			map.put(pageQueryKey, new Object[] { expireTime, totalCount });
			// 长度超阀值,移除最早进入的
			while (map.size() > aliveMax) {
				map.remove(map.keySet().iterator().next());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.cache.PageOptimizeCache#start()
	 */
	@Override
	public void start() {
	}
}
