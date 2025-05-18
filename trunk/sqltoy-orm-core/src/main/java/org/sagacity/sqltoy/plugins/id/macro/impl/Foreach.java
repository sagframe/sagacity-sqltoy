package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.AbstractMacro;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 增强loop,采用参数非拼接模式，防止sql
 * @author zhongxuchen
 * @version v1.0, Date:2025-5-18
 */
public class Foreach extends AbstractMacro {
	/**
	 * 匹配sql片段中的参数名称,包含:xxxx.xxx对象属性形式
	 */
	public final static Pattern paramPattern = Pattern
			.compile("\\:sqlToyLoopAsKey_\\d+A(\\.[a-zA-Z\u4e00-\u9fa5][0-9a-zA-Z\u4e00-\u9fa5_]*)*\\W");

	public final static String BLANK = " ";

	/**
	 * 是否跳过null和blank
	 */
	private boolean skipBlank = true;

	public Foreach() {
	}

	public Foreach(boolean skipBlank) {
		this.skipBlank = skipBlank;
	}

	@Override
	public String execute(String[] params, Map<String, Object> keyValuesMap, Object paramValues, String preSql,
			String extSign) {
		if (params == null || params.length < 2 || keyValuesMap == null || keyValuesMap.size() == 0) {
			return " ";
		}
		IgnoreKeyCaseMap<String, Object> realKeyValuesMap = new IgnoreKeyCaseMap<String, Object>(keyValuesMap);
		int loopCount = SqlToyThreadDataHolder.incrementCounterAndGet();
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
		Object[] loopValues = CollectionUtil.convertArray(realKeyValuesMap.get(loopParam));
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
		Iterator<String> keyEnums = realKeyValuesMap.keySet().iterator();
		int index = 0;
		String asName = ":sqlToyLoopAsKey_";
		while (keyEnums.hasNext()) {
			key = keyEnums.next().toLowerCase();
			// 统一标准为paramName[i]模式
			if (lowContent.contains(":" + key + "[i]") || lowContent.contains(":" + key + "[index]")) {
				keys.add(key);
				// 统一转为:sqlToyLoopAsKey_1_模式,简化后续匹配
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[index\\]", asName + index + "A");
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[i\\]", asName + index + "A");
				regParamValues.add(CollectionUtil.convertArray(realKeyValuesMap.get(key)));
				index++;
			}
		}
		StringBuilder result = new StringBuilder();
		index = 0;
		String[] loopParamNames;
		Object[] loopParamValues;
		Map<String, String[]> loopParamNamesMap = MacroUtils.parseParams(paramPattern, loopContent);
		Object loopVar;
		// 循环的参数和对应值
		Map<String, Object> loopKeyValueMap = new HashMap<String, Object>();
		String loopStr;
		String keyStart = "sqlLoopKey" + (loopCount == 0 ? "" : loopCount) + "_S";
		String realKey;
		int paramCnt = 1;
		for (int i = start; i < end; i++) {
			loopStr = loopContent;
			// 当前循环的值
			loopVar = loopValues[i];
			// 循环值为null或空白默认被跳过
			if (!skipBlank || StringUtil.isNotBlank(loopVar)) {
				if (index > 0) {
					result.append(BLANK);
					result.append(linkSign);
				}
				result.append(BLANK);
				for (int j = 0; j < keys.size(); j++) {
					key = asName + j + "A";
					loopParamNames = loopParamNamesMap.get(key);
					// paramName[i] 模式
					if (loopParamNames.length == 0) {
						// 以字母结束,避免数字产生包含关系(S1,S12,S12包含了S1)
						realKey = keyStart + paramCnt + "B";
						loopStr = loopStr.replaceAll(key, ":".concat(realKey));
						loopKeyValueMap.put(realKey, regParamValues.get(j)[i]);
						paramCnt++;
					} else {
						// paramName[i].xxxx 模式
						loopParamValues = BeanUtil.reflectBeanToAry(regParamValues.get(j)[i], loopParamNames);
						for (int k = 0; k < loopParamNames.length; k++) {
							realKey = keyStart + paramCnt + "B";
							loopStr = loopStr.replaceAll(key.concat(".").concat(loopParamNames[k]),
									":".concat(realKey));
							loopKeyValueMap.put(realKey, loopParamValues[k]);
							paramCnt++;
						}
					}
				}
				result.append(loopStr);
				index++;
			}
		}
		keyValuesMap.putAll(loopKeyValueMap);
		result.append(" ");
		return result.toString();
	}

}
