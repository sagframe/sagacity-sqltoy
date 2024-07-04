package org.sagacity.sqltoy.utils;

import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlToyConfig;

/**
 * @TODO 针对分页优化进行多线程模拟测试
 * @author zhongxuchen
 *
 */
public class PageOptimizeTest {
	public static void main(String[] args) {
		SqlToyConfig sqlToyConfig = new SqlToyConfig("mysql");
		sqlToyConfig.setId("sqltoy_showcase");
		PageOptimize pageOptimize = new PageOptimize();
		// 60秒
		pageOptimize.aliveSeconds(60);
		// 200个
		pageOptimize.aliveMax(200);
		// 模仿60个用户
		for (int i = 0; i < 60; i++) {
			PageOptimizeThread thread = new PageOptimizeThread(sqlToyConfig, pageOptimize,i);
			thread.start();
		}

	}
}
