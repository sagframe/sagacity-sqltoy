/**
 * 
 */
package org.sagacity.sqltoy.translate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.TimeSection;
import org.sagacity.sqltoy.utils.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.2
 * @description 定时检测缓存是否更新程序
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CacheUpdateWatcher.java,Revision:v1.0,Date:2018年3月11日
 * @modify {Date:2019-1-22,修改检测时间格式为yyyy-MM-dd HH:mm:ss 避免时间对比精度差异}
 * @modify {Date:2019-10-14,增加集群节点的时间差异参数,便于包容性检测缓存更新}
 * @modify {Date:2020-3-26,增加缓存增量更新机制,而不是清除缓存，然后重新全量获取更新}
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

	private final String prefix = "checker_";

	/**
	 * 时间格式到秒级别(避免存在时间精度的差异)
	 */
	private final String dateFmt = DateUtil.FORMAT.DATETIME_HORIZONTAL;

	/**
	 * 更新检测器
	 */
	private List<CheckerConfigModel> updateCheckers;

	private SqlToyContext sqlToyContext;

	private TranslateCacheManager translateCacheManager;

	/**
	 * 默认缓存刷新检测间隔时间(秒)
	 */
	private static int defaultIntervalSeconds = 15;

	/**
	 * 延时检测时长
	 */
	private int delaySeconds = 30;

	/**
	 * 集群的节点时间差异(秒)
	 */
	private int deviationSeconds = 0;

	public CacheUpdateWatcher(SqlToyContext sqlToyContext, TranslateCacheManager translateCacheManager,
			List<CheckerConfigModel> updateCheckers, int delaySeconds, int deviationSeconds) {
		this.sqlToyContext = sqlToyContext;
		this.translateCacheManager = translateCacheManager;
		this.updateCheckers = updateCheckers;
		this.delaySeconds = delaySeconds;
		this.deviationSeconds = deviationSeconds;
		// 初始化检测时间
		if (updateCheckers != null && !updateCheckers.isEmpty()) {
			Long checkTime = DateUtil.parse(System.currentTimeMillis(), dateFmt).getTime();
			for (int i = 0; i < updateCheckers.size(); i++) {
				lastCheckTime.put(prefix + i, checkTime);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.TimerTask#run()
	 */
	@Override
	public void run() {
		// 延时
		try {
			if (delaySeconds >= 1) {
				Thread.sleep(1000 * delaySeconds);
			}
		} catch (InterruptedException e) {
		}
		boolean isRun = true;
		while (isRun) {
			Long preCheck;
			CheckerConfigModel checkerConfig;
			long interval;
			long nowInterval;
			String checker;
			// 多个检测任务
			for (int i = 0; i < updateCheckers.size(); i++) {
				checker = prefix + i;
				checkerConfig = updateCheckers.get(i);
				// 上次检测时间
				preCheck = lastCheckTime.get(checker);
				// 当前检测时间
				long nowMillis = System.currentTimeMillis();
				// 当前的时间间隔
				nowInterval = (nowMillis - preCheck.longValue()) / 1000;
				LocalDateTime ldt = LocalDateTime.now();
				// 当前时间区间格式HHmm
				int hourMinutes = ldt.getHour() * 100 + ldt.getMinute();
				interval = getInterval(checkerConfig.getTimeSections(), hourMinutes);
				// 间隔大于设定阈值,执行检测
				if (nowInterval >= interval) {
					// 更新最后检测时间
					lastCheckTime.put(checker, Long.valueOf(DateUtil.parse(nowMillis, dateFmt).getTime()));
					// 执行检测(检测时间扣减集群节点时间偏离)
					doCheck(sqlToyContext, checkerConfig, DateUtil.addSecond(preCheck, deviationSeconds).getTime());
				}
			}
			try {
				// 一秒钟监测一次是否有到时的检测任务
				Thread.sleep(1000);
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
		List<CacheCheckResult> results = TranslateFactory.doCheck(sqlToyContext, checkerConfig,
				DateUtil.getTimestamp(lastCheckTime));
		if (results == null || results.isEmpty())
			return;
		// 非增量更新检测(发生变更即清空缓存)
		if (!checkerConfig.isIncrement()) {
			try {
				for (CacheCheckResult result : results) {
					logger.debug("检测到缓存:{} 类别:{} 发生更新!", result.getCacheName(), result.getCacheType());
					translateCacheManager.clear(result.getCacheName(), result.getCacheType());
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("缓存变更检测检测到更新后,清除缓存发生异常:{}", e.getMessage());
			}
		} // 增量直接更新缓存
		else {
			String cacheName = checkerConfig.getCache();
			try {
				logger.debug("检测到缓存:{} 发生{}条记录更新!", cacheName, results.size());
				HashMap<String, Object[]> cacheData;
				// 内部不存在分组的缓存
				if (!checkerConfig.isHasInsideGroup()) {
					cacheData = translateCacheManager.getCache(cacheName, null);
					// 缓存为null,等待首次调用进行加载
					if (cacheData == null)
						return;
					for (CacheCheckResult result : results) {
						// key不能为null
						if (result.getItem()[0] != null) {
							cacheData.put(result.getItem()[0].toString(), result.getItem());
						}
					}
				} // 内部存在分组的缓存(如数据字典)
				else {
					for (CacheCheckResult result : results) {
						if (result.getItem()[0] != null) {
							cacheData = translateCacheManager.getCache(cacheName, result.getCacheType());
							//为null则等待首次调用加载
							if (cacheData != null) {
								cacheData.put(result.getItem()[0].toString(), result.getItem());
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("缓存增量更新检测,更新缓存:{} 发生异常:{}", cacheName, e.getMessage());
			}
		}
	}
}
