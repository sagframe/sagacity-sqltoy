/**
 * 
 */
package org.sagacity.sqltoy.plugin.id.macro.impl;

import java.util.Date;
import java.util.HashMap;

import org.sagacity.sqltoy.plugin.id.macro.AbstractMacro;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sagacity-sqltoy4.2
 * @description 进行日期格式化
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DateFormat.java,Revision:v1.0,Date:2018年5月25日
 */
public class DateFormat extends AbstractMacro {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.plugin.id.macro.AbstractMacro#execute(java.lang.Object)
	 */
	@Override
	public String execute(String[] params, HashMap<String, Object> keyValues) {
		Object dateValue = new Date();
		String fmt = "yyMMdd";
		if (params.length == 1) {
			fmt = params[0];
		} else if (params.length > 1) {
			dateValue = keyValues.get(params[0].toLowerCase());
			fmt = params[1];
		}
		return DateUtil.formatDate(dateValue, fmt);
	}

}
