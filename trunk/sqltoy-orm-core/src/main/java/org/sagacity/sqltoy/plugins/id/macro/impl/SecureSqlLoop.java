package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
 * @description SqlLoop增强,采用参数非拼接模式，防止sql注入
 * @author zhongxuchen
 * @version v1.0,Date:2025-05-18
 */
public class SecureSqlLoop extends AbstractMacro {
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

	public SecureSqlLoop() {
	}

	public SecureSqlLoop(boolean skipBlank) {
		this.skipBlank = skipBlank;
	}

	@Override
	public String execute(String[] params, Map<String, Object> keyValuesMap, Object paramValues, String preSql,
			String extSign) {
		if (params == null || params.length < 2 || keyValuesMap == null || keyValuesMap.size() == 0) {
			return " ";
		}
		IgnoreKeyCaseMap<String, Object> realKeyValuesMap = new IgnoreKeyCaseMap<String, Object>(keyValuesMap);
		// 第几个@secure-loop,避免参数名称重复
		int secureLoopCnt = SqlToyThreadDataHolder.incrementCounterAndGet();
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
			// update 2026-9-14 start为负时按0处理:原来直接用作下标会取loopValues[-1]抛数组越界
			if (start < 0) {
				start = 0;
			}
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
		String lowContent = loopContent.toLowerCase(Locale.ROOT);
		String key;
		Iterator<String> keyEnums = realKeyValuesMap.keySet().iterator();
		int index = 0;
		String keyNamePrefix = ":sqlToyLoopAsKey_";
		while (keyEnums.hasNext()) {
			key = keyEnums.next().toLowerCase(Locale.ROOT);
			// 统一标准为paramName[i]模式
			if (lowContent.contains(":" + key + "[i]") || lowContent.contains(":" + key + "[index]")) {
				keys.add(key);
				// 统一转为:sqlToyLoopAsKey_1_模式,简化后续匹配
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[index\\]", keyNamePrefix + index + "A");
				loopContent = loopContent.replaceAll("(?i)\\:" + key + "\\[i\\]", keyNamePrefix + index + "A");
				regParamValues.add(CollectionUtil.convertArray(realKeyValuesMap.get(key)));
				index++;
			}
		}
		// 循环体内引用的其他参数数组可能短于loop依据数组,按最短数组长度的下标截断,避免regParamValues.get(j)[i]越界
		int minRegLength = Integer.MAX_VALUE;
		for (Object[] regAry : regParamValues) {
			if (regAry != null && regAry.length < minRegLength) {
				minRegLength = regAry.length;
			}
		}
		if (minRegLength != Integer.MAX_VALUE && end > minRegLength) {
			end = minRegLength;
		}
		StringBuilder result = new StringBuilder();
		index = 0;
		String[] loopParamNames;
		Object[] loopParamValues;
		Map<String, String[]> loopParamNamesMap = MacroUtils.parseParams(paramPattern, loopContent);
		Object loopVar;
		// 循环的参数和对应值
		Map<String, Object> loopKeyValueMap = new HashMap<String, Object>();
		String realLoopContent;
		// 构建最终使用的参数名称前缀
		String realKeyNamePrefix = "sqlLoopKey" + (secureLoopCnt == 0 ? "" : secureLoopCnt) + "_S";
		String realKey;
		int paramCnt = 1;
		for (int i = start; i < end; i++) {
			realLoopContent = loopContent;
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
					key = keyNamePrefix + j + "A";
					loopParamNames = loopParamNamesMap.get(key);
					// update 2026-9-14 裸形态(:x[i])与属性形态(:x[i].prop)改为各自独立处理:parseParams对
					// 同一标记两者共用一条记录,原来按loopParamNames.length二选一,导致未被选中的形态
					// 在循环体中原样残留(最终sql里出现内部标记,执行期未绑定参数);
					// 引用后紧跟名字字符(如 like ':x[i]_%')的形态无法与参数名区分,先给出明确报错
					MacroUtils.validateRefForm(loopContent, key, keys.get(j));
					// 裸形态:整体作为参数值
					if (MacroUtils.containsRef(loopContent, key)) {
						realKey = realKeyNamePrefix + paramCnt + "B";
						realLoopContent = replaceParamRef(realLoopContent, key, ":".concat(realKey));
						loopKeyValueMap.put(realKey, regParamValues.get(j)[i]);
						paramCnt++;
					}
					// 属性形态:paramName[i].xxxx逐个属性取值
					if (loopParamNames != null && loopParamNames.length > 0) {
						loopParamValues = BeanUtil.reflectBeanToAry(regParamValues.get(j)[i], loopParamNames);
						for (int k = 0; k < loopParamNames.length; k++) {
							realKey = realKeyNamePrefix + paramCnt + "B";
							realLoopContent = replaceParamRef(realLoopContent,
									key.concat(".").concat(loopParamNames[k]), ":".concat(realKey));
							loopKeyValueMap.put(realKey, loopParamValues[k]);
							paramCnt++;
						}
					}
				}
				result.append(realLoopContent);
				index++;
			}
		}
		keyValuesMap.putAll(loopKeyValueMap);
		result.append(" ");
		return result.toString();
	}

	/**
	 * 按字面量替换循环体内的参数引用,并要求引用之后不是参数名/属性链字符。 update 2026-9-14
	 * 原实现用replaceAll(key+"."+property):其一,行循环内每次替换都隐式编译正则 (元素数×引用数
	 * 次编译);其二,连接用的"."是正则的任意字符;其三,没有后边界判断—— 属性名或标记互为前缀时会相互截断(:p[i].id 吃掉
	 * :p[i].idCard 的前缀、标记_1A 吃掉 _10A、 裸引用吃掉属性引用的前缀),被截断的引用会变成一个从未绑定的参数名(执行期直接失败)。
	 * 字面量匹配消除正则开销与"."通配;后边界(含".")判断使前缀关系不再相互污染。
	 * 
	 * @param content     循环体内容
	 * @param ref         参数引用(如:sqlToyLoopAsKey_0A 或 :sqlToyLoopAsKey_0A.id)
	 * @param replacement 替换后的参数名引用(如:sqlLoopKey_S1B)
	 * @return 替换结果
	 */
	private static String replaceParamRef(String content, String ref, String replacement) {
		int index = content.indexOf(ref);
		if (index == -1) {
			return content;
		}
		StringBuilder result = new StringBuilder(content.length());
		int from = 0;
		int after;
		while (index != -1) {
			after = index + ref.length();
			// 引用后紧跟参数名或属性链字符说明这里是更长的名字(如.idCard、a.b、_10A),交给对应的替换处理
			if (after >= content.length() || !MacroUtils.isParamRefChar(content.charAt(after))) {
				result.append(content, from, index).append(replacement);
				from = after;
			}
			index = content.indexOf(ref, index + 1);
		}
		return result.append(content, from, content.length()).toString();
	}
}
