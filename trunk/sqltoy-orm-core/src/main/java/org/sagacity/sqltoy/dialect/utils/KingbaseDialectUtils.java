package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.PKStrategy;

/**
 * @project sagacity-sqltoy
 * @description 北大金仓数据库方言支持
 * @author zhongxuchen
 * @version v1.0, Date:2020-11-6
 * @modify 2020-11-6,修改说明
 */
public class KingbaseDialectUtils {
	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "isnull";

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
		// kingbase identity不允许手工赋值(2023-6-1 验证)
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}
}
