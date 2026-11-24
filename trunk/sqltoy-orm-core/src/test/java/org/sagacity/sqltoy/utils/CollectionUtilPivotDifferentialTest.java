package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.opentest4j.AssertionFailedError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sagacity.sqltoy.oracle.CollectionUtilLegacyOracle;
import org.sagacity.sqltoy.oracle.DiffSupport;

/**
 * @project sagacity-sqltoy
 * @description CollectionUtil.pivotList、innerArrayToList 与优化前实现(行为基准)的差分对拍：返回的每个单元格、入参是否原封未动、
 *              异常都必须完全一致。生产路径上类别参照集恒为二维形态(extractCategory 与 category-sql 都会做行列转置)，
 *              一维形态只有直接调用才会出现，两种都在对拍范围内。
 * @author zhongxuchen
 */
public class CollectionUtilPivotDifferentialTest {
	private static final int RANDOM_CASES = 4000;

	/** 旋转列的取值类型：只在旋转列出现，用来确认旋转值确实落进了结果(而非两边都空) */
	private static final class RotateValue {
		private final int value;

		RotateValue(int value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object other) {
			return (other instanceof RotateValue) && ((RotateValue) other).value == value;
		}

		@Override
		public int hashCode() {
			return value;
		}

		@Override
		public String toString() {
			return "rotate(" + value + ")";
		}
	}

	/** 一份旋转用例 */
	private static final class PivotCase {
		List<List<Object>> data;
		List<Object> categorys;
		Integer[] categoryCol;
		Integer[] pkColumns;
		Integer[] categCompareCol;
		int startCol;
		int endCol;
		Object defaultValue;
		/** 一维类别列表且首个元素为 null：原实现在探测维度时抛空指针，现已按普通类别处理 */
		boolean flatCategoryFirstNull;
	}

	/** 一侧的执行结果 */
	private static final class Run {
		List result;
		Throwable error;
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	// ---------------- 随机用例生成 ----------------

	/** 枚举类别：非常规取值类型，类别索引不适用 */
	private enum Size {
		S, M, L
	}

	/** 自定义取值类型：按文本相等，不属于常规取值类型 */
	private static final class TextValue {
		private final String text;

		TextValue(String text) {
			this.text = text;
		}

		@Override
		public boolean equals(Object other) {
			return (other instanceof TextValue) && ((TextValue) other).text.equals(text);
		}

		@Override
		public int hashCode() {
			return text.hashCode();
		}

		@Override
		public String toString() {
			return text;
		}
	}

	private static LocalDate dateValue(int category) {
		return LocalDate.of(2019 + category % 2, 1 + category % 12, 1 + category % 28);
	}

	/** 类别参照的取值(按取值类型组合 family 区分，见 generate) */
	private static Object poolValue(int category, int family) {
		switch (family) {
		case 1:
			return Integer.valueOf(category);
		case 4:
			return BigDecimal.valueOf(category);
		case 5:
			return "k" + category;
		case 6:
			return Size.values()[category % Size.values().length];
		case 7:
			return new TextValue("K" + category);
		case 9:
			return dateValue(category);
		default:
			return "K" + category;
		}
	}

	/**
	 * 数据行中类别列的取值：与参照同类型或跨类型(跨类型时按文本匹配)。
	 * family 0~5、9 的取值都是常规类型(走类别索引)；6、7 的类别不是常规类型(不建索引)；
	 * 8 的类别是常规类型而数据取值是自定义对象(该行退回逐个比对)
	 */
	private static Object rowValue(int category, int family) {
		switch (family) {
		case 0:
			return Integer.valueOf(category);
		case 1:
			return Long.valueOf(category);
		case 3:
			return String.valueOf(category);
		case 4:
			return Long.valueOf(category);
		case 6:
			return Size.values()[category % Size.values().length];
		case 7:
			return "K" + category;
		case 8:
			return new TextValue(String.valueOf(category));
		case 9:
			return dateValue(category);
		default:
			return "K" + category;
		}
	}

