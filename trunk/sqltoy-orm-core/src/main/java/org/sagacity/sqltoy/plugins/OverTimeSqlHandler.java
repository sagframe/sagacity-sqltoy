package org.sagacity.sqltoy.plugins;

import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.model.OverTimeSql;

/**
 * @description 超时sql处理器接口定义
 * @author zhongxuchen
 * @version v1.0, Date:2022-06-29
 */
public interface OverTimeSqlHandler {
	/**
	 * @TODO 记录日志
	 * @param overTimeSql
	 */
	public void log(OverTimeSql overTimeSql);

	/**
	 * @TODO 获取前多少条最慢的sql
	 * @param size
	 * @param hasSqlId xml中定义的有id的sql
	 * @return
	 */
	public default List<OverTimeSql> getSlowest(int size, boolean hasSqlId) {
		return new ArrayList<OverTimeSql>();
	}
}
