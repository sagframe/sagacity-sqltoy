package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.LongFunction;

import org.opentest4j.AssertionFailedError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.oracle.DiffSupport;
import org.sagacity.sqltoy.oracle.TreeDataSortLegacyOracle;
import org.sagacity.sqltoy.oracle.TreeFixtures;
import org.sagacity.sqltoy.oracle.TreeFixtures.TreeData;
import org.sagacity.sqltoy.plugins.calculator.TreeDataSort;

/**
 * @project sagacity-sqltoy
 * @description TreeDataSort 与优化前实现(行为基准)的差分对拍：树形排序(含逐层汇总、同层排序、汇总过滤)以及直接调用的逐层汇总，
 *              行顺序与每个单元格(含 BigDecimal 标度)、异常都必须完全一致。两个方法都是原地修改，两侧各拿一份独立的行对象。
 * @author zhongxuchen
 */
public class TreeDataSortDifferentialTest {
	private static final int RANDOM_CASES = 3000;

	/** 一侧的执行结果 */
	private static final class Run {
		List<List<Object>> rows;
		Throwable error;
		Map<Object, Integer> positions;
	}

	/** 一次调用的配置(两侧各按它建一份模型) */
	private static final class SortSpec {
		int sumMode;
		boolean levelOrder;
		String orderWay;
		boolean filter;
		String compareType;
		String compareValues;
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	private static LabelIndexModel labelsOf(TreeData data) {
		LabelIndexModel labels = new LabelIndexModel();
		for (Map.Entry<String, Integer> column : data.columns.entrySet()) {
			labels.put(column.getKey(), column.getValue());
		}
		return labels;
	}

	private static String[] sumColumnNames(TreeData data, int sumMode) {
		if (sumMode == 0 || data.sumCols.length == 0) {
			return new String[0];
		}
		int count = (sumMode == 1) ? 1 : data.sumCols.length;
		String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			names[i] = TreeFixtures.COL_AMT_PREFIX + i;
		}
		return names;
	}

	private static TreeSortModel modelOf(TreeData data, SortSpec spec) {
		TreeSortModel model = new TreeSortModel().setIdColumn(TreeFixtures.COL_ID)
				.setPidColumn(TreeFixtures.COL_PID);
		String[] sumColumns = sumColumnNames(data, spec.sumMode);
		if (sumColumns.length > 0) {
			model.setSumColumns(sumColumns);
		}
		if (spec.levelOrder && data.orderCol >= 0) {
			model.setLevelOrderColumn(TreeFixtures.COL_ORDER);
			// order-way 为 null 时原实现会抛空指针，属于需要保持的既有行为
			model.setOrderWay(spec.orderWay);
		}
		if (spec.filter && data.filterCol >= 0 && sumColumns.length > 0) {
			model.setFilterColumn(TreeFixtures.COL_FILTER);
			model.setCompareType(spec.compareType);
			model.setCompareValues(spec.compareValues);
		}
		return model;
	}

	private static SortSpec randomSpec(TreeData data, Random random) {
		SortSpec spec = new SortSpec();
		spec.sumMode = random.nextInt(3);
		spec.levelOrder = random.nextBoolean();
		int way = random.nextInt(4);
		spec.orderWay = (way == 0) ? null : (way == 1) ? "asc" : (way == 2) ? "desc" : "DESC";
		spec.filter = random.nextBoolean();
		String[] types = { "eq", "neq", "gt", "gte", "lt", "lte", "between", "in", "out", "unknown" };
		spec.compareType = types[random.nextInt(types.length)];
		String[] values = { "0", "1", "2", "3", "1,2", "0,2", "4" };
		spec.compareValues = values[random.nextInt(values.length)];
		return spec;
	}

	private static Integer[] sumIndexesOf(TreeData data, int sumMode) {
		int count = (sumMode == 0) ? 0 : (sumMode == 1) ? Math.min(1, data.sumCols.length) : data.sumCols.length;
		Integer[] indexes = new Integer[count];
		for (int i = 0; i < count; i++) {
			indexes[i] = data.sumCols[i];
		}
		return indexes;
	}

	private static Run runSort(boolean legacy, TreeData data, SortSpec spec) {
		Run run = new Run();
		run.rows = DiffSupport.deepCopy(data.rows);
		run.positions = DiffSupport.identityPositions(run.rows);
		try {
			if (legacy) {
				TreeDataSortLegacyOracle.process(modelOf(data, spec), labelsOf(data), run.rows);
			} else {
				TreeDataSort.process(modelOf(data, spec), labelsOf(data), run.rows);
			}
		} catch (Throwable e) {
			run.error = e;
		}
		return run;
	}

