package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 回归测试：实体扫描不做JVM级类名去重(防重职责由EntityManager.parseEntityMeta的
 * 实例级幂等承担)——同一pattern第二次扫描(模拟Spring上下文刷新/热部署/多上下文的
 * 二次initialize)返回相同结果;修复前静态LOADED_CLASS_CACHE使第二次扫描返回空,
 * 新EntityManager的实体元数据静默丢失
 */
public class EntityScannerRepeatScanTest {

	private static final String PATTERN = "org.sagacity.sqltoy.demo.domain";

	@Test
	public void secondScanSessionReturnsSameEntities() throws Exception {
		List<Class<?>> first = EntityScanner.scanEntityClasses(PATTERN, true, "UTF-8");
		assertFalse(first.isEmpty(), "测试包中应存在@SqlToyEntity实体(StaffInfo)");
		assertTrue(first.stream().anyMatch(c -> "StaffInfo".equals(c.getSimpleName())),
				"实际:" + first);
		// 第二次扫描:修复前命中静态类名缓存返回空列表
		List<Class<?>> second = EntityScanner.scanEntityClasses(PATTERN, true, "UTF-8");
		assertEquals(first.size(), second.size(), "二次扫描结果应与首次一致(修复前为0)");
		assertTrue(second.stream().anyMatch(c -> "StaffInfo".equals(c.getSimpleName())));
		// 连续多次模拟多上下文反复初始化
		for (int i = 0; i < 3; i++) {
			assertEquals(first.size(), EntityScanner.scanEntityClasses(PATTERN, true, "UTF-8").size());
		}
	}
}
