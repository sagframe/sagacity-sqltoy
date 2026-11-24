package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opentest4j.AssertionFailedError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.oracle.CollectionUtilLegacyOracle;
import org.sagacity.sqltoy.oracle.DiffSupport;

/**
 * @project sagacity-sqltoy
 * @description CollectionUtil.groupSummary 与优化前实现(行为基准)的差分对拍：原地修改后的数据(明细行的顺序与汇总行的每个单元格)、
 *              分组配置对象被改成的最终状态、异常都必须完全一致。汇总行是新建的行对象，明细行是传入的原对象，两者分别按单元格和按身份比对。
 * @author zhongxuchen
 */
public class CollectionUtilGroupSummaryDifferentialTest {
	private static final int RANDOM_CASES = 3000;

	/** 一个汇总列的配置 */
	private static final class ColSpec {
		int colIndex;
		int summaryType;
		int radixSize;
		RoundingMode roundingMode;
		boolean aveSkipNull;
	}

	/** 一个分组层级的配置 */
	private static final class GroupSpec {
		Integer[] groupCols;
		int labelIndex;
		String sumSite;
		int rowSize;
		int summaryType;
		String sumTitle;
		String averageTitle;
		boolean globalReverse;
		List<ColSpec> cols = new ArrayList<>();
	}

	/** 一份汇总用例 */
	private static final class SummaryCase {
		List<List<Object>> rows;
		List<GroupSpec> groups = new ArrayList<>();
		boolean reverse;
		String linkSign;
		boolean skipSingleRow;
	}

	/** 一侧的执行结果 */
	private static final class Run {
		List<List<Object>> rows;
		SummaryGroupMeta[] metas;
		Throwable error;
		Map<Object, Integer> positions;
	}

	private static List<Object> row(Object... cells) {
		return new ArrayList<>(Arrays.asList(cells));
	}

	// ---------------- 随机用例生成 ----------------

	/** 明细数值列的取值：类型混杂，非数字文本只占很小比例(每份用例有约 20 个数值格，比例稍高就会让多数用例抛异常) */
	private static Object cellValue(Random rnd) {
		int pick = rnd.nextInt(200);
		if (pick < 24) {
			return null;
		} else if (pick < 30) {
			return "";
		} else if (pick < 60) {
			return BigDecimal.valueOf(rnd.nextInt(100000), rnd.nextInt(3));
		} else if (pick < 100) {
			return Long.valueOf(rnd.nextInt(10000));
		} else if (pick < 140) {
			return Integer.valueOf(rnd.nextInt(1000));
		} else if (pick < 160) {
			return Double.valueOf(rnd.nextInt(500) / 8.0d);
		} else if (pick < 176) {
			return String.valueOf(rnd.nextInt(10000));
		} else if (pick < 199) {
			return (1000 + rnd.nextInt(9000)) + "," + (100 + rnd.nextInt(900));
		}
		// 非数字文本：原实现会抛 NumberFormatException
		return rnd.nextBoolean() ? "abc" : (rnd.nextInt(100) + "%");
	}

