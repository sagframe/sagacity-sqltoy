package org.sagacity.sqltoy.translate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.TimeSection;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 定时检测缓存是否更新程序
 * @author zhongxuchen
 * @version v1.0,Date:2018年3月11日
 * @modify {Date:2019-1-22,修改检测时间格式为yyyy-MM-dd HH:mm:ss 避免时间对比精度差异}
 * @modify {Date:2019-10-14,增加集群节点的时间差异参数,便于包容性检测缓存更新}
 * @modify {Date:2020-3-26,增加缓存增量更新机制,而不是清除缓存}
 */
public class CacheUpdateWatcher extends Thread {
	/**
	 * 定义日志
	 */
	private final Logger logger = LoggerFactory.getLogger(CacheUpdateWatcher.class);

	/**
	 * 最后检测时间
	 */
	private ConcurrentHashMap<String, Long> lastCheckTime = new ConcurrentHashMap<String, Long>();

	/**
	 * 时间格式到秒级别(避免存在时间精度的差异)
	 */
	private final String dateFmt = DateUtil.FORMAT.DATETIME_HORIZONTAL;

	/**
	 * 缓存数据更新检测器
	 */
	private CopyOnWriteArrayList<CheckerConfigModel> updateCheckers;

	private SqlToyContext sqlToyContext;

	private TranslateCacheManager translateCacheManager;

	private IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap = null;

	/**
	 * 默认缓存刷新检测间隔时间(秒)
	 */
	private static int defaultIntervalSeconds = 15;

	/**
	 * 延时检测时长
	 */
	private int delaySeconds = 30;

	/**
	 * 集群不同节点之间的时间差异(秒)
	 */
	private int deviationSeconds = 0;

