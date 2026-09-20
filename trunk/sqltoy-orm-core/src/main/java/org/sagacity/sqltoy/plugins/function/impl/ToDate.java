package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 转换to_date函数
 * @author zhongxuchen
 * @version v1.0,Date:2013-01-02
 * @modify Date:2026-9-5 正则收窄为仅匹配to_date(:原先的裸date(会误伤mysql/sqlite等库原生的
 *         date()取日期函数;补充mysql映射(1参DATE()取日期,2参STR_TO_DATE+格式token互换)与
 *         pg系映射(2参to_date(text,text)原生保留,1参转CAST AS date); 已知边界:oracle
 *         1参按长度启发式猜测是否含时间,占位符/表达式参数不适用
 */
public class ToDate extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wto_date\\(");

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String dialects() {
		return ALL;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		if (dialect == DBType.SQLSERVER) {
			if (args.length == 1) {
				if (args[0].length() > 12) {
					return "convert(datetime," + args[0] + ")";
				}
				return "convert(date," + args[0] + ")";
			}
			// update 2026-9-10 两参形态补常用格式模型→CONVERT style映射(原样透传报
			// 'to_date' is not a recognized built-in function name,sqlserver2022/2025实测):
			// 覆盖ISO日期/日期时间/紧凑日期/美式,其余格式模型仍原样保留(响亮报错可察)
			String fmtLow = args[1].toLowerCase(java.util.Locale.ROOT).replace("'", "").replace(" ", "");
			if ("yyyy-mm-dd".equals(fmtLow)) {
				return "convert(date," + args[0] + ",23)";
			}
			if ("yyyy-mm-ddhh24:mi:ss".equals(fmtLow) || "yyyy-mm-ddhh:mi:ss".equals(fmtLow)) {
				return "convert(datetime," + args[0] + ",120)";
			}
			if ("yyyymmdd".equals(fmtLow)) {
				return "convert(date," + args[0] + ",112)";
			}
			if ("mm/dd/yyyy".equals(fmtLow)) {
				return "convert(datetime," + args[0] + ",101)";
			}
			return super.IGNORE;
		}
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11) {
			if (args.length > 1) {
				return wrapArgs("to_date", args);
			}
			// 已知边界(遗留):单参按长度启发式猜测是否含时间,占位符/表达式参数不适用
			if (args[0].length() > 12) {
				return "to_date(" + args[0] + ",'yyyy-MM-dd HH24:mi:ss')";
			}
			return "to_date(" + args[0] + ",'yyyy-MM-dd')";
		}
		// update 2026-9-15 补db2:db2的TO_DATE必须两参(单参真库实测报SQLCODE=-20448
		// cannot be interpreted),单参按长度启发式补格式模型(db2支持oracle风格format model,
		// 与oracle单参同法);两参原样透传
		if (dialect == DBType.DB2) {
			if (args.length > 1) {
				return wrapArgs("to_date", args);
			}
			if (args[0].length() > 12) {
				return "to_date(" + args[0] + ",'yyyy-MM-dd HH24:mi:ss')";
			}
			return "to_date(" + args[0] + ",'yyyy-MM-dd')";
		}
		if (dialect == DBType.H2) {
			// update 2026-9-9 单参原用formatdatetime方向反了:H2的FORMATDATETIME是格式化
			// (日期→文本,产出VARCHAR),to_date语义为解析(文本→日期),须用PARSEDATETIME,与两参分支一致
			if (args.length == 1) {
				if (args[0].length() > 12) {
					return "parsedatetime(" + args[0] + ",'yyyy-MM-dd HH:mm:ss')";
				} else {
					return "parsedatetime(" + args[0] + ",'yyyy-MM-dd')";
				}
			}
			// 两参解析方向:PARSEDATETIME(str,格式)
			// update 2026-9-15 补oracle风格令牌映射:入参'hh24:mi:ss'的hh24/mi为oracle模型,
			// h2 PARSEDATETIME用java令牌(HH=24小时,mm=分钟),原样透传真库实测报Error parsing
			String fmt = args[1].replace("HH24", "HH").replace("hh24", "HH").replace("MI", "mm").replace("mi", "mm");
			return "parsedatetime(" + args[0] + "," + fmt + ")";
		}
		// update 2026-9-14 补KINGBASE(KingbaseES基于PG,函数语法归PG系)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.MOGDB || dialect == DBType.OPENGAUSS || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE) {
			// pg系2参to_date(text,text)原生支持,原样保留;1参转CAST AS date
			if (args.length > 1) {
				return super.IGNORE;
			}
			return "CAST(" + args[0] + " AS date)";
		}
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57 || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS) {
			// 补充mysql映射:1参DATE()取日期;
			// 2参STR_TO_DATE,格式token互换与DateFormat一致(yyyy↔%Y、MM↔%m、dd↔%d、HH24↔%H、
			// HH↔%H、mm↔%i、mi↔%i、ss↔%s)
			// update 2026-9-14 补java风格HH(24小时)→%H与mm(分钟)→%i:原链缺这两个token,
			// to_date(x,'yyyy-MM-dd HH:mm:ss')在串中残留字面HH/mm致STR_TO_DATE解析错值
			if (args.length == 1) {
				return "DATE(" + args[0] + ")";
			}
			String format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			format = format.replace("HH24", "%H").replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H")
					.replace("mm", "%i").replace("mi", "%i").replace("ss", "%s");
			return "STR_TO_DATE(" + args[0] + "," + format + ")";
		}
		if (dialect == DBType.CLICKHOUSE) {
			// update 2026-9-11 clickhouse无to_date(原样透传报Unknown function):toDate承担解析
			// 语义(ISO文本/日期时间类型自动转换);两参形态的格式模型被忽略(CH按ISO及常见格式
			// 自动解析,其他格式请直用parseDateTimeBestEffort),与sqlite分支同为文本日期体系
			return "toDate(" + args[0] + ")";
		}
		if (dialect == DBType.SQLITE) {
			// update 2026-9-10 sqlite无to_date(原样透传报no such function):日期即ISO文本,
			// date()归一即完成解析语义;占位符多为字符串绑定(to_date语义即解析文本)直接
			// date()包裹,列/表达式经sqliteDateTextExpr归一(字面量与文本函数表达式透传)
			String arg0 = args[0].trim();
			boolean placeholder = "?".equals(arg0) || arg0.matches(":[A-Za-z_][A-Za-z0-9_]*")
					|| arg0.matches("#\\[[^\\]]+\\]");
			return "date(" + (placeholder ? arg0 : FunctionUtils.sqliteDateTextExpr(args[0])) + ")";
		}
		// 表示不做修改
		return super.IGNORE;
	}
}
