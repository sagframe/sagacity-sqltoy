package org.sagacity.sqltoy.plugins;

import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.model.OverTimeSql;

/**
 * 超时sql处理器接口定义
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
	 * @param hasSqlId
	 * @return
	 */
	public default List<OverTimeSql> getSlowest(int size, boolean hasSqlId) {
		return new ArrayList<OverTimeSql>();
	}
}
