package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description MySQL系find_in_set(str,逗号分隔串)的跨库适配:判断str是否为list中的完整元素。
 *              条件上下文改写为boolean(pg系where必须boolean,不接受"非零整数即真"),函数后的
 *              ">0/>=1/!=0/&lt;&gt;0"命中比较与"=0"未命中比较由suffix钩子连带消费改写;
 *              投影/值上下文(select列表、then/else、order/group by等)改写为整数位置,
 *              支持"=2"等位置比较;裸真值形态(WHERE find_in_set(..)...)与显式比较形态全兼容。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class FindInSet extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(find_in_set)\\(");

	/**
	 * 函数闭括号后的比较符子句(裸操作符形态,suffixNeedsParen=false按命中长度消费)。
	 * update 2026-10-20 扩展为任意位置比较:布尔口径族仍走wrapInternal,
	 * =1/=2/&gt;1/&lt;3等走整数位置表达式。
	 */
	private static Pattern cmpPattern = Pattern.compile("\\s*(=|>=|<=|<>|!=|>|<)\\s*(\\d+)");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public boolean suffixNeedsParen() {
		return false;
	}

	@Override
	public Pattern suffixPattern() {
		return cmpPattern;
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		return wrapInternal(dialect, args, true);
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, boolean projection, String... args) {
		if (projection) {
			return projectionExpr(dialect, args);
		}
		return wrapInternal(dialect, args, true);
	}

	@Override
	public String wrapWithSuffix(int dialect, String functionName, String suffix, String... args) {
		String compact = suffix == null ? "" : suffix.replaceAll("\\s+", "");
		// 既有布尔口径族保持不变:>0/>=1/!=0/<>0按命中boolean,=0按未命中boolean
		if (">0".equals(compact) || ">=1".equals(compact) || "!=0".equals(compact) || "<>0".equals(compact)) {
			return wrapInternal(dialect, args, true);
		}
		if ("=0".equals(compact)) {
			return wrapInternal(dialect, args, false);
		}
		// update 2026-10-20 位置口径:=1/=2/>1/<3等按整数位置比较
		Matcher matcher = cmpPattern.matcher(suffix);
		if (!matcher.matches()) {
			return super.IGNORE;
		}
		String position = projectionExpr(dialect, args);
		if (position == null) {
			// MySQL系原生find_in_set已返回位置,连同比较符原样透传
			return super.IGNORE;
		}
		return "(" + position + " " + matcher.group(1) + " " + matcher.group(2) + ")";
	}

	/**
	 * 按方言组织"完整元素相等"的boolean表达式
	 *
	 * @param dialect 目标方言
	 * @param args    函数参数(str, list)
	 * @param hit     true=命中口径(strpos…>0),false=未命中口径(strpos…=0)
	 * @return 改写表达式;参数形态不完整或MySQL系原生返回null(原样保留)
	 */
	private String wrapInternal(int dialect, String[] args, boolean hit) {
		if (args == null || args.length < 2) {
			// 参数形态不完整,原样保留交由目标库响亮报错
			return super.IGNORE;
		}
		// find_in_set(str, list): str=待判断元素, list=逗号分隔集合串
		String str = args[0], list = args[1];
		// MySQL语法系原生支持,直接透传
		if (isMysqlFamily(dialect)) {
			return super.IGNORE;
		}
		// update 2026-10-20 mysql文档明确find_in_set首参含逗号属不可用/未定义,实测返回0;
		// 字面量首参含逗号时直接按未命中处理,避免"补逗号+子串定位"把',a,b,'整段误判成元素
		if (isQuotedComma(str)) {
			return hit ? "(0>0)" : "(0=0)";
		}
		String cmp = hit ? ">0" : "=0";
		// PG系:strpos为原生精确子串定位(无通配符语义)。首尾补逗号钉住元素边界实现"完整元素相等"。
		// 不可用LIKE '%,str,%'形态:元素含_或%时通配符会误命中(如NODE_11误命中NODE_1,而
		// mq_consumer_nodes类节点清单恰以下划线+数字命名,是真实高频场景)
		if (isPgFamily(dialect)) {
			return "(strpos(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// MySQL语法系但无find_in_set的库(h2/doris/starrocks):concat包裹+instr精确子串
		// (instr(str,sub)在此三库的原生形态,见Instr适配器);||在MySQL语法系是OR,必须用concat
		if (dialect == DBType.H2 || dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			return "(instr(concat(','," + list + ",','), concat(','," + str + ",','))" + cmp + ")";
		}
		// Oracle语法系(oracle/oracle11/dm):instr(str,sub)同序原生+||拼接
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM) {
			return "(instr(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// sqlserver:无instr/||,用charindex(sub,str)与'+'拼接
		if (dialect == DBType.SQLSERVER) {
			return "(charindex(','+" + str + "+',', ','+" + list + "+',')" + cmp + ")";
		}
		// db2:locate(sub,str)针在前(与instr参数序相反)+||
		if (dialect == DBType.DB2) {
			return "(locate(','||" + str + "||',', ','||" + list + "||',')" + cmp + ")";
		}
		// hana:locate(str,sub)草垛在前(与db2/mysql的locate反序,见Instr适配器)+||
		if (dialect == DBType.HANA) {
			return "(locate(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// sqlite:instr(str,sub)+||
		if (dialect == DBType.SQLITE) {
			return "(instr(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// clickhouse:position(haystack,needle)逗号形态原生(见Instr适配器)+concat变参
		if (dialect == DBType.CLICKHOUSE) {
			return "(position(concat(','," + list + ",','), concat(','," + str + ",','))" + cmp + ")";
		}
		return super.IGNORE;
	}

	/**
	 * 投影/值上下文:返回整数位置表达式(含首参逗号守卫)。
	 *
	 * @param dialect 目标方言
	 * @param args    函数参数(str, list)
	 * @return 位置表达式;MySQL系原生/参数不完整/未知方言返回null(原样保留)
	 */
	private String projectionExpr(int dialect, String[] args) {
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		String str = args[0], list = args[1];
		if (isMysqlFamily(dialect)) {
			// MySQL原生find_in_set即返回位置
			return super.IGNORE;
		}
		if (isQuotedComma(str)) {
			return "0";
		}
		String position = positionExpr(dialect, str, list);
		if (position == null) {
			return super.IGNORE;
		}
		String ordinal = ordinalExpr(dialect, list, position);
		String comma = commaExpr(dialect, str);
		return "(case when " + comma + " > 0 then 0 else " + ordinal + " end)";
	}

	/**
	 * @description 把"补逗号后子串定位"的字符位置还原成元素序号(MySQL find_in_set返回值):
	 *              序号=匹配起点之前的逗号个数+1。例:',a,b,' 中 ',b,' 起点为3,前段',a'含1个逗号,
	 *              序号=2;元素a的起点为1,前段空串,序号=1。
	 * @param dialect  目标方言
	 * @param list     逗号分隔集合串
	 * @param position 字符位置表达式(0表示未找到)
	 * @return 元素序号表达式
	 */
	private String ordinalExpr(int dialect, String list, String position) {
		String padded = paddedList(dialect, list);
		// 长度用case钳到>=0:未找到(position=0)或首元素(position=1)时 substring长度0,
		// 规避sqlserver "Invalid length parameter"、pg负长度等报错
		String prefixLen = "case when (" + position + ")>1 then (" + position + ")-1 else 0 end";
		String prefix = substringExpr(dialect, padded, prefixLen);
		String lengthFn = lengthFn(dialect);
		String replaced = replaceExpr(dialect, prefix);
		// oracle空串即NULL,substr(...,0)返回NULL;coalesce兜底为空长度0
		String prefixLength = "coalesce(" + lengthFn + "(" + prefix + "),0)";
		String replacedLength = "coalesce(" + lengthFn + "(" + replaced + "),0)";
		return "case when (" + position + ")=0 then 0 else (" + prefixLength + "-" + replacedLength + "+1) end";
	}

	private String paddedList(int dialect, String list) {
		if (dialect == DBType.SQLSERVER) {
			return "','+" + list + "+','";
		}
		if (dialect == DBType.H2 || dialect == DBType.DORIS || dialect == DBType.STARROCKS
				|| dialect == DBType.CLICKHOUSE) {
			return "concat(','," + list + ",',')";
		}
		return "','||" + list + "||','";
	}

	private String substringExpr(int dialect, String source, String lengthExpr) {
		if (dialect == DBType.SQLSERVER || dialect == DBType.CLICKHOUSE) {
			return "substring(" + source + ",1," + lengthExpr + ")";
		}
		return "substr(" + source + ",1," + lengthExpr + ")";
	}

	private String lengthFn(int dialect) {
		if (dialect == DBType.SQLSERVER) {
			return "len";
		}
		if (dialect == DBType.CLICKHOUSE) {
			return "lengthUTF8";
		}
		return "length";
	}

	private String replaceExpr(int dialect, String source) {
		if (dialect == DBType.CLICKHOUSE) {
			return "replaceAll(" + source + ", ',', '')";
		}
		return "replace(" + source + ", ',', '')";
	}

	/**
	 * @return 整数位置表达式(不含首参逗号守卫);未知方言返回null
	 */
	private String positionExpr(int dialect, String str, String list) {
		if (isPgFamily(dialect)) {
			return "strpos(','||" + list + "||',', ','||" + str + "||',')";
		}
		if (dialect == DBType.H2 || dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			return "instr(concat(','," + list + ",','), concat(','," + str + ",','))";
		}
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM) {
			return "instr(','||" + list + "||',', ','||" + str + "||',')";
		}
		if (dialect == DBType.SQLSERVER) {
			return "charindex(','+" + str + "+',', ','+" + list + "+',')";
		}
		if (dialect == DBType.DB2) {
			return "locate(','||" + str + "||',', ','||" + list + "||',')";
		}
		if (dialect == DBType.HANA) {
			return "locate(','||" + list + "||',', ','||" + str + "||',')";
		}
		if (dialect == DBType.SQLITE) {
			return "instr(','||" + list + "||',', ','||" + str + "||',')";
		}
		if (dialect == DBType.CLICKHOUSE) {
			return "position(concat(','," + list + ",','), concat(','," + str + ",','))";
		}
		return null;
	}

	/**
	 * @return 首参是否包含逗号的表达式(整数>0表示含逗号)
	 */
	private String commaExpr(int dialect, String str) {
		if (isPgFamily(dialect)) {
			return "strpos(" + str + ", ',')";
		}
		if (dialect == DBType.SQLSERVER) {
			return "charindex(',', " + str + ")";
		}
		if (dialect == DBType.DB2) {
			return "locate(',', " + str + ")";
		}
		if (dialect == DBType.HANA) {
			return "locate(" + str + ", ',')";
		}
		if (dialect == DBType.CLICKHOUSE) {
			return "position(" + str + ", ',')";
		}
		return "instr(" + str + ", ',')";
	}

	private static boolean isMysqlFamily(int dialect) {
		return dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.OCEANBASE;
	}

	private static boolean isPgFamily(int dialect) {
		return dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.OSCAR || dialect == DBType.STARDB
				|| dialect == DBType.MOGDB || dialect == DBType.VASTBASE || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO;
	}

	private static boolean isQuotedComma(String str) {
		String strTrim = str.trim();
		boolean quotedStr = strTrim.length() > 1 && strTrim.startsWith("'") && strTrim.endsWith("'")
				&& strTrim.indexOf('\'', 1) == strTrim.length() - 1;
		return quotedStr && strTrim.indexOf(',', 1) >= 0;
	}
}