	private static Run runSummary(boolean legacy, TreeData data, Integer[] sumIndexes, SortSpec spec) {
		Run run = new Run();
		run.rows = DiffSupport.deepCopy(data.rows);
		run.positions = DiffSupport.identityPositions(run.rows);
		try {
			if (legacy) {
				TreeDataSortLegacyOracle.summaryTreeList(modelOf(data, spec), labelsOf(data), run.rows, sumIndexes,
						data.idCol, data.pidCol);
			} else {
				TreeDataSort.summaryTreeList(modelOf(data, spec), labelsOf(data), run.rows, sumIndexes, data.idCol,
						data.pidCol);
			}
		} catch (Throwable e) {
			run.error = e;
		}
		return run;
	}

	private static void compare(Run oracle, Run current, String context, boolean hasNullId) {
		// 已登记的缺陷修复：无根节点/节点 id 为 null 时原先抛无说明的数组越界或空指针，现改为带说明的 IllegalArgumentException
		if (DiffSupport.isExplainedDivergence(oracle.error, current.error)) {
			return;
		}
		// 已登记的缺陷修复：逐层汇总原先用 id.equals(pid) 找父节点，数据中存在 id 为 null 的行时会抛空指针
		// (是否抛出还取决于扫描顺序)，现在 null 与任何非 null 值都不匹配，不再抛异常
		if (hasNullId && (oracle.error instanceof NullPointerException) && current.error == null) {
			return;
		}
		DiffSupport.assertSameError(oracle.error, current.error, context);
		if (oracle.error != null) {
			return;
		}
		List<Integer> oracleOrder = DiffSupport.identityOrder(oracle.rows, oracle.positions);
		List<Integer> currentOrder = DiffSupport.identityOrder(current.rows, current.positions);
		if (!oracleOrder.equals(currentOrder)) {
			throw new AssertionFailedError(context + ": row order differs, oracle=" + oracleOrder + ", current="
					+ currentOrder);
		}
		// 行对象未变(顺序已按身份比对一致)，这里逐格比对内容
		String diff = DiffSupport.firstDiff(oracle.rows, current.rows);
		if (diff != null) {
			throw new AssertionFailedError(context + ": " + diff);
		}
	}

	// ---------------- 随机对拍 ----------------

	@Test
	public void randomTreeSortMatchesOracle() {
		int reordered = 0;
		int summed = 0;
		int errors = 0;
		for (int seed = 0; seed < RANDOM_CASES; seed++) {
			TreeData data = TreeFixtures.generate(seed, false, true, false);
			SortSpec spec = randomSpec(data, new Random(seed * 17L + 3));
			String context = "sort seed=" + seed;
			Run oracle = runSort(true, data, spec);
			Run current = runSort(false, data, spec);
			compare(oracle, current, context, data.hasNullId);
			if (oracle.error != null) {
				errors++;
			}
			if (!isIdentityOrder(oracle)) {
				reordered++;
			}
			if (spec.sumMode > 0 && data.sumCols.length > 0 && oracle.error == null) {
				summed++;
			}
		}
		assertTrue(reordered > 1200, "发生重排的用例数=" + reordered);
		assertTrue(summed > 500, "带逐层汇总的用例数=" + summed);
		assertTrue(errors > 20, "抛异常的用例数=" + errors);
	}

	/** 结果是否仍是"全部行都在、且位置与传入时完全相同"(即既没重排也没有丢行) */
	private static boolean isIdentityOrder(Run run) {
		if (run.error != null) {
			return false;
		}
		for (int i = 0; i < run.rows.size(); i++) {
			Integer position = run.positions.get(run.rows.get(i));
			if (position == null || position != i) {
				return false;
			}
		}
		return true;
	}

