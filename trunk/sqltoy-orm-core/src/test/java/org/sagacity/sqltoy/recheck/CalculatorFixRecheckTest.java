package org.sagacity.sqltoy.recheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.ReverseModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.plugins.calculator.ColsChainRelative;
import org.sagacity.sqltoy.plugins.calculator.ReverseList;
import org.sagacity.sqltoy.plugins.calculator.RowsChainRelative;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.NumberUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 2026-11-24 结果计算器三处缺陷修复的独立复核(ReverseList 反转交换次数、RowsChainRelative 循环边界、
 *              三处负数下标)。这些修改会改变计算结果，这里用"按定义直接计算"的期望值逐项核对，
 *              并确认多分组等原有正确场景的结果没有变化。
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CalculatorFixRecheckTest {

	// ======================= ReverseList =======================

	@Test
	public void reverseMatchesSubListReverse() {
		int checked = 0;
		for (int size = 2; size <= 9; size++) {
			// 起止行取值：默认、负数、合法范围、越界
			List<Integer> bounds = new ArrayList<Integer>();
			bounds.add(null);
			for (int value = -3; value <= size + 1; value++) {
				bounds.add(Integer.valueOf(value));
			}
			for (Integer startRow : bounds) {
				for (Integer endRow : bounds) {
					List<Integer> rows = new ArrayList<Integer>();
					for (int i = 0; i < size; i++) {
						rows.add(Integer.valueOf(i));
					}
					// 期望值：把 [start, end] 当作子区间整体反转。模型注释约定负数表示倒数第几行
					List<Integer> expected = new ArrayList<Integer>(rows);
					int start = startRow == null ? 0 : startRow.intValue();
					int end = endRow == null ? size - 1 : endRow.intValue();
					if (end < 0) {
						end = size + end;
					}
					if (end > size - 1) {
						end = size - 1;
					}
					if (start < 0) {
						start = 0;
					}
					if (start <= size - 1 && start <= end) {
						Collections.reverse(expected.subList(start, end + 1));
					}
					ReverseModel model = new ReverseModel();
					model.setStartRow(startRow);
					model.setEndRow(endRow);
					ReverseList.process(model, null, rows);
					assertEquals(expected, rows, "size=" + size + ", start-row=" + startRow + ", end-row=" + endRow);
					checked++;
				}
			}
		}
		assertTrue(checked >= 1000, "核对的组合数=" + checked);
	}

	@Test
	public void twoRowsAreReallyReversed() {
		// 原实现的交换次数是 (end-start)/2，2 行时为 0，等于没有反转
		List<Integer> rows = new ArrayList<Integer>(Arrays.asList(1, 2));
		ReverseList.process(new ReverseModel(), null, rows);
		assertEquals(Arrays.asList(2, 1), rows);
		// 4 行时原实现只交换最外面一对
		List<Integer> four = new ArrayList<Integer>(Arrays.asList(1, 2, 3, 4));
		ReverseList.process(new ReverseModel(), null, four);
		assertEquals(Arrays.asList(4, 3, 2, 1), four);
	}

	// ======================= RowsChainRelative：按定义计算 =======================

	/** 两个数相除：被除数为 0 时按零/默认值处理 */
	private static Object ratio(BigDecimal div, BigDecimal dived, boolean reduceOne, int multiply, int radixSize,
			RoundingMode roundingMode, String defaultValue) {
		if (dived.compareTo(BigDecimal.ZERO) == 0) {
			return div.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : defaultValue;
		}
		BigDecimal numerator = reduceOne ? div.subtract(dived) : div;
		return numerator.multiply(BigDecimal.valueOf(multiply)).divide(dived, radixSize, roundingMode);
	}

	private static BigDecimal numberOf(Object cell) {
		return cell == null ? BigDecimal.ZERO : new BigDecimal(cell.toString());
	}

	private static void assertCellEquals(Object expected, Object actual, String context) {
		if (expected instanceof BigDecimal && actual instanceof BigDecimal) {
			assertTrue(((BigDecimal) expected).compareTo((BigDecimal) actual) == 0,
					context + ": 期望 " + expected + "，实际 " + actual);
		} else {
			assertEquals(expected, actual, context);
		}
	}

	/**
	 * 单分组(groupSize=1)环比，按定义核对：正序时每行与上一行比较(区间首行没有上一行，不动)；
	 * 逆序时每行与下一行比较(区间末行没有下一行，不动)；区间之外的行不动
	 */
	private static void checkSingleGroup(boolean reverse, boolean insert, boolean reduceOne, Integer startRow,
			Integer endRow) {
		int size = 7;
		// 数值里刻意放入 0 和 null，覆盖除零、被除数为零的分支
		Integer[] values = { 100, 150, 0, 75, null, 250, 200 };
		String defaultValue = "-";
		List<List<Object>> rows = new ArrayList<List<Object>>();
		for (int i = 0; i < size; i++) {
			// 插入模式下环比列由计算器补在数值列后面；更新模式下 sql 里已经预留了这一列
			rows.add(insert ? new ArrayList<Object>(Arrays.asList((Object) ("r" + i), values[i]))
					: new ArrayList<Object>(Arrays.asList((Object) ("r" + i), values[i], null)));
		}
		RowsChainRelativeModel model = new RowsChainRelativeModel();
		model.setRelativeColumns(new String[] { "1" });
		model.setReverse(reverse);
		model.setInsert(insert);
		model.setReduceOne(reduceOne);
		model.setMultiply(100);
		model.setRadixSize(2);
		model.setDefaultValue(defaultValue);
		model.setStartRow(startRow);
		model.setEndRow(endRow);
		RowsChainRelative.process(model, null, rows);

		int start = startRow == null ? 0 : startRow.intValue();
		int end = endRow == null ? size - 1 : endRow.intValue();
		// 模型注释约定：负数表示倒数第几行
		if (end < 0) {
			end = size + end;
		}
		if (end > size - 1) {
			end = size - 1;
		}
		if (start < 0) {
			start = 0;
		}
		String context = "reverse=" + reverse + ", insert=" + insert + ", reduceOne=" + reduceOne + ", start-row="
				+ startRow + ", end-row=" + endRow;
		for (int i = 0; i < size; i++) {
			List<Object> row = rows.get(i);
			assertEquals(3, row.size(), context + ": 第" + i + "行宽度");
			Integer partner = null;
			if (reverse) {
				// 逆序：区间内除末行外，每行与下一行比较
				if (i >= start && i < end) {
					partner = i + 1;
				}
			} else {
				// 正序：区间内除首行外，每行与上一行比较
				if (i > start && i <= end) {
					partner = i - 1;
				}
			}
			if (partner == null) {
				assertCellEquals(insert ? defaultValue : null, row.get(2), context + ": 第" + i + "行不应计算");
			} else {
				Object expected = ratio(numberOf(values[i]), numberOf(values[partner]), reduceOne, 100, 2,
						RoundingMode.HALF_UP, defaultValue);
				assertCellEquals(expected, row.get(2), context + ": 第" + i + "行(与第" + partner + "行比较)");
			}
		}
	}

	@Test
	public void singleGroupRowsChainMatchesDefinition() {
		Integer[] bounds = { null, -1, -2, 0, 1, 2, 5, 6, 7, 9 };
		int checked = 0;
		for (boolean reverse : new boolean[] { true, false }) {
			for (boolean insert : new boolean[] { true, false }) {
				for (boolean reduceOne : new boolean[] { true, false }) {
					for (Integer startRow : bounds) {
						for (Integer endRow : bounds) {
							checkSingleGroup(reverse, insert, reduceOne, startRow, endRow);
							checked++;
						}
					}
				}
			}
		}
		assertEquals(800, checked);
	}

	@Test
	public void singleGroupEndpointRowsAreNowCalculated() {
		// 原实现的正序循环是 i < end、逆序循环是 i > start，单分组时分别漏掉末行和首行
		List<List<Object>> forward = new ArrayList<List<Object>>();
		forward.add(new ArrayList<Object>(Arrays.asList((Object) "03", 100)));
		forward.add(new ArrayList<Object>(Arrays.asList((Object) "04", 150)));
		forward.add(new ArrayList<Object>(Arrays.asList((Object) "05", 120)));
		RowsChainRelativeModel model = new RowsChainRelativeModel();
		model.setRelativeColumns(new String[] { "1" });
		model.setReverse(false);
		model.setMultiply(100);
		model.setRadixSize(2);
		RowsChainRelative.process(model, null, forward);
		assertNull(forward.get(0).get(2), "首行是基准");
		assertCellEquals(new BigDecimal("150.00"), forward.get(1).get(2), "第2行");
		assertCellEquals(new BigDecimal("80.00"), forward.get(2).get(2), "末行原先漏掉");

		List<List<Object>> backward = new ArrayList<List<Object>>();
		backward.add(new ArrayList<Object>(Arrays.asList((Object) "05", 120)));
		backward.add(new ArrayList<Object>(Arrays.asList((Object) "04", 150)));
		backward.add(new ArrayList<Object>(Arrays.asList((Object) "03", 100)));
		RowsChainRelativeModel reverseModel = new RowsChainRelativeModel();
		reverseModel.setRelativeColumns(new String[] { "1" });
		reverseModel.setMultiply(100);
		reverseModel.setRadixSize(2);
		RowsChainRelative.process(reverseModel, null, backward);
		assertCellEquals(new BigDecimal("80.00"), backward.get(0).get(2), "首行原先漏掉");
		assertCellEquals(new BigDecimal("150.00"), backward.get(1).get(2), "第2行");
		assertNull(backward.get(2).get(2), "末行是基准");
	}

	// ======================= RowsChainRelative：多分组与原实现对照 =======================

	/**
	 * 修改之前的 RowsChainRelative(循环边界为 i > start / i < end，负数行为 dataSize-1+end)。
	 * 中间的计算部分没有改动，这里整体转写，只用于对照
	 */
	private static void legacyRowsChainRelative(RowsChainRelativeModel rowsRelative, LabelIndexModel labelIndexMap,
			List result) {
		if (result == null || result.size() < 2) {
			return;
		}
		int dataSize = result.size();
		boolean isAppend = rowsRelative.isInsert();
		Integer[] realRelativeCols = new Integer[rowsRelative.getRelativeColumns().length];
		int start = rowsRelative.getStartRow() == null ? 0 : rowsRelative.getStartRow();
		int end = rowsRelative.getEndRow() == null ? dataSize - 1 : rowsRelative.getEndRow();
		if (end < 0) {
			end = dataSize - 1 + end;
		}
		if (end > dataSize - 1) {
			end = dataSize - 1;
		}
		int groupSize = 1;
		if (StringUtil.isNotBlank(rowsRelative.getGroupColumn())) {
			int groupColIndex;
			if (NumberUtil.isInteger(rowsRelative.getGroupColumn())) {
				groupColIndex = Integer.parseInt(rowsRelative.getGroupColumn());
			} else {
				groupColIndex = labelIndexMap.get(rowsRelative.getGroupColumn().toLowerCase(Locale.ROOT));
			}
			if (groupColIndex >= 0 && groupColIndex < ((List) result.get(0)).size()) {
				HashSet map = new HashSet();
				for (int i = start; i <= end; i++) {
					map.add(((List) result.get(i)).get(groupColIndex));
				}
				groupSize = map.size();
			}
		}
		Integer[] relativeIndexs = rowsRelative.getRelativeIndexs();
		if (relativeIndexs == null || relativeIndexs.length == 0) {
			relativeIndexs = new Integer[groupSize];
			for (int i = 0; i < groupSize; i++) {
				relativeIndexs[i] = i;
			}
		} else {
			CollectionUtil.sortArray(relativeIndexs, false);
		}
		int addIndex = 0;
		for (int i = 0; i < rowsRelative.getRelativeColumns().length; i++) {
			String relativeCol = rowsRelative.getRelativeColumns()[i].toLowerCase(Locale.ROOT);
			if (NumberUtil.isInteger(relativeCol)) {
				realRelativeCols[i] = Integer.parseInt(relativeCol) + addIndex;
			} else {
				realRelativeCols[i] = labelIndexMap.get(relativeCol) + addIndex;
			}
			if (isAppend) {
				addIndex++;
			}
		}
		String defaultValue = rowsRelative.getDefaultValue();
		if (isAppend) {
			for (int i = 0, n = result.size(); i < n; i++) {
				List rowList = (List) result.get(i);
				for (int j = 0; j < realRelativeCols.length; j++) {
					rowList.add(realRelativeCols[j] + 1, defaultValue);
				}
			}
		}
		int radixSize = rowsRelative.getRadixSize();
		boolean isIncrement = rowsRelative.isReduceOne();
		BigDecimal multiply = BigDecimal.valueOf(rowsRelative.getMultiply());
		RoundingMode roundingMode = (rowsRelative.getRoundingMode() == null) ? RoundingMode.HALF_UP
				: rowsRelative.getRoundingMode();
		if (rowsRelative.isReverse()) {
			// 原边界：i > start
			for (int i = end - groupSize; i > start; i = i - groupSize) {
				for (int j = 0; j < relativeIndexs.length; j++) {
					int index = relativeIndexs[j];
					List divRowList = (List) result.get(i - groupSize + index + 1);
					List divedRowList = (List) result.get(i + index + 1);
					for (int k = 0; k < realRelativeCols.length; k++) {
						int colIndex = realRelativeCols[k];
						BigDecimal divData = numberOf(divRowList.get(colIndex));
						BigDecimal divedData = numberOf(divedRowList.get(colIndex));
						if (divedData.compareTo(BigDecimal.ZERO) == 0) {
							boolean divIsZero = divData.compareTo(BigDecimal.ZERO) == 0;
							divRowList.set(colIndex + 1, divIsZero ? BigDecimal.ZERO : defaultValue);
						} else {
							BigDecimal value = isIncrement
									? divData.subtract(divedData).multiply(multiply).divide(divedData, radixSize,
											roundingMode)
									: divData.multiply(multiply).divide(divedData, radixSize, roundingMode);
							divRowList.set(colIndex + 1, value);
						}
					}
				}
			}
		} else {
			// 原边界：i < end
			for (int i = start + groupSize; i < end; i = i + groupSize) {
				for (int j = 0; j < relativeIndexs.length; j++) {
					int index = relativeIndexs[j];
					List divRowList = (List) result.get(i + index);
					List divedRowList = (List) result.get(i + index - groupSize);
					for (int k = 0; k < realRelativeCols.length; k++) {
						int colIndex = realRelativeCols[k];
						BigDecimal divData = numberOf(divRowList.get(colIndex));
						BigDecimal divedData = numberOf(divedRowList.get(colIndex));
						if (divedData.compareTo(BigDecimal.ZERO) == 0) {
							boolean divIsZero = divData.compareTo(BigDecimal.ZERO) == 0;
							divRowList.set(colIndex + 1, divIsZero ? BigDecimal.ZERO : defaultValue);
						} else {
							BigDecimal value = isIncrement
									? divData.subtract(divedData).multiply(multiply).divide(divedData, radixSize,
											roundingMode)
									: divData.multiply(multiply).divide(divedData, radixSize, roundingMode);
							divRowList.set(colIndex + 1, value);
						}
					}
				}
			}
		}
	}

	/**
	 * 多分组数据：每个周期有 groupSize 行(每个产品一行)，列依次为 月份、产品、数值1、数值2
	 *
	 * @param periods 周期数
	 */
	private static List<List<Object>> multiGroupRows(int groupSize, int periods, boolean newestFirst) {
		List<List<Object>> rows = new ArrayList<List<Object>>();
		for (int p = 0; p < periods; p++) {
			int period = newestFirst ? periods - 1 - p : p;
			for (int g = 0; g < groupSize; g++) {
				// 数值里放入 0 和 null
				int first = (period * 7 + g * 3) % 5 == 0 ? 0 : 100 + period * 30 + g;
				Object second = (period + g) % 4 == 0 ? null : Integer.valueOf(50 + period * 11 + g * 2);
				rows.add(new ArrayList<Object>(
							Arrays.asList((Object) ("m" + period), "p" + g, Integer.valueOf(first), second)));
			}
		}
		return rows;
	}

	/**
	 * 插入模式：两个数值列各自后面补一列环比。更新模式：只比较数值1，sql 里在它后面预留了一列环比
	 */
	private static RowsChainRelativeModel multiGroupModel(boolean reverse, boolean insert, boolean reduceOne) {
		RowsChainRelativeModel model = new RowsChainRelativeModel();
		model.setGroupColumn("1");
		model.setRelativeColumns(insert ? new String[] { "2", "3" } : new String[] { "2" });
		model.setReverse(reverse);
		model.setInsert(insert);
		model.setReduceOne(reduceOne);
		model.setMultiply(100);
		model.setRadixSize(2);
		model.setDefaultValue("-");
		return model;
	}

	/** 插入模式、只比较数值1这一个列(环比列补在它后面) */
	private static RowsChainRelativeModel singleRelativeModel(boolean reverse) {
		RowsChainRelativeModel model = multiGroupModel(reverse, true, false);
		model.setRelativeColumns(new String[] { "2" });
		return model;
	}

	private static String cellsOf(List<List<Object>> rows) {
		StringBuilder text = new StringBuilder();
		for (List<Object> row : rows) {
			for (Object cell : row) {
				text.append(cell instanceof BigDecimal ? ((BigDecimal) cell).toPlainString() : String.valueOf(cell))
						.append('|');
			}
			text.append('\n');
		}
		return text.toString();
	}

	private static List<List<Object>> copyOf(List<List<Object>> rows) {
		List<List<Object>> copy = new ArrayList<List<Object>>(rows.size());
		for (List<Object> row : rows) {
			copy.add(new ArrayList<Object>(row));
		}
		return copy;
	}

	@Test
	public void multiGroupRowsChainUnchangedWhenRangeIsAligned() {
		int compared = 0;
		for (int groupSize = 2; groupSize <= 4; groupSize++) {
			for (int periods = 1; periods <= 5; periods++) {
				for (boolean newestFirst : new boolean[] { true, false }) {
					for (boolean insert : new boolean[] { true, false }) {
						for (boolean reduceOne : new boolean[] { true, false }) {
							List<List<Object>> source = multiGroupRows(groupSize, periods, newestFirst);
							if (!insert) {
								for (List<Object> row : source) {
									row.add(3, "keep");
								}
							}
							List<List<Object>> current = copyOf(source);
							List<List<Object>> legacy = copyOf(source);
							RowsChainRelativeModel model = multiGroupModel(newestFirst, insert, reduceOne);
							String context = "groupSize=" + groupSize + ", periods=" + periods + ", newestFirst="
									+ newestFirst + ", insert=" + insert + ", reduceOne=" + reduceOne;
							// 区间行数是分组长度的整数倍：新旧实现的结果必须一致
							RowsChainRelative.process(model, null, current);
							legacyRowsChainRelative(model, null, legacy);
							assertEquals(cellsOf(legacy), cellsOf(current), context);
							compared++;
						}
					}
				}
			}
		}
		assertEquals(3 * 5 * 2 * 2 * 2, compared, "对照的组合数");
	}

	@Test
	public void multiGroupDefinitionHoldsForAlignedRange() {
		// 3 个产品 x 4 个月：每个产品自己的本月/上月(或本月/下月)
		int groupSize = 3;
		int periods = 4;
		for (boolean newestFirst : new boolean[] { true, false }) {
			List<List<Object>> source = multiGroupRows(groupSize, periods, newestFirst);
			int[][] first = new int[periods][groupSize];
			for (int p = 0; p < periods; p++) {
				int period = newestFirst ? periods - 1 - p : p;
				for (int g = 0; g < groupSize; g++) {
					first[period][g] = ((Integer) source.get(p * groupSize + g).get(2)).intValue();
				}
			}
			List<List<Object>> rows = copyOf(source);
			RowsChainRelative.process(multiGroupModel(newestFirst, true, false), null, rows);
			for (int p = 0; p < periods; p++) {
				int period = newestFirst ? periods - 1 - p : p;
				for (int g = 0; g < groupSize; g++) {
					// 插入后列依次为：月份、产品、数值1、环比1、数值2、环比2
					List<Object> row = rows.get(p * groupSize + g);
					assertEquals(6, row.size());
					// 月份下标从旧到新递增，上一月就是 period-1(逆序时它在集合里排在后面)
					int previousPeriod = period - 1;
					boolean hasPrevious = previousPeriod >= 0;
					// 没有上一周期的行保持默认值
					Object expected = "-";
					if (hasPrevious) {
						expected = first[previousPeriod][g] == 0
								? (first[period][g] == 0 ? BigDecimal.ZERO : "-")
								: new BigDecimal(first[period][g] * 100).divide(new BigDecimal(first[previousPeriod][g]),
										2, RoundingMode.HALF_UP);
					}
					assertCellEquals(expected, row.get(3),
							"newestFirst=" + newestFirst + ", 月份 m" + period + ", 产品 p" + g);
				}
			}
		}
	}

	@Test
	public void misalignedRangeNoLongerReadsPastTheEnd() {
		// 3 个产品但只有 8 行(最后一个周期缺 1 行)：原实现的循环会多走一轮，读到区间之外
		List<List<Object>> source = new ArrayList<List<Object>>();
		for (int i = 0; i < 8; i++) {
			source.add(new ArrayList<Object>(Arrays.asList((Object) ("m" + (i / 3)), "p" + (i % 3),
					Integer.valueOf(100 + i * 10))));
		}
		// 逆序(从新到旧)：第 2、3、4 行分别与第 5、6、7 行比较；第 0、1 行没有完整的前一周期
		List<List<Object>> backward = copyOf(source);
		RowsChainRelativeModel backwardModel = singleRelativeModel(true);
		RowsChainRelative.process(backwardModel, null, backward);
		String[] backwardExpected = { "-", "-", "80.00", "81.25", "82.35", "-", "-", "-" };
		for (int i = 0; i < 8; i++) {
			assertEquals(4, backward.get(i).size(), "逆序第" + i + "行宽度");
			assertCellEquals(backwardExpected[i].equals("-") ? "-" : new BigDecimal(backwardExpected[i]),
					backward.get(i).get(3), "逆序第" + i + "行");
		}
		// 正序(从旧到新)：第 3、4、5 行分别与第 0、1、2 行比较；第 6、7 行没有完整的前一周期
		List<List<Object>> forward = copyOf(source);
		RowsChainRelativeModel forwardModel = singleRelativeModel(false);
		RowsChainRelative.process(forwardModel, null, forward);
		String[] forwardExpected = { "-", "-", "-", "130.00", "127.27", "125.00", "-", "-" };
		for (int i = 0; i < 8; i++) {
			assertEquals(4, forward.get(i).size(), "正序第" + i + "行宽度");
			assertCellEquals(forwardExpected[i].equals("-") ? "-" : new BigDecimal(forwardExpected[i]),
					forward.get(i).get(3), "正序第" + i + "行");
		}
		// 原实现在同样的数据上抛数组越界
		for (boolean newestFirst : new boolean[] { true, false }) {
			List<List<Object>> legacy = copyOf(source);
			Throwable legacyError = null;
			try {
				legacyRowsChainRelative(singleRelativeModel(newestFirst), null, legacy);
			} catch (Throwable e) {
				legacyError = e;
			}
			assertTrue(legacyError instanceof IndexOutOfBoundsException,
					"newestFirst=" + newestFirst + ": 原实现 " + legacyError);
		}
	}

	// ======================= 死循环回归的辅助工具 =======================

	/** 在独立线程里执行并限时等待：实现一旦陷入死循环，不会把测试进程卡死 */
	private static Throwable runWithTimeout(Runnable action, long millis, String context) throws InterruptedException {
		final Throwable[] error = new Throwable[1];
		Thread worker = new Thread(() -> {
			try {
				action.run();
			} catch (Throwable e) {
				error[0] = e;
			}
		});
		worker.setDaemon(true);
		worker.start();
		worker.join(millis);
		if (worker.isAlive()) {
			fail(context + ": 调用在 " + millis + " ms 内没有返回(陷入死循环)");
		}
		return error[0];
	}

	private static RowsChainRelativeModel groupModel(boolean reverse, Integer startRow, Integer endRow) {
		RowsChainRelativeModel model = new RowsChainRelativeModel();
		model.setGroupColumn("0");
		model.setRelativeColumns(new String[] { "1" });
		model.setReverse(reverse);
		model.setStartRow(startRow);
		model.setEndRow(endRow);
		return model;
	}

	private static List<List<Object>> simpleRows(int count) {
		return rangeRows(count, true);
	}

	/**
	 * 每行为 分组值、数值；插入模式下环比列由计算器补在数值列后面，更新模式下 sql 里已经预留了这一列
	 */
	private static List<List<Object>> rangeRows(int count, boolean insert) {
		List<List<Object>> rows = new ArrayList<List<Object>>();
		for (int i = 0; i < count; i++) {
			rows.add(insert ? new ArrayList<Object>(Arrays.asList((Object) ("g" + i), Integer.valueOf(100 + i)))
					: new ArrayList<Object>(
							Arrays.asList((Object) ("g" + i), Integer.valueOf(100 + i), "keep")));
		}
		return rows;
	}

	// ======================= 循环边界修改引入的回归(2026-11-24 已修复，这里防止再次出现) =======================

	@Test
	public void emptyRowRangeWithGroupColumnReturns() throws Exception {
		// 指定了分组列而行范围为空(恰好 start == end + 1)：范围内没有行，分组长度被算成 0，循环步长成了 0。
		// 循环边界修改之前(i < end / i > start)这种情况下不会进入循环；修改之后两个边界都会成立，于是死循环。
		// 典型触发：结果只有 2 行而 end-row=-3、只有 3 行而 end-row=-4(截止行超出末尾 1 行)、start-row 比 end-row 大 1
		Integer[][] ranges = { { null, -3, 2 }, { null, -4, 3 }, { 1, 0, 2 }, { 2, null, 2 }, { 3, 2, 3 }, { 4, 3, 5 } };
		for (boolean reverse : new boolean[] { true, false }) {
			for (Integer[] range : ranges) {
				for (boolean insert : new boolean[] { true, false }) {
					int rowCount = range[2];
					Integer startRow = range[0];
					Integer endRow = range[1];
					List<List<Object>> rows = rangeRows(rowCount, insert);
					RowsChainRelativeModel model = groupModel(reverse, startRow, endRow);
					model.setInsert(insert);
					String context = "reverse=" + reverse + ", insert=" + insert + ", " + rowCount + "行, start-row="
							+ startRow + ", end-row=" + endRow;
					Throwable error = runWithTimeout(() -> RowsChainRelative.process(model, null, rows), 1000, context);
					assertNull(error, context + ": " + error);
					for (List<Object> row : rows) {
						assertEquals(3, row.size(), context + ": 行宽度");
						// 没有可比较的行：环比列保持默认值/原值
						assertCellEquals(insert ? null : "keep", row.get(2), context + ": 环比列不应被计算");
					}
					// 修改之前的实现在同样的数据上正常返回，结果一致
					RowsChainRelativeModel legacyModel = groupModel(reverse, startRow, endRow);
					legacyModel.setInsert(insert);
					List<List<Object>> legacy = rangeRows(rowCount, insert);
					legacyRowsChainRelative(legacyModel, null, legacy);
					assertEquals(cellsOf(legacy), cellsOf(rows), context + ": 与修改之前的结果一致");
				}
			}
		}
	}

	@Test
	public void relativeIndexWiderThanGroupStaysInsideRange() throws Exception {
		// 不指定分组列(分组长度为 1)却给出 2 个 relative-index：下标 1 对应的行会超出区间末尾。
		// 正序在循环边界修改之前不会越界；逆序原先就会越界。现在超出起止行的下标直接跳过，结果与只用下标 0 相同
		for (boolean reverse : new boolean[] { false, true }) {
			for (int rowCount : new int[] { 2, 3, 6 }) {
				RowsChainRelativeModel wide = new RowsChainRelativeModel();
				wide.setRelativeIndexs(new Integer[] { 0, 1 });
				wide.setRelativeColumns(new String[] { "1" });
				wide.setReverse(reverse);
				List<List<Object>> rows = simpleRows(rowCount);
				String context = "reverse=" + reverse + ", " + rowCount + "行, relative-index=0,1";
				Throwable error = runWithTimeout(() -> RowsChainRelative.process(wide, null, rows), 1000, context);
				assertNull(error, context + ": " + error);
				RowsChainRelativeModel single = new RowsChainRelativeModel();
				single.setRelativeIndexs(new Integer[] { 0 });
				single.setRelativeColumns(new String[] { "1" });
				single.setReverse(reverse);
				List<List<Object>> expected = simpleRows(rowCount);
				RowsChainRelative.process(single, null, expected);
				assertEquals(cellsOf(expected), cellsOf(rows), context + ": 应与只用下标 0 的结果一致");
			}
		}
	}

	@Test
	public void partialRelativeIndexUnchangedForValidConfigs() {
		// 只指定分组内的部分下标参与比较(都小于分组长度)：越界保护不应触发，结果与修改之前一致
		int compared = 0;
		for (int groupSize = 2; groupSize <= 4; groupSize++) {
			Integer[][] subsets = groupSize == 2 ? new Integer[][] { { 0 }, { 1 }, { 1, 0 } }
					: new Integer[][] { { 0 }, { 1 }, { groupSize - 1 }, { 1, groupSize - 1 }, { 0, 1 } };
			for (Integer[] subset : subsets) {
				for (int periods = 1; periods <= 4; periods++) {
					for (boolean newestFirst : new boolean[] { true, false }) {
						List<List<Object>> source = multiGroupRows(groupSize, periods, newestFirst);
						List<List<Object>> current = copyOf(source);
						List<List<Object>> legacy = copyOf(source);
						RowsChainRelativeModel currentModel = multiGroupModel(newestFirst, true, false);
						currentModel.setRelativeIndexs(subset.clone());
						RowsChainRelativeModel legacyModel = multiGroupModel(newestFirst, true, false);
						legacyModel.setRelativeIndexs(subset.clone());
						RowsChainRelative.process(currentModel, null, current);
						legacyRowsChainRelative(legacyModel, null, legacy);
						assertEquals(cellsOf(legacy), cellsOf(current),
								"groupSize=" + groupSize + ", relative-index=" + Arrays.toString(subset) + ", periods="
										+ periods + ", newestFirst=" + newestFirst);
						compared++;
					}
				}
			}
		}
		assertEquals(104, compared);
	}

	// ======================= ColsChainRelative：负数起始列/截止列 =======================

	private static String runCols(String startColumn, String endColumn) {
		List<List<Object>> rows = new ArrayList<List<Object>>();
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "A", 10, 20, 40)));
		rows.add(new ArrayList<Object>(Arrays.asList((Object) "B", 5, 10, 20)));
		ColsChainRelativeModel model = new ColsChainRelativeModel();
		model.setGroupSize(1);
		model.setStartColumn(startColumn);
		model.setEndColumn(endColumn);
		model.setMultiply(100);
		model.setRadixSize(2);
		ColsChainRelative.process(model, new LabelIndexModel(), rows);
		return cellsOf(rows);
	}

	@Test
	public void colsChainNegativeEndColumnCountsFromLast() {
		// 列依次为：名称、1月、2月、3月。start-column=0 表示从名称列之后开始；
		// 第一个月份没有前一列，环比为默认值；其后每个月份与上一月比较
		String whole = "A|10|null|20|200.00|40|200.00|\n" + "B|5|null|10|200.00|20|200.00|\n";
		assertEquals(whole, runCols("0", null));
		// 模型注释约定：负数等同于 ${dataWidth}-x
		assertEquals(whole, runCols("0", "3"));
		assertEquals(whole, runCols("0", "-1"));
		assertEquals(whole, runCols("0", "${dataWidth}-1"));
		String withoutLast = "A|10|null|20|200.00|40|\n" + "B|5|null|10|200.00|20|\n";
		assertEquals(withoutLast, runCols("0", "2"));
		assertEquals(withoutLast, runCols("0", "-2"));
		// 负数起始列钳到 0
		assertEquals(whole, runCols("-1", "-1"));
		assertEquals(whole, runCols("-5", null));
		assertEquals(withoutLast, runCols("-5", "-2"));
	}
}
