/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.PKStrategy;

/**
 * @project sqltoy-orm
 * @description 提供sqlite数据库统一的数据库操作功能实现，便于sqlite今后多版本的共用
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月5日
 */
public class SqliteDialectUtils {
	/**
	 * @TODO 主键策略是identity或sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// 目前不支持sequence模式
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return true;
		}
		return true;
	}
}