	public CacheUpdateWatcher(SqlToyContext sqlToyContext, TranslateCacheManager translateCacheManager,
			IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap,
			CopyOnWriteArrayList<CheckerConfigModel> updateCheckers, int delaySeconds, int deviationSeconds) {
		this.sqlToyContext = sqlToyContext;
		this.translateCacheManager = translateCacheManager;
		this.translateMap = translateMap;
		this.updateCheckers = updateCheckers;
		this.delaySeconds = delaySeconds;
		this.deviationSeconds = deviationSeconds;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.TimerTask#run()
	 */
	@Override
	public void run() {
		boolean isRun = true;
		// 延时,避免项目启动过程中检测
		try {
			if (delaySeconds >= 1) {
				Thread.sleep(1000 * delaySeconds);
			}
		} catch (InterruptedException e) {
			isRun = false;
		}
		Long preCheck;
		CheckerConfigModel checkerConfig;
		long interval;
		long nowInterval;
		long nowMillis;
		String checker;
		LocalDateTime ldt;
		int hourMinutes;
		while (isRun) {
			// 多个检测任务
			for (int i = 0, n = updateCheckers.size(); i < n; i++) {
				checkerConfig = updateCheckers.get(i);
				checker = checkerConfig.getId();
				// 上次检测时间
				preCheck = lastCheckTime.get(checker);
				if (preCheck != null) {
					// 当前检测时间
					nowMillis = System.currentTimeMillis();
					// 当前的时间间隔
					nowInterval = (nowMillis - preCheck.longValue()) / 1000;
					ldt = LocalDateTime.now();
					// 当前时间区间格式HHmm
					hourMinutes = ldt.getHour() * 100 + ldt.getMinute();
					interval = getInterval(checkerConfig.getTimeSections(), hourMinutes);
					// 间隔大于设定阈值,执行检测
					if (nowInterval >= interval) {
						// 更新最后检测时间
						lastCheckTime.put(checker, Long.valueOf(DateUtil.parse(nowMillis, dateFmt).getTime()));
						// 执行检测(检测时间扣减集群节点时间偏离)
						doCheck(sqlToyContext, checkerConfig, DateUtil.addSecond(preCheck, deviationSeconds).getTime());
					}
				} // 首次检测
				else {
					lastCheckTime.put(checker, System.currentTimeMillis());
				}
			}
			try {
				// 一秒钟监测一次是否有到时的检测任务
				//update 2025-5-30 增加当前线程是否被终止的检测
				if (Thread.currentThread().isInterrupted()) {
					isRun = false;
				} else {
					Thread.sleep(1000);
				}
			} catch (InterruptedException e) {
				logger.warn("缓存翻译检测缓存变更异常,检测线程将终止!{}", e.getMessage(), e);
				isRun = false;
			}
		}
	}

	/**
	 * @todo 获取当前时间区间的检测间隔
	 * @param sections
	 * @param hourMinutes
	 * @return
	 */
	private long getInterval(List<TimeSection> sections, int hourMinutes) {
		// 默认15秒
		long interval = defaultIntervalSeconds;
		if (sections != null) {
			for (TimeSection section : sections) {
				if (hourMinutes >= section.getStart() && hourMinutes < section.getEnd()) {
					interval = section.getIntervalSeconds();
					break;
				}
			}
		}
		return interval;
	}

	/**
	 * @todo 执行检测并更新缓存
	 * @param sqlToyContext
	 * @param checkerConfig
	 * @param lastCheckTime
	 */
	private void doCheck(SqlToyContext sqlToyContext, CheckerConfigModel checkerConfig, Long lastCheckTime) {
		TranslateConfigModel translateConfig;
		// 非增量更新检测(发生变更即清空缓存)
		if (!checkerConfig.isIncrement()) {
			List<CacheCheckResult> results = TranslateFactory.doCheck(sqlToyContext, checkerConfig,
					DateUtil.getTimestamp(lastCheckTime));
			if (results == null || results.isEmpty()) {
				return;
			}
			// 指定了缓存名称
			if (StringUtil.isNotBlank(checkerConfig.getCache())) {
				translateConfig = translateMap.get(checkerConfig.getCache());
				logger.debug("检测到缓存:{} 发生更新,将清除缓存便于后续缓存全量更新!", translateConfig.getCache());
				translateCacheManager.clear(translateConfig.getCache(), null);
			} else {
				for (CacheCheckResult result : results) {
					translateConfig = translateMap.get(result.getCacheName());
					if (translateConfig != null) {
						logger.debug("检测到缓存发生更新: cacheName:{} cacheType:{}!", translateConfig.getCache(),
								(result.getCacheType() == null) ? "无" : result.getCacheType());
						translateCacheManager.clear(translateConfig.getCache(), result.getCacheType());
					}
				}
			}
		} // 增量直接更新缓存
		else {
			translateConfig = translateMap.get(checkerConfig.getCache());
			if (translateConfig == null) {
				return;
			}
			String cacheName = translateConfig.getCache();
			// 缓存还未首次加载不做更新检测
			if (!translateCacheManager.hasCache(cacheName)) {
				return;
			}
			List<CacheCheckResult> results = TranslateFactory.doCheck(sqlToyContext, checkerConfig,
					DateUtil.getTimestamp(lastCheckTime));
			if (results == null || results.isEmpty()) {
				return;
			}
			logger.debug("检测到缓存cacheName:{} 发生:{} 条记录更新!", cacheName, results.size());
			HashMap<String, Object[]> cacheData;
			int count = 0;
			try {
				// 内部不存在分组的缓存
				if (!checkerConfig.isHasInsideGroup()) {
					cacheData = translateCacheManager.getCache(cacheName, null);
					if (cacheData != null) {
						for (CacheCheckResult result : results) {
							// key不能为null
							if (result.getItem() != null && result.getItem()[0] != null) {
								cacheData.put(result.getItem()[0].toString(), result.getItem());
								count++;
							}
						}
					}
				} // 内部存在分组的缓存(如数据字典)
				else {
					for (CacheCheckResult result : results) {
						if (result.getItem() != null && result.getItem()[0] != null) {
							cacheData = translateCacheManager.getCache(cacheName, result.getCacheType());
							// 为null则等待首次调用加载
							if (cacheData != null) {
								cacheData.put(result.getItem()[0].toString(), result.getItem());
								count++;
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("缓存增量更新检测,更新缓存:{} 发生异常:{}", cacheName, e.getMessage());
			}
			logger.debug("缓存实际完成:{} 条记录更新!", count);
		}
	}
}
