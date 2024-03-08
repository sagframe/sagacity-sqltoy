/**
 * 
 */
package org.sagacity.sqltoy.utils;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.FIFOMap;

/**
 * @project sagacity-sqltoy
 * @description 测试先进先出队列
 * @author zhongxuchen
 * @version v1.0, Date:2024年1月19日
 * @modify 2024年1月19日,修改说明
 */
public class FIFOMapTest {
	@Test
	public void testFIFO() {
		FIFOMap map = new FIFOMap(5);
		for (int i = 0; i < 10; i++) {
			map.put(i, 10 * i + 1);
		}
		System.err.println("[" + map + "]");
	}

	@Test
	public void testActive() {
		FIFOMap map = new FIFOMap(10, true);
		for (int i = 0; i < 10; i++) {
			map.put(i, 10 * i + 1);
		}
		System.err.println("[" + map.get(0) + "]");
		System.err.println("[" + map.get(1) + "]");
		map.put(10, 10 * 10 + 1);
		map.put(11, 10 * 11 + 1);
		System.err.println("[" + map + "]");
	}
}
