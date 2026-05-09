/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.PKStrategy;

/**
 * @project sagacity-sqltoy
 * @description 针对dm数据库提供通用工具类
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月30日
 * @modify 2020年7月30日,修改说明
 */
public class DMDialectUtils {
	/**
	 * @TODO 主键策略是identity或sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		// identity字段不能手工赋值(2023-6-2 ,需:set IDENTITY_INSERT tableName on)
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}
}
