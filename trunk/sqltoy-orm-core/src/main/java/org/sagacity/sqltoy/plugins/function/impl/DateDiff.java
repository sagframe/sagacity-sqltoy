package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-9-6 datediff/timestampdiff 跨库适配,按统一口径契约转换(14库真库验证后默认注册)
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
	 * update 2026-9-5 datediff/timestampdiff 统一口径契约:两参 datediff(d1,d2)=d1-d2 自然天差;
	 * 三参 datediff/timestampdiff(unit,d1,d2)=d2-d1;DAY=自然天差(去时间),WEEK=天差/7截断,
	 * HOUR/MINUTE/SECOND=完整单位向零截断,MONTH/YEAR=年月分量差;各库按同一口径转换,保证跨库同值。
	 * 统一源于各库原生差异:oracle/PG 原保留1位小数与 mysql 整数口径不同值、sqlserver 按边界计数且 WEEK 受 DATEFIRST
	 * 影响、mysql timestampdiff 的 YEAR/MONTH 为完整日历单位、oracle MONTH 带日权重
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
		// update 2026-9-15 单位缩写(YY/MM/DD/WW/HH/MI/SS)前置归一为标准单位,保证各分支幂等处理;
		// 修正要点:补 MI/SS 映射(原缺失致 MINUTE/SECOND 分支不可达)、'YY'→YEAR(避免漏转走原生语义)
		unitType = getMatchedType(unitType, UNIT_CONSTRACTS);
		// update 2026-9-10 TIDB 与 mysql 函数族同构,并入本分支(原生 datediff 仅两参,口径与契约不一致)
		if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS || dbType == DBType.STARROCKS
				|| dbType == DBType.TIDB) {
			// 两参datediff(d1,d2)=自然天差,mysql原生且与契约一致,原样保留
			if (args.length == 2 && "datediff".equals(funLow)) {
				return super.IGNORE;
			}
			// update 2026-9-5 YEAR/MONTH 改年月分量差(timestampdiff 原生为完整日历单位,跨库不同值)
			if (unitType.equals("YEAR")) {
				return "(YEAR(" + realArgs[2] + ") - YEAR(" + realArgs[1] + "))";
			}
			if (unitType.equals("MONTH") || unitType.equals("MM")) {
				return "((YEAR(" + realArgs[2] + ") - YEAR(" + realArgs[1] + "))*12 + MONTH(" + realArgs[2]
						+ ") - MONTH(" + realArgs[1] + "))";
			}
			// DAY/WEEK 按自然天差(mysql timestampdiff 按完整24小时/整周锚定,与他库不同值)
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
		// 原生两参形态直接透传(与 mysql 两参语义一致 d1-d2);三参走各分支转换,避免无谓改写引入语法差异

		// update 2026-10-19 DM/OB 同为 oracle 系并入本分支(OB mysql 模式须配 dialect=mysql 走 mysql
		// 分支,不做自适应)
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.OCEANBASE) {
			// update 2026-9-5 裸字符串字面量参数包 to_date 定型(长度启发式同 ToDate 单参,规避 NLS 会话下
			// ORA-01722/01861)
			if (args.length == 2) {
				return "(TRUNC(" + wrapOracleDateExpr(args[0]) + ") - TRUNC(" + wrapOracleDateExpr(args[1]) + "))";
			}
			// 三参:d2-d1(oracle日期相减得天数数值,乘系数可表达更小单位)
			String d1 = wrapOracleDateExpr(realArgs[1]);
			String d2 = wrapOracleDateExpr(realArgs[2]);
			// update 2026-9-15 DM 用 CAST AS TIMESTAMP 保留时间(oracle 的 CAST AS DATE 对
			// timestamp 列返回 INTERVAL 无法数值化)
			String castType = (dbType == DBType.DM) ? "TIMESTAMP" : "DATE";
			// update 2026-9-5 YEAR/MONTH 为年月分量差(弃用 MONTHS_BETWEEN 小数口径)
			if (unitType.equals("YEAR")) {
				return "(EXTRACT(YEAR FROM " + d2 + ") - EXTRACT(YEAR FROM " + d1 + "))";
			} else if (unitType.equals("MONTH") || unitType.equals("MM")) {
				// update 2026-10-8 跨库月差统一为年月分量差契约(MONTHS_BETWEEN 按自然月含日权重,TRUNC 到月初即分量差形态)
				return "MONTHS_BETWEEN(TRUNC(" + d2 + ",'MM'),TRUNC(" + d1 + ",'MM'))";
			} else if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(TRUNC(" + d2 + ") - TRUNC(" + d1 + "))";
			} else if (unitType.equals("WEEK") || unitType.equals("WW")) {
				// 自然天差/7截断(TRUNC后已是DATE,相减为数值)
				return "TRUNC((TRUNC(" + d2 + ") - TRUNC(" + d1 + "))/7)";
			} else if (unitType.equals("HOUR") || unitType.equals("HH")) {
				// update 2026-9-5 timestamp 列相减返回 INTERVAL 非数值,统一 CAST AS DATE 后相减得天数
				return "TRUNC((CAST(" + d2 + " AS " + castType + ") - CAST(" + d1 + " AS " + castType + "))*24)";
			} else if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				return "TRUNC((CAST(" + d2 + " AS " + castType + ") - CAST(" + d1 + " AS " + castType + "))*1440)";
			} else if (unitType.equals("SECOND") || unitType.equals("SS")) {
				return "ROUND((CAST(" + d2 + " AS " + castType + ") - CAST(" + d1 + " AS " + castType + "))*86400)";
			}
			return super.IGNORE;
		}
		// update 2026-9-14 VASTBASE date-date 返回 integer(同 vanilla PG,区别于 openGauss 的
		// interval),归入 PG 分支;
		// 原独立分支的 YEAR/MONTH 等写法与 PG 不一致(实测报错),统一按 PG 形态
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.VASTBASE
				|| dbType == DBType.HIGHGO) {
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
				// update 2026-9-10 WEEK 按自然天差/7 trunc 截断(统一整数口径,兼容 PG 整除与 VASTBASE double 差异)
				return "trunc(((" + realArgs[2] + "::date - " + realArgs[1] + "::date)/7))";
			} else if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(" + realArgs[2] + "::date - " + realArgs[1] + "::date)";
			} else if (unitType.equals("HOUR") || unitType.equals("HH")) {
				// update 2026-9-5 HOUR 完整单位向零截断
				return "trunc(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1]
						+ "::timestamp))/3600)";
			} else if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				return "trunc(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1] + "::timestamp))/60)";
			} else if (unitType.equals("SECOND") || unitType.equals("SS")) {
				return "round(extract(epoch from(" + realArgs[2] + "::timestamp - " + realArgs[1] + "::timestamp)),0)";
			}
			return super.IGNORE;
		}
		// update 2026-9-14 KINGBASE V9 date 含时间部分(date-date 为小数天,区别于 V8 的 PG12
		// 整数语义),天差统一 trunc 向零截断即为自然天差;
		// 周=trunc(小数天差/7)与自然天差/7 恒等;年/月/时/分/秒路径与 PG 一致,复用同形态表达式
		if (dbType == DBType.KINGBASE) {
			if (args.length == 2) {
				return "trunc(" + args[0] + "::date - " + args[1] + "::date)";
			}
			if (unitType.equals("YEAR")) {
				return "(date_part('year'," + realArgs[2] + "::timestamp)-date_part('year'," + realArgs[1]
						+ "::timestamp))";
			} else if (unitType.equals("MONTH")) {
				return "((date_part('year'," + realArgs[2] + "::timestamp)-date_part('year'," + realArgs[1]
						+ "::timestamp))*12+date_part('month'," + realArgs[2] + "::timestamp)-date_part('month',"
						+ realArgs[1] + "::timestamp))";
			} else if (unitType.equals("WEEK") || unitType.equals("WW")) {
				return "trunc(((" + realArgs[2] + "::date - " + realArgs[1] + "::date)/7))";
			} else if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "trunc(" + realArgs[2] + "::date - " + realArgs[1] + "::date)";
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
		if (dbType == DBType.SQLSERVER) {
			// 两参datediff在sqlserver无此语法:d1-d2转DATEDIFF(DAY,d2,d1)
			if (args.length == 2) {
				return "DATEDIFF(DAY," + args[1] + "," + args[0] + ")";
			}
			// update 2026-9-5 WEEK/HOUR/MINUTE 改自然天差与秒差整除(sqlserver 原生 DATEDIFF 按边界计数且 WEEK
			// 受 DATEFIRST 影响)
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
			// update 2026-9-6 H2 单位须在首参且不接受字符串日期,参数统一 CAST AS TIMESTAMP/DATE 定型;两参转
			// DATEDIFF(DAY,d2,d1)
			if (args.length == 2) {
				return "DATEDIFF(DAY,CAST(" + args[1] + " AS TIMESTAMP),CAST(" + args[0] + " AS TIMESTAMP))";
			}
			String unit = getMatchedType(unitType, UNIT_CONSTRACTS);
			if (unit.equals("DAY") || unitType.equals("DD")) {
				return "DATEDIFF(DAY,CAST(" + realArgs[1] + " AS DATE),CAST(" + realArgs[2] + " AS DATE))";
			}
			// update 2026-9-10 WEEK 按自然天差/7 TRUNC 截断(原生按周界计数受周起点影响;整除返回 DECIMAL 须截断)
			if (unit.equals("WEEK")) {
				return "TRUNC(DATEDIFF(DAY,CAST(" + realArgs[1] + " AS DATE),CAST(" + realArgs[2] + " AS DATE))/7)";
			}
			return "DATEDIFF(" + unit + ",CAST(" + realArgs[1] + " AS TIMESTAMP),CAST(" + realArgs[2]
					+ " AS TIMESTAMP))";
		}
		if (dbType == DBType.DB2) {
			// update 2026-9-10 DB2 无 DATEDIFF/TIMESTAMPDIFF(SQLCODE=-440),以
			// DAYS()*86400+MIDNIGHT_SECONDS() 换算 epoch 秒差;
			// 占位符参数(?/:name/#[name])无类型信息报 -245 ambiguous function,统一 CAST AS TIMESTAMP
			// 定型,列/字面量原样保留
			String d1 = wrapDb2DateExpr((args.length == 2) ? args[1] : realArgs[1]);
			String d2 = wrapDb2DateExpr((args.length == 2) ? args[0] : realArgs[2]);
			if (args.length == 2) {
				return "(DAYS(" + d2 + ") - DAYS(" + d1 + "))";
			}
			if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "(DAYS(" + d2 + ") - DAYS(" + d1 + "))";
			}
			if (unitType.equals("MONTH") || unitType.equals("MM")) {
				// update 2026-9-9 月差按年×12+月分量计算(原仅月相减跨年错误)
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
		// update 2026-9-10 VASTBASE 已并入 PG 分支(date-date=integer 非 interval),此分支不再含
		// vastbase
		if (dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
				|| dbType == DBType.GAUSSDB) {
			// update 2026-9-5 openGauss date-date 返回 interval 且无 datediff,天差经
			// date_part('day') 提取,周/时/分/秒按 epoch 秒差换算
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
				// update 2026-9-5 WEEK 按自然天差/7 截断
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
			// update 2026-9-11 CH 无 datediff:天差=toDate 相减、年/月=toYear/toMonth 分量差、周=天差/7
			// intDiv 整除、
			// 时/分/秒用 dateDiff 完整单位截断;CH 强类型,参数统一 toDate/toDateTime 显式解析
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
			// update 2026-9-6 sqlite 无 datediff;JDBC setTimestamp 在 sqlite 存为毫秒 Long,统一
			// strftime('%s') 归一 epoch 秒再相减(毫秒须 /1000 转秒)
			if (args.length == 2) {
				// update 2026-9-9 两参按 UTC 日界自然天差(epoch 差截断到日界,避免列含时间时偏差)
				return "(" + toSqliteDayExpr(args[0]) + " - " + toSqliteDayExpr(args[1]) + ")";
			}
			// 年/月为年月分量差(strftime('%Y'/'%m')返回文本,须CAST数值化后分量相减)
			if (unitType.equals("YEAR")) {
				// update 2026-9-9 表达式括号与 MONTH 分支结构对齐
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
		if (dbType == DBType.HANA) {
			// update 2026-9-11 HANA 无 datediff/timestampdiff,以 DAYS_BETWEEN/SECONDS_BETWEEN
			// 承担;
			// BETWEEN 族参数语义为 d2-d1(两参契约 d1-d2 须交换参数);天差先 CAST AS DATE 去时间,
			// 周=自然天差/7 用 ROUND(x,0,ROUND_DOWN) 向零截断(hana 无 TRUNC 且整数除法得 decimal),
			// 时/分/秒=完整秒差整除,年/月=YEAR/MONTH 分量差;参数统一 CAST 定型
			if (args.length == 2) {
				return "DAYS_BETWEEN(CAST(" + args[1] + " AS DATE),CAST(" + args[0] + " AS DATE))";
			}
			String hd1 = "CAST(" + realArgs[1] + " AS DATE)";
			String hd2 = "CAST(" + realArgs[2] + " AS DATE)";
			if (unitType.equals("YEAR")) {
				return "(YEAR(" + hd2 + ") - YEAR(" + hd1 + "))";
			}
			if (unitType.equals("MONTH") || unitType.equals("MM")) {
				return "((YEAR(" + hd2 + ") - YEAR(" + hd1 + "))*12 + MONTH(" + hd2 + ") - MONTH(" + hd1 + "))";
			}
			if (unitType.equals("DAY") || unitType.equals("DD")) {
				return "DAYS_BETWEEN(" + hd1 + "," + hd2 + ")";
			}
			if (unitType.equals("WEEK") || unitType.equals("WW")) {
				return "ROUND(DAYS_BETWEEN(" + hd1 + "," + hd2 + ")/7,0,ROUND_DOWN)";
			}
			if (unitType.equals("HOUR") || unitType.equals("HH")) {
				return "ROUND(SECONDS_BETWEEN(CAST(" + realArgs[1] + " AS TIMESTAMP),CAST(" + realArgs[2]
						+ " AS TIMESTAMP))/3600,0,ROUND_DOWN)";
			}
			if (unitType.equals("MINUTE") || unitType.equals("MI")) {
				return "ROUND(SECONDS_BETWEEN(CAST(" + realArgs[1] + " AS TIMESTAMP),CAST(" + realArgs[2]
						+ " AS TIMESTAMP))/60,0,ROUND_DOWN)";
			}
			if (unitType.equals("SECOND") || unitType.equals("SS")) {
				return "SECONDS_BETWEEN(CAST(" + realArgs[1] + " AS TIMESTAMP),CAST(" + realArgs[2] + " AS TIMESTAMP))";
			}
			return super.IGNORE;
		}
		return super.IGNORE;
	}

	/**
	 * update 2026-9-10 db2 占位符参数(?/:name/#[name])无类型信息报 -245 ambiguous function,统一
	 * CAST AS TIMESTAMP 定型; 列/字面量参数自带类型原样保留
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
	 * update 2026-9-10 sqlite 自然天差:date() 归一日期文本后取 epoch 秒整除 86400 相减,与 mysql
	 * DATEDIFF 日历日口径一致; 字面量不带 'utc' 修饰(避免 +8 时区清晨被推到前一 UTC 日),列/毫秒 Long 经
	 * datetime(毫秒/1000,'unixepoch') 归一
	 */
	private String toSqliteDayExpr(String arg) {
		// 归一逻辑收敛至 FunctionUtils.sqliteDateTextExpr(与 DateFormat/ToChar/ToDate 共用
		// 'localtime' 本地墙钟口径)
		return "(CAST(strftime('%s',date(" + FunctionUtils.sqliteDateTextExpr(arg) + ")) AS INTEGER)/86400)";
	}

	/**
	 * sqlite 年月分量提取:字面量直接 date(x) 提取(带 'utc' 修饰的三参形态返回 null); 毫秒列/表达式经
	 * datetime(毫秒/1000,'unixepoch') 转日期文本
	 */
	private String toSqliteDateExpr(String arg) {
		return FunctionUtils.sqliteDateTextExpr(arg);
	}

	/**
	 * update 2026-9-10 sqlite 日期参数归一:字面量原样;列/毫秒 Long 经
	 * datetime(毫秒/1000,'unixepoch') 转日期文本; 口径统一本地时区:列侧补 'localtime' 还原本地墙钟、字面量侧摘除
	 * 'utc' 修饰,两侧同为本地墙钟口径
	 */
	private String toSqliteEpochExpr(String arg) {
		// update 2026-9-10 字面量侧摘除 'utc'、列侧补 'localtime',两侧统一本地墙钟口径
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