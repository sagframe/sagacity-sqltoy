package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 当月最后一天函数的跨库适配(2026-10-11源拼写补全:last_day与eomonth两种
 *              惯用写法统一进入转换——mysql/oracle系用户写last_day(),sqlserver用户写
 *              eomonth(),不能只认mysql写法)。拼写×方言:本库原生拼写透传(last_day@
 *              oracle系/dm/hana/db2/h2/og系/kingbase/mysql系;eomonth@sqlserver),
 *              外来拼写转该库等价物(eomonth源到last_day原生族转last_day();last_day源
 *              到sqlserver转EOMONTH)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class LastDay extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(last_day|eomonth)\\(");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		boolean lastDaySpelling = "last_day".equals(functionName.toLowerCase(Locale.ROOT));
		// last_day原生族:oracle/oracle11(教科书形态)、dm/hana/db2/h2(真库实测)、
		// openGauss系(og5.0实测last_day原生)、kingbase(实测)、mysql系(含OB/tidb等)
		boolean lastDayNative = dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM
				|| dialect == DBType.HANA || dialect == DBType.DB2 || dialect == DBType.H2
				|| dialect == DBType.OPENGAUSS || dialect == DBType.GAUSSDB || dialect == DBType.MOGDB
				|| dialect == DBType.VASTBASE || dialect == DBType.OSCAR || dialect == DBType.STARDB
				|| dialect == DBType.KINGBASE || dialect == DBType.HIGHGO;
		if (lastDaySpelling && lastDayNative) {
			// update 2026-10-11 瀚高:last_day对裸字符串字面量报类型推断歧义(真库实测),字面量包cast(x as
			// date)(瀚高to_date未验证);列/表达式保持原生透传
			if (dialect == DBType.HIGHGO && !args[0].trim().equals(FunctionUtils.castLiteralDate(args[0]).trim())) {
				return "LAST_DAY(" + FunctionUtils.castLiteralDate(args[0]) + ")";
			}
			// update 2026-10-11
			// oracle系(含dm/kingbase)对裸字符串字面量按NLS解析报ORA-01861,字面量参数包to_date渲染(真库实测);列/表达式保持原生透传
			if ((dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM
					|| dialect == DBType.KINGBASE || dialect == DBType.HIGHGO)
					&& !args[0].trim().equals(FunctionUtils.oracleDateLiteralExpr(args[0]).trim())) {
				return "LAST_DAY(" + FunctionUtils.oracleDateLiteralExpr(args[0]) + ")";
			}
			return super.IGNORE;
		}
		// eomonth原生:sqlserver(2012+);last_day源到sqlserver转EOMONTH
		if (dialect == DBType.SQLSERVER) {
			return !lastDaySpelling ? super.IGNORE : "EOMONTH(" + args[0] + ")";
		}
		// eomonth源到last_day原生族:直接换名(两函数语义一致,均为当月最后一天)
		if (lastDayNative) {
			return wrapArgs("last_day", args);
		}
		// vanilla pg/pg14:两拼写统一组合形态(月初+1月-1天,::date对齐mysql的date返回)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14) {
			return "((date_trunc('month'," + args[0] + ") + interval '1 month' - interval '1 day')::date)";
		}
		if (dialect == DBType.CLICKHOUSE) {
			return "toLastDayOfMonth(" + args[0] + ")";
		}
		if (dialect == DBType.SQLITE) {
			// start of month锚定后+1月-1天必为月末,无sqlite月份溢出问题;参数经日期文本归一
			return "date(" + FunctionUtils.sqliteDateTextExpr(args[0]) + ",'start of month','+1 month','-1 day')";
		}
		return super.IGNORE;
	}
}