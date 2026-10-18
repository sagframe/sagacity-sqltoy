package org.sagacity.sqltoy.lambda.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * @description 单个比较条件片段:字段名+比较方式+命名参数,如<code>STATUS = :p1</code>
 * @author zhongxuchen
 */
public class SegmentMeta implements SqlSegment {

	private final SqlCompare compare;

	/**
	 * 数据库字段名称
	 */
	private final String columnName;

	/**
	 * 命名参数名称
	 */
	private final String paramName;

	/**
	 * 参数值
	 */
	private final Object value;

	public SegmentMeta(SqlCompare compare, String columnName, String paramName, Object value) {
		this.compare = compare;
		this.columnName = columnName;
		this.paramName = paramName;
		this.value = value;
	}

	@Override
	public String getSqlSegment() {
		return compare.getMetaSql(columnName, paramName);
	}

	@Override
	public Map<String, Object> getParams() {
		Map<String, Object> params = new HashMap<>();
		params.put(paramName, value);
		return params;
	}

	public String getColumnName() {
		return columnName;
	}

	public String getParamName() {
		return paramName;
	}

	public SqlCompare getCompare() {
		return compare;
	}
}
