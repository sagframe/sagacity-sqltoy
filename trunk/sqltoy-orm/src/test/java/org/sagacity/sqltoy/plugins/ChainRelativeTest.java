/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.plugins.calculator.ColsChainRelative;
import org.sagacity.sqltoy.plugins.calculator.RowsChainRelative;
import org.sagacity.sqltoy.utils.CollectionUtil;

import com.alibaba.fastjson.JSON;

/**
 * 测试环比计算的正确性
 * 
 * @author zhong
 */
public class ChainRelativeTest {

	@Test
	public void testColsChainRelative() {
		// |------- 1月-------|------- 2月 ------|------ 3月--------|
		// |交易笔 | 金额 | 收入 |交易笔 | 金额 | 收入 |交易笔 | 金额 | 收入 |
		Object[][] values = { { "香蕉", 10, 2000, 20000, 12, 2400, 27000, 13, 2300, 27000 },
				{ "苹果", 12, 2000, 24000, 11, 1900, 26000, 13, 2000, 25000 } };
		List result = CollectionUtil.arrayToDeepList(values);
		ColsChainRelativeModel colsRelative = new ColsChainRelativeModel();
		colsRelative.setGroupSize(3);
		colsRelative.setReduceOne(false);
		colsRelative.setRelativeIndexs(new Integer[] { 1, 2 });
		colsRelative.setFormat("#.00%");
		colsRelative.setStartColumn(1);
		HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();
		ColsChainRelative.process(colsRelative, labelIndexMap, result);
		System.out.println(JSON.toJSONString(result));
	}

	@Test
	public void testRowsChainRelative() {
		// |月份 | 产品 |交易笔数 | 环比 | 金额 | 环比 | 收入 | 环比 |
		// | 5月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
		// | 5月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
		// | 4月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |0
		// | 4月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |1
		// | 3月 | 香蕉 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
		// | 3月 | 苹果 | 2000 | 环比 | 金额 | 环比 | 收入 | 环比 |
		Object[][] values = { { "5月", "香蕉", 2000 }, { "5月", "苹果", 1900 }, { "4月", "香蕉", 1800 }, { "4月", "苹果", 1800 },
				{ "3月", "香蕉", 1600 }, { "3月", "苹果", 1700 } };
		List result = CollectionUtil.arrayToDeepList(values);
		RowsChainRelativeModel rowsRelative = new RowsChainRelativeModel();
		rowsRelative.setGroupColumn("1");
		rowsRelative.setReduceOne(false);
		rowsRelative.setRelativeColumns(new String[] { "2" });
		rowsRelative.setFormat("#.00%");
		rowsRelative.setReverse(true);
		HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();
		RowsChainRelative.process(rowsRelative, labelIndexMap, result);
		for (int i = 0; i < result.size(); i++) {
			System.out.println(JSON.toJSONString(result.get(i)));
		}
	}

}
