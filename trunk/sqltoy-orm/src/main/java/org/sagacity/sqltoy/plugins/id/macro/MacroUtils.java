/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.id.macro.impl.Case;
import org.sagacity.sqltoy.plugins.id.macro.impl.DateFormat;
import org.sagacity.sqltoy.plugins.id.macro.impl.SubString;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 宏处理工具
 * @author zhongxuchen
 * @version v1.0,Date:2018年5月25日
 */
public class MacroUtils {
	/**
	 * 转换器的格式
	 */
	private static Pattern macroPattern = Pattern.compile("@[a-zA-Z]+[0-9]*[\\-]?[a-zA-Z]*\\([\\w\\W]*\\)");

	/**
	 * 字符串中内嵌参数的匹配模式 update by chenrenfei 2016-8-24 完善表达式
	 */
	private final static Pattern paramPattern = Pattern.compile(
			"(\\$|\\#)\\{\\s*\\_?[0-9a-zA-Z\u4e00-\u9fa5]+((\\.|\\_)[0-9a-zA-Z\u4e00-\u9fa5]+)*(\\[\\d*(\\,)?\\d*\\])?\\s*\\}");

	private static final HashMap<String, String> filters = new HashMap<String, String>() {
		private static final long serialVersionUID = 2445408357544337801L;

		{
			put("(", ")");
			put("'", "'");
			put("\"", "\"");
			put("[", "]");
			put("{", "}");
		}
	};

	// 宏实现类
	private static Map<String, AbstractMacro> macros = new HashMap<String, AbstractMacro>();

	static {
		macros.put("@df", new DateFormat());
		macros.put("@day", new DateFormat());
		macros.put("@case", new Case());
		macros.put("@substr", new SubString());
		macros.put("@substring", new SubString());
	}

	/**
	 * @todo 宏替换,默认先执行内部后执行外部
	 * @param hasMacroStr
	 * @param keyValues
	 * @return
	 */
	public static String replaceMacros(String hasMacroStr, Map<String, Object> keyValues) {
		return replaceMacros(hasMacroStr, keyValues, null, false, macros, null);
	}

	/**
	 * @todo 递归调用解析字符串中的转换器
	 * @param hasMacroStr     含macro宏的字符串
	 * @param keyValues
	 * @param paramsValues
	 * @param isOuter(isOuter 当@abc(@do(),xxx):为true表示从最外层的macro@abce,false则会先执行@do()
	 *                        然后再执行@abc())
	 * @param macros
	 * @param extSign         扩展标记，目前主要给@include使用，传递dialect
	 * @return
	 */
	public static String replaceMacros(String hasMacroStr, Map<String, Object> keyValues, Object paramsValues,
			boolean isOuter, Map<String, AbstractMacro> macros, String extSign) {
		if (StringUtil.isBlank(hasMacroStr)) {
			return hasMacroStr;
		}
		if (StringUtil.matches(hasMacroStr, macroPattern)) {
			String source = hasMacroStr;
			Matcher matcher = macroPattern.matcher(source);
			String matchedMacro = null;
			String tmpMatchedMacro = null;
			int count = 0;
			int macroIndex = 0;
			int index = 0;
			int startIndex = 0;
			while (matcher.find()) {
				index = matcher.start();
				tmpMatchedMacro = matcher.group();
				// 判断是否是转换器
				if (isMacro(macros, tmpMatchedMacro, true)) {
					count++;
					matchedMacro = tmpMatchedMacro;
					macroIndex = startIndex + index;
					if (isOuter) {
						break;
					}
				}
				startIndex = startIndex + index + 1;
				source = source.substring(index + 1);
				matcher = macroPattern.matcher(source);
			}
			// 匹配不上，则表示字符串中的转换器已经全部执行被替换，返回结果终止递归
			if (count == 0) {
				return hasMacroStr;
			}
			int sysMarkIndex = StringUtil.getSymMarkIndex("(", ")", matchedMacro, 0);
			// 截取宏前面部分的sql，用于宏中做一些特定关联判断(2023-8-30)
			String preSql = (macroIndex > 0) ? hasMacroStr.substring(0, macroIndex) : "";
			// 得到最后一个转换器中的参数
			String macroParam = matchedMacro.substring(matchedMacro.indexOf("(") + 1, sysMarkIndex);
			String macroName = matchedMacro.substring(0, matchedMacro.indexOf("("));
			String macroStr = matchedMacro.substring(0, sysMarkIndex + 1);
			// 调用转换器进行计算
			AbstractMacro macro = macros.get(macroName);
			String result = macro.execute(StringUtil.splitExcludeSymMark(macroParam, ",", filters), keyValues,
					paramsValues, preSql, extSign);
			// 最外层是转换器，则将转结果直接以对象方式返回
			if (hasMacroStr.trim().equals(macroStr.trim())) {
				return result;
			}
			String macroResult = (result == null) ? "" : result;
			hasMacroStr = replaceStr(hasMacroStr, macroStr, macroResult, macroIndex);
			return replaceMacros(hasMacroStr, keyValues, paramsValues, isOuter, macros, extSign);
		}
		return hasMacroStr;
	}

