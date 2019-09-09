/**
 * 
 */
package org.sagacity.sqltoy.translate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.TimeSection;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sagacity-sqltoy4.2
 * @description 定时检测缓存是否更新程序
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CacheCheckTimer.java,Revision:v1.0,Date:2018年3月11日
 * @Modification {Date:2019-1-22,修改检测时间格式为yyyy-MM-dd HH:mm:ss 避免时间对比精度差异}
 */
public class CacheCheckTimer extends Thread {
	/**
	 * 定义日志
	 */
	private final Logger logger = LogManager.getLogger(CacheCheckTimer.class);

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

	public CacheCheckTimer(SqlToyContext sqlToyContext, TranslateCacheManager translateCacheManager,
			List<CheckerConfigModel> updateCheckers) {
		this.sqlToyContext = sqlToyContext;
		this.translateCacheManager = translateCacheManager;
		this.updateCheckers = updateCheckers;
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
		boolean isRun = true;
		while (isRun) {
			Long preCheck;
			CheckerConfigModel checkerConfig;
			long interval;
			long nowInterval;
			String checker;
			//多个检测任务
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
					// 执行检测
					doCheck(sqlToyContext, checkerConfig, preCheck);
				}
			}
			try {
				// 一秒钟监测一次是否有到时的检测任务
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.warn("sql文件变更监测程序进程异常,监测将终止!{}", e.getMessage(), e);
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
		if (results != null) {
			try {
				for (CacheCheckResult result : results) {
					logger.debug("检测到缓存:{} 类别:{} 发生更新!", result.getCacheName(), result.getCacheType());
					translateCacheManager.clear(result.getCacheName(), result.getCacheType());
				}
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("doCheck clearCache error:{}", e.getMessage());
			}
		}
	}
}
