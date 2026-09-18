package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;

/**
 * 回归测试(行为锁定):组内列全null且aveSkipNull=true时汇总求平均不抛ArithmeticException。
 * 原审计声称"rowCount-nullCount=0作除数除零"不可达:calculateTotal对null只计nullCount不求和,
 * 全null列sumValue保持ZERO,被sumValue.compareTo(ZERO)==0分支短路(aveValue直接为0),
 * divide不会执行;分组切换时三者(sumValue/nullCount/rowCount)同步重置,不变式恒成立
 */
public class SummaryAllNullColumnTest {

	@Test
	public void allNullColumnSummaryCompletesWithoutException() {
		List<List> dataSet = new ArrayList<List>();
		dataSet.add(new ArrayList<>(Arrays.asList("A", 10)));
		dataSet.add(new ArrayList<>(Arrays.asList("A", null)));
		dataSet.add(new ArrayList<>(Arrays.asList("A", null)));
		dataSet.add(new ArrayList<>(Arrays.asList("B", null)));
		dataSet.add(new ArrayList<>(Arrays.asList("B", null)));

		SummaryColMeta col = new SummaryColMeta();
		col.setColIndex(1);
		col.setSummaryType(3);
		col.setRadixSize(2);
		col.setRoundingMode(RoundingMode.HALF_UP);
		col.setAveSkipNull(true);

		SummaryGroupMeta group = new SummaryGroupMeta();
		group.setGroupCols(new Integer[] { 0 });
		group.setSummaryCols(new SummaryColMeta[] { col });
		group.setSumTitle("合计");
		group.setAverageTitle("均值");
		group.setSumSite("bottom");

		assertDoesNotThrow(() -> CollectionUtil.groupSummary(dataSet, new SummaryGroupMeta[] { group }, false, ",",
				false));
		// 汇总行已插入
		boolean hasSummary = false;
		for (List row : dataSet) {
			if ("合计".equals(row.get(0))) {
				hasSummary = true;
			}
		}
		assertTrue(hasSummary, "应产生分组汇总行");
	}
}
