/**
 * 
 */
package org.sagacity.sqltoy.plugins.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	private final static Map<String, String> functionNames = new HashMap<String, String>() {
		{
			put("substr", "SubStr");
			put("trim", "Trim");
			put("instr", "Instr");
			put("concat", "Concat");
			put("concatws", "ConcatWs");
			put("nvl", "Nvl");
			put("dateformat", "DateFormat");
			put("now", "Now");
			put("length", "Length");
			put("tochar", "ToChar");
			put("if", "If");
			put("groupconcat", "GroupConcat");

		}
	};
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
				// 参数中包含同样的函数，通过递归替换
				if (StringUtil.matches(functionParams, function.regex())) {
					functionParams = replaceFunction(functionParams, dbType, function);
				}
				if (functionParams == null || functionParams.trim().equals("")) {
					args = null;
				} else {
					args = StringUtil.splitExcludeSymMark(functionParams, ",", SqlToyConstants.filters);
				}
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
	 * @param functionAry the functionConverts to set
	 */
	public static void setFunctionConverts(List<String> functionAry) {
		if (functionAry == null || functionAry.isEmpty()) {
			return;
		}
		List<IFunction> converts = new ArrayList<IFunction>();
		try {
			List<String> realConverts = new ArrayList<String>();
			boolean hasDefault = false;
			for (String convert : functionAry) {
				String[] ary = convert.split("\\,|\\;");
				for (String tmp : ary) {
					if (StringUtil.isNotBlank(tmp)) {
						if ("default".equals(tmp) || "defaults".equals(tmp)) {
							hasDefault = true;
						} else if (!realConverts.contains(tmp)) {
							realConverts.add(tmp);
						}
					}
				}
			}
			// 包含默认的函数,将默认的在后面加载
			if (hasDefault) {
				for (String convert : functions) {
					if (!realConverts.contains(convert)) {
						realConverts.add(convert);
					}
				}
			}
			String functionName = null;
			// 排除重复,让自定义同名函数生效
			Set<String> nameSet = new HashSet<String>();
			String className;
			for (int i = 0; i < realConverts.size(); i++) {
				functionName = realConverts.get(i).trim();
				// sql函数包名变更,修正调整后的包路径,保持兼容
				if (functionName.startsWith("org.sagacity.sqltoy")) {
					functionName = funPackage.concat(functionName.substring(functionName.lastIndexOf(".") + 1));
				} // trim、nvl等简写模式
				else if (!functionName.contains(".") && functionNames.containsKey(functionName.toLowerCase())) {
					functionName = funPackage.concat(functionNames.get(functionName.toLowerCase()));
				}
				className = functionName.substring(functionName.lastIndexOf(".") + 1).toLowerCase();
				// 名字已经存在的排除
				if (!nameSet.contains(className)) {
					converts.add((IFunction) (Class.forName(functionName).getDeclaredConstructor().newInstance()));
					nameSet.add(className);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		functionConverts = converts;
	}

}
