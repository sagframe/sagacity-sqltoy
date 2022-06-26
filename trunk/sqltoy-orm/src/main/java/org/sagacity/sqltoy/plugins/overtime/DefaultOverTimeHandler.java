package org.sagacity.sqltoy.plugins.overtime;

import java.util.ArrayList;
import java.util.Comparator;
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

	@Override
	public void log(OverTimeSql overTimeSql) {
		queues.offer(overTimeSql);
	}

	@Override
	public List<OverTimeSql> getSlowest(int size) {
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
