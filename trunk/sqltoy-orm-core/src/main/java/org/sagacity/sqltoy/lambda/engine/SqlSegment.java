package org.sagacity.sqltoy.lambda.engine;

import java.util.Map;

/**
 * @description lambda条件体系中最小的sql片段单元,提供sql文本和对应的命名参数
 * @author zhongxuchen
 */
public interface SqlSegment {

	/**
	 * @return 对应的sql片段
	 */
	String getSqlSegment();

	/**
	 * @return sql片段中命名参数对应的参数值集合,无参数返回null
	 */
	default Map<String, Object> getParams() {
		return null;
	}
}
