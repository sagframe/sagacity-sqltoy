package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 将其它类型数据转换成字符串
 * @author zhongxuchen
 * @version v1.0,Date:2013-01-02
 */
public class ToChar extends IFunction {
	// update 2026-9-9 正则收窄为仅匹配to_char:原先同时匹配date_format/FORMATDATETIME与DateFormat
	// 职责重叠,date_format在H2目标下先被DateFormat正确转为formatdatetime,再被本函数二次转成
	// to_char(H2 2.x无此函数),最终产物取决于注册顺序,存在链式改写风险
	private static Pattern regex = Pattern.compile("(?i)\\Wto_char\\(");

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
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		String format;
		switch (dialect) {
		case DBType.MYSQL:
		case DBType.TIDB:
		case DBType.DORIS:
		case DBType.STARROCKS:
		case DBType.MYSQL57: {
			// update 2026-9-11 数值格式模型分流:to_char(123.45,'999.99')原落date_format,
			// 数值参数静默返回NULL(date_format为日期语义,mysql9实测);改CAST AS DECIMAL(20,scale)
			// 保留oracle数值模型语义(scale=小数点后9/0占位数;无千分位分隔,与oracle形态一致)
			if (args[1].indexOf('9') >= 0 || args[1].indexOf('0') >= 0) {
				return "CAST(" + args[0] + " AS DECIMAL(20," + numericScale(args[1]) + "))";
			}
			// 日期
			format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			// 时间处理(update 2026-9-5 补java 24小时制HH→%H与分钟mm→%i;需置于hh24/hh映射之后)
			format = format.replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H").replace("mm", "%i")
					.replace("mi", "%i").replace("ss", "%s");
			return "date_format(" + args[0] + "," + format + ")";
		}
		case DBType.POSTGRESQL:
		case DBType.POSTGRESQL14:
		case DBType.ORACLE:
		case DBType.GAUSSDB:
		case DBType.MOGDB:
		case DBType.STARDB:
		case DBType.OSCAR:
		case DBType.OPENGAUSS:
		case DBType.VASTBASE:
		case DBType.OCEANBASE:
		case DBType.DM:
		case DBType.ORACLE11: {
			// 日期
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			// update 2026-9-10 PG语法系裸?首参补::timestamp:vanilla PG对to_char(unknown,unknown)
			// 报重载歧义(pgjdbc日期参数UNSPECIFIED OID);to_char兼有数值格式化场景,格式模型含
			// 9/0数值占位时不cast(numericModelPossible=true,数值参数定型绑定原生可解重载)
			return "to_char(" + FunctionUtils.pgToCharParamCast(dialect, args[0], format, true) + "," + format + ")";
		}
		case DBType.H2: {
			// H2原生支持to_char(oracle兼容格式模型,端到端实测验证);
			// update 2026-9-9 修正%y(两位年)误映射为yyyy(四位年)
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			// 时间处理(update 2026-9-5 H2 to_char为oracle兼容格式模型,%H应为HH24 24小时制,原hh为12小时制)
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			return "to_char(" + args[0] + "," + format + ")";
		}
		case DBType.CLICKHOUSE: {
			// update 2026-9-11 数值格式模型分流(同mysql分支:toDateTime包裹数值会按epoch秒误析)
			if (args[1].indexOf('9') >= 0 || args[1].indexOf('0') >= 0) {
				return "CAST(" + args[0] + " AS Decimal(20," + numericScale(args[1]) + "))";
			}
			// update 2026-9-9 clickhouse无to_char,以date_format(formatDateTime的mysql兼容别名)承担,
			// token与mysql分支同构(%i分钟/%s秒)
			format = args[1].replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			format = format.replace("hh24", "%H").replace("hh", "%h").replace("HH", "%H").replace("mm", "%i")
					.replace("mi", "%i").replace("ss", "%s");
			// update 2026-9-11 首参包toDateTime(同DateFormat的CLICKHOUSE分支:驱动日期参数
			// 以String发送,formatDateTime(String)报Illegal type)
			return "date_format(toDateTime(" + args[0] + ")," + format + ")";
		}
		case DBType.SQLSERVER: {
			// update 2026-9-10 sqlserver无to_char(原样透传报'to_char' is not a recognized
			// built-in function name,2022/2025实测),以FORMAT承担(2012+):.NET自定义格式token
			// 与oracle模型部分同形(yyyy/yy/MM/dd直接一致),差异token映射hh24→HH、mi→mm;
			// 数值格式模型(含9/0占位)将9映射为0(.NET '000.00'自定义数值格式);
			// %token先归一到oracle模型再映射(与其余分支入参口径一致)
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			if (format.indexOf('9') >= 0 || format.indexOf('0') >= 0) {
				// 数值格式模型:9→0(.NET零占位),FM/S999等oracle前缀修饰不在支持范围
				format = format.replace("9", "0");
			} else {
				format = format.replace("hh24", "HH").replace("mi", "mm");
			}
			return "FORMAT(" + args[0] + "," + format + ")";
		}
		case DBType.SQLITE: {
			// update 2026-9-10 sqlite无to_char,以strftime承担(token映射:yyyy→%Y、MM→%m、
			// dd→%d、hh24→%H、hh→%I、mi→%M、ss→%S);参数归一与DateFormat同源;数值格式
			// 模型(含9/0占位)非strftime语义,原样保留交目标库响亮报错
			if (args[1].indexOf('9') >= 0 || args[1].indexOf('0') >= 0) {
				return super.IGNORE;
			}
			format = args[1].replace("%Y", "yyyy").replace("%y", "yy").replace("%m", "MM").replace("%d", "dd");
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			format = format.replace("yyyy", "%Y").replace("yy", "%y").replace("MM", "%m").replace("dd", "%d");
			format = format.replace("hh24", "%H").replace("hh", "%I").replace("mi", "%M").replace("ss", "%S");
			return "strftime(" + format + "," + FunctionUtils.sqliteDateTextExpr(args[0]) + ")";
		}
		default:
			return super.IGNORE;
		}
	}

	/**
	 * update 2026-9-11 数值格式模型的小数位数:小数点后9/0占位符个数(无小数点返回0),
	 * 如'999.99'→2、'999'→0,供mysql系/CH的CAST AS DECIMAL(20,scale)数值to_char承担
	 */
	private static int numericScale(String format) {
		int dot = format.lastIndexOf('.');
		if (dot < 0) {
			return 0;
		}
		int scale = 0;
		for (int i = dot + 1; i < format.length(); i++) {
			char c = format.charAt(i);
			if (c == '9' || c == '0') {
				scale++;
			}
		}
		return scale;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}
}
