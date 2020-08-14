package org.sagacity.sqltoy.utils;

/**
 * @TODO 针对分页优化进行多线程模拟测试
 * @author zhongxuchen
 *
 */
public class PageOptimizeTest {
	public static void main(String[] args) {
		//模仿60个用户
		for (int i = 0; i < 60; i++) {
			PageOptimizeThread thread = new PageOptimizeThread(i);
			thread.start();
		}

	}
}
