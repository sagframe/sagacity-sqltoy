package org.sagacity.sqltoy.translate.cache.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.sagacity.sqltoy.model.FIFOMap;
import org.sagacity.sqltoy.translate.cache.DynamicFecthCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description sqltoy框架提供动态获取缓存数据的缓存管理器实现，基于FIFOMap
 * @author zhongxuchen
 * @version v1.0,Date:2026年1月16日
 */
public class FIFODynamicFetchCacheManager implements DynamicFecthCacheManager {
	/**
	 * 定义日志
	 */
	private final Logger logger = LoggerFactory.getLogger(FIFODynamicFetchCacheManager.class);
	// 提供默认的先进先出Map队列作为动态缓存存储
	// FIFOMap可设置最大数据量如10万条，
	// 同时设置频繁使用的放在前面，使用不频繁的排在最先被挤出的位置
	private static ConcurrentHashMap<String, ConcurrentHashMap<String, FIFOMap<String, Object[]>>> dynamicFetchCacheMap = new ConcurrentHashMap<>();
	// 缓存被调用登记表，用户CacheUpdateWatcher更新检测，判断是否存在，不存在则无需检测
	private static Set<String> registCaches = ConcurrentHashMap.newKeySet();

	// 存放缓存初始化的时间，用于定时判断超过keep-alive清除缓存，下次使用时重新初始化
	private static ConcurrentHashMap<String, Long[]> cacheInitTime = new ConcurrentHashMap<>();

	// 拼接的特殊字符
	private static final String CACHE_TYPE_JOIN_SIGN = "_cachetype_";

	// 定时检测的线程池（单线程，避免多线程竞争）
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	// 检测线程的执行间隔：3分钟（可根据需求调整）
	private static final long CHECK_INTERVAL = 3 * 60;

	// 标记定时任务是否已经启动，防止重复调度
	private volatile boolean schedulerStarted = false;

	@Override
	public HashMap<String, Object[]> getDynamicCache(TranslateConfigModel cacheModel, String cacheType) {
		String cacheNameLower = cacheModel.getCache().toLowerCase();
		// 如果没有cacheType则用cacheName作为cacheType形成统一的二层结构
		String cacheTypeLower = (cacheType == null) ? cacheNameLower : cacheType.toLowerCase();
		String cacheKey = (cacheType == null) ? cacheNameLower
				: cacheNameLower.concat(CACHE_TYPE_JOIN_SIGN).concat(cacheTypeLower);
		// 通过computeIfAbsent原子性完成get-or-create，替代synchronized(cacheKey.intern())
		// 消除字符串常量池内存泄漏，同时修复同cacheName不同cacheType的竞态
		ConcurrentHashMap<String, FIFOMap<String, Object[]>> cacheElements = dynamicFetchCacheMap
				.computeIfAbsent(cacheNameLower, k -> {
					// 表示缓存已经开始使用，更新检测程序可以判断到可以对此缓存进行获取变化数据进行更新
					registCaches.add(cacheNameLower);
					return new ConcurrentHashMap<>();
				});
		FIFOMap<String, Object[]> cacheDatas = cacheElements.computeIfAbsent(cacheTypeLower, k -> {
			float loadFactor = cacheModel.getDynamicCacheLoadFactor();
			FIFOMap<String, Object[]> map = new FIFOMap(cacheModel.getDynamiceCacheInitSize(),
					cacheModel.getDynamicCacheMaxSize(), (loadFactor > 1) ? 0.75F : loadFactor, true);
			// 可设置<=0,则不放入过期检测，则表示长期有效
			if (cacheModel.getKeepAlive() > 0) {
				cacheInitTime.put(cacheKey, new Long[] { System.currentTimeMillis(), cacheModel.getKeepAlive() * 1L });
			}
			return map;
		});
		return cacheDatas;
	}

	@Override
	public void clear(String cacheName, String cacheType) {
		if (cacheName == null) {
			return;
		}
		// 统一转小写
		String cacheNameLower = cacheName.toLowerCase();
		if (dynamicFetchCacheMap.containsKey(cacheNameLower)) {
			if (cacheType == null) {
				logger.debug("清除动态查询数据缓存cacheName={}!", cacheName);
				dynamicFetchCacheMap.get(cacheNameLower).clear();
			} else {
				logger.debug("清除动态查询数据缓存cacheName={},cacheType={}!", cacheName, cacheType);
				dynamicFetchCacheMap.get(cacheNameLower).remove(cacheType.toLowerCase());
			}
		}
	}

	@Override
	public boolean hasCache(String cacheName) {
		if (cacheName == null) {
			return false;
		}
		if (registCaches.contains(cacheName.toLowerCase())) {
			return true;
		}
		return false;
	}

	@Override
	public void initialize() {
		// 启动定时器
		// 防止多次调用initialize重复提交定时任务
		if (!schedulerStarted) {
			scheduler.scheduleAtFixedRate(this::checkAndRemoveTimeoutData, 0, CHECK_INTERVAL, TimeUnit.SECONDS);
			schedulerStarted = true;
		}
	}

	// 如果设置数据保留时间，则建议Map数据集合为Map<key,Object[]{key,name1,name2,...,initTimeMillis},
	// 即在数据最后一列包含时间
	/**
	 * 清除数据保留时间超过keepAlive的缓存
	 */
	private void checkAndRemoveTimeoutData() {
		if (cacheInitTime.isEmpty()) {
			return;
		}
		String cacheNameLower;
		String cacheTypeLower;
		Long[] initTimeAndKeepAlive;
		String[] keySplit;
		String cacheKey;
		for (Map.Entry<String, Long[]> entry : cacheInitTime.entrySet()) {
			cacheKey = entry.getKey();
			keySplit = StringUtil.splitByIndex(cacheKey, CACHE_TYPE_JOIN_SIGN);
			cacheNameLower = keySplit[0];
			cacheTypeLower = null;
			if (keySplit.length == 2) {
				cacheTypeLower = keySplit[1];
			}
			initTimeAndKeepAlive = entry.getValue();
			if (System.currentTimeMillis() > initTimeAndKeepAlive[0] + initTimeAndKeepAlive[1] * 1000) {
				dynamicFetchCacheMap.get(cacheNameLower)
						.remove((cacheTypeLower == null) ? cacheNameLower : cacheTypeLower);
				// 清除过期缓存使用时间定义
				cacheInitTime.remove(cacheKey);
				logger.debug("缓存:cacheName={},cacheType={}数据存放时间超过keepAlive={}秒,自动被清除!", cacheNameLower, cacheTypeLower,
						initTimeAndKeepAlive[1]);
			}
		}
	}

	@Override
	public void destroy() {
		if (scheduler != null && !scheduler.isTerminated()) {
			scheduler.shutdownNow();
		}
		schedulerStarted = false;
	}
}
