package org.sagacity.sqltoy.recheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.callback.TreeIdAndPidGet;
import org.sagacity.sqltoy.config.MacroIfLogic;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.plugins.calculator.TreeDataSort;
import org.sagacity.sqltoy.plugins.utils.CalculateUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 2026-11-24 树形算法优化(排序挂接、逐层汇总找父节点)的独立复核：参考实现按 git 历史中优化之前(提交 766bdb937)
 *              的源码重新转写，不复用已有对拍测试的基准类；随机数据规模远大于探测窗口(16行)，确保索引路径被充分覆盖。
 *              另有一条不依赖参考实现的不变量校验：每个父节点的汇总值 = 自身初值 + 各直接子节点的最终值。
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TreeAlgorithmRecheckTest {
	private static final int PROCESS_CASES = 4000;
	private static final int SORT_LIST_CASES = 4000;

	// ======================= 参考实现：优化之前的算法 =======================
	// lenient=false 为原始的 equals 语义；lenient=true 仅把 equals 换成 BeanUtil.equalsIgnoreType，
	// 即"新语义套在原算法上"，用来复核类型混用(宽松比较)时新实现与原算法结构是否一致

	private static boolean linkMatch(boolean lenient, Object id, Object pid) {
		// 原实现在这里调用 idValue.equals(pidValue)：节点 id 为 null 且还有待挂接的行时空指针(新旧实现在这类数据上都失败，
		// 新实现改为带说明的异常)，宽松语义的参考实现同样保留这一行为
		if (id == null) {
			throw new NullPointerException("node id is null");
		}
		return lenient ? BeanUtil.equalsIgnoreType(id, pid, false) : id.equals(pid);
	}

	/**
	 * 逐层汇总时查找父节点的比较。原实现同样是 id.equals(pid)(id 为 null 时空指针)；
	 * 新实现在这里对 null id 是容忍的(null 与任何非 null 值都不匹配，继续向前找)，属于已登记的差异，
	 * 由调用方按"数据含 null id"放行
	 */
	private static boolean summaryMatch(boolean lenient, Object id, Object pid) {
		return lenient ? BeanUtil.equalsIgnoreType(id, pid, false) : id.equals(pid);
	}

	private static boolean containsId(List<Object> ids, Object pid, boolean lenient) {
		for (int i = 0; i < ids.size(); i++) {
			Object id = ids.get(i);
			if (lenient ? BeanUtil.equalsIgnoreType(id, pid, false) : (id == null ? pid == null : id.equals(pid))) {
				return true;
			}
		}
		return false;
	}

	private static void refSortTree(List treeList, int idCol, int pidCol, boolean lenient) {
		List<Object> idValues = new ArrayList<Object>();
		List<Object> pidValues = new ArrayList<Object>();
		for (int i = 0, n = treeList.size(); i < n; i++) {
			List row = (List) treeList.get(i);
			idValues.add(row.get(idCol));
			pidValues.add(row.get(pidCol));
		}
		// 原实现用 HashSet 判定 pid 是否在 id 集合中
		SetLike topPids = new SetLike();
		for (int i = 0; i < pidValues.size(); i++) {
			Object pid = pidValues.get(i);
			if (!containsId(idValues, pid, lenient)) {
				topPids.add(pid);
			}
		}
		List result = new ArrayList();
		for (int i = 0; i < treeList.size(); i++) {
			List row = (List) treeList.get(i);
			if (topPids.contains(row.get(pidCol))) {
				result.add(row);
				treeList.remove(i);
				i--;
			}
		}
		int beginIndex = 0;
		int addCount = 0;
		while (treeList.size() != 0) {
			addCount = 0;
			// 没有根节点时原实现在这里数组越界
			Object idValue = ((List) result.get(beginIndex)).get(idCol);
			for (int i = 0; i < treeList.size(); i++) {
				Object pidValue = ((List) treeList.get(i)).get(pidCol);
				if (linkMatch(lenient, idValue, pidValue)) {
					result.add(beginIndex + addCount + 1, treeList.get(i));
					treeList.remove(i);
					addCount++;
					i--;
				}
			}
			beginIndex++;
			if (beginIndex + 1 > result.size()) {
				break;
			}
		}
		treeList.clear();
		treeList.addAll(result);
	}

	private static void refSum(List pidRow, List idRow, Integer[] sumIndexes) {
		for (int sumIndex : sumIndexes) {
			Object pidCellValue = pidRow.get(sumIndex);
			Object idCellValue = idRow.get(sumIndex);
			if (pidCellValue == null) {
				if (idCellValue == null) {
					pidRow.set(sumIndex, BigDecimal.ZERO);
				} else {
					pidRow.set(sumIndex, new BigDecimal(idCellValue.toString().replace(",", "")));
				}
			} else if (pidCellValue instanceof BigDecimal) {
				if (idCellValue != null) {
					pidRow.set(sumIndex,
							((BigDecimal) pidCellValue).add(new BigDecimal(idCellValue.toString().replace(",", ""))));
				}
			} else if (idCellValue != null) {
				pidRow.set(sumIndex, new BigDecimal(pidCellValue.toString().replace(",", ""))
						.add(new BigDecimal(idCellValue.toString().replace(",", ""))));
			} else {
				pidRow.set(sumIndex, new BigDecimal(pidCellValue.toString().replace(",", "")));
			}
		}
	}

	private static void refSummary(TreeSortModel model, LabelIndexModel labels, List treeList, Integer[] sumIndexes,
			int idCol, int pidCol, boolean lenient) {
		boolean hasFilter = StringUtil.isNotBlank(model.getFilterColumn())
				&& StringUtil.isNotBlank(model.getCompareValues())
				&& StringUtil.isNotBlank(model.getCompareType());
		for (int i = treeList.size() - 1; i > 0; i--) {
			List idRow = (List) treeList.get(i);
			Object pid = idRow.get(pidCol);
			boolean doSum = true;
			if (hasFilter) {
				doSum = MacroIfLogic.compare(idRow.get(labels.get(model.getFilterColumn())), model.getCompareType(),
						model.getCompareValues(), null);
			}
			if (doSum) {
				for (int j = i - 1; j >= 0; j--) {
					List pidRow = (List) treeList.get(j);
					if (summaryMatch(lenient, pidRow.get(idCol), pid)) {
						refSum(pidRow, idRow, sumIndexes);
						break;
					}
				}
			}
		}
	}

	private static void refProcess(TreeSortModel model, LabelIndexModel labels, List treeList, boolean lenient) {
		if (treeList == null || treeList.isEmpty()) {
			return;
		}
		Integer idColIndex = labels.get(model.getIdColumn());
		Integer pidColIndex = labels.get(model.getPidColumn());
		if (idColIndex == null || pidColIndex == null) {
			throw new RuntimeException("sorting tree structure data does not correctly specify id-column and pid-column");
		}
		int dataWidth = ((List) treeList.get(0)).size();
		List<Integer> sumColList = CalculateUtils.parseColumns(labels, model.getSumColumns(), dataWidth);
		refSortTree(treeList, idColIndex, pidColIndex, lenient);
		if (!sumColList.isEmpty()) {
			refSummary(model, labels, treeList, sumColList.toArray(new Integer[0]), idColIndex, pidColIndex, lenient);
		}
		if (StringUtil.isNotBlank(model.getLevelOrderColumn())) {
			Integer sortColIndex = labels.get(model.getLevelOrderColumn());
			if (sortColIndex == null) {
				throw new RuntimeException("level order column missing");
			}
			int dataType = CollectionUtil.getSortDataType(treeList, sortColIndex);
			boolean desc = model.getOrderWay().equalsIgnoreCase("desc") ? true : false;
			CollectionUtil.sortList(treeList, sortColIndex, dataType, 0, treeList.size() - 1, !desc);
			refSortTree(treeList, idColIndex, pidColIndex, lenient);
		}
	}

	private static boolean refAny(boolean lenient, Object value, Object... compareAry) {
		if (compareAry == null || compareAry.length == 0) {
			return false;
		}
		for (Object s : compareAry) {
			if (lenient) {
				if (BeanUtil.equalsIgnoreType(value, s, false)) {
					return true;
				}
			} else if (value == null) {
				if (s == null) {
					return true;
				}
			} else if (s != null && value.equals(s)) {
				return true;
			}
		}
		return false;
	}

	private static <T> List<T> refSortTreeList(List<T> treeList, TreeIdAndPidGet<T> getter, boolean lenient,
			Object... pids) {
		if (treeList == null || treeList.isEmpty() || pids == null || pids.length == 0) {
			return treeList;
		}
		List<T> result = new ArrayList<T>();
		for (int i = 0; i < treeList.size(); i++) {
			T row = treeList.get(i);
			if (refAny(lenient, getter.getIdAndPid(row)[1], pids)) {
				result.add(row);
				treeList.remove(i);
				i--;
			}
		}
		if (result.isEmpty()) {
			throw new IllegalArgumentException("the sorted tree collection does not contain the parent ids ["
					+ StringUtil.linkAry(",", false, pids) + "]!");
		}
		int beginIndex = 0;
		int addCount = 0;
		while (treeList.size() != 0) {
			addCount = 0;
			Object idValue = getter.getIdAndPid(result.get(beginIndex))[0];
			for (int i = 0; i < treeList.size(); i++) {
				Object pidValue = getter.getIdAndPid(treeList.get(i))[1];
				if (linkMatch(lenient, idValue, pidValue)) {
					result.add(beginIndex + addCount + 1, treeList.get(i));
					treeList.remove(i);
					addCount++;
					i--;
				}
			}
			beginIndex++;
			if (beginIndex + 1 > result.size()) {
				break;
			}
		}
		return result;
	}

	/** 原实现的 topPids 是 HashSet(按 equals 判定)，这里用等价的列表查找 */
	private static final class SetLike {
		private final List<Object> values = new ArrayList<Object>();

		void add(Object value) {
			values.add(value);
		}

		boolean contains(Object value) {
			for (Object item : values) {
				if (value == null ? item == null : value.equals(item)) {
					return true;
				}
			}
			return false;
		}
	}

	// ======================= 比对工具 =======================

	private static final class Outcome {
		List<List<Object>> rows;
		/** 新实现执行前各行(按对象身份)的初值快照 */
		Map<List, List<Object>> initial;
		Throwable error;
		List<Integer> order;
		List<Integer> leftover;
	}

	private static String cellKey(Object cell) {
		return cell == null ? "null" : cell.getClass().getName() + "#" + cell;
	}

	private static void assertSameRows(List<List<Object>> expected, List<List<Object>> actual, String context) {
		assertEquals(expected.size(), actual.size(), context + ": 行数不一致");
		for (int i = 0; i < expected.size(); i++) {
			List<Object> expectedRow = expected.get(i);
			List<Object> actualRow = actual.get(i);
			assertEquals(expectedRow.size(), actualRow.size(), context + ": 第" + i + "行宽度不一致");
			for (int c = 0; c < expectedRow.size(); c++) {
				assertEquals(cellKey(expectedRow.get(c)), cellKey(actualRow.get(c)),
						context + ": 第" + i + "行第" + c + "列不一致");
			}
		}
	}

	/**
	 * 新实现必须与参考实现同时成功或同时抛异常。已登记的差异只有一类：原实现抛出的无说明数组越界/空指针，
	 * 新实现改为带说明的 IllegalArgumentException
	 */
	/** 异常描述：类型、信息和框架内的第一层调用栈，便于失败时定位 */
	private static String describe(Throwable error) {
		if (error == null) {
			return "无异常";
		}
		StringBuilder builder = new StringBuilder(String.valueOf(error));
		for (StackTraceElement frame : error.getStackTrace()) {
			if (frame.getClassName().startsWith("org.sagacity")) {
				builder.append(" @ ").append(frame);
				break;
			}
		}
		return builder.toString();
	}

	private static void assertSameError(Outcome actual, Outcome expected, String context) {
		if (expected.error != null) {
			if (actual.error == null) {
				fail(context + ": 参考实现抛出 " + describe(expected.error) + "，新实现却正常返回");
			}
			boolean explained = (expected.error instanceof IndexOutOfBoundsException
					|| expected.error instanceof NullPointerException)
					&& actual.error instanceof IllegalArgumentException;
			if (!explained) {
				assertEquals(expected.error.getClass(), actual.error.getClass(), context + ": 异常类型不一致，参考实现="
						+ describe(expected.error) + "，新实现=" + describe(actual.error));
				// 空指针不比较信息：JIT 对同一位置反复抛出的隐式空指针会省略信息(OmitStackTraceInFastThrow)
				if (!(expected.error instanceof NullPointerException)) {
					assertEquals(String.valueOf(expected.error.getMessage()), String.valueOf(actual.error.getMessage()),
							context + ": 异常信息不一致，参考实现=" + describe(expected.error) + "，新实现="
									+ describe(actual.error));
				}
			}
			return;
		}
		if (actual.error != null) {
			fail(context + ": 参考实现正常返回，新实现却抛出 " + describe(actual.error));
		}
	}

	/** 原地修改 treeList 的算法：逐行逐格比对 */
	private static void assertSameOutcome(Outcome actual, Outcome expected, String context) {
		assertSameError(actual, expected, context);
		if (expected.error == null) {
			assertSameRows(expected.rows, actual.rows, context);
		}
	}

	/** sortTreeList：比对返回的行顺序(以序号表示)和入参集合中剩余的行 */
	private static void assertSameListOutcome(Outcome actual, Outcome expected, String context) {
		assertSameError(actual, expected, context);
		if (expected.error == null) {
			assertEquals(expected.order, actual.order, context + ": 返回的行顺序不一致");
			assertEquals(expected.leftover, actual.leftover, context + ": 入参集合中剩余的行不一致");
		}
	}

	private static List<List<Object>> deepCopy(List<List<Object>> source) {
		List<List<Object>> result = new ArrayList<List<Object>>(source.size());
		for (List<Object> row : source) {
			result.add(new ArrayList<Object>(row));
		}
		return result;
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<Object>(Arrays.asList(cells));
	}

	// ======================= 随机数据 =======================

	private static final class TreeCase {
		List<List<Object>> rows = new ArrayList<List<Object>>();
		Map<String, Integer> columns = new TreeMap<String, Integer>();
		int idCol;
		int pidCol;
		int sumColCount;
		int orderCol = -1;
		int filterCol = -1;
		/** id 与 pid 取值类型一致：原始 equals 语义下新旧结果也必须一致 */
		boolean sameTypes;
		boolean hasNullId;
		boolean preOrdered;
	}

	private static final class Spec {
		int sumMode;
		/** 汇总列就是 id 列：汇总会把 id 改写成 BigDecimal，数据在处理中途变成混合类型 */
		boolean sumIsId;
		/** 汇总列就是 pid 列 */
		boolean sumIsPid;
		boolean levelOrder;
		String orderWay;
		boolean filter;
		String compareType;
		String compareValues;

		/** 汇总列与 id/pid 列重叠：取值在处理过程中被改写，"同类型数据"的前提不再成立 */
		boolean sumOverlapsKey() {
			return sumIsId || sumIsPid;
		}
	}

	// id/pid 取值类型
	private static final int T_LONG = 0;
	private static final int T_INT = 1;
	private static final int T_STR = 2;
	private static final int T_DEC = 3;

	private static Object typed(int type, long value) {
		switch (type) {
		case T_INT:
			return Integer.valueOf((int) value);
		case T_STR:
			return String.valueOf(value);
		case T_DEC:
			return BigDecimal.valueOf(value);
		default:
			return Long.valueOf(value);
		}
	}

	private static Object amount(Random rnd) {
		int pick = rnd.nextInt(100);
		if (pick < 12) {
			return null;
		} else if (pick < 34) {
			return BigDecimal.valueOf(rnd.nextInt(100000), rnd.nextInt(3));
		} else if (pick < 50) {
			return Long.valueOf(rnd.nextInt(10000));
		} else if (pick < 64) {
			return Integer.valueOf(rnd.nextInt(1000));
		} else if (pick < 74) {
			return Double.valueOf(rnd.nextInt(500) / 8.0d);
		} else if (pick < 86) {
			return String.valueOf(rnd.nextInt(10000));
		}
		return (rnd.nextInt(10) == 0 ? "-" : "") + (1000 + rnd.nextInt(9000)) + "," + (100 + rnd.nextInt(900));
	}

	/**
	 * 层内排序列的取值：同一份数据内只用一种类型。混用类型时排序本身会抛 ClassCastException(既有行为)，
	 * 那样大树上的层内排序路径就跑不到重新挂接这一步
	 */
	private static Object orderValue(Random rnd, int type) {
		if (rnd.nextInt(5) == 0) {
			return null;
		}
		switch (type) {
		case 1:
			return Integer.valueOf(rnd.nextInt(6));
		case 2:
			return String.valueOf(rnd.nextInt(6));
		case 3:
			return BigDecimal.valueOf(rnd.nextInt(6));
		default:
			return Long.valueOf(rnd.nextInt(6));
		}
	}

	/**
	 * 按种子生成树形数据
	 *
	 * @param typeMode    0~3：id、pid 同为 Long/Integer/String/BigDecimal；4：id 为 Long、pid 为另一种类型；5：逐行随机混用
	 * @param allowNullId 是否允许出现 id 为 null 的行
	 */
	private static TreeCase generate(Random rnd, int minN, int maxN, int typeMode, boolean allowNullId) {
		TreeCase tc = new TreeCase();
		tc.sameTypes = typeMode <= 3;
		String[] shapes = { "chain", "star", "balanced", "random", "forest" };
		String shape = shapes[rnd.nextInt(shapes.length)];
		int n = minN + rnd.nextInt(maxN - minN + 1);
		int fanout = 2 + rnd.nextInt(7);
		int roots = shape.equals("forest") ? 1 + rnd.nextInt(4) : 1;
		long[] parent = new long[n + 1];
		for (int i = 2; i <= n; i++) {
			switch (shape) {
			case "chain":
				parent[i] = i - 1;
				break;
			case "star":
				parent[i] = 1;
				break;
			case "balanced":
				parent[i] = (i - 2) / fanout + 1;
				break;
			case "forest":
				parent[i] = (i <= roots) ? 0 : 1 + rnd.nextInt(i - 1);
				break;
			default:
				parent[i] = 1 + rnd.nextInt(i - 1);
				break;
			}
		}
		// 整体成环(没有任何根节点)
		boolean allCycle = n > 1 && rnd.nextInt(12) == 0;
		if (allCycle) {
			parent[1] = n;
		}
		int idType;
		int pidType;
		if (typeMode <= 3) {
			idType = typeMode;
			pidType = typeMode;
		} else if (typeMode == 4) {
			idType = rnd.nextInt(4);
			pidType = (idType + 1 + rnd.nextInt(3)) % 4;
		} else {
			idType = -1;
			pidType = -1;
		}
		// {id, pid, 0常规 / 1 id为null / 2 pid为null}
		List<long[]> nodes = new ArrayList<long[]>();
		int nullIdNode = 1 + rnd.nextInt(n);
		boolean withNullId = allowNullId && rnd.nextInt(4) == 0;
		for (int i = 1; i <= n; i++) {
			nodes.add(new long[] { i, parent[i], (withNullId && i == nullIdNode) ? 1 : 0 });
		}
		// 重复 id
		if (n > 3) {
			for (int k = rnd.nextInt(4); k > 0; k--) {
				nodes.add(new long[] { 1 + rnd.nextInt(n), rnd.nextInt(5) == 0 ? 0 : 1 + rnd.nextInt(n), 0 });
			}
		}
		if (!allCycle) {
			// 父 id 不存在的孤儿子树
			for (int k = rnd.nextInt(3); k > 0; k--) {
				long orphanId = 8_000_000L + k;
				nodes.add(new long[] { orphanId, 9_000_000L + k, 0 });
				if (rnd.nextBoolean()) {
					nodes.add(new long[] { orphanId + 500_000L, orphanId, 0 });
				}
			}
			// 成环
			if (rnd.nextInt(5) == 0) {
				nodes.add(new long[] { 7_000_001L, 7_000_003L, 0 });
				nodes.add(new long[] { 7_000_002L, 7_000_001L, 0 });
				nodes.add(new long[] { 7_000_003L, 7_000_002L, 0 });
			}
			// pid 为 null
			if (rnd.nextBoolean()) {
				nodes.add(new long[] { 5_000_001L, 0, 2 });
			}
		}
		// 自环
		if (rnd.nextBoolean()) {
			nodes.add(new long[] { 6_000_001L, 6_000_001L, 0 });
		}
		// 列布局
		List<String> roles = new ArrayList<String>(Arrays.asList("id", "pid"));
		tc.sumColCount = rnd.nextInt(3);
		for (int i = 0; i < tc.sumColCount; i++) {
			roles.add("amt" + i);
		}
		boolean hasOrder = rnd.nextInt(3) > 0;
		boolean hasFilter = rnd.nextInt(3) > 0;
		if (hasOrder) {
			roles.add("ord");
		}
		if (hasFilter) {
			roles.add("flt");
		}
		roles.add("ext");
		Collections.shuffle(roles, rnd);
		for (int i = 0; i < roles.size(); i++) {
			tc.columns.put(roles.get(i), i);
		}
		tc.idCol = tc.columns.get("id");
		tc.pidCol = tc.columns.get("pid");
		tc.orderCol = hasOrder ? tc.columns.get("ord") : -1;
		tc.filterCol = hasFilter ? tc.columns.get("flt") : -1;
		// 落地为行
		int orderType = rnd.nextInt(4);
		for (long[] node : nodes) {
			List<Object> treeRow = new ArrayList<Object>(Collections.nCopies(roles.size(), null));
			if (node[2] == 1) {
				tc.hasNullId = true;
			} else {
				treeRow.set(tc.idCol, typed(tc.sameTypes ? idType : rnd.nextInt(4), node[0]));
			}
			if (node[2] != 2) {
				treeRow.set(tc.pidCol, typed(tc.sameTypes ? pidType : rnd.nextInt(4), node[1]));
			}
			for (int c = 0; c < roles.size(); c++) {
				String role = roles.get(c);
				if (role.startsWith("amt")) {
					treeRow.set(c, amount(rnd));
				} else if (role.equals("ord")) {
					treeRow.set(c, orderValue(rnd, orderType));
				} else if (role.equals("flt")) {
					treeRow.set(c, Integer.valueOf(rnd.nextInt(4)));
				} else if (role.equals("ext")) {
					treeRow.set(c, "x" + rnd.nextInt(1000));
				}
			}
			tc.rows.add(treeRow);
		}
		// 行顺序：主干按前序排列(其余节点随机插入)，或整体打乱
		tc.preOrdered = rnd.nextBoolean();
		if (tc.preOrdered) {
			List<List<Integer>> kids = new ArrayList<List<Integer>>();
			for (int i = 0; i <= n; i++) {
				kids.add(new ArrayList<Integer>());
			}
			List<Integer> top = new ArrayList<Integer>();
			for (int i = 1; i <= n; i++) {
				if (parent[i] >= 1 && parent[i] <= n) {
					kids.get((int) parent[i]).add(i);
				} else {
					top.add(i);
				}
			}
			List<Integer> order = new ArrayList<Integer>();
			Deque<Integer> stack = new ArrayDeque<Integer>();
			for (int i = top.size() - 1; i >= 0; i--) {
				stack.push(top.get(i));
			}
			while (!stack.isEmpty()) {
				int current = stack.pop();
				order.add(current);
				List<Integer> children = kids.get(current);
				for (int i = children.size() - 1; i >= 0; i--) {
					stack.push(children.get(i));
				}
			}
			boolean[] placed = new boolean[nodes.size()];
			List<List<Object>> ordered = new ArrayList<List<Object>>();
			for (Integer id : order) {
				ordered.add(tc.rows.get(id - 1));
				placed[id - 1] = true;
			}
			for (int i = 0; i < nodes.size(); i++) {
				if (!placed[i]) {
					ordered.add(rnd.nextInt(ordered.size() + 1), tc.rows.get(i));
				}
			}
			tc.rows = ordered;
		} else {
			Collections.shuffle(tc.rows, rnd);
		}
		return tc;
	}

	private static Spec randomSpec(Random rnd) {
		Spec spec = new Spec();
		spec.sumMode = rnd.nextInt(3);
		int overlap = rnd.nextInt(16);
		spec.sumIsId = overlap == 0;
		spec.sumIsPid = overlap == 1;
		spec.levelOrder = rnd.nextBoolean();
		int way = rnd.nextInt(4);
		spec.orderWay = (way == 0) ? null : (way == 1) ? "asc" : (way == 2) ? "desc" : "DESC";
		spec.filter = rnd.nextBoolean();
		String[] types = { "eq", "neq", "gt", "between", "in" };
		spec.compareType = types[rnd.nextInt(types.length)];
		spec.compareValues = rnd.nextBoolean() ? "1" : "1,2";
		return spec;
	}

	private static String[] sumColumnNames(TreeCase tc, Spec spec) {
		if (spec.sumIsId) {
			return new String[] { "id" };
		}
		if (spec.sumIsPid) {
			return new String[] { "pid" };
		}
		if (spec.sumMode == 0 || tc.sumColCount == 0) {
			return new String[0];
		}
		int count = (spec.sumMode == 1) ? 1 : tc.sumColCount;
		String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			names[i] = "amt" + i;
		}
		return names;
	}

	private static TreeSortModel modelOf(TreeCase tc, Spec spec) {
		TreeSortModel model = new TreeSortModel().setIdColumn("id").setPidColumn("pid");
		String[] sumColumns = sumColumnNames(tc, spec);
		if (sumColumns.length > 0) {
			model.setSumColumns(sumColumns);
		}
		if (spec.levelOrder && tc.orderCol >= 0) {
			model.setLevelOrderColumn("ord");
			// order-way 为 null 时新旧实现都会抛空指针，属于既有行为
			model.setOrderWay(spec.orderWay);
		}
		if (spec.filter && tc.filterCol >= 0 && sumColumns.length > 0) {
			model.setFilterColumn("flt");
			model.setCompareType(spec.compareType);
			model.setCompareValues(spec.compareValues);
		}
		return model;
	}

	private static LabelIndexModel labelsOf(TreeCase tc) {
		LabelIndexModel labels = new LabelIndexModel();
		for (Map.Entry<String, Integer> column : tc.columns.entrySet()) {
			labels.put(column.getKey(), column.getValue());
		}
		return labels;
	}

	private static Outcome runProcess(boolean reference, boolean lenient, TreeCase tc, Spec spec) {
		Outcome out = new Outcome();
		out.rows = deepCopy(tc.rows);
		if (!reference) {
			out.initial = new IdentityHashMap<List, List<Object>>();
			for (List treeRow : out.rows) {
				out.initial.put(treeRow, new ArrayList<Object>((List<Object>) treeRow));
			}
		}
		try {
			if (reference) {
				refProcess(modelOf(tc, spec), labelsOf(tc), out.rows, lenient);
			} else {
				TreeDataSort.process(modelOf(tc, spec), labelsOf(tc), out.rows);
			}
		} catch (Throwable e) {
			out.error = e;
		}
		return out;
	}

	private static BigDecimal decimal(Object cell) {
		return cell == null ? BigDecimal.ZERO : new BigDecimal(cell.toString().replace(",", ""));
	}

	/**
	 * 不变量：每一行都汇总到"它前面最近的、id 与其 pid 相等的那一行"，因此
	 * 父节点最终值 = 父节点初值 + 各直接子节点最终值(参与汇总的子节点)。
	 * 该不变量直接来自汇总规则本身，不依赖参考实现的转写是否正确
	 */
	private static void assertRollUpInvariant(TreeCase tc, Spec spec, Outcome run, String context) {
		TreeSortModel model = modelOf(tc, spec);
		boolean hasFilter = StringUtil.isNotBlank(model.getFilterColumn())
				&& StringUtil.isNotBlank(model.getCompareValues())
				&& StringUtil.isNotBlank(model.getCompareType());
		int size = run.rows.size();
		int[] parentOf = new int[size];
		boolean[] doSum = new boolean[size];
		for (int i = 1; i < size; i++) {
			List<Object> treeRow = run.rows.get(i);
			parentOf[i] = -1;
			doSum[i] = !hasFilter
					|| MacroIfLogic.compare(treeRow.get(tc.filterCol), model.getCompareType(), model.getCompareValues(),
							null);
			for (int j = i - 1; j >= 0; j--) {
				if (BeanUtil.equalsIgnoreType(run.rows.get(j).get(tc.idCol), treeRow.get(tc.pidCol), false)) {
					parentOf[i] = j;
					break;
				}
			}
		}
		// 只校验模型真正指定的汇总列
		String[] sumNames = sumColumnNames(tc, spec);
		int sumCount = sumNames.length;
		int[] sumCols = new int[sumCount];
		for (int c = 0; c < sumCount; c++) {
			sumCols[c] = tc.columns.get(sumNames[c]);
		}
		Map<Integer, BigDecimal[]> sums = new TreeMap<Integer, BigDecimal[]>();
		for (int i = 1; i < size; i++) {
			if (parentOf[i] < 0 || !doSum[i]) {
				continue;
			}
			BigDecimal[] total = sums.get(parentOf[i]);
			if (total == null) {
				total = new BigDecimal[sumCount];
				for (int c = 0; c < sumCount; c++) {
					total[c] = BigDecimal.ZERO;
				}
				sums.put(parentOf[i], total);
			}
			for (int c = 0; c < sumCount; c++) {
				total[c] = total[c].add(decimal(run.rows.get(i).get(sumCols[c])));
			}
		}
		for (Map.Entry<Integer, BigDecimal[]> entry : sums.entrySet()) {
			int parentIndex = entry.getKey();
			List<Object> parentInitial = run.initial.get(run.rows.get(parentIndex));
			for (int c = 0; c < sumCount; c++) {
				int col = sumCols[c];
				BigDecimal expected = decimal(parentInitial.get(col)).add(entry.getValue()[c]);
				BigDecimal actual = (BigDecimal) run.rows.get(parentIndex).get(col);
				assertTrue(actual != null && expected.compareTo(actual) == 0,
						context + ": 第" + parentIndex + "行第" + col + "列汇总值应为 " + expected + "，实际为 " + actual);
			}
		}
	}

	// ======================= 用例 =======================

	@Test
	public void processMatchesPreChangeAlgorithmOnRandomTrees() {
		int succeeded = 0;
		int failed = 0;
		int summed = 0;
		int bigTrees = 0;
		int crossType = 0;
		int levelOrdered = 0;
		for (int seed = 0; seed < PROCESS_CASES; seed++) {
			Random rnd = new Random(seed * 7919L + 13);
			// 四档规模：单行/小树、探测窗口(16行)上下、中等、大树
			int band = seed % 4;
			int minN = (band == 0) ? 1 : (band == 1) ? 14 : (band == 2) ? 45 : 160;
			int maxN = (band == 0) ? 18 : (band == 1) ? 34 : (band == 2) ? 120 : 600;
			int typeMode = rnd.nextInt(6);
			TreeCase tc = generate(rnd, minN, maxN, typeMode, rnd.nextInt(3) == 0);
			Spec spec = randomSpec(rnd);
			String context = "process seed=" + seed + ", typeMode=" + typeMode + ", sumMode=" + spec.sumMode
					+ ", sumIsId=" + spec.sumIsId + ", sumIsPid=" + spec.sumIsPid + ", levelOrder=" + spec.levelOrder
					+ ", orderWay=" + spec.orderWay + ", filter=" + spec.filter + ", hasNullId=" + tc.hasNullId
					+ ", rows=" + tc.rows.size();
			Outcome actual = runProcess(false, false, tc, spec);
			// 新语义(宽松比较)套在原算法上，结构必须完全一致
			assertSameOutcome(actual, runProcess(true, true, tc, spec), context + " [宽松语义参考]");
			// id 与 pid 同类型、且汇总列不与它们重叠时，与原始 equals 语义的结果也必须一致。
			// (汇总列恰为 id/pid 列时取值会被改写成 BigDecimal，数据在处理中途变成混合类型，不属于"同类型数据")
			if (tc.sameTypes && !spec.sumOverlapsKey()) {
				Outcome strict = runProcess(true, false, tc, spec);
				boolean toleratedNullId = strict.error instanceof NullPointerException && tc.hasNullId
						&& actual.error == null;
				if (!toleratedNullId) {
					assertSameOutcome(actual, strict, context + " [原始语义参考]");
				}
			} else {
				crossType++;
			}
			if (actual.error == null) {
				succeeded++;
				if (sumColumnNames(tc, spec).length > 0) {
					summed++;
				}
				if (band == 3) {
					bigTrees++;
				}
				if (spec.levelOrder && tc.orderCol >= 0 && spec.orderWay != null) {
					levelOrdered++;
				}
				// 不做层内排序、汇总列不与 id/pid 列重叠时，逐层汇总的不变量成立
				if (!(spec.levelOrder && tc.orderCol >= 0) && !spec.sumOverlapsKey()
						&& sumColumnNames(tc, spec).length > 0) {
					assertRollUpInvariant(tc, spec, actual, context);
				}
			} else {
				failed++;
			}
		}
		// 以下只是确认随机数据确实覆盖到了各类情形(抛异常主要来自：orderWay 为 null、整体成环、节点 id 为 null)
		assertTrue(succeeded > PROCESS_CASES / 2, "正常返回的用例数=" + succeeded);
		assertTrue(failed > 50, "抛异常的用例数=" + failed);
		assertTrue(summed > PROCESS_CASES / 4, "带汇总的用例数=" + summed);
		assertTrue(bigTrees > 500, "大树(160~600行)用例数=" + bigTrees);
		assertTrue(crossType > 1000, "类型混用的用例数=" + crossType);
		assertTrue(levelOrdered > 400, "带层内排序的用例数=" + levelOrdered);
		System.out.println("[recheck] process: " + PROCESS_CASES + " cases, succeeded=" + succeeded + ", failed="
				+ failed + ", summed=" + summed + ", bigTrees=" + bigTrees + ", crossType=" + crossType
				+ ", levelOrdered=" + levelOrdered);
	}

	@Test
	public void sortTreeListMatchesPreChangeAlgorithmOnRandomTrees() {
		int succeeded = 0;
		int failed = 0;
		int withLeftover = 0;
		for (int seed = 0; seed < SORT_LIST_CASES; seed++) {
			Random rnd = new Random(seed * 104729L + 7);
			int band = seed % 4;
			int minN = (band == 0) ? 1 : (band == 1) ? 14 : (band == 2) ? 45 : 160;
			int maxN = (band == 0) ? 18 : (band == 1) ? 34 : (band == 2) ? 120 : 600;
			int typeMode = rnd.nextInt(6);
			TreeCase tc = generate(rnd, minN, maxN, typeMode, rnd.nextBoolean() && rnd.nextBoolean());
			// sortTreeList 只看 id、pid 两列，这里取 [id, pid, 序号]
			List<List<Object>> data = new ArrayList<List<Object>>();
			List<Object> idValues = new ArrayList<Object>();
			List<Object> pidValues = new ArrayList<Object>();
			for (int i = 0; i < tc.rows.size(); i++) {
				List<Object> source = tc.rows.get(i);
				data.add(row(source.get(tc.idCol), source.get(tc.pidCol), Integer.valueOf(i)));
				idValues.add(source.get(tc.idCol));
				pidValues.add(source.get(tc.pidCol));
			}
			// 根 pid：取数据中"找不到对应 id"的 pid，再按不同方式给出
			List<Object> naturalRoots = new ArrayList<Object>();
			for (Object pid : pidValues) {
				if (!containsId(idValues, pid, true) && !naturalRoots.contains(pid)) {
					naturalRoots.add(pid);
				}
			}
			int mode = rnd.nextInt(10);
			boolean retype = mode == 8;
			List<Object> rootPids = new ArrayList<Object>();
			if (mode == 9) {
				rootPids.add("not-a-pid-" + rnd.nextInt(1000));
			} else if (mode == 7) {
				// 不传根 pid：原样返回
			} else if (!naturalRoots.isEmpty()) {
				if (mode == 6 && naturalRoots.size() > 1) {
					rootPids.add(naturalRoots.get(rnd.nextInt(naturalRoots.size())));
				} else {
					rootPids.addAll(naturalRoots);
				}
				if (retype) {
					// 换一种类型给出同一个值：新实现按宽松比较识别为根
					for (int i = 0; i < rootPids.size(); i++) {
						Object value = rootPids.get(i);
						rootPids.set(i, value instanceof Long ? Integer.valueOf(((Long) value).intValue())
								: value instanceof Integer ? Long.valueOf(((Integer) value).longValue())
										: value instanceof String && ((String) value).matches("-?\\d+")
												? Long.valueOf((String) value) : value);
					}
				}
			}
			String context = "sortTreeList seed=" + seed + ", typeMode=" + typeMode + ", mode=" + mode;
			Outcome actual = runSortTreeList(false, false, data, rootPids);
			assertSameListOutcome(actual, runSortTreeList(true, true, data, rootPids), context + " [宽松语义参考]");
			if (tc.sameTypes && !retype && !tc.hasNullId) {
				assertSameListOutcome(actual, runSortTreeList(true, false, data, rootPids), context + " [原始语义参考]");
			}
			if (actual.error == null) {
				succeeded++;
				if (!actual.leftover.isEmpty()) {
					withLeftover++;
				}
			} else {
				failed++;
			}
		}
		assertTrue(succeeded > SORT_LIST_CASES / 2, "正常返回的用例数=" + succeeded);
		assertTrue(failed > 50, "抛异常的用例数=" + failed);
		assertTrue(withLeftover > 300, "存在挂不到树上的节点的用例数=" + withLeftover);
		System.out.println("[recheck] sortTreeList: " + SORT_LIST_CASES + " cases, succeeded=" + succeeded + ", failed="
				+ failed + ", withLeftover=" + withLeftover);
	}

	private static final TreeIdAndPidGet<List<Object>> GETTER = new TreeIdAndPidGet<List<Object>>() {
		@Override
		public Object[] getIdAndPid(List<Object> item) {
			return new Object[] { item.get(0), item.get(1) };
		}
	};

	private static Outcome runSortTreeList(boolean reference, boolean lenient, List<List<Object>> source,
			List<Object> rootPids) {
		Outcome out = new Outcome();
		List<List<Object>> data = new ArrayList<List<Object>>(source.size());
		for (List<Object> sourceRow : source) {
			data.add(new ArrayList<Object>(sourceRow));
		}
		try {
			Object[] pids = rootPids.toArray();
			List<List<Object>> result = reference ? refSortTreeList(data, GETTER, lenient, pids)
					: CollectionUtil.sortTreeList(data, GETTER, pids);
			out.order = new ArrayList<Integer>();
			for (List<Object> resultRow : result) {
				out.order.add((Integer) resultRow.get(2));
			}
			out.leftover = new ArrayList<Integer>();
			for (List<Object> left : data) {
				out.leftover.add((Integer) left.get(2));
			}
		} catch (Throwable e) {
			out.error = e;
		}
		return out;
	}

	@Test
	public void linkedOrderIsPreOrderAndKeepsSiblingOrder() {
		// 行(id, pid, 序号)：乱序输入。2、1、3 为根；12 先于 11 出现，所以作为 1 的子节点排在 11 前面；99 挂不到树上
		int[][] nodes = { { 12, 1 }, { 2, 0 }, { 111, 11 }, { 1, 0 }, { 99, 7 }, { 21, 2 }, { 3, 0 }, { 11, 1 } };
		List<List<Object>> data = new ArrayList<List<Object>>();
		for (int[] node : nodes) {
			data.add(row(Long.valueOf(node[0]), Long.valueOf(node[1]), Integer.valueOf(node[0])));
		}
		Outcome out = runSortTreeList(false, false, data, Arrays.asList((Object) 0L));
		assertNull(out.error);
		assertEquals(Arrays.asList(2, 21, 1, 12, 11, 111, 3), out.order);
		assertEquals(Arrays.asList(99), out.leftover);
	}

	/** 列顺序固定为 id、pid，其后依次是 additionalColumns */
	private static LabelIndexModel idPidLabels(String... additionalColumns) {
		LabelIndexModel labels = new LabelIndexModel();
		labels.put("id", 0);
		labels.put("pid", 1);
		for (int i = 0; i < additionalColumns.length; i++) {
			labels.put(additionalColumns[i], 2 + i);
		}
		return labels;
	}

	@Test
	public void summaryIsExactAroundTheProbeWindow() {
		// 探测窗口为 16 行：星形(父节点与最远的子节点相隔 size-2 行)和链形逐个规模核对汇总值
		for (int size = 1; size <= 60; size++) {
			List<List<Object>> star = new ArrayList<List<Object>>();
			star.add(row(1L, 0L, null));
			for (int i = 1; i < size; i++) {
				star.add(row(Long.valueOf(i + 1), 1L, Long.valueOf(1)));
			}
			TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid").setSumColumns("amt"),
					idPidLabels("amt"), star, new Integer[] { 2 }, 0, 1);
			assertEquals(0, BigDecimal.valueOf(size - 1).compareTo(decimal(star.get(0).get(2))), "星形 size=" + size);

			List<List<Object>> chain = new ArrayList<List<Object>>();
			for (int i = 0; i < size; i++) {
				chain.add(row(Long.valueOf(i + 1), Long.valueOf(i), Long.valueOf(1)));
			}
			TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid").setSumColumns("amt"),
					idPidLabels("amt"), chain, new Integer[] { 2 }, 0, 1);
			for (int i = 0; i < size; i++) {
				// 链上每个节点等于自身及全部后代之和
				assertEquals(0, BigDecimal.valueOf(size - i).compareTo(decimal(chain.get(i).get(2))), "链形 size="
						+ size + ", 第" + i + "行");
			}
		}
	}

	@Test
	public void summaryRereadsParentIdWhenSumColumnIsTheIdColumn() {
		// 汇总列就是 id 列：父节点的 id 会在汇总过程中被改写，查找父节点必须读最新值。
		// X(id=2) 在第 0 行，其后 20 行 pid 无对应，第 21 行 R(id=7,pid=1)，第 22 行 C(id=-1,pid=2)
		List<List<Object>> rows = new ArrayList<List<Object>>();
		rows.add(row(2L, 0L));
		for (int k = 1; k <= 20; k++) {
			rows.add(row(Long.valueOf(300 + k), 400L));
		}
		rows.add(row(7L, 1L));
		rows.add(row(-1L, 2L));
		TreeDataSort.summaryTreeList(new TreeSortModel().setIdColumn("id").setPidColumn("pid").setSumColumns("id"),
				idPidLabels(), rows, new Integer[] { 0 }, 0, 1);
		// C 汇入 X：X.id = 2 + (-1) = 1；随后 R 以 pid=1 命中改写后的 X：X.id = 1 + 7 = 8
		assertEquals(0, BigDecimal.valueOf(8).compareTo(decimal(rows.get(0).get(0))));
		assertEquals(0, BigDecimal.valueOf(-1).compareTo(decimal(rows.get(22).get(0))));
		assertEquals(0, BigDecimal.valueOf(7).compareTo(decimal(rows.get(21).get(0))));
	}

	@Test
	public void largeTreesSortAndRollUpQuickly() {
		int n = 100_000;
		String[] shapes = { "chain", "star", "balanced", "random" };
		for (String shape : shapes) {
			Random rnd = new Random(42);
			List<List<Object>> data = new ArrayList<List<Object>>(n);
			for (int i = 1; i <= n; i++) {
				long pid;
				if (i == 1) {
					pid = 0;
				} else if (shape.equals("chain")) {
					pid = i - 1;
				} else if (shape.equals("star")) {
					pid = 1;
				} else if (shape.equals("balanced")) {
					pid = (i - 2) / 8 + 1;
				} else {
					pid = 1 + rnd.nextInt(i - 1);
				}
				data.add(row(Long.valueOf(i), Long.valueOf(pid), Integer.valueOf(i)));
			}
			long begin = System.nanoTime();
			List<List<Object>> sorted = CollectionUtil.sortTreeList(data, GETTER, 0L);
			// 每个节点都在其父节点之后
			Map<Object, Integer> firstPosition = new java.util.HashMap<Object, Integer>();
			for (int i = 0; i < sorted.size(); i++) {
				Integer position = firstPosition.get(sorted.get(i).get(0));
				if (position == null) {
					firstPosition.put(sorted.get(i).get(0), i);
				}
			}
			assertEquals(n, sorted.size(), shape + ": 行数");
			for (int i = 1; i < sorted.size(); i++) {
				Integer parentPosition = firstPosition.get(sorted.get(i).get(1));
				assertTrue(parentPosition != null && parentPosition < i, shape + ": 第" + i + "行排在父节点之前");
			}
			// 以序号列为汇总列：整棵树的和全部汇到根节点
			TreeDataSort.summaryTreeList(
					new TreeSortModel().setIdColumn("id").setPidColumn("pid").setSumColumns("seq"), idPidLabels("seq"),
					sorted, new Integer[] { 2 }, 0, 1);
			long millis = (System.nanoTime() - begin) / 1_000_000L;
			assertEquals(0, BigDecimal.valueOf((long) n * (n + 1) / 2).compareTo(decimal(sorted.get(0).get(2))),
					shape + ": 根节点汇总值");
			// 原先是平方级(10万行需数秒到数十秒)，这里只防退化
			assertTrue(millis < 10_000L, shape + ": 10万行排序+汇总耗时 " + millis + " ms");
			System.out.println("[recheck] sortTreeList + summaryTreeList " + shape + ", rows=" + n + ", " + millis
					+ " ms");
		}
	}
}
