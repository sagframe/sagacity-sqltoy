/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.plugins.calculator.UnpivotList;
import org.sagacity.sqltoy.utils.CollectionUtil;

import com.alibaba.fastjson.JSON;

/**
 * @author zhongxuchen
 *
 */
public class UnPivotListTest {
	@Test
	public void testUnpivot() {
		// |------- 1月-------|------- 2月 ------|------ 3月--------|
		// |交易笔 | 金额 | 收入 |交易笔 | 金额 | 收入 |交易笔 | 金额 | 收入 |
		Object[][] values = { { "5月", "香蕉", 2000, 20000 }, { "5月", "苹果", 1900, 38999 }, { "4月", "香蕉", 1800, 21000 },
				{ "4月", "苹果", 1800, 400000 }, };
		List result = CollectionUtil.arrayToDeepList(values);
		UnpivotModel unpivotModel = new UnpivotModel();
		unpivotModel.setColumnsToRows(new String[] { "quantity:数量,AMT:金额" });
		unpivotModel.setNewColumnsLabels(new String[] { "indexName", "indexValue" });
		DataSetResult resultModel = new DataSetResult();
		resultModel.setLabelNames(new String[] { "month", "fruitName", "quantity", "AMT" });
		resultModel.setLabelTypes(new String[] { "string", "string", "decimal", "decimal" });
		HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();
		labelIndexMap.put("month", 0);
		labelIndexMap.put("fruitname", 1);
		labelIndexMap.put("quantity", 2);
		labelIndexMap.put("amt", 3);

		List value = UnpivotList.process(unpivotModel, resultModel, labelIndexMap, result);
		for (int i = 0; i < value.size(); i++) {
			System.out.println(JSON.toJSONString(result.get(i)));
		}
	}
}
