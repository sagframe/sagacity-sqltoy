package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;

/**
 * processLoop的计数器生命周期回归:计数器用于@secure-loop确定参数名序号,
 * 原实现结束时无条件clear,嵌套解析会让外层计数器失效(外层后续@secure-loop读到null直接NPE);
 * 修复后为"保存-恢复":内层结束应还原外层计数值而非清空
 */
public class SqlProcessLoopCounterTest {

	private static String processLoop(String sql) throws Exception {
		Method method = SqlConfigParseUtils.class.getDeclaredMethod("processLoop", String.class, Map.class);
		method.setAccessible(true);
		Map<String, Object> keyValues = new HashMap<String, Object>();
		keyValues.put("status", 1);
		return (String) method.invoke(null, sql, keyValues);
	}

	@AfterEach
	public void tearDown() {
		SqlToyThreadDataHolder.clearCounter();
	}

	@Test
	public void uninitializedCounterStaysUninitialized() throws Exception {
		SqlToyThreadDataHolder.clearCounter();
		processLoop("select * from t where status=:status");
		// 进入前未初始化:结束后仍是未初始化状态(不能变成0)
		assertNull(SqlToyThreadDataHolder.getCounter());
	}

	@Test
	public void outerCounterIsRestoredAfterInnerCall() throws Exception {
		// 模拟外层已开始计数(外层已处理过1个@secure-loop)
		SqlToyThreadDataHolder.setCounter(1);
		processLoop("select * from t where status=:status");
		// 修复前:finally clear导致此处为null,外层后续@secure-loop会NPE
		assertEquals(1, SqlToyThreadDataHolder.getCounter().intValue(), "内层结束应恢复外层计数值");
	}

	@Test
	public void counterIncrementsWithinProcessing() throws Exception {
		SqlToyThreadDataHolder.setCounter(0);
		processLoop("select * from t where status=:status");
		// 内层处理过程中计数器被重置为0并可用(此处结束后恢复为进入前的0)
		assertEquals(0, SqlToyThreadDataHolder.getCounter().intValue());
	}
}
