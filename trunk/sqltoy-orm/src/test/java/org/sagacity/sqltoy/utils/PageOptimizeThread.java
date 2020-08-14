package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.utils.PageOptimizeUtils;

public class PageOptimizeThread extends Thread {

	private int index = 0;

	public PageOptimizeThread(int i) {
		this.index = i;
	}

	@Override
	public void run() {
		SqlToyConfig sqlToyConfig = new SqlToyConfig("mysql");
		sqlToyConfig.setId("sqltoy_showcase");
		PageOptimize pageOptimize = new PageOptimize();
		//60秒
		pageOptimize.aliveSeconds(60);
		//100个
		pageOptimize.aliveMax(200);
		while (true) {
			//验证超量则需要随机记录>aliveMax,验证超时尽量将量控制在aliveMax边缘
			String key = "key_" + NumberUtil.getRandomNum(1, 250);
			try {
				//每次操作间隔在3~50秒之间
				Thread.sleep(NumberUtil.getRandomNum(3, 25)*1000);
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
