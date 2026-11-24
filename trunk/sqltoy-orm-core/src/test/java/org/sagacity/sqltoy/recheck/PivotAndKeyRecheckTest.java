package org.sagacity.sqltoy.recheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.ResultUtils;

/**
 * @project sagacity-sqltoy
 * @description 2026-11-24 行转列类别索引、in参数判重键、类别提取判重键、分组空值这几处改动的独立复核：
 *              参考实现按 git 历史中优化之前(提交 766bdb937)的源码重新转写，不复用已有对拍测试的基准类。
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class PivotAndKeyRecheckTest {
	private static final int PIVOT_CASES = 4000;
	private static final int REPEAT_CASES = 3000;
	private static final int GROUP_CASES = 3000;

	// ======================= 通用工具 =======================

	private interface Call {
		Object run() throws Throwable;
	}

	private static final class Outcome {
		Object value;
		Throwable error;
	}

	private static Outcome attempt(Call call) {
		Outcome out = new Outcome();
		try {
			out.value = call.run();
		} catch (Throwable e) {
			out.error = e;
		}
		return out;
	}

	private static String cellKey(Object cell) {
		return cell == null ? "null" : cell.getClass().getName() + "#" + cell;
	}

	/** 深比对：List 逐元素递归，其余按"类型 + 文本"比对 */
	private static void assertDeepEqual(Object expected, Object actual, String path) {
		if (expected instanceof List) {
			assertTrue(actual instanceof List, path + ": 期望 List，实际 " + actual);
			List expectedList = (List) expected;
			List actualList = (List) actual;
			assertEquals(expectedList.size(), actualList.size(), path + ": 元素个数不一致");
			for (int i = 0; i < expectedList.size(); i++) {
				assertDeepEqual(expectedList.get(i), actualList.get(i), path + "[" + i + "]");
			}
			return;
		}
		assertEquals(cellKey(expected), cellKey(actual), path + ": 取值不一致");
	}

	private static void assertSameError(Throwable actual, Throwable expected, String context) {
		if (expected == null && actual == null) {
			return;
		}
		if (expected == null || actual == null) {
			fail(context + ": 一侧抛异常、另一侧正常返回。参考实现=" + expected + "，新实现=" + actual);
		}
		assertEquals(expected.getClass(), actual.getClass(), context + ": 异常类型不一致");
		assertEquals(String.valueOf(expected.getMessage()), String.valueOf(actual.getMessage()),
				context + ": 异常信息不一致");
	}

	private static List<List<Object>> deepCopyRows(List<List<Object>> source) {
		List<List<Object>> result = new ArrayList<List<Object>>(source.size());
		for (List<Object> sourceRow : source) {
			result.add(new ArrayList<Object>(sourceRow));
		}
		return result;
	}

	/** Object[] 行集合转成 List 行集合，便于深比对 */
	private static List<List<Object>> rowsOf(Object value) {
		List<List<Object>> result = new ArrayList<List<Object>>();
		for (Object[] cells : (List<Object[]>) value) {
			result.add(new ArrayList<Object>(Arrays.asList(cells)));
		}
		return result;
	}

	private static Method privateMethod(String name, Class<?>... parameterTypes) {
		try {
			Method method = ResultUtils.class.getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ======================= 行转列 =======================

	/** 优化之前的 pivotList(含当时 innerArrayToList 的行为：每行由 Object[] 转成 List) */
	private static List refPivot(List data, List categorys, Integer[] categoryCol, Integer[] pkColumns,
			Integer[] categCompareCol, int startCol, int endCol, Object defaultValue) {
		if (data == null || data.isEmpty()) {
			return data;
		}
		Integer[] categCol;
		if (categoryCol == null) {
			categCol = new Integer[categCompareCol.length];
			for (int i = 0; i < categCompareCol.length; i++) {
				categCol[i] = i;
			}
		} else {
			categCol = categoryCol;
		}
		boolean isTwoDimensionCategory = (categorys.get(0) instanceof Collection
				|| categorys.get(0).getClass().isArray());
		if (isTwoDimensionCategory
				&& (categCompareCol.length > categorys.size() || categCompareCol.length != categCol.length)) {
			throw new IllegalArgumentException(
					"the pivot reference rows must match the number of reference columns, expect categCol.length == categCompareCol.length!");
		}
		List result = new ArrayList();
		int dataWidth = ((List) data.get(0)).size();
		int cateItemSize = isTwoDimensionCategory ? ((Collection) categorys.get(0)).size() : categorys.size();
		int rotateWith = endCol - startCol + 1;
		int lastRowWidth = dataWidth - categCompareCol.length + (cateItemSize - 1) * rotateWith;
		int rotateTotalCount = cateItemSize * rotateWith;
		int count = 0;
		boolean isRotaCol;
		Object[] rowData = null;
		int indexLength = pkColumns.length;
		boolean categoryColEqual;
		int categColSize = categCompareCol.length;
		boolean pkColumnsEqual;
		List compareRow = null;
		List rowList;
		int rowSize = data.size();
		Object pkColValue;
		Object compareValue;
		for (int i = 0; i < rowSize; i++) {
			rowList = (List) data.get(i);
			pkColumnsEqual = true;
			if (i == 0) {
				pkColumnsEqual = false;
			} else {
				for (int k = 0; k < indexLength; k++) {
					pkColValue = rowList.get(pkColumns[k]);
					if (pkColValue == null) {
						pkColValue = "null";
					}
					compareValue = compareRow.get(pkColumns[k]);
					if (compareValue == null) {
						compareValue = "null";
					}
					pkColumnsEqual = pkColumnsEqual && BeanUtil.equalsIgnoreType(pkColValue, compareValue, false);
					if (!pkColumnsEqual) {
						break;
					}
				}
			}
			if (!pkColumnsEqual) {
				compareRow = rowList;
				if (i != 0) {
					result.add(rowData);
				}
				rowData = new Object[lastRowWidth];
				if (defaultValue != null) {
					for (int j = 0; j < rotateTotalCount; j++) {
						rowData[dataWidth - rotateWith - categCompareCol.length + j] = defaultValue;
					}
				}
				count = 0;
				for (int k = 0; k < dataWidth; k++) {
					isRotaCol = false;
					for (int m = 0; m < categColSize; m++) {
						if (k == categCompareCol[m]) {
							isRotaCol = true;
							break;
						}
					}
					if (k >= startCol && k <= endCol) {
						isRotaCol = true;
					}
					if (!isRotaCol) {
						rowData[count] = rowList.get(k);
						count++;
					}
				}
			}
			for (int j = 0; j < cateItemSize; j++) {
				// 单个数据
				if (categColSize == 1) {
					pkColValue = rowList.get(categCompareCol[0]);
					if (pkColValue == null) {
						pkColValue = "null";
					}
					compareValue = isTwoDimensionCategory ? ((List) categorys.get(categCol[0])).get(j)
							: categorys.get(j);
					if (compareValue == null) {
						compareValue = "null";
					}
					if (BeanUtil.equalsIgnoreType(pkColValue, compareValue, false)) {
						for (int t = 0; t < rotateWith; t++) {
							rowData[count + j * rotateWith + t] = rowList.get(startCol + t);
						}
					}
				} else {
					categoryColEqual = true;
					for (int k = 0; k < categColSize; k++) {
						pkColValue = rowList.get(categCompareCol[k]);
						if (pkColValue == null) {
							pkColValue = "null";
						}
						compareValue = isTwoDimensionCategory ? ((List) categorys.get(categCol[k])).get(j)
								: categorys.get(j);
						if (compareValue == null) {
							compareValue = "null";
						}
						categoryColEqual = categoryColEqual
								&& BeanUtil.equalsIgnoreType(pkColValue, compareValue, false);
					}
					if (categoryColEqual) {
						for (int t = 0; t < rotateWith; t++) {
							rowData[count + j * rotateWith + t] = rowList.get(startCol + t);
						}
					}
				}
			}
			// 最后一行
			if (i == rowSize - 1) {
				result.add(rowData);
			}
		}
		List converted = new ArrayList();
		for (Object row : result) {
			Object[] cells = (Object[]) row;
			List convertedRow = new ArrayList(cells.length);
			for (Object cell : cells) {
				convertedRow.add(cell);
			}
			converted.add(convertedRow);
		}
		return converted;
	}

	/** 自定义类型：equals/hashCode/toString 均按数值实现，但不属于常规取值类型 */
	private static final class Key {
		private final int value;

		Key(int value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object other) {
			return (other instanceof Key) && ((Key) other).value == value;
		}

		@Override
		public int hashCode() {
			return value;
		}

		@Override
		public String toString() {
			return "key" + value;
		}
	}

	private static final class PivotCase {
		List<List<Object>> data = new ArrayList<List<Object>>();
		List<Object> categories = new ArrayList<Object>();
		Integer[] categoryCol;
		Integer[] pkColumns;
		Integer[] categCompareCol;
		int startCol;
		int endCol;
		Object defaultValue;
		boolean twoDimension;
		int mode;
	}

	/**
	 * 类别参照值的取值
	 *
	 * @param mode 0 Integer、1 Long、2 String、3 BigDecimal、4 数字类型混用、5 含 null、6 自定义类型、
	 *            7 参照为 String 而数据为自定义类型
	 */
	private static Object categoryValue(int mode, int value) {
		switch (mode) {
		case 1:
			return Long.valueOf(value);
		case 2:
			return "c" + value;
		case 3:
			return BigDecimal.valueOf(value);
		case 6:
			return new Key(value);
		case 7:
			return "key" + value;
		default:
			return Integer.valueOf(value);
		}
	}

	/** 数据中与类别比对的单元格取值：多数与类别文本相同(类型可以不同)，少数故意不同 */
	private static Object dataValue(int mode, Object category, Random rnd) {
		if (category == null) {
			return rnd.nextInt(3) == 0 ? Integer.valueOf(1) : null;
		}
		switch (mode) {
		case 4: {
			int number = ((Number) category).intValue();
			int pick = rnd.nextInt(10);
			if (pick < 4) {
				return Integer.valueOf(number);
			} else if (pick < 7) {
				return Long.valueOf(number);
			} else if (pick < 9) {
				return BigDecimal.valueOf(number);
			}
			// 文本与类别不同(如 5.0 对 5)，新旧实现都不匹配
			return Double.valueOf(number);
		}
		case 5:
			return rnd.nextInt(8) == 0 ? null : category;
		case 6:
			return new Key(((Key) category).value);
		case 7:
			return new Key(Integer.parseInt(category.toString().substring(3)));
		default:
			return category;
		}
	}

	private static Object cellValue(Random rnd) {
		switch (rnd.nextInt(5)) {
		case 0:
			return null;
		case 1:
			return Integer.valueOf(rnd.nextInt(1000));
		case 2:
			return Double.valueOf(rnd.nextInt(500) / 8.0d);
		case 3:
			return "v" + rnd.nextInt(100);
		default:
			return Long.valueOf(rnd.nextInt(100000));
		}
	}

	private static PivotCase generatePivot(Random rnd) {
		PivotCase pc = new PivotCase();
		int mode = rnd.nextInt(8);
		pc.mode = mode;
		int pkCount = 1 + rnd.nextInt(3);
		int categCount = (rnd.nextInt(10) < 7) ? 1 : 2 + rnd.nextInt(2);
		int rotateWith = 1 + rnd.nextInt(3);
		int extCount = rnd.nextInt(3);
		pc.twoDimension = categCount > 1;
		// 列布局：主键列、类别列、旋转列、其他列四个区块内部连续(旋转列必须连续)，区块之间随机排列
		List<Integer> blocks = new ArrayList<Integer>(Arrays.asList(0, 1, 2, 3));
		Collections.shuffle(blocks, rnd);
		int[] blockSize = { pkCount, categCount, rotateWith, extCount };
		int[] blockStart = new int[4];
		int width = 0;
		for (int b = 0; b < blocks.size(); b++) {
			int block = blocks.get(b);
			blockStart[block] = width;
			width += blockSize[block];
		}
		pc.pkColumns = new Integer[pkCount];
		for (int i = 0; i < pkCount; i++) {
			pc.pkColumns[i] = blockStart[0] + i;
		}
		pc.categCompareCol = new Integer[categCount];
		for (int i = 0; i < categCount; i++) {
			pc.categCompareCol[i] = blockStart[1] + i;
		}
		pc.startCol = blockStart[2];
		pc.endCol = blockStart[2] + rotateWith - 1;
		// 类别组合
		int itemCount = 1 + rnd.nextInt(30);
		int valueRange = 1 + itemCount / 2;
		List<List<Object>> combos = new ArrayList<List<Object>>();
		Set<String> seen = new HashSet<String>();
		for (int i = 0; i < itemCount; i++) {
			List<Object> combo = new ArrayList<Object>();
			StringBuilder key = new StringBuilder();
			for (int d = 0; d < categCount; d++) {
				Object value = categoryValue(mode, 1 + rnd.nextInt(valueRange));
				if (mode == 5 && rnd.nextInt(6) == 0) {
					value = null;
				}
				combo.add(value);
				key.append(value == null ? "null" : value.toString()).append('|');
			}
			// 重复的类别组合保留一部分(旋转结果会同时写入多个列位)
			if (seen.add(key.toString()) || rnd.nextInt(4) == 0) {
				combos.add(combo);
			}
		}
		// 首个类别为 null 时原实现探测维度会空指针(新实现按一维处理)，这类数据由专门的用例覆盖，
		// 随机数据里保证首个类别非 null
		if (combos.get(0).get(0) == null) {
			boolean swapped = false;
			for (int i = 1; i < combos.size(); i++) {
				if (combos.get(i).get(0) != null) {
					List<Object> first = combos.get(0);
					combos.set(0, combos.get(i));
					combos.set(i, first);
					swapped = true;
					break;
				}
			}
			if (!swapped) {
				combos.get(0).set(0, categoryValue(mode, 1));
			}
		}
		if (pc.twoDimension) {
			// categorys 的每个元素是一个维度的取值；显式给出 categoryCol 时，比对列 k 对应 categorys.get(categoryCol[k])
			for (int d = 0; d < categCount; d++) {
				pc.categories.add(null);
			}
			List<Integer> order = new ArrayList<Integer>();
			for (int d = 0; d < categCount; d++) {
				order.add(d);
			}
			if (rnd.nextBoolean()) {
				Collections.shuffle(order, rnd);
				pc.categoryCol = new Integer[categCount];
				for (int d = 0; d < categCount; d++) {
					pc.categoryCol[d] = order.get(d);
				}
			}
			for (int d = 0; d < categCount; d++) {
				List<Object> dimension = new ArrayList<Object>();
				for (List<Object> combo : combos) {
					dimension.add(combo.get(d));
				}
				pc.categories.set(order.get(d), dimension);
			}
		} else {
			for (List<Object> combo : combos) {
				pc.categories.add(combo.get(0));
			}
		}
		// 数据行
		int groupCount = 1 + rnd.nextInt(12);
		boolean nullPk = rnd.nextInt(6) == 0;
		boolean pkIsNumber = rnd.nextBoolean();
		for (int g = 0; g < groupCount; g++) {
			int picks = 1 + rnd.nextInt(combos.size());
			for (int p = 0; p < picks; p++) {
				List<Object> combo = combos.get(rnd.nextInt(combos.size()));
				List<Object> row = new ArrayList<Object>(Collections.nCopies(width, null));
				for (int c = 0; c < pkCount; c++) {
					Object pkValue = (c == 0) ? (pkIsNumber ? Integer.valueOf(g) : "g" + g)
							: "p" + rnd.nextInt(3);
					row.set(blockStart[0] + c, (nullPk && rnd.nextInt(10) == 0) ? null : pkValue);
				}
				for (int d = 0; d < categCount; d++) {
					row.set(blockStart[1] + d, dataValue(mode, combo.get(d), rnd));
				}
				for (int t = 0; t < rotateWith; t++) {
					row.set(blockStart[2] + t, cellValue(rnd));
				}
				for (int e = 0; e < extCount; e++) {
					row.set(blockStart[3] + e, "e" + e);
				}
				pc.data.add(row);
			}
		}
		// 多数情况下同一主键的行连续，少数整体打乱(同一主键被拆成多段时每段单独输出一行)
		if (rnd.nextInt(4) == 0) {
			Collections.shuffle(pc.data, rnd);
		}
		switch (rnd.nextInt(3)) {
		case 1:
			pc.defaultValue = Integer.valueOf(0);
			break;
		case 2:
			pc.defaultValue = "-";
			break;
		default:
			break;
		}
		return pc;
	}

	private static Outcome runPivot(boolean reference, PivotCase pc) {
		List<List<Object>> data = deepCopyRows(pc.data);
		List<Object> categories = new ArrayList<Object>();
		for (Object category : pc.categories) {
			categories.add(category instanceof List ? new ArrayList<Object>((List<Object>) category) : category);
		}
		Integer[] categoryCol = pc.categoryCol;
		Integer[] pkColumns = pc.pkColumns;
		Integer[] categCompareCol = pc.categCompareCol;
		int startCol = pc.startCol;
		int endCol = pc.endCol;
		Object defaultValue = pc.defaultValue;
		return attempt(() -> reference
				? refPivot(data, categories, categoryCol, pkColumns, categCompareCol, startCol, endCol, defaultValue)
				: CollectionUtil.pivotList(data, categories, categoryCol, pkColumns, categCompareCol, startCol, endCol,
						defaultValue));
	}

	@Test
	public void pivotMatchesPreChangeAlgorithmOnRandomData() {
		int[] modes = new int[8];
		int errors = 0;
		int multiCategory = 0;
		for (int seed = 0; seed < PIVOT_CASES; seed++) {
			PivotCase pc = generatePivot(new Random(seed * 65537L + 5));
			modes[pc.mode]++;
			if (pc.twoDimension) {
				multiCategory++;
			}
			Outcome actual = runPivot(false, pc);
			Outcome expected = runPivot(true, pc);
			String context = "pivot seed=" + seed + ", mode=" + pc.mode;
			assertSameError(actual.error, expected.error, context);
			if (expected.error != null) {
				errors++;
				continue;
			}
			assertDeepEqual(expected.value, actual.value, context);
		}
		assertEquals(0, errors, "抛异常的用例数");
		assertTrue(multiCategory > 600, "多类别列的用例数=" + multiCategory);
		System.out.println("[recheck] pivot: " + PIVOT_CASES + " cases, modes=" + Arrays.toString(modes)
				+ ", multiCategory=" + multiCategory);
	}

	private static PivotCase simplePivot(List<List<Object>> data, List<Object> categories) {
		PivotCase pc = new PivotCase();
		pc.data = data;
		pc.categories = categories;
		pc.pkColumns = new Integer[] { 0 };
		pc.categCompareCol = new Integer[] { 1 };
		pc.startCol = 2;
		pc.endCol = 2;
		return pc;
	}

	@Test
	public void emptyCategoryListThrowsExplainedErrorInsteadOfIndexOutOfBounds() {
		List<List<Object>> data = new ArrayList<List<Object>>();
		data.add(new ArrayList<Object>(Arrays.asList((Object) "a", 1, 10)));
		List<Object> categories = new ArrayList<Object>();
		Throwable legacy = runPivot(true, simplePivot(data, categories)).error;
		Throwable current = runPivot(false, simplePivot(data, categories)).error;
		assertTrue(legacy instanceof IndexOutOfBoundsException, "原实现: " + legacy);
		assertTrue(current instanceof IllegalArgumentException, "新实现: " + current);
	}

	@Test
	public void twoDimensionReferenceCountMismatchThrowsSameError() {
		PivotCase pc = new PivotCase();
		pc.data.add(new ArrayList<Object>(Arrays.asList((Object) "a", 1, 2, 10, 20)));
		pc.data.add(new ArrayList<Object>(Arrays.asList((Object) "a", 3, 4, 30, 40)));
		pc.categories.add(new ArrayList<Object>(Arrays.asList((Object) 1, 3)));
		pc.categories.add(new ArrayList<Object>(Arrays.asList((Object) 2, 4)));
		pc.pkColumns = new Integer[] { 0 };
		pc.categCompareCol = new Integer[] { 1, 2 };
		pc.startCol = 3;
		pc.endCol = 4;
		// 只给出了 1 个类别列序号，与比对的 2 列不一致
		pc.categoryCol = new Integer[] { 0 };
		Throwable legacy = runPivot(true, pc).error;
		Throwable current = runPivot(false, pc).error;
		assertTrue(legacy instanceof IllegalArgumentException, "原实现: " + legacy);
		assertTrue(current instanceof IllegalArgumentException, "新实现: " + current);
		assertEquals(legacy.getMessage(), current.getMessage());
	}

	@Test
	public void flatCategoryListWithNullFirstElementIsOneDimension() {
		// 首个类别为 null：原实现在探测维度时空指针；新实现按一维类别处理，null 类别与数据中的 null 对应
		List<List<Object>> data = new ArrayList<List<Object>>();
		data.add(new ArrayList<Object>(Arrays.asList((Object) "a", null, 10)));
		data.add(new ArrayList<Object>(Arrays.asList((Object) "b", "x", 20)));
		List<Object> categories = new ArrayList<Object>();
		categories.add(null);
		categories.add("x");
		Outcome current = runPivot(false, simplePivot(data, categories));
		assertNull(current.error, String.valueOf(current.error));
		// 两行主键不同，各输出一行：[主键, null类别对应的值, x类别对应的值]
		List<List<Object>> expected = new ArrayList<List<Object>>();
		expected.add(new ArrayList<Object>(Arrays.asList((Object) "a", 10, null)));
		expected.add(new ArrayList<Object>(Arrays.asList((Object) "b", null, 20)));
		assertDeepEqual(expected, current.value, "null-first-category");
		// 原实现在同样的数据上抛空指针
		Throwable legacy = runPivot(true, simplePivot(data, categories)).error;
		assertTrue(legacy instanceof NullPointerException, "原实现: " + legacy);
	}

	// ======================= in 参数判重 =======================

	/** 优化之前的 clearRepeat：判重键是 ",{值}" 依次拼接的字符串 */
	private static List<Object[]> refClearRepeat(List<Object[]> inArgsList) {
		if (inArgsList == null || inArgsList.isEmpty() || inArgsList.get(0).length < 2) {
			return inArgsList;
		}
		int size = inArgsList.size();
		List<List> middleList = new ArrayList<List>();
		List<Object[]> result = new ArrayList<Object[]>();
		for (int i = 0; i < size; i++) {
			middleList.add(new ArrayList());
		}
		HashSet<String> notRepeatSet = new HashSet<String>();
		String key;
		int loopSize = inArgsList.get(0).length;
		for (int i = 0; i < loopSize; i++) {
			key = "";
			for (int j = 0; j < size; j++) {
				key = key + ",{" + inArgsList.get(j)[i] + "}";
			}
			if (!notRepeatSet.contains(key)) {
				notRepeatSet.add(key);
				for (int j = 0; j < size; j++) {
					middleList.get(j).add(inArgsList.get(j)[i]);
				}
			}
		}
		for (int i = 0; i < size; i++) {
			result.add(middleList.get(i).toArray());
		}
		return result;
	}

	private static Outcome runClearRepeat(boolean reference, List<Object[]> source) {
		List<Object[]> copy = new ArrayList<Object[]>();
		for (Object[] values : source) {
			copy.add(values.clone());
		}
		return attempt(() -> reference ? refClearRepeat(copy) : CollectionUtil.clearRepeat(copy));
	}

	@Test
	public void clearRepeatMatchesPreChangeAlgorithmForOrdinaryValues() {
		int removed = 0;
		int earlyReturns = 0;
		for (int seed = 0; seed < REPEAT_CASES; seed++) {
			Random rnd = new Random(seed * 8191L + 3);
			int size = rnd.nextInt(5);
			int loopSize = rnd.nextInt(9);
			List<Object[]> source = new ArrayList<Object[]>();
			for (int j = 0; j < size; j++) {
				Object[] values = new Object[loopSize];
				for (int i = 0; i < loopSize; i++) {
					// 取值不含 "},{"
					values[i] = rnd.nextInt(10) < 8 ? Integer.valueOf(rnd.nextInt(4))
							: (rnd.nextBoolean() ? null : "s" + rnd.nextInt(3));
				}
				source.add(values);
			}
			Outcome actual = runClearRepeat(false, source);
			Outcome expected = runClearRepeat(true, source);
			String context = "clearRepeat seed=" + seed;
			assertSameError(actual.error, expected.error, context);
			if (expected.error != null) {
				continue;
			}
			assertDeepEqual(rowsOf(expected.value), rowsOf(actual.value), context);
			if (size == 0 || loopSize < 2) {
				earlyReturns++;
			} else if (((List<?>) actual.value).get(0) instanceof Object[]
					&& ((Object[]) ((List<?>) actual.value).get(0)).length < loopSize) {
				removed++;
			}
		}
		assertTrue(removed > 500, "发生去重的用例数=" + removed);
		assertTrue(earlyReturns > 200, "不足 2 个元素直接返回的用例数=" + earlyReturns);
	}

	@Test
	public void clearRepeatKeepsTuplesWhoseValuesContainSeparatorLookalike() {
		// 两个不同的参数组合原先会拼出相同的键 ",{a},{b},{c}"，被误判为重复
		List<Object[]> source = new ArrayList<Object[]>();
		source.add(new Object[] { "a},{b", "a" });
		source.add(new Object[] { "c", "b},{c" });
		List<Object[]> current = CollectionUtil.clearRepeat(source);
		assertEquals(2, current.get(0).length);
		assertEquals("a},{b", current.get(0)[0]);
		assertEquals("a", current.get(0)[1]);
		// 原实现在同样的数据上只剩一组
		List<Object[]> copy = new ArrayList<Object[]>();
		copy.add(new Object[] { "a},{b", "a" });
		copy.add(new Object[] { "c", "b},{c" });
		assertEquals(1, refClearRepeat(copy).get(0).length);
	}

	@Test
	public void clearRepeatStillComparesByText() {
		// inArgsList 是"每个 in 参数一个取值数组"，一组参数取各数组的同下标元素。
		// null 与字符串 "null" 文本相同，新旧实现都视为重复
		List<Object[]> source = new ArrayList<Object[]>();
		source.add(new Object[] { null, "null", "x" });
		source.add(new Object[] { 1L, "1", 2 });
		List<Object[]> current = CollectionUtil.clearRepeat(source);
		assertEquals(2, current.get(0).length);
		assertNull(current.get(0)[0]);
		assertEquals(1L, current.get(1)[0]);
		assertEquals("x", current.get(0)[1]);
		assertEquals(2, current.get(1)[1]);
		assertDeepEqual(rowsOf(CollectionUtil.clearRepeat(new ArrayList<Object[]>(source))),
				rowsOf(refClearRepeat(new ArrayList<Object[]>(source))), "clearRepeat-text");
	}

	// ======================= 类别提取 =======================

	/** 优化之前的 extractCategory：判重键是各列文本直接拼成的字符串。去重之后的排序、行列转换没有改动，借用现有私有方法 */
	private static List refExtractCategory(List items, Integer[] categoryCols) throws Exception {
		Method sortList = privateMethod("sortList", List.class, int.class, int.class, int.class, boolean.class);
		Method sortGroupList = privateMethod("sortGroupList", List.class, int.class, int.class, boolean.class);
		List categoryList = new ArrayList();
		Set<String> identitySet = new HashSet<String>();
		for (int i = 0, size = items.size(); i < size; i++) {
			List row = (List) items.get(i);
			List categoryRow = new ArrayList();
			String tmpStr = "";
			for (int j = 0; j < categoryCols.length; j++) {
				Object obj = row.get(categoryCols[j]);
				categoryRow.add(obj);
				tmpStr = tmpStr.concat(obj == null ? "null" : obj.toString());
			}
			if (!identitySet.contains(tmpStr)) {
				categoryList.add(categoryRow);
				identitySet.add(tmpStr);
			}
		}
		if (categoryCols.length > 1) {
			categoryList = (List) sortList.invoke(null, categoryList, 0, 0, categoryList.size() - 1, true);
			for (int i = 1; i < categoryCols.length; i++) {
				categoryList = (List) sortGroupList.invoke(null, categoryList, i - 1, i, true);
			}
		}
		return CollectionUtil.convertColToRow(categoryList, null);
	}

	private static Outcome runExtractCategory(boolean reference, List<List<Object>> items, Integer[] categoryCols) {
		List<List<Object>> copy = deepCopyRows(items);
		return attempt(() -> {
			if (reference) {
				return refExtractCategory(copy, categoryCols);
			}
			return privateMethod("extractCategory", List.class, Integer[].class).invoke(null, copy, categoryCols);
		});
	}

	/** 把"按列"给出的类别还原成"逐列文本"组成的组合集合 */
	private static Set<String> combosOf(Object columnWise, int columnCount) {
		Set<String> combos = new HashSet<String>();
		List<?> columns = (List<?>) columnWise;
		int size = columns.isEmpty() ? 0 : ((List<?>) columns.get(0)).size();
		for (int j = 0; j < size; j++) {
			StringBuilder key = new StringBuilder();
			for (int d = 0; d < columnCount; d++) {
				Object value = ((List<?>) columns.get(d)).get(j);
				key.append(value == null ? "null" : value.toString()).append('|');
			}
			combos.add(key.toString());
		}
		return combos;
	}

	@Test
	public void extractCategoryDedupsByWholeCombination() throws Exception {
		// 取值池里的 1、12、23、3、123 很容易拼出相同文本，如 (1,23) 与 (12,3) 都是 "123"
		Integer[] pool = { 1, 2, 3, 12, 23, 31, 123 };
		int collisionCases = 0;
		int sameAsLegacy = 0;
		for (int seed = 0; seed < 2000; seed++) {
			Random rnd = new Random(seed * 3571L + 11);
			int columnCount = 1 + rnd.nextInt(3);
			int rowCount = rnd.nextInt(25);
			List<List<Object>> items = new ArrayList<List<Object>>();
			Set<String> expectedCombos = new HashSet<String>();
			Set<String> concatTexts = new HashSet<String>();
			for (int r = 0; r < rowCount; r++) {
				// 类别列放在一行的中间，前后各放一列无关数据
				List<Object> row = new ArrayList<Object>();
				row.add("x");
				StringBuilder comboKey = new StringBuilder();
				StringBuilder concat = new StringBuilder();
				for (int d = 0; d < columnCount; d++) {
					Object value = rnd.nextInt(12) == 0 ? null : pool[rnd.nextInt(pool.length)];
					row.add(value);
					comboKey.append(value == null ? "null" : value.toString()).append('|');
					concat.append(value == null ? "null" : value.toString());
				}
				row.add(Integer.valueOf(r));
				items.add(row);
				expectedCombos.add(comboKey.toString());
				concatTexts.add(concat.toString());
			}
			Integer[] categoryCols = new Integer[columnCount];
			for (int d = 0; d < columnCount; d++) {
				categoryCols[d] = 1 + d;
			}
			String context = "extractCategory seed=" + seed;
			Outcome current = runExtractCategory(false, items, categoryCols);
			assertNull(current.error, context + ": " + current.error);
			// 每个不同的类别组合都要保留下来
			assertEquals(expectedCombos, combosOf(current.value, columnCount), context + ": 类别组合集合");
			Outcome legacy = runExtractCategory(true, items, categoryCols);
			if (expectedCombos.size() == concatTexts.size()) {
				// 不存在拼接文本相同的类别组合：与原实现完全一致
				sameAsLegacy++;
				assertSameError(current.error, legacy.error, context);
				if (legacy.error == null) {
					assertDeepEqual(legacy.value, current.value, context);
				}
			} else {
				// 原实现把文本相同的组合并成了一个类别，行转列因此少列
				collisionCases++;
				assertNull(legacy.error, context + ": 原实现 " + legacy.error);
				assertEquals(concatTexts.size(), ((List<?>) ((List<?>) legacy.value).get(0)).size(),
						context + ": 原实现的类别数");
			}
		}
		assertTrue(collisionCases > 100, "存在拼接种类的用例数=" + collisionCases);
		assertTrue(sameAsLegacy > 500, "不存在拼接种类的用例数=" + sameAsLegacy);
	}

	@Test
	public void extractCategoryKeepsCombosThatOnlyCollideAsConcatenatedText() throws Exception {
		// (1,23) 与 (12,3) 原先都拼成 "123"，被当成同一个类别，行转列会少一列
		List<List<Object>> items = new ArrayList<List<Object>>();
		items.add(new ArrayList<Object>(Arrays.asList((Object) 1, 23, "a")));
		items.add(new ArrayList<Object>(Arrays.asList((Object) 12, 3, "b")));
		Integer[] categoryCols = { 0, 1 };
		Outcome current = runExtractCategory(false, items, categoryCols);
		assertNull(current.error, String.valueOf(current.error));
		// 结果已转成按列：两列各有 2 个取值
		assertEquals(new HashSet<String>(Arrays.asList("1|23|", "12|3|")), combosOf(current.value, 2));
		// 原实现在同样的数据上只剩一个类别
		List<?> legacyColumns = (List<?>) runExtractCategory(true, items, categoryCols).value;
		assertEquals(1, ((List<?>) legacyColumns.get(0)).size());
	}

	// ======================= 分组排序 / 分组计算 =======================

	/** 优化之前的 groupSort：分组列用 tempObj[j].equals(compareValue[j]) 判断，取值为 null 时空指针 */
	private static void refGroupSort(List<List> dataSet, Integer[] groupIndexes, int sortIndex, boolean desc) {
		if (dataSet == null || dataSet.size() < 2) {
			return;
		}
		int length = dataSet.size();
		int dataType = CollectionUtil.getSortDataType(dataSet, sortIndex);
		int start = 0;
		int end;
		int groupSize = groupIndexes.length;
		Object[] compareValue = new Object[groupSize];
		Object[] tempObj = new Object[groupSize];
		List row;
		boolean isEqual;
		for (int i = 0; i < length; i++) {
			row = dataSet.get(i);
			isEqual = true;
			for (int j = 0; j < groupSize; j++) {
				tempObj[j] = row.get(groupIndexes[j]);
				if (i == 0) {
					compareValue[j] = tempObj[j];
				}
				isEqual = isEqual && (tempObj[j].equals(compareValue[j]));
			}
			if (!isEqual) {
				end = i - 1;
				CollectionUtil.sortList(dataSet, sortIndex, dataType, start, end, !desc);
				start = i;
				for (int j = 0; j < groupSize; j++) {
					compareValue[j] = tempObj[j];
				}
			}
			if (i == length - 1) {
				CollectionUtil.sortList(dataSet, sortIndex, dataType, start, i, !desc);
			}
		}
	}

	private static BigDecimal refToDecimal(Object tmpCellValue) {
		if (tmpCellValue == null) {
			return BigDecimal.ZERO;
		} else if (tmpCellValue instanceof BigDecimal) {
			return (BigDecimal) tmpCellValue;
		} else if (tmpCellValue instanceof Number) {
			return new BigDecimal(tmpCellValue.toString());
		}
		return new BigDecimal(
				tmpCellValue.toString().replaceAll(",", "").replaceFirst(CollectionUtil.ILLEGAL_NUM_REGEX, ""));
	}

	/** 优化之前的 groupCalculate */
	private static void refGroupCalculate(List<List> dataSet, Integer[] groupIndexes, int calcuateIndex,
			boolean isSum) {
		int groupSize = groupIndexes.length;
		int length = dataSet.size();
		Object[] compareValue = new Object[groupSize];
		Object[] tempObj = new Object[groupSize];
		List row;
		boolean isEqual;
		int start = 0;
		int end = 0;
		BigDecimal calculateValue = new BigDecimal(0);
		Object tmpCellValue;
		BigDecimal cellValue;
		for (int i = 0; i < length; i++) {
			row = dataSet.get(i);
			tmpCellValue = row.get(calcuateIndex);
			cellValue = refToDecimal(tmpCellValue);
			isEqual = true;
			for (int j = 0; j < groupSize; j++) {
				tempObj[j] = row.get(groupIndexes[j]);
				if (i == 0) {
					compareValue[j] = row.get(groupIndexes[j]);
				}
				isEqual = isEqual && (tempObj[j].equals(compareValue[j]));
			}
			if (isEqual) {
				calculateValue = calculateValue.add(cellValue);
			} else {
				end = i - 1;
				if (!isSum && end > start) {
					calculateValue = calculateValue.divide(BigDecimal.valueOf(end - start + 1L), 4,
							RoundingMode.HALF_DOWN);
				}
				for (int k = start; k <= end; k++) {
					dataSet.get(k).add(calculateValue);
				}
				for (int j = 0; j < groupSize; j++) {
					compareValue[j] = tempObj[j];
				}
				calculateValue = new BigDecimal(0).add(cellValue);
				start = i;
			}
			if (i == length - 1) {
				end = i;
				if (!isSum && end > start) {
					calculateValue = calculateValue.divide(BigDecimal.valueOf(end - start + 1L), 4,
							RoundingMode.HALF_DOWN);
				}
				for (int k = start; k <= end; k++) {
					dataSet.get(k).add(calculateValue);
				}
			}
		}
	}

	private static Object calcValue(Random rnd) {
		switch (rnd.nextInt(6)) {
		case 0:
			return null;
		case 1:
			return BigDecimal.valueOf(rnd.nextInt(100000), rnd.nextInt(3));
		case 2:
			return Double.valueOf(rnd.nextInt(500) / 8.0d);
		case 3:
			return (rnd.nextInt(8) == 0 ? "-" : "") + (1000 + rnd.nextInt(9000)) + "," + (100 + rnd.nextInt(900));
		default:
			return Integer.valueOf(rnd.nextInt(1000));
		}
	}

	@Test
	public void groupSortAndCalculateMatchPreChangeAlgorithmForNonNullGroupValues() {
		for (int seed = 0; seed < GROUP_CASES; seed++) {
			Random rnd = new Random(seed * 15485863L + 17);
			int groupSize = 1 + rnd.nextInt(2);
			boolean groupIsText = rnd.nextBoolean();
			// 分组值相同的行连续排列，夹杂单行分组
			int rowCount = rnd.nextInt(25);
			List<List<Object>> source = new ArrayList<List<Object>>();
			int currentGroup = -1;
			int runLeft = 0;
			for (int i = 0; i < rowCount; i++) {
				if (runLeft == 0) {
					currentGroup = rnd.nextInt(6);
					runLeft = 1 + rnd.nextInt(5);
				}
				runLeft--;
				List<Object> row = new ArrayList<Object>();
				for (int g = 0; g < groupSize; g++) {
					row.add(groupIsText ? (Object) ("g" + currentGroup) : Long.valueOf(currentGroup));
				}
				row.add(rnd.nextInt(5) == 0 ? null : Integer.valueOf(rnd.nextInt(20)));
				row.add(calcValue(rnd));
				source.add(row);
			}
			Integer[] groupIndexes = new Integer[groupSize];
			for (int g = 0; g < groupSize; g++) {
				groupIndexes[g] = g;
			}
			int sortIndex = groupSize;
			int calcIndex = groupSize + 1;
			boolean desc = rnd.nextBoolean();
			boolean isSum = rnd.nextBoolean();

			List current = deepCopyRows(source);
			List legacy = deepCopyRows(source);
			String context = "group seed=" + seed;
			Outcome currentOutcome = attempt(() -> {
				CollectionUtil.groupSort(current, groupIndexes, sortIndex, desc);
				CollectionUtil.groupCalculate(current, groupIndexes, calcIndex, isSum);
				return null;
			});
			Outcome legacyOutcome = attempt(() -> {
				refGroupSort(legacy, groupIndexes, sortIndex, desc);
				refGroupCalculate(legacy, groupIndexes, calcIndex, isSum);
				return null;
			});
			assertSameError(currentOutcome.error, legacyOutcome.error, context);
			if (legacyOutcome.error != null) {
				continue;
			}
			assertDeepEqual(legacy, current, context);
		}
	}

	@Test
	public void groupCalculateTreatsNullGroupValuesAsOneGroup() {
		// 分组列为 null 的行属于同一组：原实现空指针
		List dataSet = new ArrayList<List<Object>>();
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) null, 1)));
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) null, 2)));
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) "a", 3)));
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) null, 4)));
		CollectionUtil.groupCalculate(dataSet, new Integer[] { 0 }, 1, true);
		List<List<Object>> expected = new ArrayList<List<Object>>();
		expected.add(new ArrayList<Object>(Arrays.asList((Object) null, 1, BigDecimal.valueOf(3))));
		expected.add(new ArrayList<Object>(Arrays.asList((Object) null, 2, BigDecimal.valueOf(3))));
		expected.add(new ArrayList<Object>(Arrays.asList((Object) "a", 3, BigDecimal.valueOf(3))));
		expected.add(new ArrayList<Object>(Arrays.asList((Object) null, 4, BigDecimal.valueOf(4))));
		assertDeepEqual(expected, dataSet, "groupCalculate-null-group");

		List legacyCopy = new ArrayList<List<Object>>();
		legacyCopy.add(new ArrayList<Object>(Arrays.asList((Object) null, 1)));
		legacyCopy.add(new ArrayList<Object>(Arrays.asList((Object) null, 2)));
		Outcome legacy = attempt(() -> {
			refGroupCalculate(legacyCopy, new Integer[] { 0 }, 1, true);
			return null;
		});
		assertTrue(legacy.error instanceof NullPointerException, "原实现: " + legacy.error);
	}

	@Test
	public void groupSortTreatsNullGroupValuesAsOneGroup() {
		List dataSet = new ArrayList<List<Object>>();
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) null, 3)));
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) null, 1)));
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) "a", 9)));
		dataSet.add(new ArrayList<Object>(Arrays.asList((Object) null, 2)));
		CollectionUtil.groupSort(dataSet, new Integer[] { 0 }, 1, false);
		// 前两行是一组(升序)，"a" 单独一组，最后一行又是一组
		List<List<Object>> expected = new ArrayList<List<Object>>();
		expected.add(new ArrayList<Object>(Arrays.asList((Object) null, 1)));
		expected.add(new ArrayList<Object>(Arrays.asList((Object) null, 3)));
		expected.add(new ArrayList<Object>(Arrays.asList((Object) "a", 9)));
		expected.add(new ArrayList<Object>(Arrays.asList((Object) null, 2)));
		assertDeepEqual(expected, dataSet, "groupSort-null-group");

		List legacyCopy = new ArrayList<List<Object>>();
		legacyCopy.add(new ArrayList<Object>(Arrays.asList((Object) null, 3)));
		legacyCopy.add(new ArrayList<Object>(Arrays.asList((Object) null, 1)));
		legacyCopy.add(new ArrayList<Object>(Arrays.asList((Object) "a", 9)));
		Outcome legacy = attempt(() -> {
			refGroupSort(legacyCopy, new Integer[] { 0 }, 1, false);
			return null;
		});
		assertTrue(legacy.error instanceof NullPointerException, "原实现: " + legacy.error);
	}
}
