/**
 * @Copyright 2008 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.utils;

import static java.lang.System.out;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * @project sagacity-sqltoy
 * @description 用于debug提供输出工具以及执行时间统计
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:DebugUtil.java,Revision:v1.0,Date:2010-8-18 下午05:32:15 $
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class DebugUtil {
	/**
	 * 用于计时存储map
	 */
	private static HashMap timeMap = new HashMap();
	private static ThreadLocal orderTime = new ThreadLocal();
	static {
		orderTime.remove();
	}

	/**
	 * @todo 打印集合数据
	 * @param obj
	 * @param appendStr
	 * @param newLine
	 */
	public static void printAry(Object obj, String appendStr, boolean newLine) {
		if (obj == null) {
			if (newLine) {
				out.println(obj);
			} else {
				out.print(obj + appendStr);
			}
		} else {
			Object rowData;
			if (obj.getClass().isArray()) {
				Object[] tmp = CollectionUtil.convertArray(obj);
				for (int i = 0; i < tmp.length; i++) {
					rowData = tmp[i];
					if (rowData instanceof Collection || rowData instanceof Object[]) {
						out.println();
						printAry(rowData, appendStr, false);
					} else {
						if (newLine) {
							out.println(rowData);
						} else {
							out.print(rowData + ((i == tmp.length - 1) ? "" : appendStr));
						}
					}
				}
			} else if (obj instanceof Collection) {
				Collection tmp = (Collection) obj;
				int index = 0;
				int size = tmp.size();
				for (Iterator iter = tmp.iterator(); iter.hasNext();) {
					index++;
					rowData = iter.next();
					if (rowData instanceof Collection || rowData instanceof Object[]) {
						out.println();
						printAry(rowData, appendStr, false);
					} else {
						if (newLine) {
							out.println(rowData);
						} else {
							out.print(rowData + (index == size ? "" : appendStr));
						}
					}
				}
			} else if (obj instanceof Set) {
				Set tmp = (Set) obj;
				int index = 0;
				int size = tmp.size();
				for (Iterator iter = tmp.iterator(); iter.hasNext();) {
					index++;
					rowData = iter.next();
					if (rowData instanceof Collection || rowData instanceof Object[]) {
						out.println();
						printAry(rowData, appendStr, false);
					} else {
						if (newLine) {
							out.println(rowData);
						} else {
							out.print(rowData + (index == size ? "" : appendStr));
						}
					}
				}
			} else {
				out.println(obj);
			}
		}
	}

	/**
	 * @todo 开始计时
	 * @param transactionId
	 */
	public static void beginTime(String transactionId) {
		long nowTime = System.nanoTime();
		if (transactionId == null) {
			orderTime.set(Long.valueOf(nowTime));
		} else {
			timeMap.put(transactionId, nowTime);
		}
	}

	/**
	 * @todo 截止计时并输出开始到截止之间的时长
	 * @param transactionId
	 */
	public static void endTime(String transactionId) {
		long endTime = System.nanoTime();
		long totalTime = 0;
		if (transactionId == null) {
			totalTime = endTime - (Long) orderTime.get();
			orderTime.remove();
		} else {
			Long preTime = (Long) timeMap.get(transactionId);
			if (preTime == null)
				preTime = Long.valueOf(System.nanoTime());
			totalTime = endTime - preTime;
			timeMap.remove(transactionId);
		}

		out.println("事务:".concat((transactionId == null ? "" : transactionId)).concat("花费时间为:")
				.concat(new BigDecimal(totalTime * 1.000 / 1000000000).toString()).concat("秒"));

	}
}
