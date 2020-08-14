package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

public class PageOptimizeThread extends Thread {

	public PageOptimizeThread() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() {
		SqlToyConfig sqlToyConfig = new SqlToyConfig("mysql");
		sqlToyConfig.setId("sqltoy_showcase");
		PageOptimize pageOptimize = new PageOptimize();
		pageOptimize.aliveSeconds(30);
		pageOptimize.aliveMax(100);
		while (true) {
			String key = "key_" + NumberUtil.getRandomNum(10000, 100000);
		}
	}

}
