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
	 * 指的是在identity、sequence主键场景下，是否允许手工给主键赋值
	 * @param pkStrategy
	 * @return
	 */
	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
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
