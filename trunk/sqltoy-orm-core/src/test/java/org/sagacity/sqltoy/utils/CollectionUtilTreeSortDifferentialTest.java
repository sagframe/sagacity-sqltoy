package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opentest4j.AssertionFailedError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sagacity.sqltoy.callback.TreeIdAndPidGet;
import org.sagacity.sqltoy.oracle.CollectionUtilLegacyOracle;
import org.sagacity.sqltoy.oracle.DiffSupport;
import org.sagacity.sqltoy.oracle.TreeFixtures;
import org.sagacity.sqltoy.oracle.TreeFixtures.TreeData;

/**
 * @project sagacity-sqltoy
 * @description CollectionUtil.sortTreeList 与优化前实现(行为基准)的差分对拍：结果顺序、入参被改成的样子、异常都必须完全一致。
 *              随机用例按种子生成，失败信息带种子可复现；手写用例另附人工推算的期望，作为行为的文字说明。
 * @author zhongxuchen
 */
public class CollectionUtilTreeSortDifferentialTest {
	private static final int RANDOM_CASES = 3000;

	/** 一侧的执行结果 */
	private static final class Run {
		/** 传入的 List 实例(方法会原地移除已排序的节点) */
		List<List<Object>> input;
		List<List<Object>> result;
		Throwable error;
		/** 每个行对象在传入时的位置(按对象身份) */
		Map<Object, Integer> positions;
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	private static TreeIdAndPidGet<List<Object>> firstTwoColumnsAsIdAndPid() {
		return (row) -> new Object[] { row.get(0), row.get(1) };
	}

	private static TreeIdAndPidGet<List<Object>> getterOf(TreeData data) {
		return (row) -> new Object[] { row.get(data.idCol), row.get(data.pidCol) };
	}

	private static Run run(boolean legacy, List<List<Object>> rows, TreeIdAndPidGet<List<Object>> getter,
			Object[] pids) {
		Run run = new Run();
		// 两侧各拿一份独立的行对象：方法会原地修改入参，共享对象会让比对失去意义
		run.input = DiffSupport.deepCopy(rows);
		run.positions = DiffSupport.identityPositions(run.input);
		try {
			run.result = legacy ? CollectionUtilLegacyOracle.sortTreeList(run.input, getter, pids)
					: CollectionUtil.sortTreeList(run.input, getter, pids);
		} catch (Throwable e) {
			run.error = e;
		}
		return run;
	}

	/**
	 * 比对两侧：异常、结果顺序(按对象身份)、入参中剩余的行、返回的是否为入参实例、行内容是否原封未动
	 * 
	 * @param source 数据的原始行(未拷贝、未被修改)，用于核对行内容
	 */
	private static void compare(Run oracle, Run current, List<List<Object>> source, String context) {
		// 已登记的缺陷修复：id 为 null 时原先抛无说明的空指针，现改为带说明的 IllegalArgumentException
		if (DiffSupport.isExplainedDivergence(oracle.error, current.error)) {
			return;
		}
		DiffSupport.assertSameError(oracle.error, current.error, context);
		if (oracle.error != null) {
			return;
		}
		List<Integer> oracleOrder = DiffSupport.identityOrder(oracle.result, oracle.positions);
		List<Integer> currentOrder = DiffSupport.identityOrder(current.result, current.positions);
		if (!oracleOrder.equals(currentOrder)) {
			throw new AssertionFailedError(context + ": result order differs, oracle=" + oracleOrder + ", current="
					+ currentOrder);
		}
		List<Integer> oracleLeft = DiffSupport.identityOrder(oracle.input, oracle.positions);
		List<Integer> currentLeft = DiffSupport.identityOrder(current.input, current.positions);
		if (!oracleLeft.equals(currentLeft)) {
			throw new AssertionFailedError(context + ": rows left in treeList differ, oracle=" + oracleLeft
					+ ", current=" + currentLeft);
		}
		assertEquals(oracle.result == oracle.input, current.result == current.input,
				context + ": whether the input instance itself is returned differs");
		// 当前的行对象必须全部来自输入、内容原封未动，且每一行恰好出现在结果或剩余集合中一次
		int seen = 0;
		for (Object item : current.result) {
			Integer position = current.positions.get(item);
			assertTrue(position != null, context + ": result contains a row that is not from the input");
			assertEquals(source.get(position), item, context + ": a result row was modified");
			seen++;
		}
		if (current.result != current.input) {
			for (Object item : current.input) {
				Integer position = current.positions.get(item);
				assertTrue(position != null, context + ": treeList contains a row that is not from the input");
				assertEquals(source.get(position), item, context + ": a row left in treeList was modified");
				seen++;
			}
		}
		assertEquals(source.size(), seen, context + ": rows were lost or duplicated");
	}

	private static void assertSameAsOracle(List<List<Object>> rows, Object[] pids, String context) {
		compare(run(true, rows, firstTwoColumnsAsIdAndPid(), pids),
				run(false, rows, firstTwoColumnsAsIdAndPid(), pids), rows, context);
	}