	private static SummaryCase generate(long seed) {
		Random rnd = new Random(seed);
		SummaryCase summaryCase = new SummaryCase();
		// 数据：按两个分组列排好序(偶尔打乱)，数值列类型混杂
		int groupCount = 1 + rnd.nextInt(6);
		summaryCase.rows = new ArrayList<>();
		for (int g0 = 0; g0 < groupCount; g0++) {
			int subCount = rnd.nextInt(3);
			for (int g1 = 0; g1 < subCount + 1; g1++) {
				int rowCount = rnd.nextInt(4);
				for (int r = 0; r < rowCount; r++) {
					summaryCase.rows.add(row("g" + g0, rnd.nextInt(5) == 0 ? null : "s" + g1, cellValue(rnd),
							cellValue(rnd)));
				}
			}
		}
		if (rnd.nextInt(6) == 0) {
			Collections.shuffle(summaryCase.rows, rnd);
		}
		// 分组层级：1~3 层，分组列取自前两列的各种组合，顺序随机(子分组会合并前面出现过的分组列)。
		// 无分组配置、汇总列为空这类早退/异常路径只在手写用例里保留，随机用例以成功路径为主
		Integer[][] colSets = { {}, { 0 }, { 1 }, { 0, 1 }, { 1, 0 } };
		int levelCount = 1 + rnd.nextInt(3);
		String[] sites = { "top", "bottom", "left", "right", "TOP", null, "" };
		for (int i = 0; i < levelCount; i++) {
			GroupSpec group = new GroupSpec();
			group.groupCols = colSets[rnd.nextInt(colSets.length)];
			// 标签列偶尔超出列数：原实现抛 IndexOutOfBoundsException
			group.labelIndex = (rnd.nextInt(30) == 0) ? 4 + rnd.nextInt(2) : rnd.nextInt(4);
			group.sumSite = sites[rnd.nextInt(sites.length)];
			group.rowSize = 1 + rnd.nextInt(2);
			group.summaryType = 1 + rnd.nextInt(3);
			group.sumTitle = rnd.nextInt(4) == 0 ? null : "sum" + i;
			group.averageTitle = rnd.nextInt(4) == 0 ? null : "avg" + i;
			group.globalReverse = rnd.nextBoolean();
			// 汇总列偶尔为空：原实现抛 ArrayIndexOutOfBoundsException
			int colCount = (rnd.nextInt(20) == 0) ? 0 : 1 + rnd.nextInt(2);
			for (int c = 0; c < colCount; c++) {
				ColSpec col = new ColSpec();
				// 汇总列偶尔指向不存在的列：原实现抛 IndexOutOfBoundsException
				col.colIndex = (rnd.nextInt(30) == 0) ? 4 + rnd.nextInt(2) : 2 + rnd.nextInt(2);
				col.summaryType = rnd.nextInt(4);
				col.radixSize = rnd.nextInt(5);
				col.roundingMode = RoundingMode.values()[rnd.nextInt(RoundingMode.values().length)];
				col.aveSkipNull = rnd.nextBoolean();
				group.cols.add(col);
			}
			summaryCase.groups.add(group);
		}
		summaryCase.reverse = rnd.nextBoolean();
		String[] signs = { null, "", " / ", "," };
		summaryCase.linkSign = signs[rnd.nextInt(signs.length)];
		summaryCase.skipSingleRow = rnd.nextBoolean();
		return summaryCase;
	}

	private static SummaryGroupMeta[] buildMetas(List<GroupSpec> groups) {
		SummaryGroupMeta[] metas = new SummaryGroupMeta[groups.size()];
		for (int i = 0; i < groups.size(); i++) {
			GroupSpec spec = groups.get(i);
			SummaryGroupMeta meta = new SummaryGroupMeta();
			meta.setGroupCols(spec.groupCols);
			meta.setLabelIndex(spec.labelIndex);
			meta.setSumSite(spec.sumSite);
			meta.setRowSize(spec.rowSize);
			meta.setSummaryType(spec.summaryType);
			meta.setSumTitle(spec.sumTitle);
			meta.setAverageTitle(spec.averageTitle);
			meta.setGlobalReverse(spec.globalReverse);
			SummaryColMeta[] cols = new SummaryColMeta[spec.cols.size()];
			for (int c = 0; c < cols.length; c++) {
				ColSpec colSpec = spec.cols.get(c);
				SummaryColMeta col = new SummaryColMeta();
				col.setColIndex(colSpec.colIndex);
				col.setSummaryType(colSpec.summaryType);
				col.setRadixSize(colSpec.radixSize);
				col.setRoundingMode(colSpec.roundingMode);
				col.setAveSkipNull(colSpec.aveSkipNull);
				cols[c] = col;
			}
			meta.setSummaryCols(cols);
			metas[i] = meta;
		}
		return metas;
	}

