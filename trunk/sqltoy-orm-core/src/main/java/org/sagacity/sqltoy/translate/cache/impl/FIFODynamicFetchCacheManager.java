package org.sagacity.sqltoy.translate.cache.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.sagacity.sqltoy.model.FIFOMap;
import org.sagacity.sqltoy.translate.cache.DynamicFecthCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * @project sqltoy-orm
 * @description sqltoy框架提供动态获取缓存数据的缓存管理器实现，基于FIFOMap
 * @author zhongxuchen
 * @version v1.0,Date:2026年1月16日
 */
public class FIFODynamicFetchCacheManager implements DynamicFecthCacheManager {
	// 提供默认的先进先出Map队列作为动态缓存存储
	// FIFOMap可设置最大数据量如10万条，
	// 同时设置频繁使用的放在前面，使用不频繁的排在最先被挤出的位置
	private static ConcurrentHashMap<String, FIFOMap<String, Object[]>> dynamicFetchCacheMap = new ConcurrentHashMap<>();
	// 缓存被调用登记表，用户CacheUpdateWatcher更新检测，判断是否存在，不存在则无需检测
	private static ConcurrentHashMap<String, Integer> cacheRegistMap = new ConcurrentHashMap<>();

	// 存放缓存初始化的时间，用于定时判断超过keep-alive清除缓存，下次使用时重新初始化
	private static ConcurrentHashMap<String, Long[]> cacheInitTime = new ConcurrentHashMap<>();

	// 拼接的特殊字符
	private static final String CACHE_TYPE_JOIN_SIGN = "_cachetype_";

	// 定时检测的线程池（单线程，避免多线程竞争）
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	// 检测线程的执行间隔：3分钟（可根据需求调整）
	private static final long CHECK_INTERVAL = 3 * 60;

	@Override
	public HashMap<String, Object[]> getDynamicCache(TranslateConfigModel cacheModel, String cacheType) {
		// 统一转小写
		String cacheNameLower = (cacheType == null) ? cacheModel.getCache().toLowerCase()
				: cacheModel.getCache().concat(CACHE_TYPE_JOIN_SIGN).concat(cacheType).toLowerCase();
		FIFOMap<String, Object[]> cacheDatas = dynamicFetchCacheMap.get(cacheNameLower);
		if (cacheDatas == null) {
			int initSize = cacheModel.getDynamiceCacheInitSize();
			int maxSize = cacheModel.getDynamicCacheMaxSize();
			float loadFactor = cacheModel.getDynamicCacheLoadFactor();
			cacheDatas = new FIFOMap(initSize, maxSize, (loadFactor > 1) ? 0.75F : loadFactor, true);
			dynamicFetchCacheMap.put(cacheNameLower, cacheDatas);
			// 表示缓存已经开始使用，更新检测程序可以判断到可以对此缓存进行获取变化数据进行更新
			cacheRegistMap.put(cacheModel.getCache().toLowerCase(), 1);
			// 可用小于零表示长期有效
			if (cacheModel.getKeepAlive() > 0) {
				cacheInitTime.put(cacheNameLower,
						new Long[] { System.currentTimeMillis(), cacheModel.getKeepAlive() * 1L });
			}
		}
		return cacheDatas;
	}

	@Override
	public void clear(String cacheName, String cacheType) {
		String cacheNameLower = (cacheType == null) ? cacheName.toLowerCase()
				: cacheName.concat(CACHE_TYPE_JOIN_SIGN).concat(cacheType).toLowerCase();
		if (dynamicFetchCacheMap.containsKey(cacheNameLower)) {
			dynamicFetchCacheMap.remove(cacheNameLower);
			if (cacheType == null) {
				cacheRegistMap.remove(cacheName.toLowerCase());
			}
		}
	}

	@Override
	public boolean hasCache(String cacheName) {
		if (cacheRegistMap.containsKey(cacheName.toLowerCase())) {
			return true;
		}
		return false;
	}

	@Override
	public void initialize() {
		// 启动定时器
		scheduler.scheduleAtFixedRate(this::checkAndRemoveTimeoutData, 0, CHECK_INTERVAL, TimeUnit.SECONDS);
	}

	// 如果是数据保留时间，则建议Map数据集合为Map<key,Object[]{key,name1,name2,...,initTimeMillis},
	// 即在数据最后一列包含时间
	private void checkAndRemoveTimeoutData() {
		if (cacheInitTime.isEmpty()) {
			return;
		}
		long currentTime = System.currentTimeMillis();
		String cacheName;
		Long[] initTimeAndKeepAlive;
		for (Map.Entry<String, Long[]> entry : cacheInitTime.entrySet()) {
			cacheName = entry.getKey();
			initTimeAndKeepAlive = entry.getValue();
			if (currentTime > initTimeAndKeepAlive[0] + initTimeAndKeepAlive[1]) {
				dynamicFetchCacheMap.remove(cacheName);
				cacheInitTime.remove(cacheName);
				// 如果不是内部分组的缓存，同时清理掉注册表
				if (!cacheName.contains(CACHE_TYPE_JOIN_SIGN)) {
					cacheRegistMap.remove(cacheName);
				}
			}
		}
	}

	@Override
	public void destroy() {
		if (scheduler != null && !scheduler.isTerminated()) {
			scheduler.shutdownNow();
		}
	}
}
