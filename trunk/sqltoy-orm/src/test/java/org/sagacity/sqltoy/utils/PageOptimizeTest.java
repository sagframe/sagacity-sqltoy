package org.sagacity.sqltoy.utils;

public class PageOptimizeTest {
	public static void main(String[] args) {
		for (int i = 0; i < 100; i++) {
			PageOptimizeThread thread = new PageOptimizeThread(i);
			thread.start();
		}

	}
}