	private static PivotCase generate(long seed) {
		Random rnd = new Random(seed);
		PivotCase pivot = new PivotCase();
		int family = rnd.nextInt(10);
		// 类别数偶尔为 0、首类别偶尔为 null：原实现分别抛 IndexOutOfBoundsException、NullPointerException，属于要原样保持的入参
		int categories = (rnd.nextInt(20) == 0) ? 0 : 1 + rnd.nextInt(11);
		int categoryColCount = rnd.nextBoolean() ? 1 : 2;
		int pkCount = rnd.nextBoolean() ? 1 : 2;
		int rotateWidth = 1 + rnd.nextInt(3);
		int extras = rnd.nextInt(3);
		// 两个类别列时大多数为二维参照，偶尔也用一维列表(生产路径上恒为二维，一维只在直接调用时出现)
		boolean twoDimension = (categoryColCount == 2 && rnd.nextInt(4) != 0) || rnd.nextBoolean();
		List<Object> pool = new ArrayList<>();
		for (int j = 0; j < categories; j++) {
			pool.add(rnd.nextInt(10) == 0 ? null : poolValue(j, family));
		}
		if (categories > 2 && rnd.nextInt(6) == 0) {
			int to = rnd.nextInt(categories);
			pool.set(to, pool.get(rnd.nextInt(categories)));
		}
		pivot.categorys = new ArrayList<>();
		if (twoDimension) {
			for (int k = 0; k < categoryColCount; k++) {
				List<Object> reference = new ArrayList<>();
				for (int j = 0; j < categories; j++) {
					reference.add(k == 0 ? pool.get(j) : poolValue(j, 2));
				}
				pivot.categorys.add(reference);
			}
			Integer[] references = rnd.nextBoolean() ? null : new Integer[categoryColCount];
			for (int k = 0; references != null && k < categoryColCount; k++) {
				references[k] = rnd.nextInt(categoryColCount + 1);
			}
			if (rnd.nextInt(10) == 0) {
				// 参照行数或 categoryCol 长度与类别列数不一致：原实现抛 IllegalArgumentException
				if (rnd.nextBoolean() && categoryColCount == 2) {
					pivot.categorys.remove(1);
				} else if (references != null) {
					references = new Integer[] { Integer.valueOf(rnd.nextInt(3)) };
				}
			}
			pivot.categoryCol = references;
		} else {
			pivot.categorys.addAll(pool);
			pivot.flatCategoryFirstNull = !pool.isEmpty() && pool.get(0) == null;
			Integer[] flat = rnd.nextBoolean() ? null : new Integer[categoryColCount];
			for (int k = 0; flat != null && k < categoryColCount; k++) {
				flat[k] = k;
			}
			pivot.categoryCol = flat;
		}
		// 列布局：主键/类别列打乱后作为首块，旋转列、附加列各自整块，块间再打乱
		List<String> headRoles = new ArrayList<>();
		for (int i = 0; i < pkCount; i++) {
			headRoles.add("pk");
		}
		for (int i = 0; i < categoryColCount; i++) {
			headRoles.add("cat");
		}
		Collections.shuffle(headRoles, rnd);
		List<List<String>> blocks = new ArrayList<>();
		blocks.add(headRoles);
		List<String> rotateBlock = new ArrayList<>();
		for (int i = 0; i < rotateWidth; i++) {
			rotateBlock.add("rot");
		}
		blocks.add(rotateBlock);
		if (extras > 0) {
			List<String> extraBlock = new ArrayList<>();
			for (int i = 0; i < extras; i++) {
				extraBlock.add("ext");
			}
			blocks.add(extraBlock);
		}
		Collections.shuffle(blocks, rnd);
		List<Integer> pkColumns = new ArrayList<>();
		List<Integer> categoryColumns = new ArrayList<>();
		int width = 0;
		int start = -1;
		int end = -1;
		for (List<String> block : blocks) {
			int first = width;
			for (String role : block) {
				if (role.equals("pk")) {
					pkColumns.add(width);
				} else if (role.equals("cat")) {
					categoryColumns.add(width);
				}
				width++;
			}
			if (block.get(0).equals("rot")) {
				start = first;
				end = width - 1;
			}
		}
		pivot.pkColumns = pkColumns.toArray(new Integer[0]);
		pivot.categCompareCol = categoryColumns.toArray(new Integer[0]);
		pivot.startCol = start;
		pivot.endCol = end;
		pivot.defaultValue = rnd.nextInt(2) == 0 ? "N/A" : null;
		// 数据行：若干主键分组，各自连续；偶尔把一个主键的行拆到另一个主键两侧
		int groups = rnd.nextInt(9);
		List<List<List<Object>>> rowsByGroup = new ArrayList<>();
		for (int g = 0; g < groups; g++) {
			List<List<Object>> rows = new ArrayList<>();
			int rowCount = rnd.nextInt(categories + 2);
			for (int r = 0; r < rowCount; r++) {
				List<Object> row = new ArrayList<>(width);
				for (int w = 0; w < width; w++) {
					row.add(null);
				}
				for (int k = 0; k < pkCount; k++) {
					// 相邻的两个分组第一列主键相同、第二列不同，这样会出现"前一列相等、后一列不等"的比较
					row.set(pkColumns.get(k), k == 0 ? (Object) Long.valueOf(g / 2) : (Object) ("x" + g));
					// 主键偶尔为 null(与上一行比较时按字符串 null 处理)
					if (rnd.nextInt(15) == 0) {
						row.set(pkColumns.get(k), null);
					}
				}
				for (int k = 0; k < categoryColCount; k++) {
					int pick = rnd.nextInt(100);
					Object value;
					if (pick < 70 && categories > 0) {
						value = (k == 0) ? rowValue(rnd.nextInt(categories), family)
								: poolValue(rnd.nextInt(categories), 2);
					} else if (pick < 82) {
						value = null;
					} else {
						// 不在参照中的类别
						value = (k == 0) ? rowValue(500 + rnd.nextInt(5), family)
								: poolValue(500 + rnd.nextInt(5), 2);
					}
					row.set(categoryColumns.get(k), value);
				}
				for (int w = start; w <= end; w++) {
					row.set(w, rnd.nextInt(5) == 0 ? null : (Object) new RotateValue(rnd.nextInt(1000)));
				}
				for (int w = 0; w < width; w++) {
					if (row.get(w) == null && !pkColumns.contains(w) && !categoryColumns.contains(w)
							&& (w < start || w > end)) {
						row.set(w, "e" + rnd.nextInt(10));
					}
				}
				rows.add(row);
			}
			rowsByGroup.add(rows);
		}
		pivot.data = new ArrayList<>();
		if (rnd.nextInt(4) == 0 && groups > 1) {
			List<List<Object>> first = rowsByGroup.get(0);
			pivot.data.addAll(first.subList(0, first.size() / 2));
			pivot.data.addAll(rowsByGroup.get(1));
			pivot.data.addAll(first.subList(first.size() / 2, first.size()));
			for (int g = 2; g < groups; g++) {
				pivot.data.addAll(rowsByGroup.get(g));
			}
		} else {
			for (List<List<Object>> rows : rowsByGroup) {
				pivot.data.addAll(rows);
			}
		}
		return pivot;
	}

