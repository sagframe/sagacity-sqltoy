/**
 * 
 */
package org.sagacity.sqltoy.plugins.formater.impl;

import org.sagacity.sqltoy.plugins.formater.SqlFormater;

import com.alibaba.druid.sql.SQLUtils;

/**
 * @project sagacity-sqltoy
 * @description 对sql进行格式化
 * @author zhongxuchen
 * @version v1.0, Date:2023年2月3日
 * @modify 2023年2月3日,修改说明
 */
public class DefaultSqlFormater implements SqlFormater {

	@Override
	public String format(String sql, String dialect) {
		return SQLUtils.format(sql, dialect);
	}

}
