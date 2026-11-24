package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * ResultUtils 集合处理缺陷修复回归:
 * 1) hashGroupList 分组键逗号碰撞——逐列文本List键,不同组合不再误合并(一对多级联分组正确);
 * 2) getLinkColumnsId 多列link键下划线碰撞——逐列文本List,identity.equals逐元素比较不再误合并;
 * 3) sortList 混合类型列——按比较对动态判型,不再ClassCastException,同类型排序行为不变。
 */
public class ResultUtilsCollectionTest {

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	@SuppressWarnings("unchecked")
	private static LinkedHashMap<List<String>, List> hashGroup(List<List<Object>> rows, int[] idx) throws Exception {
		Method m = ResultUtils.class.getDeclaredMethod("hashGroupList", List.class, int[].class);
		m.setAccessible(true);
		return (LinkedHashMap<List<String>, List>) m.invoke(null, rows, idx);
	}

	private static List sortList(List<List<Object>> rows, int orderCol, boolean ascend) throws Exception {
		Method m = ResultUtils.class.getDeclaredMethod("sortList", List.class, int.class, int.class, int.class,
				boolean.class);
		m.setAccessible(true);
		return (List) m.invoke(null, rows, orderCol, 0, rows.size() - 1, ascend);
	}

	private static Object linkId(ResultSet rs, String[] columns) throws Exception {
		Method m = ResultUtils.class.getDeclaredMethod("getLinkColumnsId", ResultSet.class, String[].class);
		m.setAccessible(true);
		return m.invoke(null, rs, columns);
	}

	// ==================== hashGroupList ====================
	@Test
	public void hashGroupSeparatesCommaContainingValues() throws Exception {
		List<List<Object>> collide = new ArrayList<>();
		collide.add(row("x,y", "z", 1));
		collide.add(row("x", "y,z", 2));
		LinkedHashMap<List<String>, List> g = hashGroup(collide, new int[] { 0, 1 });
		assertEquals(2, g.size(), "含逗号的不同分组组合不应被合并");
	}

	@Test
	public void hashGroupNormalGrouping() throws Exception {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("a", "b", 1));
		rows.add(row("a", "b", 2));
		rows.add(row("c", "d", 3));
		LinkedHashMap<List<String>, List> g = hashGroup(rows, new int[] { 0, 1 });
		assertEquals(2, g.size(), "正常两个分组");
		assertEquals(2, g.get(Arrays.asList("a", "b")).size(), "同组两行");
		assertEquals(1, g.get(Arrays.asList("c", "d")).size());
	}

	// ==================== getLinkColumnsId ====================
	@Test
	public void linkIdSeparatesUnderscoreContainingValues() throws Exception {
		String url = "jdbc:h2:mem:link_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
		try (Connection conn = DriverManager.getConnection(url, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("create table link_t (c1 varchar(32), c2 varchar(32))");
			st.execute("insert into link_t values ('a_b','c'),('a','b_c')");
			try (ResultSet rs = st.executeQuery("select c1,c2 from link_t order by c1")) {
				assertTrue(rs.next());
				Object id1 = linkId(rs, new String[] { "c1", "c2" });
				assertTrue(rs.next());
				Object id2 = linkId(rs, new String[] { "c1", "c2" });
				assertNotEquals(id1, id2, "含下划线的不同多列组合link身份不应相同");
			}
			st.execute("shutdown");
		}
	}

	// ==================== sortList ====================
	@Test
	public void sortListMixedTypeDoesNotThrow() throws Exception {
		List<List<Object>> mixed = new ArrayList<>();
		mixed.add(row(1));
		mixed.add(row("abc"));
		mixed.add(row(2));
		sortList(mixed, 0, true);
		assertEquals(3, mixed.size(), "混合类型列排序不应抛ClassCastException");
	}

	@Test
	public void sortListHomogeneousBehaviourUnchanged() throws Exception {
		List<List<Object>> nums = new ArrayList<>();
		nums.add(row(3));
		nums.add(row(1));
		nums.add(row(2));
		sortList(nums, 0, true);
		assertEquals(Arrays.asList(row(1), row(2), row(3)), nums, "数字升序");

		List<List<Object>> strs = new ArrayList<>();
		strs.add(row("bb"));
		strs.add(row("a"));
		strs.add(row("cc"));
		sortList(strs, 0, true);
		assertEquals(Arrays.asList(row("a"), row("bb"), row("cc")), strs, "字符串按长度优先升序");

		List<List<Object>> dates = new ArrayList<>();
		dates.add(row(LocalDate.of(2020, 1, 3)));
		dates.add(row(LocalDate.of(2020, 1, 1)));
		dates.add(row(LocalDate.of(2020, 1, 2)));
		sortList(dates, 0, true);
		assertEquals(LocalDate.of(2020, 1, 1), dates.get(0).get(0), "LocalDate升序");
		assertEquals(LocalDate.of(2020, 1, 3), dates.get(2).get(0));

		// null排序: null视为最小(升序在前)
		List<List<Object>> withNull = new ArrayList<>();
		withNull.add(row(2));
		withNull.add(row((Object) null));
		withNull.add(row(1));
		sortList(withNull, 0, true);
		assertEquals(null, withNull.get(0).get(0), "null升序排在最前");
	}
}