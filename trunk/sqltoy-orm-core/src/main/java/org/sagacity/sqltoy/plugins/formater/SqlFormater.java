/**
 * 
 */
package org.sagacity.sqltoy.plugins.formater;

/**
 * @project sagacity-sqltoy
 * @description 对sql进行格式化输出
 * @author zhongxuchen
 * @version v1.0, Date:2023年2月3日
 * @modify 2023年2月3日,修改说明
 */
public interface SqlFormater {
	public default String format(String sql, String dialect) {
		return sql;
	}
}
