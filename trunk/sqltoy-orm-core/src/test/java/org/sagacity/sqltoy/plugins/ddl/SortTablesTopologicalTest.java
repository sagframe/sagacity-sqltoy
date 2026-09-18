package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.ForeignModel;

/**
 * DDLUtils.sortTables Kahn拓扑排序功能矩阵:
 * 1.无依赖→字典序;2.单链/链式依赖;3.多依赖;4.自环(树表自引用);5.外表引用忽略;
 * 6.环→名序追加不吞表;7.同层字典序可复现;8.空map;9.多外键指向同表去重。
 */
public class SortTablesTopologicalTest {

	private static EntityMeta table(String name) {
		EntityMeta em = new EntityMeta();
		em.setTableName(name);
		return em;
	}

	private static EntityMeta tableWithFk(String name, String... foreignTables) {
		EntityMeta em = table(name);
		Map<String, ForeignModel> fks = new HashMap<String, ForeignModel>();
		int i = 0;
		for (String ft : foreignTables) {
			ForeignModel fm = new ForeignModel();
			fm.setForeignTable(ft);
			fks.put("fk_" + i, fm);
			i++;
		}
		if (!fks.isEmpty()) {
			em.setForeignFields(fks);
		}
		return em;
	}

	private static ConcurrentHashMap<String, EntityMeta> mapOf(EntityMeta... metas) {
		ConcurrentHashMap<String, EntityMeta> map = new ConcurrentHashMap<String, EntityMeta>();
		for (EntityMeta em : metas) {
			map.put(em.getTableName(), em);
		}
		return map;
	}

	private static List<String> names(List<EntityMeta> list) {
		return list.stream().map(EntityMeta::getTableName).collect(Collectors.toList());
	}

	@Test
	public void noDependenciesAlphabetical() {
		List<EntityMeta> result = DDLUtils.sortTables(mapOf(table("c"), table("a"), table("b")));
		assertEquals(Arrays.asList("a", "b", "c"), names(result), "无依赖按字典序");
	}

	@Test
	public void singleDependency() {
		// B依赖A → A在前
		List<EntityMeta> result = DDLUtils.sortTables(mapOf(tableWithFk("B", "A"), table("A")));
		assertEquals(Arrays.asList("A", "B"), names(result), "被依赖表在前");
	}

	@Test
	public void chainedDependency() {
		// C依赖B, B依赖A → A,B,C (原贪心算法在ConcurrentHashMap不利迭代序下产出B,C,A)
		List<EntityMeta> result = DDLUtils.sortTables(
				mapOf(tableWithFk("C", "B"), tableWithFk("B", "A"), table("A")));
		assertEquals(Arrays.asList("A", "B", "C"), names(result), "链式依赖顺序正确");
	}

	@Test
	public void multipleDependencies() {
		// D依赖A和B → A,B都在D之前
		List<EntityMeta> result = DDLUtils.sortTables(
				mapOf(tableWithFk("D", "A", "B"), table("A"), table("B")));
		List<String> order = names(result);
		assertTrue(order.indexOf("A") < order.indexOf("D") && order.indexOf("B") < order.indexOf("D"),
				"多依赖表的依赖均在前: " + order);
	}

	@Test
	public void selfReferenceIgnored() {
		// 树表自引用外键(id的parent_id指向同表id),不应阻塞排序
		List<EntityMeta> result = DDLUtils.sortTables(mapOf(tableWithFk("tree_table", "tree_table")));
		assertEquals(1, result.size(), "自环表应正常输出");
		assertEquals("tree_table", result.get(0).getTableName(), "自环不阻塞");
	}

	@Test
	public void foreignTableOutsideSetIgnored() {
		// 外键指向不存在的表,应忽略不参与排序
		List<EntityMeta> result = DDLUtils.sortTables(mapOf(tableWithFk("B", "NONEXISTENT")));
		assertEquals(1, result.size(), "节点集外的外表引用应忽略");
	}

	@Test
	public void cycleAppendedInNameOrder() {
		// A依赖B,B依赖A(环):排序层不吞表,按名序追加
		List<EntityMeta> result = DDLUtils.sortTables(
				mapOf(tableWithFk("B", "A"), tableWithFk("A", "B")));
		assertEquals(2, result.size(), "环不应吞表");
	}

	@Test
	public void emptyMapReturnsEmpty() {
		assertTrue(DDLUtils.sortTables(new ConcurrentHashMap<String, EntityMeta>()).isEmpty(), "空map返回空列表");
	}

	@Test
	public void duplicateFkToSameTableDeduped() {
		// 同表两个外键指向同一外表(如两个字段都引用dict表),不应虚增入度
		EntityMeta multi = table("multi_fk");
		Map<String, ForeignModel> fks = new HashMap<String, ForeignModel>();
		ForeignModel fk1 = new ForeignModel();
		fk1.setForeignTable("dict");
		ForeignModel fk2 = new ForeignModel();
		fk2.setForeignTable("dict");
		fks.put("fk_type", fk1);
		fks.put("fk_status", fk2);
		multi.setForeignFields(fks);
		List<EntityMeta> result = DDLUtils.sortTables(mapOf(multi, table("dict")));
		assertEquals(2, result.size(), "同表多外键指向同表去重后应正常出队");
	}

	@Test
	public void alphabeticalWithinSameLayer() {
		// 同层(零入度)表按字典序出队,跨环境可复现
		List<EntityMeta> result = DDLUtils.sortTables(mapOf(table("delta"), table("alpha"), table("charlie")));
		assertEquals(Arrays.asList("alpha", "charlie", "delta"), names(result), "同层字典序");
	}
}
