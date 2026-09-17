package org.sagacity.sqltoy.plugins.id.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * @version v1.0,Date:2018-05-25
 */
public class MacroUtils {
	/**
	 * 转换器的格式
	 * update 2026-9-15 修复宏名多连字符不分发:原正则[\-]?仅允许单个连字符,@secure-loop-full
	 * (两个连字符)永远匹配不上宏模式,宏体从不被执行(真库实测原样透传,循环参数被先行
	 * 名参转换破坏);改为允许多段"连字符+字母数字"组合
	 */
	private static Pattern macroPattern = Pattern.compile("@[a-zA-Z]+[0-9]*(?:\\-[a-zA-Z0-9]+)*\\([\\w\\W]*\\)");

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
	 * 宏替换,默认先执行内部后执行外部
	 * 
	 * @param hasMacroStr
	 * @param keyValues
	 * @return
	 */
	public static String replaceMacros(String hasMacroStr, Map<String, Object> keyValues) {
		return replaceMacros(hasMacroStr, keyValues, null, false, macros, null);
	}

	/**
	 * 递归调用解析字符串中的转换器
	 * 
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
			hasMacroStr = replaceFirst(hasMacroStr, macroStr, macroResult, macroIndex);
			return replaceMacros(hasMacroStr, keyValues, paramsValues, isOuter, macros, extSign);
		}
		return hasMacroStr;
	}

	/**
	 * 判断匹配的字符串是否是转换器
	 * 
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

	/**
	 * 从开始位置替换一次
	 * 
	 * @param source
	 * @param template
	 * @param target
	 * @param fromIndex
	 * @return
	 */
	private static String replaceFirst(String source, String template, String target, int fromIndex) {
		if (source == null || template == null || template.isEmpty() || template.equals(target)) {
			return source;
		}
		int realFrom = Math.max(0, fromIndex);
		if (realFrom >= source.length() - 1) {
			return source;
		}
		int index = source.indexOf(template, realFrom);
		if (index != -1) {
			source = source.substring(0, index).concat(target).concat(source.substring(index + template.length()));
		}
		return source;
	}

	/**
	 * 替换变量参数
	 * 
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
					result = StringUtil.replaceAllStr(result, entry.getKey(), value.toString());
				}
			}
		}
		return result;
	}

	/**
	 * 解析模板中的参数
	 * 
	 * @param paramPattern
	 * @param template
	 * @return
	 */
	public static Map<String, String[]> parseParams(Pattern paramPattern, String template) {
		Map<String, String[]> paramsMap = new HashMap<String, String[]>();
		Matcher m = paramPattern.matcher(template.concat(" "));
		String group;
		String key;
		int dotIndex;
		int start = 0;
		while (m.find(start)) {
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
				// update 2026-9-14 裸形态不覆盖已解析出的属性列表:同一标记既当整体引用(:x[i])又当
				// 属性引用(:x[i].prop)时,原实现"后出现者覆盖"会让先出现的形态丢失解析结果,
				// 导致该形态在循环体内不被替换、标记原样漏进最终sql(执行期未绑定参数)
				// 裸形态是否存在由调用方用containsBareRef从模板文本判定
				if (!paramsMap.containsKey(group)) {
					paramsMap.put(group, new String[] {});
				}
			}
			start = m.end() - 1;
		}
		return paramsMap;
	}

	/**
	 * 查找标记在模板中实际出现的形态(含标记后紧跟的参数名字符)。 update 2026-9-14
	 * parseParams要求标记之后是非名字字符,故":x[i]_%"这类标记紧邻用户文本的形态
	 * (归一化后为":sqlToyLoopAsKey_0A_%")解析不到;此时既不能按标记本身登记/替换(该引用会原样残留
	 * 内部标记),也不能把下划线等文本吞进参数值(会静默改变语义,如like模式),调用方需据此给出明确报错。
	 * 
	 * @param template 模板(已做过标记替换的循环体内容)
	 * @param marker   标记(如:sqlToyLoopAsKey_0A)
	 * @return 实际出现的名字(含前导:,按出现顺序去重);以"."续接的属性形态不在此列(由属性分支处理)
	 */
	public static List<String> findRefNames(String template, String marker) {
		List<String> names = new ArrayList<String>();
		if (template == null || marker == null) {
			return names;
		}
		int index = template.indexOf(marker);
		int end;
		String name;
		while (index != -1) {
			end = index + marker.length();
			// 属性形态交由属性分支处理
			if (end >= template.length() || template.charAt(end) != '.') {
				while (end < template.length() && isParamNameChar(template.charAt(end))) {
					end++;
				}
				name = template.substring(index, end);
				if (!names.contains(name)) {
					names.add(name);
				}
			}
			index = template.indexOf(marker, index + 1);
		}
		return names;
	}

	/**
	 * 判断模板中是否存在标记的引用(裸形态或紧跟名字字符的形态均算,属性形态由调用方另行处理)
	 */
	public static boolean containsRef(String template, String marker) {
		return !findRefNames(template, marker).isEmpty();
	}

	/**
	 * 校验标记在模板中的出现形态是否合法:引用之后必须是参数名之外的分隔字符。 循环变量引用形如
	 * :param[i](可带.属性),其后紧跟字母/数字/下划线时无法与参数名区分, 原实现会因此抛NPE或把内部标记漏进最终sql,此处统一给出可定位的报错
	 * 
	 * @param template  模板
	 * @param marker    标记(如:sqlToyLoopAsKey_0A)
	 * @param loopParam 用户书写的循环参数名(用于报错提示)
	 */
	public static void validateRefForm(String template, String marker, String loopParam) {
		for (String refName : findRefNames(template, marker)) {
			if (refName.length() > marker.length()) {
				throw new IllegalArgumentException("invalid loop variable reference:[" + refName
						+ "] the loop variable [:" + loopParam
						+ "[i]] must be followed by a non-name character(space/comma/quote/bracket etc.), please adjust the sql!");
			}
		}
	}

	/**
	 * 参数名/属性链的续接字符:续接字符说明这里是更长的名字(如.idCard、a.b、_10A)
	 * 
	 * @param ch
	 * @return
	 */
	public static boolean isParamRefChar(char ch) {
		return ch == '_' || ch == '.' || Character.isLetterOrDigit(ch);
	}

	/**
	 * 参数名字符(与sql参数名扫描器一致):字母/数字/下划线/中文
	 * 
	 * @param ch
	 * @return
	 */
	private static boolean isParamNameChar(char ch) {
		return ch == '_' || Character.isLetterOrDigit(ch);
	}

	/**
	 * 解析模板中的参数
	 * 
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
			paramsMap.put(group, group.substring(2, group.length() - 1).trim().toLowerCase(Locale.ROOT));
		}
		return paramsMap;
	}
}