	private static Run run(boolean legacy, PivotCase pivot) {
		Run run = new Run();
		try {
			run.result = legacy ? CollectionUtilLegacyOracle.pivotList(pivot.data, pivot.categorys,
					pivot.categoryCol, pivot.pkColumns, pivot.categCompareCol, pivot.startCol, pivot.endCol,
					pivot.defaultValue)
					: CollectionUtil.pivotList(pivot.data, pivot.categorys, pivot.categoryCol, pivot.pkColumns,
							pivot.categCompareCol, pivot.startCol, pivot.endCol, pivot.defaultValue);
		} catch (Throwable e) {
			run.error = e;
		}
		return run;
	}

	private static void compare(PivotCase pivot, Run oracle, Run current, List<List<Object>> untouched,
			String context) {
		// 旋转只读取入参，入参必须原封未动
		assertEquals(untouched, pivot.data, context + ": input data was modified");
		// 已登记的缺陷修复：类别参照为空时原先抛无说明的越界/空指针，现改为带说明的 IllegalArgumentException
		if (DiffSupport.isExplainedDivergence(oracle.error, current.error)) {
			return;
		}
		// 已登记的缺陷修复：一维类别首个元素为 null 时原先在探测维度时抛空指针，现在按普通类别正常处理
		if (pivot.flatCategoryFirstNull && (oracle.error instanceof NullPointerException) && current.error == null) {
			return;
		}
		DiffSupport.assertSameError(oracle.error, current.error, context);
		if (oracle.error != null) {
			return;
		}
		String diff = DiffSupport.firstDiff(oracle.result, current.result);
		if (diff != null) {
			throw new AssertionFailedError(context + ": " + diff);
		}
		for (int i = 0; i < oracle.result.size(); i++) {
			assertEquals(oracle.result.get(i).getClass(), current.result.get(i).getClass(),
					context + ": row " + i + " is not the same kind of list");
		}
	}

