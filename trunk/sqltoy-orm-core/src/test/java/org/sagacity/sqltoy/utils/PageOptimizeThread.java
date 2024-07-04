package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.utils.PageOptimizeUtils;

/**
 * 模拟单个用户不停的组合查询
 * 
 * @author zhong
 *
 */
public class PageOptimizeThread extends Thread {

	private SqlToyConfig sqlToyConfig;
	private PageOptimize pageOptimize;
	private int userId;

	public PageOptimizeThread(SqlToyConfig sqlToyConfig, PageOptimize pageOptimize, int userId) {
		this.sqlToyConfig = sqlToyConfig;
		this.pageOptimize = pageOptimize;
		this.userId = userId;
	}

	@Override
	public void run() {
		while (true) {
			// 验证超量则需要随机记录>aliveMax,验证超时尽量将量控制在aliveMax边缘
			String key = "key_" + NumberUtil.getRandomNum(1, 250);
			try {
				// 每次操作间隔在3~50秒之间
				Thread.sleep(NumberUtil.getRandomNum(3, 25) * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Long count = PageOptimizeUtils.getPageTotalCount(sqlToyConfig, pageOptimize, key);
			// System.err.println("thread=" + index + " key=" + key + " count=" + count);
			if (count == null) {
				PageOptimizeUtils.registPageTotalCount(sqlToyConfig, pageOptimize, key, 500L);
			}
		}
	}

}
