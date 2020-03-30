package org.sagacity.sqltoy.plugins.calculator;

import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.config.model.ReverseModel;

/**
 * @project sqltoy-orm
 * @description 对集合数据进行反转
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:ReverseList.java,Revision:v1.0,Date:2020-3-25 上午10:08:15
 */
public class ReverseList {
	/**
	 * @TODO 集合首尾反转
	 * @param reverseModel
	 * @param labelIndexMap
	 * @param result
	 */
	public static void process(ReverseModel reverseModel, HashMap<String, Integer> labelIndexMap, List result) {
		if (result == null || result.size() < 2)
			return;
		int dataSize = result.size();
		int start = (reverseModel.getStartRow() == null) ? 0 : reverseModel.getStartRow();
		// 不合法反转
		if (start > dataSize - 1)
			return;
		int end = (reverseModel.getEndRow() == null) ? dataSize - 1 : reverseModel.getEndRow();
		if (end < 0) {
			end = dataSize - 1 + end;
		}
		if (end > dataSize - 1) {
			end = dataSize - 1;
		}
		int loopCnt = (end - start) / 2;
		Object row;
		for (int i = 0; i < loopCnt; i++) {
			row = result.get(start + i);
			result.set(start + i, result.get(end - i));
			result.set(end - i, row);
		}
	}
}
