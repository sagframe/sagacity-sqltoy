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
	 * 指的是在identity、sequence主键场景下，是否允许手工给主键赋值
	 * @param pkStrategy
	 * @return
	 */
	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
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
