/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.PKStrategy;

/**
 * @project sqltoy-orm
 * @description mysql数据库各类操作的统一函数实现（便于今后mysql版本以及变种数据库统一使用，减少主体代码重复量）
 * @author zhongxuchen
 * @version v1.0,Date:2015年2月13日
 */
public class MySqlDialectUtils {

	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// 目前不支持sequence模式
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return false;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return true;
		}
		return true;
	}
}
