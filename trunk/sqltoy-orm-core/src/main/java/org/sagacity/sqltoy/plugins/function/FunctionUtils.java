package org.sagacity.sqltoy.plugins.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 实现sql不同数据库方言的函数替换
 * @author zhongxuchen
 * @version v1.0,Date:2019-09-15
 * @modify Date:2019-09-15,修改说明
 */
public class FunctionUtils {
	private final static Logger logger = LoggerFactory.getLogger(FunctionUtils.class);

	private final static String funPackage = "org.sagacity.sqltoy.plugins.function.impl.";
	// 提供默认函数配置
	// update 2026-9-6 DateDiff(口径统一:两参=d1-d2天差,三参=年月分量差/自然天/完整单位截断)与
	// Decode(转case when)经14库真实执行验证后默认注册
	public final static String[] functions = { funPackage.concat("SubStr"), funPackage.concat("Trim"),
			funPackage.concat("Instr"), funPackage.concat("Concat"), funPackage.concat("ConcatWs"),
			funPackage.concat("Nvl"), funPackage.concat("DateFormat"), funPackage.concat("Now"),
			// update 2026-10-4 CurrentDate(当前日期不带时间,curdate/CURRENT_DATE双形态)
			funPackage.concat("CurrentDate"), funPackage.concat("Length"), funPackage.concat("ToChar"),
			funPackage.concat("If"), funPackage.concat("GroupConcat"), funPackage.concat("ToNumber"),
			funPackage.concat("ToDate"), funPackage.concat("DateDiff"), funPackage.concat("Decode"),
			// update 2026-10-11 补FindInSet(MySQL系原生,其余库以"补逗号+精确子串定位"改写)
			funPackage.concat("FindInSet"),
			// update 2026-10-11 常用函数补齐:uuid/year-month-day分量提取/last_day/add_months
			funPackage.concat("Uuid"), funPackage.concat("YearMonthDay"), funPackage.concat("LastDay"),
			funPackage.concat("AddMonths"),
			// update 2026-10-11 日期区间加减族:date_add/date_sub/dateadd/adddate/subdate
			funPackage.concat("DateAdd"),
			// update 2026-10-11 A方案:left/right、lpad/rpad、EXTRACT/date_part源、日期截断族
			funPackage.concat("LeftRight"), funPackage.concat("Pad"), funPackage.concat("Extract"),
			funPackage.concat("DateTrunc"),
			// update 2026-10-11 mod取模(18环境仅sqlserver无mod函数,转%运算符)
			funPackage.concat("Mod") };

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
			put("currentdate", "CurrentDate");
			put("length", "Length");
			put("tochar", "ToChar");
			put("if", "If");
			put("groupconcat", "GroupConcat");
			put("tonumber", "ToNumber");
			put("todate", "ToDate");
			// update 2026-9-9 补齐默认注册列表中缺失的简写映射(原配置datediff/decode简写不生效)
			put("datediff", "DateDiff");
			put("decode", "Decode");
			// update 2026-10-11 常用函数补齐的简写映射
			put("uuid", "Uuid");
			put("year", "YearMonthDay");
			put("month", "YearMonthDay");
			put("day", "YearMonthDay");
			put("lastday", "LastDay");
			put("addmonths", "AddMonths");
			put("dateadd", "DateAdd");
			put("left", "LeftRight");
			put("right", "LeftRight");
			put("lpad", "Pad");
			put("rpad", "Pad");
			put("extract", "Extract");
			put("datepart", "Extract");
			put("trunc", "DateTrunc");
			put("datetrunc", "DateTrunc");
			put("mod", "Mod");
			put("findinset", "FindInSet");
		}
	};
	private static volatile List<IFunction> functionConverts = new ArrayList<IFunction>();

	public static String getDialectSql(String sql, String dialect) {
		if (functionConverts.isEmpty() || StringUtil.isBlank(dialect) || StringUtil.isBlank(sql)) {
			return sql;
		}
		return convertFunctions(dialect, sql);
	}

	/**
	 * 执行不同数据库函数的转换
	 * 
	 * @param dialect
	 * @param sqlContent
	 * @return
	 */
	private static String convertFunctions(String dialect, String sqlContent) {
		int dbType = DataSourceUtils.getDBType(dialect);
		IFunction function;
		String dialectLow = dialect.toLowerCase(Locale.ROOT);
		// update 2026-10-9 修复串首裸函数永不转换:整条sql即函数表达式(如sql片段include的碎片形态)
		// 时函数名前无任何字符,适配正则\W(函数名)\(恒不命中;入口加空格哨兵使串首函数获得\W
		// 前缀参与探测与转换(内部全部绝对偏移随哨兵统一平移,无需逐处适配),返回前精确剥离;
		// 常规SQL以select/insert等关键字开头,行为不变
		// update 2026-10-19 串尾同样补哨兵:sysdate/current_date等裸关键字位于整条SQL末尾时
		// 正则的\W后缀无字符可匹配,原实现漏转换(PG等目标库报函数不存在)
		String dialectSql = " ".concat(sqlContent).concat(" ");
		// update 2026-9-14 预检加速(检测不改写,无耦合):常态SQL不含任何可转换函数,原循环
		// 对每个适配函数各做一次全SQL maskLiterals拷贝(注册函数最多16次);预检在原串的
		// 单次掩码上做只读正则探测,无任何命中直接返回原串(仅1次拷贝);有命中才进入改写
		// 循环(结构与原形态一致,改写会变更串长,循环内掩码仍按原逻辑每函数各自重算)
		String preCheckMask = null;
		boolean hasMatch = false;
		for (int i = 0, n = functionConverts.size(); i < n && !hasMatch; i++) {
			function = functionConverts.get(i);
			if (matchDialect(function.dialects(), dialectLow)) {
				if (preCheckMask == null) {
					preCheckMask = SqlConfigParseUtils.maskLiterals(dialectSql,
							SqlConfigParseUtils.isBackslashEscapeDialect(dbType));
				}
				hasMatch = function.regex().matcher(preCheckMask).find();
			}
		}
		if (!hasMatch) {
			return sqlContent;
		}
		for (int i = 0, n = functionConverts.size(); i < n; i++) {
			function = functionConverts.get(i);
			// 方言为null或空白表示适配所有数据库,适配的方言包含当前方言也执行替换
			if (matchDialect(function.dialects(), dialectLow)) {
				dialectSql = replaceFunction(dialectSql, dbType, function);
			}
		}
		// 剥离首尾哨兵(replaceFunction的result恒自串首0位构造,首字符必为前导哨兵;
		// 尾部哨兵用于让串尾裸关键字获得\W后缀,无改写时原样保留)
		String converted = dialectSql.substring(1);
		return converted.endsWith(" ") ? converted.substring(0, converted.length() - 1) : converted;
	}

	/**
	 * update 2026-9-9 方言匹配改为按逗号/分号切分后逐项精确比对:原contains子串匹配存在
	 * 双向偏差(dialects="oracle11"会被当前方言"oracle"误命中;dialects="oracle"又匹配不上
	 * 方言"oracle11"),自定义函数须显式枚举适配的方言名
	 * 
	 * @param dialects   函数声明的适配方言串,null/空白表示适配所有数据库
	 * @param dialectLow 当前方言(小写)
	 * @return 是否适配当前方言
	 */
	private static boolean matchDialect(String dialects, String dialectLow) {
		if (StringUtil.isBlank(dialects)) {
			return true;
		}
		for (String item : dialects.toLowerCase(Locale.ROOT).split("\\,|\\;")) {
			if (item.trim().equals(dialectLow)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 单个sql函数转换处理
	 *
	 * @param sqlContent
	 * @param dbType
	 * @param function
	 * @return
	 */
	private static String replaceFunction(String sqlContent, int dbType, IFunction function) {
		String dialectSql = sqlContent;
		// update 2026-9-5 函数匹配在字面量掩码串上进行(掩码串与原串等长,位置一致),
		// 内容截取仍基于原串:规避字面量内的函数文本(如'nvl(a,b)')被误转换破坏字面量
		String maskSql = SqlConfigParseUtils.maskLiterals(dialectSql,
				SqlConfigParseUtils.isBackslashEscapeDialect(dbType));
		Matcher matcher = function.regex().matcher(maskSql);
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
				// 引号感知配对:规避函数参数中字面量内的括号(如nvl(remark,'('))被误当参数终结符
				endMarkIndex = StringUtil.getSymMarkIndexSkipQuoted("(", ")", dialectSql, matchedIndex);
				functionParams = dialectSql.substring(dialectSql.indexOf("(", matchedIndex) + 1, endMarkIndex);
				// 参数中包含同样的函数，通过递归替换;update 2026-10-9 修复参数串首位的嵌套函数
				// (如nvl(nvl(a,b),c)、nvl(ifnull(a,b),c)的内层)无前导\W字符正则无法命中而漏转换,
				// 加空格哨兵参与探测与递归后精确剥离(replaceFunction保持串首字符,substring(1)恰好去除哨兵)
				String recursionParams = " ".concat(functionParams);
				if (StringUtil.matches(recursionParams, function.regex())) {
					functionParams = replaceFunction(recursionParams, dbType, function).substring(1);
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
			// update 2026-10-4 尾部子句钩子:在字面量掩码串上探测(掩码与原串等长同位,防字面量内
			// within group等文本误伤),命中后在原串按平衡括号消费整个子句(防order by f(x)内嵌
			// 括号截断),suffix原文交由wrapWithSuffix处理;endMarkIndex延伸后,IGNORE/替换两条
			// 路径与maskSql裁剪自然连带消费suffix,无需平行逻辑
			String suffix = null;
			if (hasArgs && function.suffixPattern() != null) {
				Matcher suffixMatcher = function.suffixPattern().matcher(maskSql);
				suffixMatcher.region(endMarkIndex + 1, maskSql.length());
				if (suffixMatcher.lookingAt()) {
					if (function.suffixNeedsParen()) {
						// pattern以\(结尾,region为绝对偏移,开括号位置即end()-1
						int openIdx = suffixMatcher.end() - 1;
						int closeIdx = StringUtil.getSymMarkIndexSkipQuoted("(", ")", dialectSql, openIdx);
						if (closeIdx > openIdx) {
							suffix = dialectSql.substring(endMarkIndex + 1, closeIdx + 1);
							endMarkIndex = closeIdx;
						}
					} else {
						// update 2026-10-11 裸操作符子句(如find_in_set后的">0"):无括号外壳,
						// 按pattern命中长度消费;matcher以region起点(endMarkIndex+1)为基准,
						// end()为绝对终点,消费至end()-1与括号形态的closeIdx口径对齐
						suffix = dialectSql.substring(endMarkIndex + 1, suffixMatcher.end());
						endMarkIndex = suffixMatcher.end() - 1;
					}
				}
			}
			wrapResult = (suffix == null) ? function.wrap(dbType, functionName, hasArgs, args)
					: function.wrapWithSuffix(dbType, functionName, suffix, args);
			if (null == wrapResult) {
				result.append(dialectSql.substring(0, endMarkIndex + 1));
			} else {
				result.append(dialectSql.substring(0, matchedIndex)).append(wrapResult);
			}
			if (hasArgs) {
				dialectSql = dialectSql.substring(endMarkIndex + 1);
				maskSql = maskSql.substring(endMarkIndex + 1);
			} else if (null == wrapResult) {
				// update 2026-10-4 修复裸形态IGNORE分隔符重复:IGNORE分支已append至endMarkIndex+1
				// (含\W分隔符),此处仍从endMarkIndex续接导致分隔符输出两次(sysdate,,name)
				dialectSql = dialectSql.substring(endMarkIndex + 1);
				maskSql = maskSql.substring(endMarkIndex + 1);
			} else {
				dialectSql = dialectSql.substring(endMarkIndex);
				maskSql = maskSql.substring(endMarkIndex);
			}
			matcher.reset(maskSql);
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
			for (String functionName : resolveFunctionClassNames(functionAry)) {
				converts.add((IFunction) (Class.forName(functionName).getDeclaredConstructor().newInstance()));
			}
		} catch (Exception e) {
			logger.error("setFunctionConverts method execution failed", e);
			// 某函数类加载失败时保留原有完整转换器列表,不用部分列表覆盖全局,
			// 避免部分函数的方言转换静默失效
			return;
		}
		functionConverts = converts;
	}

	/**
	 * update 2026-10-18 (6.0.8) 自 setFunctionConverts 抽出的函数类名解析逻辑并公开:
	 * default/defaults 关键字展开为内置默认集、org.sagacity.sqltoy 旧包路径修正、trim/nvl 等简写展开、
	 * 按小写类名去重(让自定义同名函数生效),与运行期实例化语义逐字一致; AOT 处理器在构建期经此取得将被 Class.forName
	 * 实例化的类名清单注册反射 (native 下未注册类无法 Class.forName,配置了 functionConverts 的应用会启动即失败)
	 *
	 * @param functionAry 原始配置值(可含 default/defaults 关键字、简写、逗号/分号分隔条目)
	 * @return 解析后的函数实现类全限定名清单(实例化顺序)
	 */
	public static List<String> resolveFunctionClassNames(List<String> functionAry) {
		List<String> resolved = new ArrayList<String>();
		if (functionAry == null || functionAry.isEmpty()) {
			return resolved;
		}
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
		// 排除重复,让自定义同名函数生效
		Set<String> nameSet = new HashSet<String>();
		String className;
		for (int i = 0, n = realConverts.size(); i < n; i++) {
			String functionName = realConverts.get(i).trim();
			// sql函数包名变更,修正调整后的包路径,保持兼容
			if (functionName.startsWith("org.sagacity.sqltoy")) {
				functionName = funPackage.concat(functionName.substring(functionName.lastIndexOf(".") + 1));
			} // trim、nvl等简写模式
			else if (!functionName.contains(".") && functionNames.containsKey(functionName.toLowerCase(Locale.ROOT))) {
				functionName = funPackage.concat(functionNames.get(functionName.toLowerCase(Locale.ROOT)));
			}
			className = functionName.substring(functionName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
			// 名字已经存在的排除
			if (!nameSet.contains(className)) {
				nameSet.add(className);
				resolved.add(functionName);
			}
		}
		return resolved;
	}

	/**
	 * update 2026-9-10 sqlite日期参数归一为日期文本:JDBC绑定的日期列/参数为毫秒Long
	 * (strftime/date等日期函数直接作用于毫秒返回null或垃圾值),须datetime(x/1000,
	 * 'unixepoch','localtime')转本地墙钟文本——不带'localtime'为UTC墙钟,东八区本地午夜
	 * 毫秒值被推到前一日16:00,自然天差/日期格式化偏1天(sqlite实测);裸字符串字面量
	 * ('YYYY-MM-DD[...]')与函数调用表达式(含'(',如to_date/date/strftime等转换产物——
	 * sqlite日期函数均返回文本,且嵌套转换时外层函数先于内层被改写,前缀白名单不可枚举)
	 * 已是日期文本,原样保留;仅裸列名/占位符(JDBC绑定为毫秒Long)做归一
	 *
	 * @param arg 日期参数SQL片段(字面量/列/占位符/表达式)
	 * @return sqlite日期函数可消费的日期文本表达式
	 */
	/**
	 * @description 判断是否为单引号包裹的完整SQL字符串字面量(不含内嵌引号,内嵌情形保守不处理)
	 * @param value 原始参数
	 * @return true表示可安全做格式模型改写
	 */
	private static boolean isQuotedLiteral(String value) {
		if (value == null) {
			return false;
		}
		String tmp = value.trim();
		return tmp.length() > 1 && tmp.startsWith("'") && tmp.endsWith("'")
				&& tmp.indexOf('\'', 1) == tmp.length() - 1;
	}

	/**
	 * @description Java风格时间token归一为Oracle模型:java的HH=24小时/mm=分钟,而Oracle的HH=12小时/mm=月。
	 *              只归一冒号邻接的"HH:mm"/"HH:mm:ss"与"hh:mm"/"hh:mm:ss"形态,其余Oracle token原样保留;
	 *              归一后各SQL函数适配器可统一按Oracle模型分派,避免mm被误当分钟
	 * @param formatArg SQL格式参数(含引号的字面量)
	 * @return 归一后的格式参数
	 */
	public static String normalizeJavaTimePattern(String formatArg) {
		if (!isQuotedLiteral(formatArg)) {
			return formatArg;
		}
		String tmp = formatArg.trim();
		String content = tmp.substring(1, tmp.length() - 1);
		// 含双引号字面量片段时保守跳过,避免改写Oracle/MySQL格式串中的字面文本
		if (content.indexOf('"') >= 0) {
			return formatArg;
		}
		String normalized = content.replace("HH:mm:ss", "hh24:mi:ss").replace("HH:mm", "hh24:mi")
				.replace("hh:mm:ss", "hh:mi:ss").replace("hh:mm", "hh:mi");
		return normalized.equals(content) ? formatArg : "'" + normalized + "'";
	}

	/**
	 * @description 将Oracle/Java风格格式模型转换为MySQL % 风格token,供DateFormat(源为mysql date_format)
	 *              在非%写法时统一走既有%分派。Oracle的mm=月/mi=分钟;java的HH:mm=24小时/分钟;
	 *              裸HH按Java习惯作24小时,裸hh作12小时;双引号字面量片段原样保留
	 * @param formatArg SQL格式参数(含引号的字面量)
	 * @return MySQL % 风格格式参数;已是%风格或非字面量时原样返回
	 */
	public static String toMySqlFormat(String formatArg) {
		if (!isQuotedLiteral(formatArg)) {
			return formatArg;
		}
		String tmp = formatArg.trim();
		String content = tmp.substring(1, tmp.length() - 1);
		// 已是MySQL % 风格,保持原有分派
		if (content.indexOf('%') >= 0) {
			return formatArg;
		}
		StringBuilder out = new StringBuilder(content.length() + 8);
		for (int i = 0; i < content.length();) {
			char c = content.charAt(i);
			// Oracle/MySQL格式串中的双引号字面量片段原样保留
			if (c == '"') {
				int end = content.indexOf('"', i + 1);
				if (end < 0) {
					out.append(content.substring(i));
					break;
				}
				out.append(content, i, end + 1);
				i = end + 1;
				continue;
			}
			String rest = content.substring(i);
			String lower = rest.toLowerCase(Locale.ROOT);
			if (lower.startsWith("hh24")) {
				out.append("%H");
				i += 4;
				continue;
			}
			// java风格时间:HH=24小时、hh=12小时、mm=分钟
			if (rest.startsWith("HH:mm:ss")) {
				out.append("%H:%i:%s");
				i += 9;
				continue;
			}
			if (rest.startsWith("HH:mm")) {
				out.append("%H:%i");
				i += 5;
				continue;
			}
			if (rest.startsWith("hh:mm:ss")) {
				out.append("%h:%i:%s");
				i += 9;
				continue;
			}
			if (rest.startsWith("hh:mm")) {
				out.append("%h:%i");
				i += 5;
				continue;
			}
			if (lower.startsWith("yyyy")) {
				out.append("%Y");
				i += 4;
				continue;
			}
			if (lower.startsWith("yy")) {
				out.append("%y");
				i += 2;
				continue;
			}
			// 非时间上下文的mm为Oracle的月份
			if (lower.startsWith("mm")) {
				out.append("%m");
				i += 2;
				continue;
			}
			if (lower.startsWith("mi")) {
				out.append("%i");
				i += 2;
				continue;
			}
			if (lower.startsWith("dd")) {
				out.append("%d");
				i += 2;
				continue;
			}
			if (lower.startsWith("ss")) {
				out.append("%s");
				i += 2;
				continue;
			}
			// 裸HH按Java习惯24小时,裸hh按12小时
			if (rest.startsWith("HH")) {
				out.append("%H");
				i += 2;
				continue;
			}
			if (rest.startsWith("hh")) {
				out.append("%h");
				i += 2;
				continue;
			}
			out.append(c);
			i++;
		}
		String mapped = out.toString();
		return mapped.equals(content) ? formatArg : "'" + mapped + "'";
	}

	/**
	 * update 2026-10-11 oracle系日期函数的字面量参数包裹:裸字符串字面量按长度启发式包
	 * to_date(规避oracle默认NLS格式不认ISO串,真库实测ORA-01861);列/表达式原样返回
	 *
	 * @param arg 日期参数SQL片段
	 * @return oracle系可直接消费的日期表达式
	 */
	public static String oracleDateLiteralExpr(String arg) {
		if (arg == null) {
			return arg;
		}
		String tmp = arg.trim();
		if (tmp.length() > 2 && tmp.startsWith("'") && tmp.endsWith("'")
				&& tmp.indexOf((char) 39, 1) == tmp.length() - 1) {
			if (tmp.length() > 12) {
				return "to_date(" + tmp + ",'yyyy-MM-dd hh24:mi:ss')";
			}
			return "to_date(" + tmp + ",'yyyy-MM-dd')";
		}
		return arg;
	}

	/**
	 * update 2026-10-11 pg系日期运算的字面量参数定型:裸字符串字面量包::timestamp
	 * (og7/vastbase真库实测,unknown字面量+interval会按interval解析报错);列/表达式原样返回
	 *
	 * @param arg 日期参数SQL片段
	 * @return pg系可直接参与interval运算的表达式
	 */
	/**
	 * update 2026-10-11 日期函数字面量参数的cast定型(瀚高形态):裸字符串字面量包 cast(x as
	 * date)(瀚高真库实测:last_day/add_months对裸串报类型推断歧义,cast可用;
	 * 瀚高to_date未验证故不用oracle包裹形态);列/表达式原样返回
	 *
	 * @param arg 日期参数SQL片段
	 * @return 可直接参与日期函数的表达式
	 */
	public static String castLiteralDate(String arg) {
		if (arg == null) {
			return arg;
		}
		String tmp = arg.trim();
		if (tmp.length() > 2 && tmp.startsWith("'") && tmp.endsWith("'")
				&& tmp.indexOf((char) 39, 1) == tmp.length() - 1) {
			return "cast(" + tmp + " as date)";
		}
		return arg;
	}

	public static String castLiteralTimestamp(String arg) {
		if (arg == null) {
			return arg;
		}
		String tmp = arg.trim();
		if (tmp.length() > 2 && tmp.startsWith("'") && tmp.endsWith("'")
				&& tmp.indexOf((char) 39, 1) == tmp.length() - 1) {
			return "(" + tmp + ")::timestamp";
		}
		return arg;
	}

	public static String sqliteDateTextExpr(String arg) {
		if (arg == null) {
			return arg;
		}
		String tmp = arg.trim();
		if (tmp.length() > 2 && tmp.startsWith("'") && tmp.endsWith("'") && tmp.indexOf('\'', 1) == tmp.length() - 1) {
			return tmp;
		}
		// 函数调用/表达式形态:sqlite日期与文本函数返回值均为文本,直接透传
		// (数值表达式传入日期函数本属误用,不做防御)
		if (tmp.indexOf('(') >= 0) {
			return tmp;
		}
		return "datetime(" + tmp + "/1000,'unixepoch','localtime')";
	}

	/**
	 * update 2026-9-10 PG语法系to_char首参为参数占位符时的显式定型:pgjdbc系驱动对日期参数 以UNSPECIFIED
	 * OID发送,vanilla PG的to_char(unknown,unknown)因timestamp/date/numeric/
	 * interval多重载报"function is not unique"(postgresql16.15实爆),openGauss/gaussdb误选
	 * numeric重载报"invalid input syntax for type numeric",og7报"could not determine
	 * data type",仅vastbase恰好选对——同一SQL四种结局,统一以::timestamp显式定型修复。
	 * 注意:函数转换发生在命名参数替换为?之前,首参可能是?、:name或#[name]三种占位形态,
	 * 须保留原token返回(替换为?会破坏参数索引对齐);oracle/dm/oceanbase驱动日期参数定型 发送且无::语法,不在处理范围。
	 *
	 * @param dialect              目标方言dbType
	 * @param firstArg             to_char首参SQL片段
	 * @param format               转换后的格式模型串(用于数值模型判别)
	 * @param numericModelPossible 是否可能为数值格式化场景(to_char(numeric,'999.99')):格式模型
	 *                             含9/0数值占位时不cast——数值参数经setBigDecimal等定型绑定
	 *                             原生可解重载,强转timestamp反而破坏该场景
	 * @return 处理后的首参SQL片段
	 */
	public static String pgToCharParamCast(int dialect, String firstArg, String format, boolean numericModelPossible) {
		if (firstArg == null) {
			return firstArg;
		}
		String argTrim = firstArg.trim();
		// 参数占位符三形态:?(已替换)、:name(命名参数)、#[name](sqltoy中括号命名参数)
		boolean placeholder = "?".equals(argTrim) || argTrim.matches(":[A-Za-z_][A-Za-z0-9_]*")
				|| argTrim.matches("#\\[[^\\]]+\\]");
		if (!placeholder) {
			return firstArg;
		}
		// update 2026-9-14
		// 补KINGBASE(KingbaseES基于PG,占位符参数同样存在to_char(unknown,unknown)重载歧义)
		// update 2026-10-11 补HIGHGO(瀚高PG12内核,同vanilla PG的to_char重载歧义)
		boolean pgSyntax = dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.MOGDB || dialect == DBType.STARDB || dialect == DBType.OSCAR
				|| dialect == DBType.OPENGAUSS || dialect == DBType.VASTBASE || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO;
		if (!pgSyntax) {
			return firstArg;
		}
		if (numericModelPossible && format != null && (format.indexOf('9') >= 0 || format.indexOf('0') >= 0)) {
			return firstArg;
		}
		return "(" + argTrim + "::timestamp)";
	}

}