	/**
	 * @todo <b>判断匹配的字符串是否是转换器</b>
	 * @param macros
	 * @param matchedStr
	 * @param isStart
	 * @return
	 */
	private static boolean isMacro(Map<String, AbstractMacro> macros, String matchedStr, boolean isStart) {
		int index = matchedStr.indexOf("(");
		if (matchedStr.startsWith("@") && index != -1) {
			if (macros.containsKey(matchedStr.substring(0, index))) {
				return true;
			}
		}
		return false;
	}

	private static String replaceStr(String source, String template, String target, int fromIndex) {
		if (source == null) {
			return null;
		}
		if (template == null) {
			return source;
		}
		if (fromIndex >= source.length() - 1) {
			return source;
		}
		int index = source.indexOf(template, fromIndex);
		if (index != -1) {
			source = source.substring(0, index).concat(target).concat(source.substring(index + template.length()));
		}
		return source;
	}

	/**
	 * @todo 替换变量参数
	 * @param template
	 * @param keyValues
	 * @return
	 */
	public static String replaceParams(String template, Map<String, Object> keyValues) {
		if (StringUtil.isBlank(template) || keyValues == null || keyValues.isEmpty()) {
			return template;
		}
		LinkedHashMap<String, String> paramsMap = parseParams(template);
		String result = template;
		if (paramsMap.size() > 0) {
			Map.Entry<String, String> entry;
			Object value;
			for (Iterator<Map.Entry<String, String>> iter = paramsMap.entrySet().iterator(); iter.hasNext();) {
				entry = iter.next();
				value = keyValues.get(entry.getValue());
				if (value != null) {
					// 支持枚举类型
					if (value instanceof Enum) {
						value = BeanUtil.getEnumValue(value);
					}
					result = replaceAllStr(result, entry.getKey().toString(), value.toString());
				}
			}
		}
		return result;
	}

	/**
	 * @todo 解析模板中的参数
	 * @param template
	 * @return
	 */
	private static LinkedHashMap<String, String> parseParams(String template) {
		LinkedHashMap<String, String> paramsMap = new LinkedHashMap<String, String>();
		Matcher m = paramPattern.matcher(template);
		String group;
		while (m.find()) {
			group = m.group();
			// key as ${name} value:name
			paramsMap.put(group, group.substring(2, group.length() - 1).trim().toLowerCase());
		}
		return paramsMap;
	}

	private static String replaceAllStr(String source, String template, String target) {
		if (source == null || template.equals(target)) {
			return source;
		}
		int index = source.indexOf(template, 0);
		int subLength = target.length() - template.length();
		int begin = index - 1;
		while (index != -1 && index >= begin) {
			source = source.substring(0, index).concat(target).concat(source.substring(index + template.length()));
			begin = index + subLength + 1;
			index = source.indexOf(template, begin);
		}
		return source;
	}
}
