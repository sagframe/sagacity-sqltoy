package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.PKStrategy;

/**
 * @project sagacity-sqltoy
 * @description 提供gaussdb数据库相关的特殊逻辑处理封装
 * @author zhongxuchen
 * @version v1.0, Date:2023年6月8日
 * @modify 2023年6月8日,修改说明
 */
public class GaussDialectUtils {
	/**
	 * @TODO 定义当使用sequence或identity时,是否允许自定义值(即不通过sequence或identity产生，而是由外部直接赋值)
	 * @param pkStrategy
	 * @return
	 */
	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// sequence
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		// postgresql10+ 支持identity
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return true;
		}
		return true;
	}
}
