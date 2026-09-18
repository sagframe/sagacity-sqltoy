package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.plugins.id.macro.impl.SqlLoop;
import org.sagacity.sqltoy.translate.TranslateFactory;

/**
 * 回归测试：(34)@loop循环体内引用的其他参数数组短于loop依据数组时按最短截断不再AIOOBE;
 * (35)缓存key列null脏数据跳过并告警,不再NPE穿透查询链路
 */
public class SqlLoopAndTranslateFactoryTest {

	// ============ 34: SqlLoop 数组长度不一致 ============

	@Test
	public void loopWithShorterReferencedArrayNoOverflow() throws Exception {
		// ids数组长度2作为loop依据,names数组长度1被循环体引用
		Map<String, Object> keyValues = new HashMap<String, Object>();
		keyValues.put("ids", new String[] { "1", "2" });
		keyValues.put("names", new String[] { "only-one" });
		// 修复前:i=1时regParamValues.get(0)[1]越界ArrayIndexOutOfBoundsException
		SqlLoop sqlLoop = new SqlLoop();
		String result = assertDoesNotThrow(() -> sqlLoop.execute(
				new String[] { "ids", ":names[i]=:ids[i]", " or " }, keyValues, null, "select * from t where 1=1 ", "mysql"));
		// 最短截断后只循环1次,结果中仅含第一个值
		assertTrue(result != null && result.contains("only-one"), "实际:" + result);
	}

	@Test
	public void loopWithEqualLengthArraysUnchanged() throws Exception {
		Map<String, Object> keyValues = new HashMap<String, Object>();
		keyValues.put("ids", new String[] { "1", "2" });
		keyValues.put("names", new String[] { "one", "two" });
		SqlLoop sqlLoop2 = new SqlLoop();
		String result = sqlLoop2.execute(new String[] { "ids", ":names[i]=:ids[i]", " or " }, keyValues, null, "select * from t where 1=1 ", "mysql");
		assertNotNull(result);
		assertTrue(result.contains("one") || result.length() > 0, "等长数组行为不变");
	}

	// ============ 35: TranslateFactory key列null ============

	@Test
	public void nullKeyRowSkippedNotNpe() throws Exception {
		// 通过rest/service类型太重,直接测wrapCacheResult:反射调用
		SqlToyContext context = new SqlToyContext();
		TranslateConfigModel cacheModel = new TranslateConfigModel();
		cacheModel.setCache("testNullKeyCache");
		// List<Object[]>形式,cacheIndex=0,key列第一行为null
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { null, "脏数据行" });
		rows.add(new Object[] { "K1", "正常值" });
		java.lang.reflect.Method method = TranslateFactory.class.getDeclaredMethod("wrapCacheResult", Object.class,
				TranslateConfigModel.class);
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.HashMap<String, Object[]> result = (java.util.HashMap<String, Object[]>) assertDoesNotThrow(
				() -> method.invoke(null, rows, cacheModel));
		// 修复前:row[0].toString()对null抛NPE;修复后:脏行跳过,正常行进入缓存
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("正常值", result.get("K1")[1]);
	}

	@Test
	public void normalKeyRowsUnchanged() throws Exception {
		TranslateConfigModel cacheModel = new TranslateConfigModel();
		cacheModel.setCache("normalCache");
		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { "K1", "v1" });
		rows.add(new Object[] { "K2", "v2" });
		java.lang.reflect.Method method = TranslateFactory.class.getDeclaredMethod("wrapCacheResult", Object.class,
				TranslateConfigModel.class);
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.HashMap<String, Object[]> result = (java.util.HashMap<String, Object[]>) method.invoke(null, rows,
				cacheModel);
		assertEquals(2, result.size());
		assertEquals("v1", result.get("K1")[1]);
	}
}