	/** 取每行第一列，便于书写人工推算的期望 */
	private static List<Object> firstColumn(List<List<Object>> rows) {
		List<Object> values = new ArrayList<>(rows.size());
		for (List<Object> row : rows) {
			values.add(row.get(0));
		}
		return values;
	}

	// ---------------- 随机对拍 ----------------

	@Test
	public void randomTreesMatchOracle() {
		int sortedCases = 0;
		int errorCases = 0;
		int earlyReturnCases = 0;
		for (int seed = 0; seed < RANDOM_CASES; seed++) {
			TreeData data = TreeFixtures.generate(seed, false, true, false);
			Random random = new Random(seed * 31L + 7);
			// 根pid的几种写法：精确匹配、带多余值、为空。类型不一致的根pid在修复后会匹配成功(行为变化)，
			// 由 TreeIdPidTypeToleranceTest 单独覆盖
			for (int variant : new int[] { 0, 1, 3 }) {
				Object[] pids = rootPids(data, variant, random);
				String context = "seed=" + seed + ", variant=" + variant;
				Run oracle = run(true, data.rows, getterOf(data), pids);
				Run current = run(false, data.rows, getterOf(data), pids);
				compare(oracle, current, data.rows, context);
				if (oracle.result == oracle.input) {
					earlyReturnCases++;
				} else if (oracle.error != null) {
					errorCases++;
				} else {
					sortedCases++;
				}
			}
		}
		// 三类路径都要真正跑到，否则"全等"没有说服力
		assertTrue(sortedCases > 3000, "成功排序的用例数=" + sortedCases);
		assertTrue(errorCases > 100, "抛异常的用例数=" + errorCases);
		assertTrue(earlyReturnCases > 1000, "直接返回的用例数=" + earlyReturnCases);
	}

	/** 根 pid 实参的几种写法：精确匹配、带多余值、为空 */
	private static Object[] rootPids(TreeData data, int variant, Random random) {
		Object root = data.rootPidValue;
		switch (variant) {
		case 0:
			return new Object[] { root };
		case 1:
			return new Object[] { root, unrelatedValue(root) };
		default:
			return random.nextBoolean() ? new Object[0] : new Object[] { null, root };
		}
	}

	private static Object unrelatedValue(Object sameTypeAs) {
		if (sameTypeAs instanceof Long) {
			return Long.valueOf(424242L);
		}
		if (sameTypeAs instanceof Integer) {
			return Integer.valueOf(424242);
		}
		if (sameTypeAs instanceof BigDecimal) {
			return BigDecimal.valueOf(424242L);
		}
		return "unrelated";
	}

	// ---------------- 手写边界用例(期望为人工推算) ----------------

	@Test
	public void childrenFollowParentAndSiblingsKeepInputOrder() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(5, 2, "e"));
		rows.add(row(1, 0, "b"));
		rows.add(row(6, 4, "f"));
		rows.add(row(3, 1, "d"));
		rows.add(row(2, 1, "c"));
		rows.add(row(4, 2, "g"));

