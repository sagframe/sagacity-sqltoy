package org.sagacity.sqltoy.translate.cache.impl;

import java.util.HashMap;
import java.util.Locale;
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
 * @project sagacity-sqltoy
 * @description sqltoy框架提供动态获取缓存数据的缓存管理器实现，基于FIFOMap
 * @author zhongxuchen
 * @version v1.0,Date:2026-01-16
 */
public class FIFODynamicFetchCacheManager implements DynamicFecthCacheManager {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(FIFODynamicFetchCacheManager.class);

	// update 2026-11-15 状态Map与检测调度器由static改为实例级:JVM内多个SqlToyContext共用同一份
	// 动态缓存时,同名cache会串用数据、keepAlive/容量以首个创建者为准,且任一上下文destroy会停掉
	// 全局检测线程并保留静态数据(context重建后沿用旧数据);改为随上下文实例隔离,destroy只影响自身
	// 提供默认的先进先出Map队列作为动态缓存存储
	// FIFOMap可设置最大数据量如10万条，
	// 同时设置频繁使用的放在前面，使用不频繁的排在最先被挤出的位置
	private final ConcurrentHashMap<String, ConcurrentHashMap<String, FIFOMap<String, Object[]>>> dynamicFetchCacheMap = new ConcurrentHashMap<>();

	// 缓存被调用登记表，用户CacheUpdateWatcher更新检测，判断是否存在，不存在则无需检测
	private final Set<String> registCaches = ConcurrentHashMap.newKeySet();

	// 存放缓存初始化的时间，用于定时判断超过keep-alive清除缓存，下次使用时重新初始化
	private final ConcurrentHashMap<String, Long[]> cacheInitTime = new ConcurrentHashMap<>();

	// 拼接的特殊字符
	private static final String CACHE_TYPE_JOIN_SIGN = "_cachetype_";

	// 检测线程池(本实例独享),按需延迟启动:未使用动态缓存或未配置keepAlive的上下文不会产生线程
	private final Object schedulerLock = new Object();

	private volatile ScheduledExecutorService scheduler;

	// 检测线程的执行间隔：3分钟（可根据需求调整）
	private static final long CHECK_INTERVAL = 3 * 60;

	// 标记定时任务是否已经启动，防止重复调度
	private volatile boolean schedulerStarted = false;

	// 本实例是否已销毁,销毁后不再启动检测线程(重新initialize可恢复)
	private volatile boolean destroyed = false;