	private static void assertSameAsOracle(PivotCase pivot, String context) {
		List<List<Object>> untouched = DiffSupport.deepCopy(pivot.data);
		compare(pivot, run(true, pivot), run(false, pivot), untouched, context);
	}

	// ---------------- 随机对拍 ----------------

	@Test
	public void randomPivotsMatchOracle() {
		int filledCases = 0;
		int errorCases = 0;
		int multiRowCases = 0;
		for (long seed = 0; seed < RANDOM_CASES; seed++) {
			PivotCase pivot = generate(seed);
			String context = "seed=" + seed;
			List<List<Object>> untouched = DiffSupport.deepCopy(pivot.data);
			Run oracle = run(true, pivot);
			Run current = run(false, pivot);
			compare(pivot, oracle, current, untouched, context);
			if (oracle.error != null) {
				errorCases++;
			} else {
				if (containsRotateValue(oracle.result)) {
					filledCases++;
				}
				if (oracle.result.size() > 1) {
					multiRowCases++;
				}
			}
		}
		// 旋转值要真的落进结果(否则"全等"可能只是两边都没匹配上)，多行输出与异常路径也都要跑到
		assertTrue(filledCases > 1000, "旋转值落进结果的用例数=" + filledCases);
		assertTrue(multiRowCases > 1800, "多行输出的用例数=" + multiRowCases);
		assertTrue(errorCases > 30, "抛异常的用例数=" + errorCases);
	}

	private static boolean containsRotateValue(List result) {
		for (Object item : result) {
			for (Object cell : (List<?>) item) {
				if (cell instanceof RotateValue) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------- 手写用例(期望为人工推算) ----------------

	private static List<Object> twoDimension(Object... categories) {
		List<Object> references = new ArrayList<>();
		references.add(new ArrayList<>(Arrays.asList(categories)));
		return references;
	}

	/** 文档"动态行转列"一节的示例：每个品类一行，每个月份下三个指标横向展开 */
	@Test
	public void documentationExamplePivot() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("香蕉", "2019年5月", 10, 2000, 2000));
		data.add(row("香蕉", "2019年4月", 12, 2400, 2700));
		data.add(row("香蕉", "2019年3月", 13, 2300, 2700));
		data.add(row("苹果", "2019年5月", 12, 2000, 2400));
		data.add(row("苹果", "2019年4月", 11, 1900, 2600));
		data.add(row("苹果", "2019年3月", 13, 2000, 2500));
		// 类别参照保持首次出现的先后顺序(与 ResultUtils.extractCategory 的行为一致)
		List<Object> categorys = twoDimension("2019年5月", "2019年4月", "2019年3月");

		List result = CollectionUtil.pivotList(data, categorys, null, new Integer[] { 0 }, new Integer[] { 1 }, 2, 4,
				null);
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("香蕉", 10, 2000, 2000, 12, 2400, 2700, 13, 2300, 2700));
		expected.add(row("苹果", 12, 2000, 2400, 11, 1900, 2600, 13, 2000, 2500));
		assertEquals(expected, result, "应与文档给出的结果表(按月份首次出现顺序)一致");
	}

	@Test
	public void defaultValueFillsMissingCategoriesAndNullCategoryMatches() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("p1", "A", "a1"));
		data.add(row("p2", null, "n1"));
		data.add(row("p3", "Z", "z1"));
		List<Object> categorys = twoDimension(null, "A", "B");

