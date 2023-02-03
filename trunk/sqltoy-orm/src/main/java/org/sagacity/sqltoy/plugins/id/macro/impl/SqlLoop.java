/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 此类不用于主键策略的配置,提供在sql中通过@loop(:args,loopContent,linkSign,start,end)
 *              函数来循环组织sql(借用主键里面的宏工具来完成@loop处理)
 * @author zhongxuchen
 * @version v1.0, Date:2020-9-23
 * @modify 2021-10-14 支持@loop(:args,and args[i].xxx,linkSign,start,end)
 *         args[i].xxx对象属性模式
 */
public class SqlLoop extends AbstractMacro {
	/**
	 * 匹配sql片段中的参数名称,包含:xxxx.xxx对象属性形式
	 */
	private final static Pattern paramPattern = Pattern
			.compile("\\:sqlToyLoopAsKey_\\d+A(\\.[a-zA-Z\u4e00-\u9fa5][0-9a-zA-Z\u4e00-\u9fa5_]*)*\\W");

	/**
	 * 是否跳过null和blank
	 */
	private boolean skipBlank = true;

	public SqlLoop() {
	}

	public SqlLoop(boolean skipBlank) {
		this.skipBlank = skipBlank;
	}

	@Override
	public String execute(String[] params, Map<String, Object> keyValues) {
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
		// 返回@blank(:paramName),便于#[ and @loop(:name,"name like ':name[i]'"," or ")]
		// 先loop后没有参数导致#[]中内容全部被剔除的缺陷
		if (loopValues == null || loopValues.length == 0) {
			return " @blank(:" + loopParam + ") ";
		}
		int start = 0;
		int end = loopValues.length;
		if (params.length > 3) {
			start = Integer.parseInt(params[3].trim());
		}
		if (start > loopValues.length - 1) {
			return " @blank(:" + loopParam + ") ";
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
		Iterator<String> keyEnums = keyValues.keySet().iterator();
		int index = 0;
		while (keyEnums.hasNext()) {
			key = keyEnums.next().toLowerCase();
			// 统一标准为paramName[i]模式
			if (lowContent.contains(":" + key + "[i]") || lowContent.contains(":" + key + "[index]")) {
				keys.add(key);
				// 统一转为:sqlToyLoopAsKey_1_模式,简化后续匹配
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[index\\]",
						":sqlToyLoopAsKey_" + index + "A");
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[i\\]", ":sqlToyLoopAsKey_" + index + "A");
				regParamValues.add(CollectionUtil.convertArray(keyValues.get(key)));
				index++;
			}
		}
		StringBuilder result = new StringBuilder();
		result.append(" @blank(:" + loopParam + ") ");
		String loopStr;
		index = 0;
		String[] loopParamNames;
		Object[] loopParamValues;
		Map<String, String[]> loopParamNamesMap = parseParams(loopContent);
		Object loopVar;
		for (int i = start; i < end; i++) {
			// 当前循环的值
			loopVar = loopValues[i];
			// 循环值为null或空白默认被跳过
			if (!skipBlank || StringUtil.isNotBlank(loopVar)) {
				loopStr = loopContent;
				if (index > 0) {
					result.append(" ");
					result.append(linkSign);
				}
				result.append(" ");
				// 替换paramName[i]或paramName[i].xxxx
				for (int j = 0; j < keys.size(); j++) {
					key = ":sqlToyLoopAsKey_" + j + "A";
					loopParamNames = loopParamNamesMap.get(key);
					// paramName[i] 模式
					if (loopParamNames.length == 0) {
						loopStr = loopStr.replaceAll(key, toString(regParamValues.get(j)[i]));
					} else {
						// paramName[i].xxxx 模式
						loopParamValues = BeanUtil.reflectBeanToAry(regParamValues.get(j)[i], loopParamNames);
						for (int k = 0; k < loopParamNames.length; k++) {
							loopStr = loopStr.replaceAll(key.concat(".").concat(loopParamNames[k]),
									toString(loopParamValues[k]));
						}
					}
				}
				result.append(loopStr);
				index++;
			}
		}
		result.append(" ");
		return result.toString();
	}

	/**
	 * @TODO 将参数值转成字符传
	 * @param paramValue
	 * @return
	 */
	private static String toString(Object paramValue) {
		String valueStr;
		if (paramValue instanceof Date || paramValue instanceof LocalDateTime) {
			valueStr = "" + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
		} else if (paramValue instanceof LocalDate) {
			valueStr = "" + DateUtil.formatDate(paramValue, "yyyy-MM-dd");
		} else if (paramValue instanceof LocalTime) {
			valueStr = "" + DateUtil.formatDate(paramValue, "HH:mm:ss");
		} else {
			valueStr = "" + paramValue;
		}
		return valueStr;
	}

	/**
	 * @todo 解析模板中的参数
	 * @param template
	 * @return
	 */
	public static Map<String, String[]> parseParams(String template) {
		Map<String, String[]> paramsMap = new HashMap<String, String[]>();
		Matcher m = paramPattern.matcher(template.concat(" "));
		String group;
		String key;
		int dotIndex;
		while (m.find()) {
			group = m.group();
			group = group.substring(0, group.length() - 1);
			dotIndex = group.indexOf(".");
			if (dotIndex != -1) {
				key = group.substring(0, dotIndex);
				String[] items = paramsMap.get(key);
				if (items == null) {
					paramsMap.put(key, new String[] { group.substring(dotIndex + 1) });
				} else {
					String[] newItems = new String[items.length + 1];
					newItems[items.length] = group.substring(dotIndex + 1);
					System.arraycopy(items, 0, newItems, 0, items.length);
					paramsMap.put(key, newItems);
				}
			} else {
				paramsMap.put(group, new String[] {});
			}
		}
		return paramsMap;
	}
}
