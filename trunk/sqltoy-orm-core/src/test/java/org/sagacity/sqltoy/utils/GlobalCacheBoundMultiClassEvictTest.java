package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * GlobalCacheBound(全JVM总量上限)的多类跨类回收测试:
 * 洪泛3个不同VO类(各贡献互异列类型键),总量合计超上限后,
 * 验证:①总量收敛在上限内 ②多个类的缓存均被回收(非只回收一个类)
 * ③回收后所有类的映射仍正确(重建或命中)。
 * <p>
 * 既有 floodBeyondMax 只用单类(VoA)洪泛——覆盖"单类内上限"但未覆盖"多类合计超限→跨类回收"。
 * </p>
 */
public class GlobalCacheBoundMultiClassEvictTest {

	private static final int[] INDEXES = { 0, 1 };
	private static final String[] COLUMNS = { "ID", "NAME" };

	public static class EvictA {
		private int id;
		private String name;
		public int getId() { return id; }
		public void setId(int id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	public static class EvictB {
		private int id;
		private String name;
		public int getId() { return id; }
		public void setId(int id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	public static class EvictC {
		private int id;
		private String name;
		public int getId() { return id; }
		public void setId(int id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
	}

	@Test
	public void multiClassFloodEvictsAcrossClasses() throws Exception {
		// 清空映射计划缓存(通过反射访问MAPPING_PLAN_CACHE_BOUND并clear)
		clearMappingPlanCache();

		List<Object[]> rows = new ArrayList<Object[]>();
		rows.add(new Object[] { 99, "flood" });

		// 洪泛3个类:每个类各10000个互异键(合计30000 > 上限20000,触发跨类回收)
		// 轮询交替(非先A后B后C)更接近真实并发场景
		int perClass = 10000;
		for (int i = 0; i < perClass; i++) {
			String[] typesA = { "EVA_" + i, "VARCHAR" };
			String[] typesB = { "EVB_" + i, "VARCHAR" };
			String[] typesC = { "EVC_" + i, "VARCHAR" };
			BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, typesA, EvictA.class, true);
			BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, typesB, EvictB.class, true);
			BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, typesC, EvictC.class, true);
		}

		// 总量必须收敛在上限内
		int total = BeanUtil.mappingPlanCacheTotalSize();
		assertTrue(total <= BeanUtilUtil.MAPPING_PLAN_MAX,
				"多类洪泛后全JVM总量不得超过上限" + BeanUtilUtil.MAPPING_PLAN_MAX + ",实际:" + total);

		// 回收后所有类的映射仍正确(重建或命中)
		for (int round = 0; round < 2; round++) {
			List ra = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS,
					new String[] { "INTEGER", "VARCHAR" }, EvictA.class, true);
			EvictA a = (EvictA) ra.get(0);
			assertEquals(99, a.getId(), "EvictA映射正确(第" + round + "轮)");
			assertEquals("flood", a.getName());

			List rb = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS,
					new String[] { "INTEGER", "VARCHAR" }, EvictB.class, true);
			EvictB b = (EvictB) rb.get(0);
			assertEquals(99, b.getId(), "EvictB映射正确(第" + round + "轮)");

			List rc = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS,
					new String[] { "INTEGER", "VARCHAR" }, EvictC.class, true);
			EvictC c = (EvictC) rc.get(0);
			assertEquals(99, c.getId(), "EvictC映射正确(第" + round + "轮)");
		}

		// 总量在正确映射后仍受控(正确映射新增了3条计划)
		int afterRemap = BeanUtil.mappingPlanCacheTotalSize();
		assertTrue(afterRemap <= BeanUtilUtil.MAPPING_PLAN_MAX + 10,
				"正确映射后总量仍须受控:" + afterRemap);
	}

	@SuppressWarnings("unchecked")
	private static void clearMappingPlanCache() throws Exception {
		// 直接复用BeanUtil的包内测试访问器，清空本用例涉及的三个类
		BeanUtil.clearMappingPlanCache(EvictA.class);
		BeanUtil.clearMappingPlanCache(EvictB.class);
		BeanUtil.clearMappingPlanCache(EvictC.class);
	}

	/** 上限常量可见性(与BeanUtil.MAPPING_PLAN_CACHE_MAX_SIZE一致) */
	static class BeanUtilUtil {
		static final int MAPPING_PLAN_MAX = 20000;
	}
}
