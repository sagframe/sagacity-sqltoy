package org.sagacity.sqltoy.plugins.calculator;

import java.util.List;

import org.sagacity.sqltoy.config.model.LabelIndexModel;
import org.sagacity.sqltoy.config.model.ReverseModel;

/**
 * @project sagacity-sqltoy
 * @description 对集合数据进行反转
 * @author zhongxuchen
 * @version v1.0,Date:2020-03-25
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ReverseList {
	/**
	 * 集合首尾反转
	 * 
	 * @param reverseModel
	 * @param labelIndexMap
	 * @param result
	 */
	public static void process(ReverseModel reverseModel, LabelIndexModel labelIndexMap, List result) {
		if (result == null || result.size() < 2) {
			return;
		}
		int dataSize = result.size();
		int start = (reverseModel.getStartRow() == null) ? 0 : reverseModel.getStartRow();
		// update 2026-11-24 负数startRow钳到0,避免result.get(负下标)越界
		if (start < 0) {
			start = 0;
		}
		// 不合法反转
		if (start > dataSize - 1) {
			return;
		}
		int end = (reverseModel.getEndRow() == null) ? dataSize - 1 : reverseModel.getEndRow();
		// update 2026-11-24 负数endRow按python式从末尾计:dataSize+end(-1指最后一行);原dataSize-1+end会差1指向倒数第二行
		if (end < 0) {
			end = dataSize + end;
		}
		if (end > dataSize - 1) {
			end = dataSize - 1;
		}
		// update 2026-11-24 交换次数取区间长度的一半:(end-start+1)/2;原(end-start)/2在偶数长度时少交换最中间一对(2行时loopCnt=0完全不反转)
		int loopCnt = (end - start + 1) / 2;
		Object row;
		for (int i = 0; i < loopCnt; i++) {
			row = result.get(start + i);
			result.set(start + i, result.get(end - i));
			result.set(end - i, row);
		}
	}
}