	private static Run run(boolean legacy, SummaryCase summaryCase) {
		Run run = new Run();
		run.rows = DiffSupport.deepCopy(summaryCase.rows);
		run.positions = DiffSupport.identityPositions(run.rows);
		// 配置对象两侧必须各自独立：方法会原地修改它们
		run.metas = buildMetas(summaryCase.groups);
		try {
			if (legacy) {
				CollectionUtilLegacyOracle.groupSummary(run.rows, run.metas, summaryCase.reverse,
						summaryCase.linkSign, summaryCase.skipSingleRow);
			} else {
				CollectionUtil.groupSummary(run.rows, run.metas, summaryCase.reverse, summaryCase.linkSign,
						summaryCase.skipSingleRow);
			}
		} catch (Throwable e) {
			run.error = e;
		}
		return run;
	}

	/** 配置对象的可观察状态(方法执行完毕后留在外面的样子) */
	private static List<Object> snapshot(SummaryGroupMeta[] metas) {
		List<Object> state = new ArrayList<>();
		for (SummaryGroupMeta meta : metas) {
			state.add(meta.getGroupColumn());
			state.add(meta.getLabelColumn());
			state.add(meta.getLabelIndex());
			state.add(meta.getSumTitle());
			state.add(meta.getAverageTitle());
			state.add(meta.getSumSite());
			state.add(meta.getRowSize());
			state.add(meta.getSummaryType());
			state.add(meta.isGlobalReverse());
			state.add(meta.getOrderColumn());
			state.add(meta.getOrderWay());
			state.add(meta.getOrderWithSum());
			state.add(Arrays.asList(meta.getGroupCols()));
			for (SummaryColMeta col : meta.getSummaryCols()) {
				state.add(col.getColIndex());
				state.add(col.getSummaryType());
				state.add(col.getRadixSize());
				state.add(col.getRoundingMode());
				state.add(col.getSumValue());
				state.add(col.isAveSkipNull());
				state.add(col.getRowCount());
				state.add(col.getNullCount());
			}
		}
		return state;
	}

	private static void compare(SummaryCase summaryCase, Run oracle, Run current, String context) {
		DiffSupport.assertSameError(oracle.error, current.error, context);
		if (oracle.error != null) {
			return;
		}
		// 明细行是传入的原对象：它们在结果中的先后(汇总行记为-1)必须一致
		List<Integer> oracleOrder = DiffSupport.identityOrder(oracle.rows, oracle.positions);
		List<Integer> currentOrder = DiffSupport.identityOrder(current.rows, current.positions);
		if (!oracleOrder.equals(currentOrder)) {
			throw new AssertionFailedError(context + ": row structure differs, oracle=" + oracleOrder + ", current="
					+ currentOrder);
		}
		String diff = DiffSupport.firstDiff(oracle.rows, current.rows);
		if (diff != null) {
			throw new AssertionFailedError(context + ": " + diff);
		}
		List<Object> oracleState = snapshot(oracle.metas);
		List<Object> currentState = snapshot(current.metas);
		if (!oracleState.equals(currentState)) {
			throw new AssertionFailedError(context + ": group metas left in a different state, oracle=" + oracleState
					+ ", current=" + currentState);
		}
	}

	// ---------------- 随机对拍 ----------------

	@Test
	public void randomSummariesMatchOracle() {
		int summaryRowCases = 0;
		int errorCases = 0;
		for (long seed = 0; seed < RANDOM_CASES; seed++) {
			SummaryCase summaryCase = generate(seed);
			String context = "seed=" + seed;
			Run oracle = run(true, summaryCase);
			Run current = run(false, summaryCase);
			compare(summaryCase, oracle, current, context);
			if (oracle.error != null) {
				errorCases++;
			} else if (oracleOrderHasSummaryRows(oracle)) {
				summaryRowCases++;
			}
		}
		assertTrue(summaryRowCases > 1200, "插入了汇总行的用例数=" + summaryRowCases);
		assertTrue(errorCases > 20, "抛异常的用例数=" + errorCases);
	}

