package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.plugins.calculator.TreeDataSort;
import org.sagacity.sqltoy.utils.CollectionUtil;

/**
 * @project sagacity-sqltoy
 * @description 树形排序中 id 与 pid 的宽松匹配：类型相同时按 equals，类型不同时按文本比较(与行转列类别判重的口径一致)。
 *              典型场景是 id 列为 bigint、pid 列为 int，或 id 为数字、pid 为字符串等，此前任何 pid 都不等于任何 id，
 *              整批数据会被当成互不相干的根节点，树形结构与逐层汇总都不会发生。
 * @author zhongxuchen
 */
public class TreeIdPidTypeToleranceTest {
	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	private static LabelIndexModel labels() {
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		labels.put("pid", 1);
		labels.put("amt", 2);
		return labels;
	}

	private static List<Object> firstColumn(List<List<Object>> rows) {
		List<Object> values = new ArrayList<>(rows.size());
		for (List<Object> row : rows) {
			values.add(row.get(0));
		}
		return values;
	}

	/** id 为 Long、pid 为 Integer：仍能正确识别父子关系 */
	@Test
	public void processLinksAcrossIntegerWidths() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(2L, 1, "child"));
		rows.add(row(1L, 0, "root"));
		TreeDataSort.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows);
		assertEquals(Arrays.asList(1L, 2L), firstColumn(rows), "根在前、子节点紧随其后");
	}

	/** 类型不一致时逐层汇总同样生效 */
	@Test
	public void processRollsUpAcrossIntegerWidths() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0, 10L));
		rows.add(row(2L, 1, 5L));
		TreeDataSort.process(
				new TreeSortModel().setIdColumn("id").setPidColumn("pid").setSumColumns("amt"), labels(), rows);
		assertEquals(new BigDecimal("15"), rows.get(0).get(2), "子节点的值应汇总到父节点");
		assertEquals(5L, rows.get(1).get(2), "叶子节点保留原值");
	}

	/** id 为 BigDecimal、pid 为 Long：按文本 "1" 匹配 */
	@Test
	public void processMatchesNumericTextAcrossTypes() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(new BigDecimal("1"), 0L, 10L));
		rows.add(row(new BigDecimal("2"), 1L, 5L));
		TreeDataSort.process(
				new TreeSortModel().setIdColumn("id").setPidColumn("pid").setSumColumns("amt"), labels(), rows);
		assertEquals(Arrays.asList(new BigDecimal("1"), new BigDecimal("2")), firstColumn(rows));
		assertEquals(new BigDecimal("15"), rows.get(0).get(2));
	}

	/** 数字与字符串按文本匹配：id 为字符串 "1"，pid 为整数 1 */
	@Test
	public void processMatchesNumberAgainstString() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("1", 0, "root"));
		rows.add(row("2", 1, "child"));
		TreeDataSort.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows);
		assertEquals(Arrays.asList("1", "2"), firstColumn(rows));
	}

	/** 逐层汇总的父节点查找同样支持类型不一致 */
	@Test
	public void summaryFindsParentAcrossTypes() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0, 860L));
		for (int i = 2; i <= 41; i++) {
			rows.add(row((long) i, 1, 1L));
		}
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows,
				new Integer[] { 2 }, 0, 1);
		assertEquals(new BigDecimal("900"), rows.get(0).get(2), "父节点原始 860 + 40 个子节点各 1");
	}

	/** sortTreeList：传入的根 pid 与数据中的 pid 类型不同时仍能识别为根 */
	@Test
	public void sortTreeListAcceptsRootPidOfDifferentType() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(2L, 1L));
		rows.add(row(1L, 0L));
		List<List<Object>> sorted = CollectionUtil.sortTreeList(rows,
				(item) -> new Object[] { item.get(0), item.get(1) }, new Object[] { Integer.valueOf(0) });
		assertEquals(Arrays.asList(1L, 2L), firstColumn(sorted));
		assertTrue(rows.isEmpty(), "全部节点都挂到了树上");
	}

	/** 值本身不同(文本也不同)时不会被误配 */
	@Test
	public void differentValuesStillDoNotMatch() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0, "root"));
		rows.add(row(2L, 3, "no-such-parent"));
		TreeDataSort.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows);
		assertEquals(Arrays.asList(1L, 2L), firstColumn(rows), "pid=3 不对应任何 id，自己成为根");
	}

	/**
	 * 边界：同类型但小数位不同(BigDecimal 1.0 与 1.00)。宽松比较在类型相同时按 equals，两者不相等，不做匹配。
	 * 这条明确"宽松"的边界是文本比较，而不是数值相等
	 */
	@Test
	public void sameTypeDifferentScaleDoesNotMatch() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(new BigDecimal("1.0"), 0L, "root"));
		rows.add(row(new BigDecimal("2.0"), new BigDecimal("1.00"), "child"));
		TreeDataSort.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows);
		assertEquals(2, rows.size(), "两行都在(未被误配，也没有被丢弃前的异常)");
		assertTrue(firstColumn(rows).contains(new BigDecimal("1.0")));
		assertTrue(firstColumn(rows).contains(new BigDecimal("2.0")));
	}

	/** getTopPids 的公开语义：返回"没有对应 id 的 pid 取值" */
	@Test
	public void getTopPidsUsesTolerantMatching() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0L));
		rows.add(row(2L, 1L));
		assertEquals(Arrays.asList(0L), new ArrayList<>(TreeDataSort.getTopPids(rows, 0, 1)),
				"0 没有对应的 id，是根；1 对应 id=1");
		List<List<Object>> crossType = new ArrayList<>();
		crossType.add(row(1L, 0));
		crossType.add(row(2L, 1));
		assertEquals(Arrays.asList(0), new ArrayList<>(TreeDataSort.getTopPids(crossType, 0, 1)),
				"类型不同但文本相同，pid=1 仍对应 id=1");
	}

	/**
	 * 边界：逐层汇总时存在 id 为 null 的行。原先用 id.equals(pid) 会抛空指针(且是否抛出取决于扫描顺序)，
	 * 宽松比较对 null 是安全的：null 与任何非 null 值都不匹配，这一行不充当父节点，其余汇总正常进行
	 */
	@Test
	public void summaryToleratesNullNodeId() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(null, 0, 10L));
		rows.add(row(1L, 0, 5L));
		rows.add(row(2L, 1, 1L));
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows,
				new Integer[] { 2 }, 0, 1);
		assertEquals(10L, rows.get(0).get(2), "id 为 null 的行不参与汇总，保持原值");
		assertEquals(new BigDecimal("6"), rows.get(1).get(2), "子节点的值汇总到 id=1 的行");
	}

	/**
	 * 自定义取值类型：equals 按 id 判等，但 toString 带实例标记(相同 id 的文本不同)。
	 * 父子挂接必须按 equals 归并，不能按文本分桶，否则子节点会被拆成互不相干的组而挂不到父节点上
	 */
	static final class TaggedKey {
		private final long id;
		private final String tag;

		TaggedKey(long id, String tag) {
			this.id = id;
			this.tag = tag;
		}

		@Override
		public boolean equals(Object other) {
			return (other instanceof TaggedKey) && ((TaggedKey) other).id == id;
		}

		@Override
		public int hashCode() {
			return Long.valueOf(id).hashCode();
		}

		@Override
		public String toString() {
			return "k" + id + "#" + tag;
		}
	}

	@Test
	public void customTypeEqualButDifferentTextStillGroups() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(new TaggedKey(1, "root"), new TaggedKey(0, "r")));
		rows.add(row(new TaggedKey(2, "c1"), new TaggedKey(1, "c2")));
		rows.add(row(new TaggedKey(3, "c3"), new TaggedKey(1, "c4")));
		TreeDataSort.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows);
		assertEquals(3, rows.size(), "两个子节点的 pid 文本不同但 equals 相等，都应挂到根节点上");
		assertEquals(new TaggedKey(1, "root"), rows.get(0).get(0));
		assertEquals(new TaggedKey(2, "c1"), rows.get(1).get(0));
		assertEquals(new TaggedKey(3, "c3"), rows.get(2).get(0));
	}

	/** 空数据不受影响 */
	@Test
	public void emptyInputIsUntouched() {
		List<List<Object>> rows = new ArrayList<>();
		TreeDataSort.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels(), rows);
		assertTrue(rows.isEmpty());
		assertNull(CollectionUtil.sortTreeList((List) null,
				(item) -> new Object[] { ((List) item).get(0) }, new Object[] { 0 }));
	}
}