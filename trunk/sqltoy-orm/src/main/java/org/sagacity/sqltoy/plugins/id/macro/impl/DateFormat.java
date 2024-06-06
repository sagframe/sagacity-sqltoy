/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.Date;
import java.util.Map;

import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sagacity-sqltoy
 * @description 进行日期格式化,如果想不包含日期@df('')
 * @author zhongxuchen
 * @version v1.0,Date:2018年5月25日
 */
public class DateFormat extends AbstractMacro {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.plugins.id.macro.AbstractMacro#execute(java.lang.Object)
	 */
	@Override
	public String execute(String[] params, Map<String, Object> keyValues, Object paramValues, String preSql,
			String extSign) {
		Object dateValue = null;
		String fmt = "yyMMdd";
		if (params != null) {
			if (params.length == 1) {
				fmt = params[0];
				dateValue = new Date();
			} else if (params.length > 1) {
				if (params[0].contains("$")) {
					dateValue = MacroUtils.replaceParams(params[0], keyValues);
				} else {
					if (keyValues != null && keyValues.containsKey(params[0])) {
						dateValue = keyValues.get(params[0]).toString();
					} else {
						dateValue = params[0];
					}
				}
				fmt = params[1];
			}
		}
		// 提出单引号和双引号
		String realFmt = fmt.replaceAll("\"", "").replaceAll("\\'", "").trim();
		if ("".equals(realFmt) || "null".equals(realFmt.toLowerCase())) {
			return "";
		}
		return DateUtil.formatDate(dateValue, realFmt);
	}

}
