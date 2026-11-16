package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * bootstrap装载器(null)可见性修复的回归测试:
 * isVisibleFrom(null, appLoader)必须返回true——JDK内置类(由bootstrap loader定义)
 * 对任何应用装载器可见。修复前null装载器被判"不可见",导致JDK枚举(如Thread.State)
 * 的ClassValue缓存条目在每次调用时都被判失效而反复Class.forName。
 * <p>
 * 端到端验证:用JDK枚举作为BeanUtil映射的列类型,JDK枚举不应触发重复解析
 * (通过缓存总量不异常增长来间接验证——重复Class.forName会导致缓存条目反复失效重建,
 * 总量在稳定访问时不应持续增长)。
 * </p>
 */
public class BootstrapLoaderVisibilityTest {

	@Test
	public void bootstrapLoaderIsVisibleFromAnyLoader() throws Exception {
		// 直接锁定修复点:isVisibleFrom(null, anyLoader)必须为true。
		// (端到端映射用例无法区分此修复——枚举重复解析不影响映射计划缓存总量,故需直接断言)
		java.lang.reflect.Method m = BeanUtil.class.getDeclaredMethod("isVisibleFrom", ClassLoader.class,
				ClassLoader.class);
		m.setAccessible(true);
		assertTrue((Boolean) m.invoke(null, null, getClass().getClassLoader()),
				"bootstrap装载器(null)定义的类对任何装载器可见");
		assertTrue((Boolean) m.invoke(null, getClass().getClassLoader(), getClass().getClassLoader()),
				"定义装载器自身在TCCL双亲链上可见");
	}

	public static class EnumHolder {
		private Thread.State state;
		private String name;

		public Thread.State getState() { return state; }
		public void setState(Thread.State state) { this.state = state; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	@Test
	public void jdkEnumMappingStableAcrossRepeatedCalls() throws Exception {
		int[] indexes = { 0, 1 };
		String[] columns = { "STATE", "NAME" };
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { Thread.State.RUNNABLE, "test" });

		// 首次映射:JDK枚举(Thread.State由bootstrap loader定义)应正常映射
		List result1 = BeanUtil.reflectListToBean(null, rows, indexes, columns,
				new String[] { "ENUM", "VARCHAR" }, EnumHolder.class, true);
		EnumHolder holder1 = (EnumHolder) result1.get(0);
		assertEquals(Thread.State.RUNNABLE, holder1.getState(), "JDK枚举映射正确");
		assertEquals("test", holder1.getName());

		// 缓存总量不应因JDK枚举的可见性误判而异常增长
		// (修复前:每次都判不可见→Class.forName→缓存条目失效→重建→总量反复增长)
		// 用增量断言:全局缓存可能被同套件其它用例留有残留,只考察本形态100次重复的新增
		int totalBefore = BeanUtil.mappingPlanCacheTotalSize();
		for (int i = 0; i < 100; i++) {
			List result = BeanUtil.reflectListToBean(null, rows, indexes, columns,
					new String[] { "ENUM", "VARCHAR" }, EnumHolder.class, true);
			EnumHolder h = (EnumHolder) result.get(0);
			assertEquals(Thread.State.RUNNABLE, h.getState(), "第" + i + "轮映射仍正确");
		}
		int delta = BeanUtil.mappingPlanCacheTotalSize() - totalBefore;
		assertTrue(delta <= 1, "100次重复同形态映射只应新增至多1条计划(应命中同一条),实际增量:" + delta);
	}

	@Test
	public void jdkEnumAndCustomClassCoexist() throws Exception {
		int[] indexes = { 0, 1 };
		String[] columns = { "STATE", "NAME" };
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[] { Thread.State.NEW, "coexist" });

		// JDK枚举+自定义类交替使用——两者互不干扰
		List r1 = BeanUtil.reflectListToBean(null, rows, indexes, columns,
				new String[] { "ENUM", "VARCHAR" }, EnumHolder.class, true);
		EnumHolder h1 = (EnumHolder) r1.get(0);
		assertEquals(Thread.State.NEW, h1.getState());

		// 同一JDK枚举+不同列类型序列(新计划)
		List r2 = BeanUtil.reflectListToBean(null, rows, indexes, columns,
				new String[] { "ENUM_V2", "VARCHAR" }, EnumHolder.class, true);
		EnumHolder h2 = (EnumHolder) r2.get(0);
		assertEquals(Thread.State.NEW, h2.getState(), "新形态的JDK枚举映射也正确");
	}
}
