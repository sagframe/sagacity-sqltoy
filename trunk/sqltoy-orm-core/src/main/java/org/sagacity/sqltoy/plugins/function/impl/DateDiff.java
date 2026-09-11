package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 增加date_diff/datediff函数不同数据库适配(update 2026-9-6 14库真实执行验证后默认注册)
 */
public class DateDiff extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(datediff|timestampdiff)\\(");

	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	/**
	 * datediff语义契约(update 2026-9-5 口径统一): 两参datediff(d1,d2)结果为d1-d2(mysql
	 * datediff风格); 三参datediff/timestampdiff(unit,d1,d2)结果为d2-d1; 单位口径统一为:
	 * DAY/两参=自然天差(去时间),WEEK=自然天差/7截断,HOUR/MINUTE=完整单位向零截断,SECOND=秒差整数,
	 * MONTH/YEAR=年月分量差;各库按同一口径转换,保证同一条SQL在各库返回同值
	 * (修正点:原oracle/PG系WEEK-HOUR-MIN保留1位小数与mysql整数不一致;sqlserver按边界计数且WEEK受
	 * DATEFIRST影响;mysql的timestampdiff(YEAR/MONTH)是完整日历单位与分量差不一致;oracle的MONTH带日权重)
	 */
	private static final String[][] UNIT_CONSTRACTS = { { "DD", "DAY" }, { "MM", "MONTH" }, { "YY", "YEAR" },
			{ "YYYY", "YEAR" }, { "WW", "WEEK" }, { "HH", "HOUR" }, { "MI", "MINUTE" }, { "SS", "SECOND" } };

	@Override
	public String wrap(int dbType, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase(Locale.ROOT);
		String[] realArgs;
		// 两参默认为天:realArgs=[DAY,d1,d2],语义d1-d2
		if (args.length == 2) {
			realArgs = new String[] { "DAY", args[0], args[1] };
		} else {
			// 三参语义d2-d1
			realArgs = args;
		}
		// 去除掉单引号、双引号
		String unitType = realArgs[0].toUpperCase(Locale.ROOT).replace("'", "").replace("\"", "");
		// update 2026-9-10 补TIDB:TiDB原生兼容mysql的datediff两参/timestampdiff三参/YEAR/MONTH/
		// TRUNCATE函数族(8.5.1实测),此前TIDB落IGNORE致三参形态原样透传,单位词被解析为列名报
		// "Unknown column 'day' in 'field list'"
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS || dbType == DBType.STARROCKS
				|| dbType == DBType.TIDB) {
			// 两参datediff(d1,d2)=自然天差,mysql原生且与契约一致,原样保留
			if (args.length == 2 && "datediff".equals(funLow)) {
				return super.IGNORE;
			}
			// update 2026-9-5 口径统一:年/月为年月分量差(timestampdiff的YEAR/MONTH是完整日历单位,跨库不同值)
			if (unitType.equals("YEAR")) {
				return "(YEAR(" + realArgs[2] + ") - YEAR(" + realArgs[1] + "))";
			}
			if (unitType.equals("MONTH") || unitType.equals("MM")) {
				return "((YEAR(" + realArgs[2] + ") - YEAR(" + realArgs[1] + "))*12 + MONTH(" + realArgs[2]
						+ ") - MONTH(" + realArgs[1] + "))";
			}
			// DAY/WEEK按自然天差(mysql的timestampdiff(DAY/WEEK)按完整24小时/整周锚定,与他库自然天口径不同值)
			if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "DATEDIFF(" + realArgs[2] + "," + realArgs[1] + ")";
			}
			if (unitType.equals("WEEK") || unitType.equals("WW")) {
				return "TRUNCATE(DATEDIFF(" + realArgs[2] + "," + realArgs[1] + ")/7,0)";
			}
			// HOUR/MINUTE/SECOND为完整单位向零截断,timestampdiff原生即此口径,原样保留
			realArgs[0] = getMatchedType(unitType, UNIT_CONSTRACTS);
			return wrapArgs("timestampdiff", realArgs);
		}
		// update 2026-9-6 dm与oracle同为oracle系(实测DM也无两参datediff,报-2007),并入oracle分支
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
			// update 2026-9-5 裸字符串字面量参数包to_date(长度启发式同ToDate单参),
			// 规避TRUNC('2026-01-20')等在默认NLS_DATE_FORMAT(DD-MON-RR)会话下报
			// ORA-01722/ORA-01861;列/表达式参数原样保留
			// 两参:d1-d2
			if (args.length == 2) {
				return "(TRUNC(" + wrapOracleDateExpr(args[0]) + ") - TRUNC(" + wrapOracleDateExpr(args[1]) + "))";
			}
			// 三参:d2-d1(oracle日期相减得天数数值,乘系数可表达更小单位)
			String d1 = wrapOracleDateExpr(realArgs[1]);
			String d2 = wrapOracleDateExpr(realArgs[2]);
			// update 2026-9-5 口径统一:年/月为年月分量差(原MONTHS_BETWEEN保留1位小数含日权重,跨库不同值)
			if (unitType.equals("YEAR")) {
				return "(EXTRACT(YEAR FROM " + d2 + ") - EXTRACT(YEAR FROM " + d1 + "))";
			} else if (unitType.equals("MONTH") || unitType.equals("MM")) {
				return "MONTHS_BETWEEN(TRUNC(" + d2 + ",'MM'),TRUNC(" + d1 + ",'MM'))";
			} else if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(TRUNC(" + d2 + ") - TRUNC(" + d1 + "))";
			} else if (unitType.equals("WEEK") || unitType.equals("WW")) {
				// 自然天差/7截断(TRUNC后已是DATE,相减为数值)
				return "TRUNC((TRUNC(" + d2 + ") - TRUNC(" + d1 + "))/7)";
			} else if (unitType.equals("HOUR") || unitType.equals("HH")) {
				// update 2026-9-5 Spring全栈真实验证:timestamp列直接相减返回INTERVAL(interval乘除系数
				// 仍是interval,java侧拿到非数值),统一CAST AS DATE后相减得天数数值(DATE列原样兼容)
				return "TRUNC((CAST(" + d2 + " AS DATE) - CAST(" + d1 + " AS DATE))*24)";
			} else if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				return "TRUNC((CAST(" + d2 + " AS DATE) - CAST(" + d1 + " AS DATE))*1440)";
			} else if (unitType.equals("SECOND") || unitType.equals("SS")) {
				return "ROUND((CAST(" + d2 + " AS DATE) - CAST(" + d1 + " AS DATE))*86400)";
			}
			return super.IGNORE;
		}
		// update 2026-9-10 vastbase G100 3.0.9(PG兼容模式)实测:date-date返回integer(同vanilla
		// PG),
		// 与openGauss 5.0返回interval不同,原og系分支的date_part('day',整数)隐式转换后恒为0,
		// 两参/DAY/WEEK天差全部失真(两参datediff应10得0)——VASTBASE从og系分支归入本PG分支
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.VASTBASE) {
			// vanilla PG:date-date为整数天,原形态可用
			if (args.length == 2) {
				return "(" + args[0] + "::date - " + args[1] + "::date)";
			}
			// 年/月为年月分量差
			if (unitType.equals("YEAR")) {
				return "(date_part('year'," + realArgs[2] + "::timestamp)-date_part('year'," + realArgs[1]
						+ "::timestamp))";
			} else if (unitType.equals("MONTH")) {
				return "((date_part('year'," + realArgs[2] + "::timestamp)-date_part('year'," + realArgs[1]
						+ "::timestamp))*12+date_part('month'," + realArgs[2] + "::timestamp)-date_part('month',"
						+ realArgs[1] + "::timestamp))";
			} else if (unitType.equals("WEEK") || unitType.equals("WW")) {
				// update 2026-9-5 口径统一:自然天差/7整数截断(原保留1位小数与mysql整数口径不同值)
				// update 2026-9-10 vastbase G100 3.0实测整数/整数返回double(9/7=1.2857,vanilla PG
				// 为整除得1),统一trunc截断保证跨库同值(vanilla PG上trunc(整除结果)幂等)
				return "trunc(((" + realArgs[2] + "::date - " + realArgs[1] + "::date)/7))";
			} else if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(" + realArgs[2] + "::date - " + realArgs[1] + "::date)";
			} else if (unitType.equals("HOUR") || unitType.equals("HH")) {
				// update 2026-9-5 口径统一:完整单位截断(原保留1位小数与mysql整数口径不同值)
				return "trunc(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1]
						+ "::timestamp))/3600)";
			} else if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				return "trunc(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1] + "::timestamp))/60)";
			} else if (unitType.equals("SECOND") || unitType.equals("SS")) {
				return "round(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1] + "::timestamp)),0)";
			}
			return super.IGNORE;
		}
		if (dbType == DBType.SQLSERVER) {
			// 两参datediff在sqlserver无此语法:d1-d2转DATEDIFF(DAY,d2,d1)
			if (args.length == 2) {
				return "DATEDIFF(DAY," + args[1] + "," + args[0] + ")";
			}
			// update 2026-9-5 口径统一:WEEK/HOUR/MINUTE改自然天差与秒差整除(DATEDIFF原生按边界计数,
			// 且WEEK受DATEFIRST设置影响,跨库不同值);YEAR/MONTH边界计数恰为年月分量差,DAY恰为自然天差,保持原生
			String unit = getMatchedType(unitType, UNIT_CONSTRACTS);
			if (unit.equals("WEEK")) {
				return "(DATEDIFF(DAY," + realArgs[1] + "," + realArgs[2] + ")/7)";
			}
			if (unit.equals("HOUR")) {
				return "(DATEDIFF(SECOND," + realArgs[1] + "," + realArgs[2] + ")/3600)";
			}
			if (unit.equals("MINUTE")) {
				return "(DATEDIFF(SECOND," + realArgs[1] + "," + realArgs[2] + ")/60)";
			}
			realArgs[0] = unit;
			return wrapArgs("DATEDIFF", realArgs);
		}
		if (dbType == DBType.H2) {
			// update 2026-9-6 实测H2 2.x:DATEDIFF要求单位在首参(与sqlserver同形态),
			// 且不接受字符串字面量日期(报"Invalid value for date-time field"),日期参数须cast;
			// 两参d1-d2转DATEDIFF(DAY,cast(d2),cast(d1))
			if (args.length == 2) {
				return "DATEDIFF(DAY,CAST(" + args[1] + " AS TIMESTAMP),CAST(" + args[0] + " AS TIMESTAMP))";
			}
			String unit = getMatchedType(unitType, UNIT_CONSTRACTS);
			if (unit.equals("DAY") || unitType.equals("DD")) {
				return "DATEDIFF(DAY,CAST(" + realArgs[1] + " AS DATE),CAST(" + realArgs[2] + " AS DATE))";
			}
			// update 2026-9-9 口径统一:H2原生DATEDIFF(WEEK)按周界计数(结果受周起点影响),
			// 改自然天差/7整数截断,与mysql/oracle/pg系分支契约一致
			// update 2026-9-10 实测H2 2.x整数相除返回DECIMAL(9/7=1.2857),须TRUNC向零截断对齐口径
			if (unit.equals("WEEK")) {
				return "TRUNC(DATEDIFF(DAY,CAST(" + realArgs[1] + " AS DATE),CAST(" + realArgs[2] + " AS DATE))/7)";
			}
			return "DATEDIFF(" + unit + ",CAST(" + realArgs[1] + " AS TIMESTAMP),CAST(" + realArgs[2]
					+ " AS TIMESTAMP))";
		}
		if (dbType == DBType.DB2) {
			// update 2026-9-6 实测db2 11.5/12.1:无DATEDIFF亦无TIMESTAMPDIFF函数(SQLCODE=-440),
			// 以DAYS()*86400+MIDNIGHT_SECONDS()组合出epoch总秒数差,除以单位秒数换算;
			// 两参/日差用DAYS()相减得天数
			// update 2026-9-10 占位符参数显式定型:DAYS/YEAR/MONTH/MIDNIGHT_SECONDS均为多重载
			// 函数,裸参数标记无类型信息报-245 ambiguous function(db2 12.1.5实测
			// datediff(day,:d1,:d2)),占位符参数统一CAST AS TIMESTAMP(字符串/日期绑定均可
			// 显式定型解析),列/字面量参数自带类型原样保留
			String d1 = wrapDb2DateExpr((args.length == 2) ? args[1] : realArgs[1]);
			String d2 = wrapDb2DateExpr((args.length == 2) ? args[0] : realArgs[2]);
			if (args.length == 2) {
				return "(DAYS(" + d2 + ") - DAYS(" + d1 + "))";
			}
			if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(DAYS(" + d2 + ") - DAYS(" + d1 + "))";
			}
			if (unitType.equals("MONTH") || unitType.equals("MM")) {
				// update 2026-9-9 口径统一:年月分量差(原实现仅MONTH分量相减,跨年错误,
				// 如2024-01→2026-03应为26原返回2,与mysql/oracle/pg等分支契约不一致)
				return "((YEAR(" + d2 + ") - YEAR(" + d1 + "))*12 + MONTH(" + d2 + ") - MONTH(" + d1 + "))";
			}
			if (unitType.equals("YEAR")) {
				return "(YEAR(" + d2 + ") - YEAR(" + d1 + "))";
			}
			String divisor;
			if (unitType.equals("WEEK") || unitType.equals("WW")) {
				divisor = "604800";
			} else if (unitType.equals("HOUR") || unitType.equals("HH")) {
				divisor = "3600";
			} else if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				divisor = "60";
			} else {
				divisor = "1";
			}
			return "((DAYS(" + d2 + ") - DAYS(" + d1 + "))*86400" + "+(MIDNIGHT_SECONDS(" + d2 + ") - MIDNIGHT_SECONDS("
					+ d1 + ")))/" + divisor;
		}
		// update 2026-9-10 VASTBASE已上移归入vanilla PG分支(G100
		// 3.0实测date-date=integer非interval)
		if (dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
				|| dbType == DBType.GAUSSDB) {
			// update 2026-9-5 实测openGauss 5.0.0:date-date相减返回interval(vanilla PG为整数天),
			// 且无原生datediff/timestampdiff(原样保留会报函数不存在);
			// 天差用date_part('day',interval)取整数天,周/时/分/秒用epoch总秒数换算,年/月date_part与PG一致
			if (args.length == 2) {
				return "(date_part('day',(" + args[0] + "::date - " + args[1] + "::date)))";
			}
			if (unitType.equals("YEAR")) {
				return "(date_part('year'," + realArgs[2] + "::timestamp)-date_part('year'," + realArgs[1]
						+ "::timestamp))";
			} else if (unitType.equals("MONTH")) {
				return "((date_part('year'," + realArgs[2] + "::timestamp)-date_part('year'," + realArgs[1]
						+ "::timestamp))*12+date_part('month'," + realArgs[2] + "::timestamp)-date_part('month',"
						+ realArgs[1] + "::timestamp))";
			} else if (unitType.equals("WEEK") || unitType.equals("WW")) {
				// update 2026-9-5 口径统一:自然天差/7整数截断
				return "trunc(date_part('day',(" + realArgs[2] + "::date - " + realArgs[1] + "::date))/7)";
			} else if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(date_part('day',(" + realArgs[2] + "::date - " + realArgs[1] + "::date)))";
			} else if (unitType.equals("HOUR") || unitType.equals("HH")) {
				return "trunc(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1]
						+ "::timestamp))/3600)";
			} else if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				return "trunc(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1] + "::timestamp))/60)";
			} else if (unitType.equals("SECOND") || unitType.equals("SS")) {
				return "round(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1] + "::timestamp)),0)";
			}
			return super.IGNORE;
		}
		if (dbType == DBType.CLICKHOUSE) {
			// update 2026-9-11 clickhouse分支:无datediff(原样透传报Unknown function)。
			// 天差=toDate相减(日历日差,与pg系口径一致,CH的Date-Date返回整数天);
			// 年/月=toYear/toMonth分量差(CH原生dateDiff('year')为完整年单位,与契约分量差
			// 不同值,如2026-06-01→2027-01-01契约=1而dateDiff('year')=0);周=自然天差/7整除
			// (intDiv);时/分/秒=dateDiff完整单位截断(原生口径即契约);两参=arg0-arg1自然天差;
			// CH强类型不做String隐式转换,参数统一toDate/toDateTime显式解析(字面量/占位符/列均适用)
			if (args.length == 2) {
				return "(toDate(" + args[0] + ") - toDate(" + args[1] + "))";
			}
			String d1 = "toDate(" + realArgs[1] + ")";
			String d2 = "toDate(" + realArgs[2] + ")";
			if (unitType.equals("YEAR")) {
				return "(toYear(" + d2 + ") - toYear(" + d1 + "))";
			}
			if (unitType.equals("MONTH") || unitType.equals("MM")) {
				return "((toYear(" + d2 + ") - toYear(" + d1 + "))*12 + toMonth(" + d2 + ") - toMonth(" + d1 + "))";
			}
			if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(" + d2 + " - " + d1 + ")";
			}
			if (unitType.equals("WEEK") || unitType.equals("WW")) {
				return "intDiv((" + d2 + " - " + d1 + "), 7)";
			}
			if (unitType.equals("HOUR") || unitType.equals("HH")) {
				return "dateDiff('hour',toDateTime(" + realArgs[1] + "),toDateTime(" + realArgs[2] + "))";
			}
			if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				return "dateDiff('minute',toDateTime(" + realArgs[1] + "),toDateTime(" + realArgs[2] + "))";
			}
			if (unitType.equals("SECOND") || unitType.equals("SS")) {
				return "dateDiff('second',toDateTime(" + realArgs[1] + "),toDateTime(" + realArgs[2] + "))";
			}
			return super.IGNORE;
		}
		if (dbType == DBType.SQLITE) {
			// update 2026-9-6 sqlite无datediff;JDBC setTimestamp在sqlite存为毫秒Long,
			// 而字面量参数是'YYYY-MM-DD'文本,两种形态统一以strftime('%s',x)归一为epoch秒再相减
			// (strftime('%s',毫秒Long)=null,须/1000转秒;strftime('%s','文本')直接解析)
			if (args.length == 2) {
				// update 2026-9-9 两参改自然天差(epoch秒截断到UTC日界后相减):原round(epoch差/86400.0)
				// 在列值含时间部分时与mysql/oracle"去时间的自然天差"口径偏差±1天
				return "(" + toSqliteDayExpr(args[0]) + " - " + toSqliteDayExpr(args[1]) + ")";
			}
			// 年/月为年月分量差(strftime('%Y'/'%m')返回文本,须CAST数值化后分量相减)
			if (unitType.equals("YEAR")) {
				// update 2026-9-9 修复原表达式括号不闭合(第二个CAST前多一个左括号,opens=6/closes=5,
				// 生成SQL必然语法错误),结构与MONTH分支对齐
				return "(CAST(strftime('%Y'," + toSqliteDateExpr(realArgs[2]) + ") AS INTEGER) - CAST(strftime('%Y',"
						+ toSqliteDateExpr(realArgs[1]) + ") AS INTEGER))";
			}
			if (unitType.equals("MONTH") || unitType.equals("MM")) {
				return "((CAST(strftime('%Y'," + toSqliteDateExpr(realArgs[2]) + ") AS INTEGER) - CAST(strftime('%Y',"
						+ toSqliteDateExpr(realArgs[1]) + ") AS INTEGER))*12 + CAST(strftime('%m',"
						+ toSqliteDateExpr(realArgs[2]) + ") AS INTEGER) - CAST(strftime('%m',"
						+ toSqliteDateExpr(realArgs[1]) + ") AS INTEGER))";
			}
			// DAY/WEEK为自然天差(及/7整数截断),与两参形态同口径
			if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(" + toSqliteDayExpr(realArgs[2]) + " - " + toSqliteDayExpr(realArgs[1]) + ")";
			}
			if (unitType.equals("WEEK") || unitType.equals("WW")) {
				return "((" + toSqliteDayExpr(realArgs[2]) + " - " + toSqliteDayExpr(realArgs[1]) + ")/7)";
			}
			// HOUR/MINUTE/SECOND为完整单位向零截断(epoch秒差整除)
			String d2 = toSqliteEpochExpr(realArgs[2]);
			String d1 = toSqliteEpochExpr(realArgs[1]);
			String divisor;
			if (unitType.equals("HOUR") || unitType.equals("HH")) {
				divisor = "3600";
			} else if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				divisor = "60";
			} else {
				divisor = "1";
			}
			return "round((strftime('%s'," + d2 + ") - strftime('%s'," + d1 + "))/" + divisor + ")";
		}
		return super.IGNORE;
	}

	/**
	 * db2日期参数显式定型(update 2026-9-10):DAYS/YEAR/MONTH/MIDNIGHT_SECONDS均为多重载函数,
	 * 裸参数标记(?/:name/#[name])无类型信息时报-245 ambiguous function reference (db2
	 * 12.1.5实测datediff(day,:d1,:d2)),统一CAST AS TIMESTAMP定型(字符串/日期类型绑定
	 * 均可解析);列/字面量/表达式参数自带类型,原样保留
	 */
	private String wrapDb2DateExpr(String expr) {
		if (expr == null) {
			return expr;
		}
		String argTrim = expr.trim();
		if ("?".equals(argTrim) || argTrim.matches(":[A-Za-z_][A-Za-z0-9_]*") || argTrim.matches("#\\[[^\\]]+\\]")) {
			return "CAST(" + argTrim + " AS TIMESTAMP)";
		}
		return expr;
	}

	/**
	 * sqlite自然日序号表达式(update 2026-9-9):先date()归一到日期文本再取epoch秒整除86400,
	 * 两日期相减即自然天差,与mysql DATEDIFF的去时间日历日差口径一致。 update 2026-9-10
	 * 修正:字面量不能带'utc'修饰——'utc'含义为"输入是本地时间,转为UTC", +8时区下清晨时刻(如'2026-01-15
	 * 06:00:00')会被推到前一UTC日(实测两参天差多1); 自然天差契约按字面日期取值(与mysql
	 * DATEDIFF对齐),date()默认按UTC解释文本无偏移;
	 * 列/表达式(JDBC绑定为毫秒Long)仍经datetime(毫秒/1000,'unixepoch')归一为UTC日期文本
	 */
	private String toSqliteDayExpr(String arg) {
		// update 2026-9-10 归一逻辑收敛至FunctionUtils.sqliteDateTextExpr(补文本返回函数
		// 表达式透传+'localtime'本地墙钟口径,与DateFormat/ToChar/ToDate共用)
		return "(CAST(strftime('%s',date(" + FunctionUtils.sqliteDateTextExpr(arg) + ")) AS INTEGER)/86400)";
	}

	/**
	 * sqlite年月分量提取的日期参数归一:字面量直接用date(x)提取(不带'utc'修饰——
	 * 实测strftime('%Y'/'%m',x,'utc')三参形态返回null,仅'%s'支持utc修饰);
	 * 毫秒列/表达式以datetime(毫秒/1000,'unixepoch')转日期文本
	 */
	private String toSqliteDateExpr(String arg) {
		return FunctionUtils.sqliteDateTextExpr(arg);
	}

	/**
	 * update 2026-9-6 sqlite日期参数归一:裸字符串字面量('YYYY-MM-DD[ HH:MM:SS]')原样;
	 * 其余(列引用/表达式,JDBC绑定值为毫秒Long)须datetime(毫秒/1000,...)转日期文本
	 * (strftime('%s',数字)返回null)。 update 2026-9-10
	 * 口径统一改本地时区:列路径补'localtime'还原本地墙钟(原纯'unixepoch'
	 * 输出UTC墙钟文本,再经strftime('%s')按本地解析产生双重时区偏移,列与字面量混算时
	 * HOUR/MINUTE差恒偏一个时区);字面量侧同步摘除'utc'修饰(按本地解析),两侧同为 本地墙钟口径,任意组合差值正确
	 */
	private String toSqliteEpochExpr(String arg) {
		// update 2026-9-10 字面量侧摘除'utc'修饰、列侧补'localtime':两侧统一本地墙钟口径
		// (原字面量UTC+列unixepoch双重偏移,列与字面量混算HOUR/MINUTE差恒偏一个时区)
		return FunctionUtils.sqliteDateTextExpr(arg);
	}

	private String getMatchedType(String unitType, String[][] matchConstract) {
		for (String[] constract : matchConstract) {
			if (unitType.equals(constract[0])) {
				return constract[1];
			}
		}
		return unitType;
	}

	/**
	 * oracle下裸字符串字面量日期参数包to_date,长度启发式同ToDate单参(含引号长度>12视为含时间部分)
	 */
	private String wrapOracleDateExpr(String arg) {
		String tmp = arg.trim();
		if (tmp.length() > 2 && tmp.startsWith("'") && tmp.endsWith("'") && tmp.indexOf('\'', 1) == tmp.length() - 1) {
			if (tmp.length() > 12) {
				return "to_date(" + tmp + ",'yyyy-MM-dd hh24:mi:ss')";
			}
			return "to_date(" + tmp + ",'yyyy-MM-dd')";
		}
		return arg;
	}
}
