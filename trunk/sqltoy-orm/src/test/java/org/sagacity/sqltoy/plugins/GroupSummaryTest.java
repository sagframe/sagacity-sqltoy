package org.sagacity.sqltoy.plugins;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SummaryColMeta;
import org.sagacity.sqltoy.config.model.SummaryGroupMeta;
import org.sagacity.sqltoy.utils.CollectionUtil;

import com.alibaba.fastjson.JSON;

public class GroupSummaryTest {
	@Test
	public void testSummary() {
		Object[][] values = new Object[][] { { "202101", "手机", 100, 2000 }, { "202101", "电脑", 90, null },
				{ "202102", "手机", 80, 1700 }, { "202102", "电脑", 60, 7900 } };
		List dataSet = new ArrayList();
		for (int i = 0; i < values.length; i++) {
			List row = new ArrayList();
			for (int j = 0; j < values[i].length; j++) {
				row.add(values[i][j]);
			}
			dataSet.add(row);
		}

		SummaryColMeta[] colMetas = new SummaryColMeta[2];
		SummaryColMeta colMeta1 = new SummaryColMeta();
		colMeta1.setColIndex(2);
		colMeta1.setAveSkipNull(true);
		colMeta1.setSummaryType(1);
		SummaryColMeta colMeta2 = new SummaryColMeta();
		colMeta2.setColIndex(3);
		colMeta2.setAveSkipNull(true);
		colMeta2.setSummaryType(3);
		colMetas[0] = colMeta1;
		colMetas[1] = colMeta2;
		
		SummaryColMeta[] colMetas1 = new SummaryColMeta[2];
		SummaryColMeta colMeta11 = new SummaryColMeta();
		colMeta11.setColIndex(2);
		colMeta11.setAveSkipNull(true);
		colMeta11.setSummaryType(1);
		SummaryColMeta colMeta21 = new SummaryColMeta();
		colMeta21.setColIndex(3);
		colMeta21.setAveSkipNull(true);
		colMeta21.setSummaryType(3);
		colMetas1[0] = colMeta11;
		colMetas1[1] = colMeta21;
		
		SummaryGroupMeta[] groupMetas = new SummaryGroupMeta[2];
		SummaryGroupMeta globalMeta = new SummaryGroupMeta();
		globalMeta.setLabelIndex(0);
		// globalMeta.setAveTitle("平均值");
		globalMeta.setSumTitle("总计/平均值");
		globalMeta.setSumSite("left");
		globalMeta.setRowSize(1);
		globalMeta.setBothSumAverage(true);
		globalMeta.setSummaryCols(colMetas);
		groupMetas[0] = globalMeta;

		SummaryGroupMeta groupMeta = new SummaryGroupMeta();
		groupMeta.setGroupCols(new Integer[] { 0 });
		// groupMeta.setAveTitle("平均值");
		groupMeta.setSumTitle("小计/平均值");
		groupMeta.setLabelIndex(0);
		groupMeta.setBothSumAverage(true);
		groupMeta.setRowSize(1);
		groupMeta.setSummaryCols(colMetas1);
		groupMeta.setSumSite("left");
		groupMetas[1] = groupMeta;

		CollectionUtil.groupSummary(dataSet, groupMetas, false, null);
		for (int i = 0; i < dataSet.size(); i++) {
			System.err.println(JSON.toJSONString(dataSet.get(i)));
		}
	}

}