	@Test
	public void randomTreeSummaryMatchesOracle() {
		int summed = 0;
		int errors = 0;
		for (int seed = 0; seed < RANDOM_CASES; seed++) {
			TreeData data = TreeFixtures.generate(seed, true, true, false);
			Random random = new Random(seed * 23L + 11);
			int sumMode = random.nextInt(3);
			SortSpec spec = new SortSpec();
			spec.sumMode = sumMode;
			spec.filter = random.nextBoolean();
			String[] types = { "eq", "neq", "gt", "between", "in" };
			spec.compareType = types[random.nextInt(types.length)];
			spec.compareValues = random.nextBoolean() ? "1" : "1,2";
			String context = "summary seed=" + seed;
			Integer[] sumIndexes = sumIndexesOf(data, sumMode);
			// 不把 id 列当汇总列：汇总会改写 id 的取值类型(变成 BigDecimal)，与 pid 的匹配结果会随宽松比较而变化，
			// 该无意义配置另有两个专门用例覆盖
			Run oracle = runSummary(true, data, sumIndexes, spec);
			Run current = runSummary(false, data, sumIndexes, spec);
			compare(oracle, current, context, data.hasNullId);
			if (oracle.error != null) {
				errors++;
			} else if (sumIndexes.length > 0) {
				summed++;
			}
		}
		assertTrue(summed > 1000, "成功汇总的用例数=" + summed);
		assertTrue(errors > 20, "抛异常的用例数=" + errors);
	}