	@Override
	public HashMap<String, Object[]> getDynamicCache(TranslateConfigModel cacheModel, String cacheType) {
		String cacheNameLower = cacheModel.getCache().toLowerCase(Locale.ROOT);
		// 如果没有cacheType则用cacheName作为cacheType形成统一的二层结构
		String cacheTypeLower = (cacheType == null) ? cacheNameLower : cacheType.toLowerCase(Locale.ROOT);
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
			FIFOMap<String, Object[]> map = new FIFOMap(cacheModel.getDynamicCacheInitSize(),
					cacheModel.getDynamicCacheMaxSize(), (loadFactor > 1) ? 0.75F : loadFactor, true);
			// 可设置<=0,则不放入过期检测，则表示长期有效
			if (cacheModel.getKeepAlive() > 0) {
				cacheInitTime.put(cacheKey, new Long[] { System.currentTimeMillis(), cacheModel.getKeepAlive() * 1L });
			}
			return map;
		});
		// 需要过期检测时才延迟启动本实例的检测线程
		if (cacheModel.getKeepAlive() > 0) {
			startSchedulerIfNeeded();
		}
		return cacheDatas;
	}

	@Override
	public void clear(String cacheName, String cacheType) {
		if (cacheName == null) {
			return;
		}
		// 统一转小写
		String cacheNameLower = cacheName.toLowerCase(Locale.ROOT);
		if (dynamicFetchCacheMap.containsKey(cacheNameLower)) {
			if (cacheType == null) {
				logger.debug("cleared dynamic query data cache cacheName={}!", cacheName);
				// 先清过期登记再清数据:避免清数据后、清登记前并发重建写入的新登记被误删
				cacheInitTime.keySet().removeIf(key -> key.equals(cacheNameLower)
						|| key.startsWith(cacheNameLower.concat(CACHE_TYPE_JOIN_SIGN)));
				dynamicFetchCacheMap.get(cacheNameLower).clear();
			} else {
				logger.debug("cleared dynamic query data cache cacheName={}, cacheType={}!", cacheName, cacheType);
				cacheInitTime
						.remove(cacheNameLower.concat(CACHE_TYPE_JOIN_SIGN).concat(cacheType.toLowerCase(Locale.ROOT)));
				dynamicFetchCacheMap.get(cacheNameLower).remove(cacheType.toLowerCase(Locale.ROOT));
			}
		}
	}

	@Override
	public boolean hasCache(String cacheName) {
		if (cacheName == null) {
			return false;
		}
		if (registCaches.contains(cacheName.toLowerCase(Locale.ROOT))) {
			return true;
		}
		return false;
	}

	@Override
	public void initialize() {
		// 不再在初始化时创建检测线程:改为首个需要过期检测(keepAlive>0)的动态缓存创建时延迟启动,
		// 避免未使用动态缓存的SqlToyContext也常驻一个检测线程
		destroyed = false;
	}

	/**
	 * 启动过期检测定时任务;检测线程为本实例独享且为daemon,destroy未被调用时不会阻止JVM退出
	 */
	private void startSchedulerIfNeeded() {
		if (schedulerStarted && scheduler != null && !scheduler.isTerminated()) {
			return;
		}
		synchronized (schedulerLock) {
			if (schedulerStarted && scheduler != null && !scheduler.isTerminated()) {
				return;
			}
			// 已销毁的实例不再启动(重新initialize会复位destroyed)
			if (destroyed) {
				return;
			}
			ScheduledExecutorService newScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "sqltoy-fifo-dynamic-cache-checker");
				thread.setDaemon(true);
				return thread;
			});
			newScheduler.scheduleAtFixedRate(() -> {
				try {
					checkAndRemoveTimeoutData();
				} catch (Throwable t) {
					// update 2026-9-14
					// 兜底:scheduleAtFixedRate任务一旦抛出未捕获异常,后续执行会被永久取消(所有动态缓存的keepAlive检测静默失效且不再有任何日志),此处必须吞掉并记录
					logger.error("checkAndRemoveTimeoutData failed, the keepAlive check schedule will continue!", t);
				}
			}, 0, CHECK_INTERVAL, TimeUnit.SECONDS);
			scheduler = newScheduler;
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
		// update 2026-9-14
		// 逐条目隔离:任何单条登记数据的异常(值缺失、cacheKey形态异常、缓存已被移除等)不得中断整轮清理,更不能让定时任务因异常被永久取消
		for (Map.Entry<String, Long[]> entry : cacheInitTime.entrySet()) {
			try {
				cacheKey = entry.getKey();
				keySplit = StringUtil.splitByIndex(cacheKey, CACHE_TYPE_JOIN_SIGN);
				cacheNameLower = keySplit[0];
				cacheTypeLower = null;
				if (keySplit.length == 2) {
					cacheTypeLower = keySplit[1];
				}
				initTimeAndKeepAlive = entry.getValue();
				if (initTimeAndKeepAlive != null && initTimeAndKeepAlive[0] != null && initTimeAndKeepAlive[1] != null
						&& System.currentTimeMillis() > initTimeAndKeepAlive[0] + initTimeAndKeepAlive[1] * 1000) {
					ConcurrentHashMap<String, FIFOMap<String, Object[]>> cacheElements = dynamicFetchCacheMap
							.get(cacheNameLower);
					// 先条件删除过期登记再删数据:登记被并发重建(getDynamicCache写入新Long[]实例)时
					// remove(key,value)按身份不命中,跳过数据删除;若先删数据后删登记,重建写入的新登记
					// 会被误删导致该缓存永不过期
					if (cacheInitTime.remove(cacheKey, initTimeAndKeepAlive)) {
						// 缓存可能已不存在(如clear后被清理),此时仅需清理过期登记
						if (cacheElements != null) {
							cacheElements.remove((cacheTypeLower == null) ? cacheNameLower : cacheTypeLower);
						}
						logger.debug(
								"cache:cacheName={}, cacheType={} data stored longer than keepAlive={} seconds is automatically cleared!",
								cacheNameLower, cacheTypeLower, initTimeAndKeepAlive[1]);
					}
				}
			} catch (Exception e) {
				logger.error("failed to check the timeout data of the dynamic cache, cacheKey:{}", entry.getKey(), e);
			}
		}
	}

	@Override
	public void destroy() {
		// 只停止/清理本实例:一个SqlToyContext销毁不得影响同JVM其它存活上下文的动态缓存与检测线程
		ScheduledExecutorService current;
		synchronized (schedulerLock) {
			destroyed = true;
			current = scheduler;
			scheduler = null;
			schedulerStarted = false;
		}
		if (current != null && !current.isTerminated()) {
			current.shutdownNow();
		}
		// 释放本实例缓存数据与过期登记,避免上下文重建后沿用旧数据
		dynamicFetchCacheMap.clear();
		registCaches.clear();
		cacheInitTime.clear();
	}
}
