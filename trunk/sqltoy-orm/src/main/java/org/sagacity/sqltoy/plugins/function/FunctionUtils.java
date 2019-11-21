/**
 * 
 */
package org.sagacity.sqltoy.plugins.function;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 实现sql不同数据库方言的函数替换
 * @author zhongxuchen
 * @version v1.0, Date:2019年9月15日
 * @modify 2019年9月15日,修改说明
 */
public class FunctionUtils {
	private final static String funPackage = "org.sagacity.sqltoy.plugins.function.impl.";
	// 提供默认函数配置
	public final static String[] functions = { funPackage.concat("SubStr"), funPackage.concat("Trim"),
			funPackage.concat("Instr"), funPackage.concat("Concat"), funPackage.concat("ConcatWs"),
			funPackage.concat("Nvl"), funPackage.concat("DateFormat"), funPackage.concat("Now"),
			funPackage.concat("Length"), funPackage.concat("ToChar"), funPackage.concat("If"),
			funPackage.concat("GroupConcat") };

	private static List<IFunction> functionConverts = new ArrayList<IFunction>();

	public static String getDialectSql(String sql, String dialect) {
		if (functionConverts.isEmpty() || StringUtil.isBlank(dialect) || StringUtil.isBlank(sql)) {
			return sql;
		}
		return convertFunctions(dialect, sql);
	}

	/**
	 * @todo 执行不同数据库函数的转换
	 * @param dialect
	 * @param sqlContent
	 * @return
	 */
	private static String convertFunctions(String dialect, String sqlContent) {
		int dbType = DataSourceUtils.getDBType(dialect);
		IFunction function;
		String dialectSql = sqlContent;
		String dialectLow = dialect.toLowerCase();
		for (int i = 0; i < functionConverts.size(); i++) {
			function = functionConverts.get(i);
			// 方言为null或空白表示适配所有数据库,适配的方言包含当前方言也执行替换
			if (StringUtil.isBlank(function.dialects()) || function.dialects().toLowerCase().contains(dialectLow)) {
				dialectSql = replaceFunction(dialectSql, dbType, function);
			}
		}
		return dialectSql;
	}

	/**
	 * @todo 单个sql函数转换处理
	 * @param sqlContent
	 * @param dbType
	 * @param function
	 * @return
	 */
	private static String replaceFunction(String sqlContent, int dbType, IFunction function) {
		String dialectSql = sqlContent;
		Matcher matcher = function.regex().matcher(dialectSql);
		int index = -1;
		String functionParams;
		String[] args = null;
		int matchedIndex;
		int endMarkIndex = -1;
		StringBuilder result = new StringBuilder();
		String wrapResult;
		String functionName = null;
		boolean hasArgs = true;
		String matchedGroup;
		while (matcher.find()) {
			index = matcher.start();
			matchedGroup = matcher.group();
			// 是 function()模式
			if (matchedGroup.endsWith("(")) {
				hasArgs = true;
			} else {
				hasArgs = false;
			}
			matchedIndex = index + 1;
			// 函数(:args) 存在参数
			if (hasArgs) {
				functionName = dialectSql.substring(matchedIndex, dialectSql.indexOf("(", matchedIndex));
				endMarkIndex = StringUtil.getSymMarkIndex("(", ")", dialectSql, matchedIndex);
				functionParams = dialectSql.substring(dialectSql.indexOf("(", matchedIndex) + 1, endMarkIndex);
				args = StringUtil.splitExcludeSymMark(functionParams, ",", SqlToyConstants.filters);
			} else {
				args = null;
				endMarkIndex = matcher.end() - 1;
				functionName = dialectSql.substring(matchedIndex, endMarkIndex);
			}
			wrapResult = function.wrap(dbType, functionName, hasArgs, args);
			if (null == wrapResult) {
				result.append(dialectSql.substring(0, endMarkIndex + 1));
			} else {
				result.append(dialectSql.substring(0, matchedIndex)).append(wrapResult);
			}
			if (hasArgs) {
				dialectSql = dialectSql.substring(endMarkIndex + 1);
			} else {
				dialectSql = dialectSql.substring(endMarkIndex);
			}
			matcher.reset(dialectSql);
		}
		result.append(dialectSql);
		return result.toString();
	}

	/**
	 * @param functionConverts the functionConverts to set
	 */
	public static void setFunctionConverts(List<String> functionAry) {
		List<IFunction> converts = new ArrayList<IFunction>();
		try {
			if (functionAry != null && !functionAry.isEmpty()) {
				List<String> realConverts = new ArrayList<String>();
				boolean hasDefault = false;
				for (String convert : functionAry) {
					String[] ary = convert.split("\\,|\\;");
					for (String tmp : ary) {
						if (StringUtil.isNotBlank(tmp)) {
							if (tmp.equals("default") || tmp.equals("defaults")) {
								hasDefault = true;
							} else if (!realConverts.contains(tmp)) {
								realConverts.add(tmp);
							}
						}
					}
				}
				// 包含默认的函数
				if (hasDefault) {
					for (String convert : functions) {
						if (!realConverts.contains(convert)) {
							realConverts.add(convert);
						}
					}
				}
				String functionName = null;
				for (int i = 0; i < realConverts.size(); i++) {
					functionName = realConverts.get(i).toString().trim();
					// sql函数包名变更,修正调整后的包路径,保持兼容
					if (functionName.startsWith("org.sagacity.sqltoy")) {
						String funName = functionName.substring(functionName.lastIndexOf(".") + 1);
						converts.add((IFunction) (Class.forName(funPackage.concat(funName)).getDeclaredConstructor()
								.newInstance()));
					} else {
						converts.add((IFunction) (Class.forName(functionName).getDeclaredConstructor().newInstance()));
					}
				}
			} // 为null时启用默认配置
			else {
				for (String convert : functions) {
					converts.add((IFunction) (Class.forName(convert).getDeclaredConstructor().newInstance()));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		functionConverts = converts;
	}

	
}
