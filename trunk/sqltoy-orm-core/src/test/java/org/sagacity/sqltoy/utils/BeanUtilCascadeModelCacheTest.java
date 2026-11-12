package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.TableCascadeModel;

/**
 * 回归测试:级联关系缓存改ClassValue后语义不变(同一Class只解析一次、不同Class互不干扰)
 *
 * <p>
 * 原静态ConcurrentHashMap以Class为键会强引用应用类(devtools/OSGi热重载下阻滞加载器回收);
 * ClassValue把条目挂在Class自身,类卸载即回收,同时保持"惰性解析+共享同一份结果"的既有行为。
 */
public class BeanUtilCascadeModelCacheTest {

	@SuppressWarnings("unused")
	private static class PlainEntity {
		private Integer id;
		private String name;
	}

	@SuppressWarnings("unused")
	private static class AnotherEntity {
		private Long code;
	}

	@Test
	public void sameClassSharesOneParsedResult() {
		List<TableCascadeModel> first = BeanUtil.getCascadeModels(PlainEntity.class);
		List<TableCascadeModel> second = BeanUtil.getCascadeModels(PlainEntity.class);
		assertSame(first, second, "同一Class只应解析一次并共享结果");
		// 无级联注解时为空列表(既有语义)
		assertTrue(first.isEmpty());
	}

	@Test
	public void differentClassesHaveOwnEntries() {
		List<TableCascadeModel> first = BeanUtil.getCascadeModels(PlainEntity.class);
		List<TableCascadeModel> second = BeanUtil.getCascadeModels(AnotherEntity.class);
		assertNotSame(first, second);
	}
}
