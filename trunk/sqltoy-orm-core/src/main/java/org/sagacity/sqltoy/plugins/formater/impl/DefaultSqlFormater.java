package org.sagacity.sqltoy.plugins.formater.impl;

import org.sagacity.sqltoy.plugins.formater.SqlFormater;

import com.alibaba.druid.sql.SQLUtils;

/**
 * @project sagacity-sqltoy
 * @description 对sql进行格式化,默认提供基于阿里的druid进行sql输出格式化
 * @author zhongxuchen
 * @version v1.0,Date:2023-02-03
 * @modify Date:2023-02-03,修改说明
 */
public class DefaultSqlFormater implements SqlFormater {

	@Override
	public String format(String sql, String dialect) {
		return SQLUtils.format(sql, dialect);
	}

}
