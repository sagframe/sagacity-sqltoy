/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.macro.impl;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.IllegalFormatFlagsException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
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
 * @modify 2023-05-01 支持loop中的内容体含#[and t.xxx=:xxx] 为null判断和 in (:args) 数组输出
 * @modify 2023-08-31 优化@loop在update语句参数为null的场景,之前缺陷是值为null时被转为field is
 *         null，正确模式field=null
 */
public class SqlLoop extends AbstractMacro {
	/**
	 * 匹配sql片段中的参数名称,包含:xxxx.xxx对象属性形式
	 */
	private final static Pattern paramPattern = Pattern
			.compile("\\:sqlToyLoopAsKey_\\d+A(\\.[a-zA-Z\u4e00-\u9fa5][0-9a-zA-Z\u4e00-\u9fa5_]*)*\\W");

	// sql中的比较运算符号
	public final static Pattern COMPARE_PATTERN = Pattern.compile("(!=|<>|\\^=|=|>=|<=|>|<)\\s*$");

	public final static String BLANK = " ";

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
	public String execute(String[] params, Map<String, Object> keyValues, Object paramValues, String preSql) {
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
		// sql中是否存在条件判空
		boolean hasNullFilter = (lowContent.indexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK) > 0) ? true : false;
		StringBuilder result = new StringBuilder();
		result.append(" @blank(:" + loopParam + ") ");
		String loopStr;
		index = 0;
		String[] loopParamNames;
		Object[] loopParamValues;
		Map<String, String[]> loopParamNamesMap = parseParams(loopContent);
		Object loopVar;
		// 循环的参数和对应值
		Map<String, Object> loopKeyValueMap = new HashMap<String, Object>();
		// 全部参数和对应值
		IgnoreKeyCaseMap<String, Object> allKeyValueMap = new IgnoreKeyCaseMap<String, Object>();
		for (int i = start; i < end; i++) {
			// 当前循环的值
			loopVar = loopValues[i];
			// 循环值为null或空白默认被跳过
			if (!skipBlank || StringUtil.isNotBlank(loopVar)) {
				loopStr = loopContent;
				if (index > 0) {
					result.append(BLANK);
					result.append(linkSign);
				}
				result.append(BLANK);
				loopKeyValueMap.clear();
				for (int j = 0; j < keys.size(); j++) {
					key = ":sqlToyLoopAsKey_" + j + "A";
					loopParamNames = loopParamNamesMap.get(key);
					// paramName[i] 模式
					if (loopParamNames.length == 0) {
						loopKeyValueMap.put(key, regParamValues.get(j)[i]);
					} else {
						// paramName[i].xxxx 模式
						loopParamValues = BeanUtil.reflectBeanToAry(regParamValues.get(j)[i], loopParamNames);
						for (int k = 0; k < loopParamNames.length; k++) {
							loopKeyValueMap.put(key.concat(".").concat(loopParamNames[k]), loopParamValues[k]);
						}
					}
				}
				// 处理#[ and t.xxx=:paramName]模式，决定是否要去除
				if (hasNullFilter) {
					allKeyValueMap.clear();
					// 放入整天sql涉及的参数，#[loop[i] and status=:status],即循环内容中存在非循环集合的条件参数:status
					allKeyValueMap.putAll(keyValues);
					allKeyValueMap.putAll(loopKeyValueMap);
					loopStr = processNullConditions(loopStr, allKeyValueMap);
				}
				// 只替换循环变量参数值
				loopStr = replaceAllArgs(loopStr, loopKeyValueMap, preSql);
				result.append(loopStr);
				index++;
			}
		}
		result.append(" ");
		return result.toString();
	}

	/**
	 * @TODO 处理loop循环sql中存在#[and t.xx=:xxx] 模式
	 * @param queryStr
	 * @param loopParamNamesMap
	 * @return
	 */
	private String processNullConditions(String queryStr, IgnoreKeyCaseMap<String, Object> loopParamNamesMap) {
		int pseudoMarkStart = queryStr.indexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK);
		if (pseudoMarkStart == -1) {
			return queryStr;
		}
		int beginMarkIndex, endMarkIndex;
		String preSql, markContentSql, tailSql;
		String[] fullParamNames;
		boolean hasNull = false;
		while (pseudoMarkStart != -1) {
			// 始终从最后一个#[]进行处理
			beginMarkIndex = queryStr.lastIndexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK);
			// update 2021-01-17 按照"["和"]" 找对称位置，兼容sql中存在[]场景
			endMarkIndex = StringUtil.getSymMarkIndex(SqlConfigParseUtils.SQL_PSEUDO_SYM_START_MARK,
					SqlConfigParseUtils.SQL_PSEUDO_END_MARK, queryStr, beginMarkIndex);
			if (endMarkIndex == -1) {
				throw new IllegalFormatFlagsException("sql语句中缺乏\"#[\" 相对称的\"]\"符号,请检查sql格式!");
			}
			// 最后一个#[前的sql
			preSql = queryStr.substring(0, beginMarkIndex).concat(BLANK);
			// 最后#[]中的查询语句,加空白减少substr(index+1)可能引起的错误
			markContentSql = queryStr.substring(beginMarkIndex + SqlConfigParseUtils.SQL_PSEUDO_START_MARK_LENGTH,
					endMarkIndex);
			tailSql = queryStr.substring(endMarkIndex + SqlConfigParseUtils.SQL_PSEUDO_END_MARK_LENGTH);
			// #[] 中存在的全部条件参数名称
			fullParamNames = SqlConfigParseUtils.getSqlParamsName(markContentSql, true);
			// 无参数直接剔除#[]中的内容
			if (fullParamNames == null) {
				queryStr = preSql.concat(BLANK).concat(tailSql);
			} else {
				// 判断#[] 中的参数是否有为null的
				hasNull = false;
				Object tmp;
				for (String key : fullParamNames) {
					tmp = loopParamNamesMap.get(":".concat(key));
					if (tmp == null) {
						tmp = loopParamNamesMap.get(key);
					}
					if (StringUtil.isBlank(tmp)) {
						hasNull = true;
						break;
					}
				}
				// 参数存在null直接剔除#[]中的内容
				if (hasNull) {
					queryStr = preSql.concat(BLANK).concat(tailSql);
				} else {
					queryStr = preSql.concat(BLANK).concat(markContentSql).concat(BLANK).concat(tailSql);
				}
			}
			// 继续下一个#[]
			pseudoMarkStart = queryStr.indexOf(SqlConfigParseUtils.SQL_PSEUDO_START_MARK);
		}
		return queryStr;
	}

	/**
	 * @TODO 替换循环语句中的参数
	 * @param queryStr
	 * @param loopParamNamesMap
	 * @param fullPreSql
	 * @return
	 */
	private String replaceAllArgs(String queryStr, Map<String, Object> loopParamNamesMap, String fullPreSql) {
		// 首位补充一个空白
		String matchStr = BLANK.concat(queryStr);
		Matcher m = SqlToyConstants.SQL_NAMED_PATTERN.matcher(matchStr);
		StringBuilder lastSql = new StringBuilder();
		int start = 0;
		String group;
		String paramName;
		String preSql;
		Object paramValue;
		boolean hasCompare = false;
		String key;
		int meter = 0;
		boolean updateSet = false;
		while (m.find()) {
			group = m.group();
			// 剔除\\W\\: 两位字符
			paramName = group.substring(2).trim();
			// 往后移1位(因为\\W表达式开头)
			preSql = matchStr.substring(start, m.start() + 1);
			// 以第一次为判断依据,判断是否是update table set field=? 模式
			if (meter == 0) {
				// update table set xxx=? 模式，或前面的sql中没有where关键词(补充了where判断)
				if (StringUtil.matches(fullPreSql.concat(BLANK).concat(preSql),
						SqlConfigParseUtils.UPDATE_EQUAL_PATTERN)
						|| !StringUtil.matches(fullPreSql.concat(BLANK).concat(preSql).concat(BLANK),
								SqlConfigParseUtils.WHERE_PATTERN)) {
					updateSet = true;
				}
			}
			// 是否是=:param 或!=:param等判断符号直接连接参数的情况，便于输出日期、字符参数时判断是否加单引号
			hasCompare = StringUtil.matches(preSql, COMPARE_PATTERN);
			key = ":".concat(paramName);
			paramValue = loopParamNamesMap.get(key);
			// 判断是否非循环参数，非循环参数在循环后继续处理
			if (!loopParamNamesMap.containsKey(key)) {
				preSql = preSql.concat(":").concat(paramName);
			} else if (paramValue == null) {
				preSql = compareNull(preSql, updateSet);
			} else {
				preSql = preSql.concat(toString(paramValue, hasCompare));
			}
			// 参数名称以空白结尾，处理完参数后补全空白
			if (StringUtil.matches(group, SqlToyConstants.BLANK_END)) {
				preSql = preSql.concat(BLANK);
			}
			lastSql.append(preSql);
			start = m.end();
			meter++;
		}
		// 没有别名参数
		if (start == 0) {
			return queryStr;
		}
		// 添加尾部sql
		lastSql.append(matchStr.substring(start));
		// 剔除开始补充的空白
		return lastSql.toString().substring(1);
	}

	/**
	 * @TODO 将=null 和!=null 转化为 is null 和 is not null
	 * @param preSql
	 * @param updateSet
	 * @return
	 */
	private String compareNull(String preSql, boolean updateSet) {
		String sqlPart = " is not ";
		// 判断不等于
		int compareIndex = StringUtil.matchIndex(preSql, SqlConfigParseUtils.NOT_EQUAL_PATTERN);
		// 判断等于
		if (compareIndex == -1) {
			compareIndex = StringUtil.matchIndex(preSql, SqlConfigParseUtils.EQUAL_PATTERN);
			if (compareIndex != -1) {
				// 不在where语句后面，即类似update table set field=? 形式
				if (updateSet) {
					compareIndex = -1;
				}
			}
			// 判断等于时是[^><!]= 非某个字符开头，要往后移动一位
			if (compareIndex != -1) {
				compareIndex = compareIndex + 1;
			}
			sqlPart = " is ";
		}
		// 存在where条件参数为=或<> 改成is (not) null
		if (compareIndex != -1) {
			return preSql.substring(0, compareIndex).concat(sqlPart).concat("null");
		}
		return preSql.concat("null");
	}

	/**
	 * @TODO 将参数值转成字符传
	 * @param paramValue
	 * @param hasCompare 参数前面的字符串是否是等于、不等于、大于等于、小于等于比较符号
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	private static String toString(Object paramValue, boolean hasCompare) {
		if (paramValue == null) {
			return "null";
		}
		// 参数前面是否是条件比较符号，如果是比较符号针对日期、字符串加单引号
		String sign = hasCompare ? "'" : "";
		String valueStr;
		if (paramValue instanceof CharSequence) {
			valueStr = sign + paramValue + sign;
		} else if (paramValue instanceof Timestamp) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss.SSS") + sign;
		} else if (paramValue instanceof Date || paramValue instanceof LocalDateTime) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + sign;
		} else if (paramValue instanceof LocalDate) {
			valueStr = sign + DateUtil.formatDate(paramValue, "yyyy-MM-dd") + sign;
		} else if (paramValue instanceof LocalTime) {
			valueStr = sign + DateUtil.formatDate(paramValue, "HH:mm:ss") + sign;
		} else if (paramValue instanceof Object[]) {
			valueStr = combineArray((Object[]) paramValue);
		} else if (paramValue instanceof Collection) {
			valueStr = combineArray(((Collection) paramValue).toArray());
		} else if (paramValue instanceof Enum) {
			valueStr = BeanUtil.getEnumValue(paramValue).toString();
		} else {
			valueStr = "" + paramValue;
		}
		return valueStr;
	}

	/**
	 * @TODO 组合in参数
	 * @param array
	 * @return
	 */
	private static String combineArray(Object[] array) {
		if (array == null || array.length == 0) {
			return " null ";
		}
		StringBuilder result = new StringBuilder();
		Object value;
		for (int i = 0; i < array.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			value = array[i];
			if (value == null) {
				result.append("null");
			} else {
				// 支持枚举类型
				if (value instanceof Enum) {
					value = BeanUtil.getEnumValue(value);
				}
				if (value instanceof CharSequence) {
					result.append("'" + value + "'");
				} else if (value instanceof Timestamp) {
					result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss.SSS") + "'");
				} else if (value instanceof Date || value instanceof LocalDateTime) {
					result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss") + "'");
				} else if (value instanceof LocalDate) {
					result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd") + "'");
				} else if (value instanceof LocalTime) {
					result.append("'" + DateUtil.formatDate(value, "HH:mm:ss") + "'");
				} else {
					result.append("" + value);
				}
			}
		}
		return result.toString();
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
