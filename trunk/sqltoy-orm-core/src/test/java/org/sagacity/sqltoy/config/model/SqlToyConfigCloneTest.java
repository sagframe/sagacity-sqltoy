package org.sagacity.sqltoy.config.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：clone()必须对translateMap做深拷贝。DialectUtils.getUnifyParamsNamedConfig 会对clone后的
 * translateMap就地put动态translate并改写FieldTranslate的translates数组,浅共享会把本次查询的动态翻译
 * 永久写入缓存中的配置实例(污染后续同sqlId查询),并发下还会写非线程安全的HashMap
 */
public class SqlToyConfigCloneTest {

	@Test
	public void cloneIsolatesTranslateMapFromDynamicTranslateWrite() {
		SqlToyConfig config = new SqlToyConfig("sqlId_1", "select * from table");
		HashMap<String, FieldTranslate> translateMap = new HashMap<String, FieldTranslate>();
		FieldTranslate statusTranslate = new FieldTranslate();
		statusTranslate.colName = "status";
		statusTranslate.put(new Translate("dictStatus").setColumn("status"));
		translateMap.put("status", statusTranslate);
		config.setTranslateMap(translateMap);

		SqlToyConfig cloned = config.clone();
		assertNotSame(config.getTranslateMap(), cloned.getTranslateMap());
		assertNotSame(config.getTranslateMap().get("status"), cloned.getTranslateMap().get("status"));

		// 模拟getUnifyParamsNamedConfig的动态translate写入:同列追加新缓存翻译+新增列
		cloned.getTranslateMap().get("status").put(new Translate("dictStatus2").setColumn("status"));
		cloned.getTranslateMap().put("sexType", new FieldTranslate());

		// 原配置(缓存实例)必须保持不变
		assertEquals(1, config.getTranslateMap().size());
		assertSame(statusTranslate, config.getTranslateMap().get("status"));
		assertEquals(1, statusTranslate.translates.length);
		// clone自身生效
		assertEquals(2, cloned.getTranslateMap().size());
		assertEquals(2, cloned.getTranslateMap().get("status").translates.length);
	}

	@Test
	public void emptyTranslateMapCloneIsolation() {
		// 原配置无xml翻译声明时动态translate查询正是往这个空map里put,空map同样不能共享
		SqlToyConfig config = new SqlToyConfig("sqlId_2", "select * from table");
		SqlToyConfig cloned = config.clone();
		assertNotSame(config.getTranslateMap(), cloned.getTranslateMap());
		cloned.getTranslateMap().put("sexType", new FieldTranslate());
		assertEquals(0, config.getTranslateMap().size());
		assertEquals(1, cloned.getTranslateMap().size());
	}
}
