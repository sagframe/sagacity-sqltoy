package org.sagacity.sqltoy.integration;

import java.util.Date;

/**
 * @project sagacity-sqltoy
 * @description 分布式Id产生器
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public interface DistributeIdGenerator {
	/**
	 * @TODO 批量获取key值,并指定过期时间
	 * @param key
	 * @param increment
	 * @param expireTime
	 * @return
	 */
	public long generateId(String key, int increment, Date expireTime);

	/**
	 * 初始化
	 * 
	 * @param appContext
	 */
	public void initialize(AppContext appContext);
}