		Run current = run(false, rows, firstTwoColumnsAsIdAndPid(), new Object[] { 0 });
		assertNull(current.error);
		// 输入中 3 排在 2 前面、5 排在 4 前面：兄弟节点保持的是输入顺序而非 id 顺序
		assertEquals(Arrays.asList(1, 3, 2, 5, 4, 6), firstColumn(current.result));
		assertTrue(current.input.isEmpty(), "全部节点都能挂到树上，入参中不应有剩余");
		assertSameAsOracle(rows, new Object[] { 0 }, "childrenFollowParent");
	}

	@Test
	public void multipleRootsOrphansAndCycles() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(10, 0));
		rows.add(row(11, 10));
		rows.add(row(20, 99));
		rows.add(row(30, 40));
		rows.add(row(40, 30));
		rows.add(row(12, 10));
		rows.add(row(13, 0));
		rows.add(row(21, 11));

		Run current = run(false, rows, firstTwoColumnsAsIdAndPid(), new Object[] { 0 });
		assertNull(current.error);
		assertEquals(Arrays.asList(10, 11, 21, 12, 13), firstColumn(current.result), "根节点按输入顺序，子节点紧随父节点");
		assertEquals(Arrays.asList(20, 30, 40), firstColumn(current.input), "孤儿节点与成环节点挂不到树上，留在入参中");
		assertSameAsOracle(rows, new Object[] { 0 }, "multipleRoots");
	}

	@Test
	public void duplicateIdAttachesChildrenToTheFirstOne() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1, 0));
		rows.add(row(2, 1));
		rows.add(row(1, 0));
		rows.add(row(3, 1));

		Run current = run(false, rows, firstTwoColumnsAsIdAndPid(), new Object[] { 0 });
		assertNull(current.error);
		assertEquals(Arrays.asList(1, 2, 3, 1), firstColumn(current.result), "子节点只挂到最先处理的那个重复 id 上");
		assertSameAsOracle(rows, new Object[] { 0 }, "duplicateId");
	}

	@Test
	public void nullPidMatchesNullRoot() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(2, null));
		rows.add(row(1, 1));
		rows.add(row(3, null));
		rows.add(row(4, 3));

		Run current = run(false, rows, firstTwoColumnsAsIdAndPid(), new Object[] { null });
		assertNull(current.error);
		assertEquals(Arrays.asList(2, 3, 4), firstColumn(current.result));
		assertEquals(Arrays.asList(1), firstColumn(current.input));
		assertSameAsOracle(rows, new Object[] { null }, "nullPid");
	}

	@Test
	public void throwsWhenNoRowMatchesRootPid() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1, 5));
		rows.add(row(2, 1));
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> CollectionUtil.sortTreeList(rows, firstTwoColumnsAsIdAndPid(), new Object[] { 0 }));
		assertTrue(error.getMessage().contains("does not contain the parent ids"), error.getMessage());
		assertSameAsOracle(rows, new Object[] { 0 }, "noRoot");
	}

	@Test
	public void nullIdWithPendingNodesThrowsExplainedError() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1, 0));
		// id 为 null 的行，以及一个挂不到任何节点上的孤儿行：后者让"还有待挂接节点"成立
		rows.add(row(null, 1));
		rows.add(row(3, 2));
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> CollectionUtil.sortTreeList(rows, firstTwoColumnsAsIdAndPid(), new Object[] { 0 }));
		assertTrue(error.getMessage().contains("node id can not be null"), error.getMessage());
	}

	@Test
	public void returnsInputAsIsWhenNothingToSort() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row(1, 0));
		assertNull(CollectionUtil.sortTreeList(null, firstTwoColumnsAsIdAndPid(), new Object[] { 0 }));
		assertSame(rows, CollectionUtil.sortTreeList(rows, firstTwoColumnsAsIdAndPid(), new Object[0]));
		assertSame(rows, CollectionUtil.sortTreeList(rows, firstTwoColumnsAsIdAndPid(), (Object[]) null));
		List<List<Object>> empty = new ArrayList<>();
		assertSame(empty, CollectionUtil.sortTreeList(empty, firstTwoColumnsAsIdAndPid(), new Object[] { 0 }));
		assertSameAsOracle(rows, new Object[0], "emptyPids");
		assertSameAsOracle(new ArrayList<>(), new Object[] { 0 }, "emptyRows");
	}

	// ---------------- 负例对照：比对逻辑必须能发现故意的差异 ----------------

	/** 每个对照都用一次全新的基准执行，避免对照之间通过共享的行对象互相污染 */
	private static Run freshControlRun(TreeData data, Object[] pids) {
		Run run = run(true, data.rows, getterOf(data), pids);
		assertNull(run.error, "对照数据本身应能正常排序");
		assertTrue(run.result.size() >= 4, "对照数据的结果应不少于4行");
		return run;
	}

	@Test
	public void comparisonDetectsDeliberateDifferences() {
		// 找一份能正常排序、结果不少于4行的数据
		TreeData data = null;
		Object[] pids = null;
		for (int seed = 0; seed < RANDOM_CASES && data == null; seed++) {
			TreeData candidate = TreeFixtures.generate(seed, false, true, false);
			Object[] roots = { candidate.rootPidValue };
			Run run = run(true, candidate.rows, getterOf(candidate), roots);
			if (run.error == null && run.result.size() >= 4) {
				data = candidate;
				pids = roots;
			}
		}
		assertTrue(data != null, "需要一份能正常排序的对照数据");
		final TreeData control = data;
		final Object[] controlPids = pids;


		compare(freshControlRun(control, controlPids), freshControlRun(control, controlPids), control.rows, "基准自身两次执行不应报差异");

		Run reversed = freshControlRun(control, controlPids);
		Collections.reverse(reversed.result);
		assertDetected(() -> compare(freshControlRun(control, controlPids), reversed, control.rows, "顺序被打乱"));

		Run modified = freshControlRun(control, controlPids);
		modified.result.get(0).set(0, "changed");
		assertDetected(() -> compare(freshControlRun(control, controlPids), modified, control.rows, "行内容被改"));

		Run foreign = freshControlRun(control, controlPids);
		foreign.result.set(0, new ArrayList<>(foreign.result.get(0)));
		assertDetected(() -> compare(freshControlRun(control, controlPids), foreign, control.rows, "结果里混入了输入之外的行"));

		Run dropped = freshControlRun(control, controlPids);
		// 往剩余行里混入一个输入之外的对象(对照数据可能本来就没有剩余行，所以不能靠"清空"来构造差异)
		dropped.input.add(new ArrayList<>(dropped.result.get(0)));
		assertDetected(() -> compare(freshControlRun(control, controlPids), dropped, control.rows, "剩余行不一致"));

		Run failing = freshControlRun(control, controlPids);
		failing.error = new IllegalStateException("boom");
		assertDetected(() -> compare(freshControlRun(control, controlPids), failing, control.rows, "一侧抛了异常"));

		Run swapped = freshControlRun(control, controlPids);
		Collections.swap(swapped.result, 0, 1);
		assertDetected(() -> compare(freshControlRun(control, controlPids), swapped, control.rows, "相邻两行互换"));
	}

	private static void assertDetected(Executable comparison) {
		assertThrows(AssertionFailedError.class, comparison, "比对逻辑未能发现故意的差异");
	}
}
