/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description mysql数据库各类操作的统一函数实现（便于今后mysql版本以及变种数据库统一使用，减少主体代码重复量）
 * @author zhongxuchen
 * @version v1.0,Date:2015年2月13日
 */
public class MySqlDialectUtils {

	/**
	 * @TODO 主键策略是identity或sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy, Integer dbType) {
		if (pkStrategy == null) {
			return true;
		}
		// 目前不支持sequence模式
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return false;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			if (dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
				return false;
			}
			return true;
		}
		return true;
	}
}
