package org.sagacity.sqltoy.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 统一数据权限过滤配置模型
 * @author chenrenfei
 * @version v1.0, Date:2021-10-11
 */
public class DataAuthFilterConfig implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2593018971677025720L;

	/**
	 * 比如授权租户的id值
	 */
	private Object values;

	/**
	 * 是否强制约束数据授权范围，比如:授权机构是A、B、C,而前端传递过滤的参数是C、D,当强制约束时就抛出数据越权异常
	 */
	private boolean isForcelimit = true;

	public Object getValues() {
		return values;
	}

	public DataAuthFilterConfig setValues(Object values) {
		this.values = values;
		return this;
	}

	public boolean isForcelimit() {
		return isForcelimit;
	}

	public DataAuthFilterConfig setForcelimit(boolean isForcelimit) {
		this.isForcelimit = isForcelimit;
		return this;
	}

}