	private static boolean oracleOrderHasSummaryRows(Run run) {
		return DiffSupport.identityOrder(run.rows, run.positions).contains(Integer.valueOf(-1));
	}

	// ---------------- 手写用例(期望按算法逐步推算) ----------------

	/** 文档"多维分组汇总"示例的数据与配置：全局总计 + 按品类小计，reverse=true 使汇总行置于明细之前 */
	@Test
	public void documentationExampleSummaryData() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("苹果", "2019年3月", 13, 2000, 2500));
		rows.add(row("苹果", "2019年4月", 11, 1900, 2600));
		rows.add(row("苹果", "2019年5月", 12, 2000, 2400));
		rows.add(row("香蕉", "2019年3月", 13, 2300, 2700));
		rows.add(row("香蕉", "2019年4月", 12, 2400, 2700));
		rows.add(row("香蕉", "2019年5月", 10, 2000, 2000));
		SummaryGroupMeta global = new SummaryGroupMeta();
		global.setLabelIndex(0);
		global.setSumTitle("总计");
		global.setSumSite("top");
		global.setRowSize(1);
		global.setSummaryType(1);
		global.setSummaryCols(sumColumns(2, 3, 4));
		SummaryGroupMeta group = new SummaryGroupMeta();
		group.setGroupCols(new Integer[] { 0 });
		group.setLabelIndex(0);
		group.setSumTitle("小计");
		group.setSumSite("top");
		group.setRowSize(1);
		group.setSummaryType(1);
		group.setSummaryCols(sumColumns(2, 3, 4));

		CollectionUtil.groupSummary(rows, new SummaryGroupMeta[] { global, group }, true, null, false);

		List<List<Object>> expected = new ArrayList<>();
		// 汇总行与明细行等长(5 列)：标签在第 0 列，合计值落在各自的汇总列上
		expected.add(row("总计", null, new BigDecimal("71"), new BigDecimal("12600"), new BigDecimal("14900")));
		expected.add(row("小计", null, new BigDecimal("36"), new BigDecimal("5900"), new BigDecimal("7500")));
		expected.add(row("苹果", "2019年3月", 13, 2000, 2500));
		expected.add(row("苹果", "2019年4月", 11, 1900, 2600));
		expected.add(row("苹果", "2019年5月", 12, 2000, 2400));
		expected.add(row("小计", null, new BigDecimal("35"), new BigDecimal("6700"), new BigDecimal("7400")));
		expected.add(row("香蕉", "2019年3月", 13, 2300, 2700));
		expected.add(row("香蕉", "2019年4月", 12, 2400, 2700));
		expected.add(row("香蕉", "2019年5月", 10, 2000, 2000));
		// reverse 只是让汇总行置顶(整体先反转、算完再反转回来)，明细行保持 SQL 的先后顺序
		assertEquals(expected, rows, "合计与小计的数值应与文档一致");
	}

	private static SummaryColMeta[] sumColumns(int... indexes) {
		SummaryColMeta[] cols = new SummaryColMeta[indexes.length];
		for (int i = 0; i < indexes.length; i++) {
			SummaryColMeta col = new SummaryColMeta();
			col.setColIndex(indexes[i]);
			col.setSummaryType(1);
			cols[i] = col;
		}
		return cols;
	}

	@Test
	public void lessThanTwoRowsOrNoGroupMetaDoesNothing() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("a", 1));
		SummaryGroupMeta[] metas = { new SummaryGroupMeta() };
		CollectionUtil.groupSummary(rows, metas, false, null, false);
		assertEquals(Arrays.asList(row("a", 1)), rows);
		List<List<Object>> two = new ArrayList<>();
		two.add(row("a", 1));
		two.add(row("b", 2));
		CollectionUtil.groupSummary(two, new SummaryGroupMeta[0], false, null, false);
		assertEquals(Arrays.asList(row("a", 1), row("b", 2)), two, "没有分组配置时不做任何处理");
		assertNull(DiffSupport.capture(() -> CollectionUtil.groupSummary(null, metas, false, null, false)));
	}

	@Test
	public void averageSkipsNullValues() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("a", 10L));
		rows.add(row("a", null));
		rows.add(row("a", 30L));
		SummaryColMeta col = new SummaryColMeta();
		col.setColIndex(1);
		col.setSummaryType(3);
		col.setAveSkipNull(true);
		col.setRadixSize(2);
		SummaryGroupMeta group = new SummaryGroupMeta();
		group.setLabelIndex(0);
		group.setSumTitle("sum");
		group.setAverageTitle("avg");
		group.setSumSite("left");
		group.setSummaryType(3);
		group.setSummaryCols(new SummaryColMeta[] { col });

		CollectionUtil.groupSummary(rows, new SummaryGroupMeta[] { group }, false, " / ", false);
		// 非空值 2 个：平均 = 40 / 2 = 20.00；sum-site 为 left 时合计与平均用连接符拼成一格
		assertEquals(Arrays.asList("sum / avg", "40 / 20.00"), rows.get(3));
	}

	@Test
	public void nonNumericValueThrowsNumberFormatException() {
		List<List<Object>> rows = new ArrayList<>();
		rows.add(row("a", "abc"));
		rows.add(row("b", 1L));
		SummaryColMeta col = new SummaryColMeta();
		col.setColIndex(1);
		col.setSummaryType(1);
		SummaryGroupMeta group = new SummaryGroupMeta();
		group.setLabelIndex(0);
		group.setSummaryCols(new SummaryColMeta[] { col });
		assertThrows(NumberFormatException.class,
				() -> CollectionUtil.groupSummary(rows, new SummaryGroupMeta[] { group }, false, null, false));
	}

	// ---------------- 负例对照：比对逻辑必须能发现故意的差异 ----------------

	@Test
	public void comparisonDetectsDeliberateDifferences() {
		// 找一份成功执行且插入了汇总行的数据
		SummaryCase summaryCase = null;
		for (long seed = 0; seed < RANDOM_CASES && summaryCase == null; seed++) {
			SummaryCase candidate = generate(seed);
			Run run = run(true, candidate);
			if (run.error == null && oracleOrderHasSummaryRows(run)) {
				summaryCase = candidate;
			}
		}
		assertTrue(summaryCase != null, "需要一份插入了汇总行的对照数据");
		final SummaryCase control = summaryCase;

		compare(control, run(true, control), run(true, control), "基准自身两次执行不应报差异");
		assertDetected(() -> {
			Run reversed = run(true, control);
			Collections.reverse(reversed.rows);
			compare(control, run(true, control), reversed, "行顺序被打乱");
		});
		assertDetected(() -> {
			Run modified = run(true, control);
			// 找一行汇总行改掉它的标签
			for (List<Object> item : modified.rows) {
				if (!modified.positions.containsKey(item)) {
					item.set(0, "changed");
					break;
				}
			}
			compare(control, run(true, control), modified, "汇总行被改");
		});
		assertDetected(() -> {
			Run moved = run(true, control);
			// 把一个汇总行挪到别的位置
			for (int i = 0; i < moved.rows.size(); i++) {
				if (!moved.positions.containsKey(moved.rows.get(i))) {
					moved.rows.add(moved.rows.size() - 1, moved.rows.remove(i));
					break;
				}
			}
			compare(control, run(true, control), moved, "汇总行位置被挪");
		});
		assertDetected(() -> {
			Run tampered = run(true, control);
			tampered.metas[0].setLabelIndex(tampered.metas[0].getLabelIndex() + 1);
			compare(control, run(true, control), tampered, "分组配置的最终状态不同");
		});
		assertDetected(() -> {
			Run failing = run(true, control);
			failing.error = new IllegalStateException("boom");
			compare(control, run(true, control), failing, "一侧抛了异常");
		});
	}

	private static void assertDetected(Executable comparison) {
		assertThrows(AssertionFailedError.class, comparison, "比对逻辑未能发现故意的差异");
	}
}
