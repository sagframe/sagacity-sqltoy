package org.sagacity.sqltoy.plugins.overtime;

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
 *
 */
public class DefaultOverTimeHandler implements OverTimeSqlHandler {

	private PriorityLimitSizeQueue<OverTimeSql> queues = new PriorityLimitSizeQueue<OverTimeSql>(1000,
			new Comparator<OverTimeSql>() {
				@Override
				public int compare(OverTimeSql o1, OverTimeSql o2) {
					return new Long(o1.getTakeTime() - o2.getTakeTime()).intValue();
				}
			});
	private HashMap<String, OverTimeSql> slowSqlMap = new HashMap<String, OverTimeSql>();

	@Override
	public void log(OverTimeSql overTimeSql) {
		String sqlId = overTimeSql.getId();
		if (null != sqlId && !sqlId.trim().equals("")) {
			OverTimeSql preSql = slowSqlMap.get(sqlId);
			if (null == preSql) {
				slowSqlMap.put(sqlId, overTimeSql);
			} else {
				if (overTimeSql.getTakeTime() > preSql.getTakeTime()) {
					overTimeSql.setOverTimeCount(1 + preSql.getOverTimeCount());
					slowSqlMap.put(sqlId, overTimeSql);
				} else {
					preSql.setOverTimeCount(preSql.getOverTimeCount() + 1);
				}
			}
		} else {
			queues.offer(overTimeSql);
		}
	}

	@Override
	public List<OverTimeSql> getSlowest(int size, boolean hasSqlId) {
		if (!hasSqlId) {
			return getSlowest(size);
		} else {
			List<OverTimeSql> result = new ArrayList<OverTimeSql>();
			Iterator<OverTimeSql> iter = slowSqlMap.values().iterator();
			while (iter.hasNext()) {
				result.add(iter.next());
			}
			Collections.sort(result, new Comparator<OverTimeSql>() {
				public int compare(OverTimeSql o1, OverTimeSql o2) {
					return new Long(o2.getTakeTime() - o1.getTakeTime()).intValue();
				}
			});
			if (size >= result.size()) {
				return result;
			}
			return result.subList(0, size - 1);
		}
	}

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