	/** 一个根节点带 40 个子节点(子节点离父节点最远有 40 行)，amt 列为 Long；id、pid 由 keyOf 生成 */
	private static List<List<Object>> wideTree(Object rootId, Object rootPid, LongFunction<Object> keyOf) {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(rootId, rootPid, null));
		long total = 0;
		for (long i = 2; i <= 41; i++) {
			rows.add(row(keyOf.apply(i), keyOf.apply(1L), Long.valueOf(i)));
			total += i;
		}
		// 2..41 之和
		assertEquals(new BigDecimal("860"), BigDecimal.valueOf(total), "测试数据自检");
		return rows;
	}

	private static LabelIndexModel idPidAmountLabels() {
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		labels.put("pid", 1);
		labels.put("amt", 2);
		return labels;
	}

	/** 父节点离得很远(超出探测范围)时通过索引找到父节点 */
	@Test
	public void summaryFindsFarParentThroughIndex() {
		List<List<Object>> rows = wideTree(1L, 0L, Long::valueOf);
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), idPidAmountLabels(),
				rows, new Integer[] { 2 }, 0, 1);
		assertEquals(new BigDecimal("860"), rows.get(0).get(2), "父节点应等于全部子节点之和");
	}

	/** id 不是常规取值类型时不建索引，仍按逐行扫描找到父节点，结果相同 */
	@Test
	public void summaryFallsBackToScanForCustomIdType() {
		List<List<Object>> rows = wideTree(new TreeFixtures.TreeKey(1), new TreeFixtures.TreeKey(0),
				TreeFixtures.TreeKey::new);
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), idPidAmountLabels(),
				rows, new Integer[] { 2 }, 0, 1);
		assertEquals(new BigDecimal("860"), rows.get(0).get(2), "父节点应等于全部子节点之和");
	}

	/** 汇总列正好是 id 列：id 在汇总过程中被改写，原先每次都读到最新值，这里必须保持同样的结果 */
	@Test
	public void sumColumnOverlappingIdColumnKeepsOriginalBehaviour() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0L, 10L));
		rows.add(row(2L, 1L, 5L));
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), idPidAmountLabels(),
				rows, new Integer[] { 0 }, 0, 1);
		assertEquals(row(new BigDecimal("3"), 0L, 10L), rows.get(0), "父节点的 id 列被改写为 1+2");
		assertEquals(row(2L, 1L, 5L), rows.get(1));
	}

	/**
	 * id 重复出现且相距很远时，父节点是"当前行之前最近的那一行"。父节点在 16 行的探测范围之外，会走索引查找
	 */
	@Test
	public void summaryUsesNearestPrecedingRowWhenIdsRepeat() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0L, 100L));
		// 20 行无关数据(pid 指向不存在的 id)
		for (long i = 1; i <= 20; i++) {
			rows.add(row(1000L + i, 2000L, Long.valueOf(i)));
		}
		// 下标 21：与第一行 id 相同
		rows.add(row(1L, 0L, 5L));
		for (long i = 21; i <= 40; i++) {
			rows.add(row(1000L + i, 2000L, Long.valueOf(i)));
		}
		// 下标 42：pid 指向 id=1
		rows.add(row(7L, 1L, 1L));
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), idPidAmountLabels(),
				rows, new Integer[] { 2 }, 0, 1);
		// 第一行没有被任何子节点汇总，保留原始取值(类型也不变)
		assertEquals(100L, rows.get(0).get(2), "远处那个同 id 的行不受影响");
		assertEquals(new BigDecimal("6"), rows.get(21).get(2), "子节点汇总到当前行之前最近的那个 id=1 的行");
	}

	/**
	 * 汇总列是 id 列、且子节点离父节点很远：只有最先处理的那一个子节点(最后一行)能找到父节点，
	 * 因为汇总后父节点的 id 已由 1 变成 42，其余子节点的 pid 就再也对不上了。原先每次查找读到的都是最新值，这里必须一致
	 */
	@Test
	public void sumColumnOverlappingIdColumnWithFarParentKeepsOriginalBehaviour() {
		List<List<Object>> rows = wideTree(1L, 0L, Long::valueOf);
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), idPidAmountLabels(),
				rows, new Integer[] { 0 }, 0, 1);
		assertEquals(new BigDecimal("42"), rows.get(0).get(0), "只有最后一个子节点(id=41)汇总进了父节点");
		for (int i = 1; i < rows.size(); i++) {
			assertEquals(Long.valueOf(i + 1), rows.get(i).get(0), "其余子节点的 id 列保持不变");
		}
	}

	// ---------------- 手写用例(期望为人工推算) ----------------

	/** 文档"树形排序与逐层汇总"一节的示例：上层节点汇总所有子孙节点的数据，同层级按销售量降序 */
	@Test
	public void documentationExampleTreeSortWithSumAndLevelOrder() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("松江", "上海", 120L));
		rows.add(row("杨浦", "上海", 116L));
		rows.add(row("上海", "中国", null));
		rows.add(row("南京", "江苏", 110L));
		rows.add(row("浦东", "上海", 64L));
		rows.add(row("江苏", "中国", null));
		rows.add(row("苏州", "江苏", 90L));
		rows.add(row("无锡", "江苏", 70L));
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("area_code", 0);
		labels.put("pid_area", 1);
		labels.put("sale_cnt", 2);
		TreeSortModel model = new TreeSortModel().setIdColumn("area_code").setPidColumn("pid_area")
				.setSumColumns("sale_cnt").setLevelOrderColumn("sale_cnt").setOrderWay("desc");

		TreeDataSort.process(model, labels, rows);
		List<List<Object>> expected = new ArrayList<>();
		// 只有作为父节点的行会被改写成累计值(BigDecimal)，叶子节点保留原始取值
		expected.add(row("上海", "中国", new BigDecimal("300")));
		expected.add(row("松江", "上海", 120L));
		expected.add(row("杨浦", "上海", 116L));
		expected.add(row("浦东", "上海", 64L));
		expected.add(row("江苏", "中国", new BigDecimal("270")));
		expected.add(row("南京", "江苏", 110L));
		expected.add(row("苏州", "江苏", 90L));
		expected.add(row("无锡", "江苏", 70L));
		assertEquals(expected, rows, "应与文档给出的结果表一致");
	}

	@Test
	public void summaryRollsLeafValuesUpLevelByLevel() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0L, 10L));
		rows.add(row(2L, 1L, 5L));
		rows.add(row(3L, 2L, 1L));
		rows.add(row(4L, 1L, null));
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		labels.put("pid", 1);
		labels.put("amt", 2);
		Integer[] sumIndexes = { 2 };
		TreeSortModel model = new TreeSortModel().setIdColumn("id").setPidColumn("pid");

		TreeDataSort.summaryTreeList(model, labels, rows, sumIndexes, 0, 1);
		// 父节点被改写为累计值(BigDecimal)，叶子节点保留原始取值
		assertEquals(Arrays.asList(new BigDecimal("16"), new BigDecimal("6"), 1L, null),
				Arrays.asList(rows.get(0).get(2), rows.get(1).get(2), rows.get(2).get(2), rows.get(3).get(2)),
				"子节点的值逐层累加到父节点，自身为 null 的子节点不参与");
	}

	/** 成环数据没有根节点：原先抛数组越界(提示语 Index 0 out of bounds...)，现在给出带说明的异常 */
	@Test
	public void cycleWithoutRootThrowsExplainedError() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 2L));
		rows.add(row(2L, 1L));
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		labels.put("pid", 1);
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TreeDataSort
				.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels, rows));
		assertTrue(error.getMessage().contains("no root node"), error.getMessage());
	}

	/** 节点 id 为 null：原先抛空指针，现在给出带说明的异常(生产路径同样如此) */
	@Test
	public void nullNodeIdThrowsExplainedError() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0L));
		rows.add(row(null, 1L));
		// 一对成环节点：让"还有待挂接节点"在 null 行被处理时仍然成立
		rows.add(row(5L, 6L));
		rows.add(row(6L, 5L));
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		labels.put("pid", 1);
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TreeDataSort
				.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels, rows));
		assertTrue(error.getMessage().contains("node id can not be null"), error.getMessage());
	}

	/**
	 * 环上的行挂不到任何根上：既有行为是把它们从结果中移除(仅新增告警)，此处固化下来
	 */
	@Test
	public void unlinkableCycleRowsAreRemoved() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0L));
		rows.add(row(2L, 1L));
		rows.add(row(5L, 6L));
		rows.add(row(6L, 5L));
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		labels.put("pid", 1);
		TreeDataSort.process(new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels, rows);
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row(1L, 0L));
		expected.add(row(2L, 1L));
		assertEquals(expected, rows, "成环的两行被移除，正常树保持不变");
	}

	@Test
	public void missingIdOrPidColumnThrows() {
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1L, 0L));
		RuntimeException error = assertThrows(RuntimeException.class, () -> TreeDataSort.process(
				new TreeSortModel().setIdColumn("id").setPidColumn("pid"), labels, rows));
		assertTrue(error.getMessage().contains("does not correctly specify id-column and pid-column"),
				error.getMessage());
	}

	// ---------------- 负例对照：比对逻辑必须能发现故意的差异 ----------------

	@Test
	public void comparisonDetectsDeliberateDifferences() {
		// 找一份能正常排序、带汇总、结果里出现过 BigDecimal、行数不少于4的数据
		TreeData data = null;
		SortSpec spec = new SortSpec();
		spec.sumMode = 2;
		spec.levelOrder = true;
		spec.orderWay = "desc";
		for (int seed = 0; seed < RANDOM_CASES && data == null; seed++) {
			TreeData candidate = TreeFixtures.generate(seed, false, true, false);
			Run run = runSort(true, candidate, spec);
			if (run.error == null && run.rows.size() >= 4 && hasBigDecimal(run.rows)) {
				data = candidate;
			}
		}
		assertTrue(data != null, "需要一份能正常排序且带 BigDecimal 汇总值的对照数据");
		final TreeData control = data;

		compare(runSort(true, control, spec), runSort(true, control, spec), "基准自身两次执行不应报差异", control.hasNullId);
		assertDetected(() -> {
			Run reversed = runSort(true, control, spec);
			Collections.reverse(reversed.rows);
			compare(runSort(true, control, spec), reversed, "行顺序被打乱", control.hasNullId);
		});
		assertDetected(() -> {
			Run modified = runSort(true, control, spec);
			modified.rows.get(0).set(modified.rows.get(0).size() - 1, "changed");
			compare(runSort(true, control, spec), modified, "单元格被改", control.hasNullId);
		});
		assertDetected(() -> {
			Run rescaled = runSort(true, control, spec);
			// 数值相同但 BigDecimal 标度不同，也必须算差异
			rescaleFirstBigDecimal(rescaled.rows);
			compare(runSort(true, control, spec), rescaled, "BigDecimal 标度被改", control.hasNullId);
		});
		assertDetected(() -> {
			Run failing = runSort(true, control, spec);
			failing.error = new IllegalStateException("boom");
			compare(runSort(true, control, spec), failing, "一侧抛了异常", control.hasNullId);
		});
	}

	/** 结果里是否出现过 BigDecimal */
	private static boolean hasBigDecimal(List<List<Object>> rows) {
		for (List<Object> item : rows) {
			for (Object cell : item) {
				if (cell instanceof BigDecimal) {
					return true;
				}
			}
		}
		return false;
	}

	/** 给结果里第一个 BigDecimal 加大标度(数值不变) */
	private static void rescaleFirstBigDecimal(List<List<Object>> rows) {
		outer: for (List<Object> item : rows) {
			for (int i = 0; i < item.size(); i++) {
				if (item.get(i) instanceof BigDecimal) {
					BigDecimal value = (BigDecimal) item.get(i);
					item.set(i, value.setScale(value.scale() + 4));
					break outer;
				}
			}
		}
	}

	private static void assertDetected(Executable comparison) {
		assertThrows(AssertionFailedError.class, comparison, "比对逻辑未能发现故意的差异");
	}
}
