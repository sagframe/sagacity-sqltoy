package org.sagacity.sqltoy.plugins.overtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.sagacity.sqltoy.model.OverTimeSql;
import org.sagacity.sqltoy.model.PriorityLimitSizeQueue;
import org.sagacity.sqltoy.plugins.OverTimeSqlHandler;

/**
 * @TODO 提供默认的sql执行超时日志队列，便于应用获取
 * @author zhongxuchen
 * @version v1.0, Date:2022-06-29
 */
public class DefaultOverTimeHandler implements OverTimeSqlHandler {
	/**
	 * 无sqlId 的超时sql
	 */
	private PriorityLimitSizeQueue<OverTimeSql> queues = new PriorityLimitSizeQueue<OverTimeSql>(500,
			new Comparator<OverTimeSql>() {
				@Override
				public int compare(OverTimeSql o1, OverTimeSql o2) {
					return Long.valueOf(o1.getTakeTime() - o2.getTakeTime()).intValue();
				}
			});
	// 所有执行超时且含sqlId的sql语句
	private HashMap<String, OverTimeSql> slowSqlMap = new HashMap<String, OverTimeSql>();

	@Override
	public void log(OverTimeSql overTimeSql) {
		String sqlId = overTimeSql.getId();
		if (null != sqlId && !"".equals(sqlId.trim())) {
			OverTimeSql preSql = slowSqlMap.get(sqlId);
			if (null == preSql) {
				overTimeSql.setAveTakeTime(new BigDecimal(overTimeSql.getTakeTime()));
				slowSqlMap.put(sqlId, overTimeSql);
			} else {
				// 新的相同sqlId的超时执行时长大于之前的
				if (overTimeSql.getTakeTime() > preSql.getTakeTime()) {
					// 平均执行时长
					overTimeSql
							.setAveTakeTime(preSql.getAveTakeTime().multiply(new BigDecimal(preSql.getOverTimeCount()))
									.add(new BigDecimal(overTimeSql.getTakeTime()))
									.divide(new BigDecimal(preSql.getOverTimeCount() + 1), 3, RoundingMode.HALF_UP));
					// 执行次数进行累加
					overTimeSql.setOverTimeCount(preSql.getOverTimeCount() + 1);
					// 设置首次超时发生时间
					overTimeSql.setFirstLogTime(preSql.getFirstLogTime());
					slowSqlMap.put(sqlId, overTimeSql);
				} else {
					preSql.setAveTakeTime(preSql.getAveTakeTime().multiply(new BigDecimal(preSql.getOverTimeCount()))
							.add(new BigDecimal(overTimeSql.getTakeTime()))
							.divide(new BigDecimal(preSql.getOverTimeCount() + 1), 3, RoundingMode.HALF_UP));
					// 更新最后超时发生时间
					preSql.setLogTime(overTimeSql.getLogTime());
					preSql.setOverTimeCount(preSql.getOverTimeCount() + 1);
				}
			}
		} else {
			queues.offer(overTimeSql);
		}
	}

	/**
	 * 获取最慢的sql
	 */
	@Override
	public List<OverTimeSql> getSlowest(int size, boolean hasSqlId) {
		if (size < 1) {
			throw new IllegalArgumentException("取最慢查询:size 参数必须>=1,如果要获取全部，可使用:Integer.MAX_VALUE");
		}
		// 非xml中定义的sql，没有具体的sqlId
		if (!hasSqlId) {
			return getSlowest(size);
		} else {
			List<OverTimeSql> result = new ArrayList<OverTimeSql>();
			Iterator<OverTimeSql> iter = slowSqlMap.values().iterator();
			while (iter.hasNext()) {
				result.add(iter.next());
			}
			// 按照执行时长从大到小排序
			Collections.sort(result, new Comparator<OverTimeSql>() {
				@Override
				public int compare(OverTimeSql o1, OverTimeSql o2) {
					return Long.valueOf(o2.getTakeTime() - o1.getTakeTime()).intValue();
				}
			});
			if (size >= result.size()) {
				return result;
			}
			return result.subList(0, size - 1);
		}
	}

	/**
	 * @TODO 从队列中取出最慢的sql记录
	 * @param size
	 * @return
	 */
	private List<OverTimeSql> getSlowest(int size) {
		List<OverTimeSql> result = new ArrayList<OverTimeSql>();
		Iterator<OverTimeSql> iter = queues.iterator();
		int index = 0;
		int start = queues.size() - size;
		if (start < 0) {
			start = 0;
		}
		OverTimeSql nextVal;
		while (iter.hasNext()) {
			nextVal = iter.next();
			if (index >= start) {
				result.add(0, nextVal);
			}
			index++;
		}
		return result;
	}

}