		List result = CollectionUtil.pivotList(data, categorys, null, new Integer[] { 0 }, new Integer[] { 1 }, 2, 2,
				"0");
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("p1", "0", "a1", "0"));
		expected.add(row("p2", "n1", "0", "0"));
		expected.add(row("p3", "0", "0", "0"));
		assertEquals(expected, result, "缺失的类别用默认值补齐，类别为 null 与参照中的 null 对应");
	}

	@Test
	public void categoryComparedByTextAcrossTypes() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("p1", 1L, "x"));
		data.add(row("p2", new BigDecimal("2"), "y"));
		data.add(row("p3", "1", "z"));
		List<Object> categorys = twoDimension(1, 2);

		List result = CollectionUtil.pivotList(data, categorys, null, new Integer[] { 0 }, new Integer[] { 1 }, 2, 2,
				null);
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("p1", "x", null));
		expected.add(row("p2", null, "y"));
		expected.add(row("p3", "z", null));
		assertEquals(expected, result, "类别与数据类型不同(整数对长整数、对字符串)时按文本比较");
	}

	@Test
	public void multipleKeyColumnsAndInterleavedGroups() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("a", 1, "A", "v1"));
		data.add(row("b", 1, "B", "v2"));
		data.add(row("a", 2, "A", "v3"));
		data.add(row("a", 1, "B", "v4"));
		List<Object> categorys = twoDimension("A", "B");

		List result = CollectionUtil.pivotList(data, categorys, null, new Integer[] { 0, 1 }, new Integer[] { 2 }, 3,
				3, "-");
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("a", 1, "v1", "-"));
		expected.add(row("b", 1, "-", "v2"));
		expected.add(row("a", 2, "v3", "-"));
		expected.add(row("a", 1, "-", "v4"));
		assertEquals(expected, result, "主键分组不连续时会各成一行");
	}

	@Test
	public void multipleCategoryColumnsMustAllMatch() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("p", "B", "X", "none"));
		data.add(row("p", "B", "Y", "hit"));
		List<List<Object>> categorys = new ArrayList<>();
		categorys.add(row("A", "B"));
		categorys.add(row("X", "Y"));

		List result = CollectionUtil.pivotList(data, categorys, null, new Integer[] { 0 }, new Integer[] { 1, 2 }, 3,
				3, null);
		assertEquals(Arrays.asList(row("p", null, "hit")), result, "多个类别列须全部对应一致才落入对应位置");
	}

	/** 类别参照为空(如 category-sql 查不到数据)：原先抛无说明的数组越界，现在给出带说明的异常 */
	@Test
	public void emptyCategoryListThrowsExplainedError() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("p", "A", "v"));
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> CollectionUtil.pivotList(data, new ArrayList<>(), null, new Integer[] { 0 },
						new Integer[] { 1 }, 2, 2, null));
		assertTrue(error.getMessage().startsWith("invalid pivot config"), error.getMessage());
		IllegalArgumentException nullError = assertThrows(IllegalArgumentException.class,
				() -> CollectionUtil.pivotList(data, null, null, new Integer[] { 0 }, new Integer[] { 1 }, 2, 2, null));
		assertTrue(nullError.getMessage().startsWith("invalid pivot config"), nullError.getMessage());
	}

	/** 一维类别列表首元素为 null：原先在探测类别维度时抛空指针，现在按普通类别正常旋转 */
	@Test
	public void flatCategoryListWithNullFirstElementIsSupported() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("p1", null, "n1"));
		data.add(row("p2", "A", "a1"));
		data.add(row("p3", "Z", "z1"));
		List<Object> categorys = new ArrayList<>(Arrays.asList(null, "A"));
		List result = CollectionUtil.pivotList(data, categorys, null, new Integer[] { 0 }, new Integer[] { 1 }, 2, 2,
				"-");
		List<List<Object>> expected = new ArrayList<>();
		expected.add(row("p1", "n1", "-"));
		expected.add(row("p2", "-", "a1"));
		expected.add(row("p3", "-", "-"));
		assertEquals(expected, result, "null 类别与一维类别中的 null 对应，A 对应第二列，其它类别留默认值");
	}

	@Test
	public void twoDimensionReferenceCountMismatchThrows() {
		List<List<Object>> data = new ArrayList<>();
		data.add(row("p", "A", "v"));
		List<List<Object>> categorys = new ArrayList<>();
		categorys.add(row("A"));
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> CollectionUtil.pivotList(data, categorys, new Integer[] { 0, 1 }, new Integer[] { 0 },
						new Integer[] { 1 }, 2, 2, null));
		assertTrue(error.getMessage().contains("pivot reference rows must match"), error.getMessage());
	}

	@Test
	public void emptyOrAbsentDataReturnedAsIs() {
		List<List<Object>> data = new ArrayList<>();
		assertSame(data, CollectionUtil.pivotList(data, twoDimension("A"), null, new Integer[] { 0 },
				new Integer[] { 1 }, 2, 2, null));
		assertNull(CollectionUtil.pivotList(null, twoDimension("A"), null, new Integer[] { 0 }, new Integer[] { 1 }, 2,
				2, null));
	}

	// ---------------- innerArrayToList ----------------

	@Test
	public void innerArrayToListMatchesOracle() {
		for (long seed = 0; seed < 1500; seed++) {
			Random rnd = new Random(seed);
			// 每一行：对象数组、原始类型数组、已是 List、null(在首位时原实现抛空指针)
			List<Object> rows = new ArrayList<>();
			int rowCount = rnd.nextInt(8);
			for (int i = 0; i < rowCount; i++) {
				int kind = rnd.nextInt(10);
				if (kind < 5) {
					rows.add(new Object[] { "a" + i, Integer.valueOf(i), null });
				} else if (kind < 7) {
					rows.add(new int[] { i, i + 1 });
				} else if (kind < 9) {
					rows.add(row("list" + i));
				} else {
					rows.add(null);
				}
			}
			List oracleList = new ArrayList<>(rows);
			List currentList = new ArrayList<>(rows);
			Throwable oracleError = DiffSupport.capture(() -> CollectionUtilLegacyOracle.innerArrayToList(oracleList));
			Throwable currentError = DiffSupport.capture(() -> CollectionUtil.innerArrayToList(currentList));
			DiffSupport.assertSameError(oracleError, currentError, "innerArrayToList seed=" + seed);
			if (oracleError != null) {
				continue;
			}
			assertEquals(oracleList.size(), currentList.size(), "innerArrayToList seed=" + seed);
			// 首行是数组时每一行都会被换成新建的 ArrayList(数组行转成元素，原本就是 List 的行被复制一份)；
			// 首行不是数组则整个集合不动
			boolean converted = !rows.isEmpty() && rows.get(0) != null && rows.get(0).getClass().isArray();
			for (int i = 0; i < oracleList.size(); i++) {
				Object expected = oracleList.get(i);
				Object actual = currentList.get(i);
				if (converted) {
					assertEquals(expected, actual, "innerArrayToList seed=" + seed + " row " + i);
					assertEquals(expected.getClass(), actual.getClass(), "innerArrayToList seed=" + seed + " row " + i);
					assertNotSame(rows.get(i), actual, "innerArrayToList seed=" + seed + " row " + i + " 应替换为新对象");
				} else {
					assertSame(expected, actual, "innerArrayToList seed=" + seed + " row " + i);
				}
			}
		}
	}

	// ---------------- 负例对照：比对逻辑必须能发现故意的差异 ----------------

	@Test
	public void comparisonDetectsDeliberateDifferences() {
		// 找一份能正常旋转、旋转值落进结果且多行的数据
		PivotCase pivot = null;
		for (long seed = 0; seed < RANDOM_CASES && pivot == null; seed++) {
			PivotCase candidate = generate(seed);
			Run run = run(true, candidate);
			if (run.error == null && run.result.size() >= 2 && containsRotateValue(run.result)) {
				pivot = candidate;
			}
		}
		assertTrue(pivot != null, "需要一份能正常旋转的对照数据");
		final PivotCase control = pivot;

		compare(control, run(true, control), run(true, control), DiffSupport.deepCopy(control.data), "基准自身两次执行不应报差异");
		assertDetected(() -> {
			Run reversed = run(true, control);
			Collections.reverse(reversed.result);
			compare(control, run(true, control), reversed, DiffSupport.deepCopy(control.data), "行顺序被打乱");
		});
		assertDetected(() -> {
			Run modified = run(true, control);
			((List<Object>) modified.result.get(0)).set(0, "changed");
			compare(control, run(true, control), modified, DiffSupport.deepCopy(control.data), "单元格被改");
		});
		assertDetected(() -> {
			Run dropped = run(true, control);
			dropped.result.remove(0);
			compare(control, run(true, control), dropped, DiffSupport.deepCopy(control.data), "少了一行");
		});
		assertDetected(() -> {
			// 入参被改动同样要被发现：拿改动前做的快照当"期望"
			List<List<Object>> untouched = DiffSupport.deepCopy(control.data);
			control.data.get(0).set(0, "touched");
			try {
				compare(control, run(true, control), run(true, control), untouched, "入参被改动");
			} finally {
				control.data.get(0).set(0, untouched.get(0).get(0));
			}
		});
		assertDetected(() -> {
			Run failing = run(true, control);
			failing.error = new IllegalStateException("boom");
			compare(control, run(true, control), failing, DiffSupport.deepCopy(control.data), "一侧抛了异常");
		});
	}

	private static void assertDetected(Executable comparison) {
		assertThrows(AssertionFailedError.class, comparison, "比对逻辑未能发现故意的差异");
	}
}
