package org.sagacity.sqltoy.plugins;

import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.model.OverTimeSql;

/**
 * 超时sql
 * 
 * @author zhongxuchen
 *
 */
public interface OverTimeSqlHandler {
	/**
	 * 记录日志
	 * 
	 * @param overTimeSql
	 */
	public void log(OverTimeSql overTimeSql);

	/**
	 * 获取前多少条最慢的sql
	 * 
	 * @param size
	 * @return
	 */
	public default List<OverTimeSql> getSlowest(int size) {
		return new ArrayList<OverTimeSql>();
	}
}
