/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sagacity-sqltoy
 * @description 此类不用于主键策略的配置,提供在sql中通过@loop(:args,loopContent,linkSign,start,end)
 *              函数来循环组织sql(借用主键里面的宏工具来完成@loop处理)
 * @author zhongxuchen
 * @version v1.0, Date:2020-9-23
 * @modify 2020-9-23,修改说明
 */
public class SqlLoop extends AbstractMacro {

	@Override
	public String execute(String[] params, IgnoreKeyCaseMap<String, Object> keyValues) {
		if (params == null || params.length < 2 || keyValues == null || keyValues.size() == 0) {
			return " ";
		}
		// 剔除为了规避宏参数切割附加的符号
		String varStr;
		for (int i = 0; i < params.length; i++) {
			varStr = params[i].trim();
			if ((varStr.startsWith("'") && varStr.endsWith("'")) || (varStr.startsWith("\"") && varStr.endsWith("\""))
					|| (varStr.startsWith("{") && varStr.endsWith("}"))) {
				varStr = varStr.substring(1, varStr.length() - 1);
			}
			params[i] = varStr;
		}
		// 循环依据的数组参数
		String loopParam = params[0].trim();
		// 剔除:符号
		if (loopParam.startsWith(":")) {
			loopParam = loopParam.substring(1).trim();
		}
		// 循环内容
		String loopContent = params[1];
		// 循环连接符号(字符串)
		String linkSign = (params.length > 2) ? params[2] : " ";

		// 获取循环依据的参数数组值
		Object[] loopValues = CollectionUtil.convertArray(keyValues.get(loopParam));

		if (loopValues == null || loopValues.length == 0) {
			return " ";
		}
		int start = 0;
		int end = loopValues.length;
		if (params.length > 3) {
			start = Integer.parseInt(params[3].trim());
		}
		if (start > loopValues.length - 1) {
			return " ";
		}
		if (params.length > 4) {
			end = Integer.parseInt(params[4].trim());
		}
		if (end >= loopValues.length) {
			end = loopValues.length;
		}
		// 提取循环体内的参数对应的值
		List<String> keys = new ArrayList<String>();
		List<Object[]> regParamValues = new ArrayList<Object[]>();
		String lowContent = loopContent.toLowerCase();
		String key;
		Enumeration<String> keyEnums = keyValues.keys();
		while (keyEnums.hasMoreElements()) {
			key = keyEnums.nextElement().toLowerCase();
			// 统一标准为paramName[i]模式
			if (lowContent.contains(":" + key + "[i]") || lowContent.contains(":" + key + "[index]")) {
				keys.add(key);
				// 统一转为key.lowCase[i]模式
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[index\\]", ":" + key + "[i]");
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[i\\]", ":" + key + "[i]");
				regParamValues.add(CollectionUtil.convertArray(keyValues.get(key)));
			}
		}

		StringBuilder result = new StringBuilder();
		String loopStr;
		Object paramValue;
		String valueStr;
		int index = 0;
		for (int i = start; i < end; i++) {
			loopStr = loopContent;
			if (index > 0) {
				result.append(" ");
				result.append(linkSign);
			}
			result.append(" ");
			// 替换paramName[i]
			for (int j = 0; j < keys.size(); j++) {
				key = "\\:" + keys.get(j) + "\\[i\\]";
				paramValue = regParamValues.get(j)[i];
				if (paramValue instanceof Date || paramValue instanceof LocalDateTime) {
					valueStr = "" + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
				} else if (paramValue instanceof LocalDate) {
					valueStr = "" + DateUtil.formatDate(paramValue, "yyyy-MM-dd");
				} else if (paramValue instanceof LocalTime) {
					valueStr = "" + DateUtil.formatDate(paramValue, "HH:mm:ss");
				} else {
					valueStr = "" + paramValue;
				}
				loopStr = loopStr.replaceAll(key, valueStr);
			}
			result.append(loopStr);
			index++;
		}
		result.append(" ");
		return result.toString();
	}

}
