package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description add_months(x,n)月份加减(oracle惯用写法)的跨库适配。oracle系/dm/hana/
 *              openGauss系/kingbase原生透传(月末溢出均钳制到月末,与oracle语义一致);
 *              mysql系转DATE_ADD(x, INTERVAL n
 *              MONTH);sqlserver转DATEADD(month,n,x); vanilla
 *              pg用make_interval(months => n);db2用(x + n MONTHS);
 *              ch用addMonths(x,n);h2用DATEADD('MONTH',n,x)。
 *              响亮放弃(2026-10-11真库探针):sqlite的date修饰符月份加法不钳制月末
 *              (date('2026-01-31','+1 months')得2026-03-03,oracle系为2026-02-28),
 *              静默错值风险故不转换,交由目标库响亮报错(sqlite无add_months函数)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class AddMonths extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wadd_months\\(");

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
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		// 原生:oracle/oracle11、dm/hana(真库实测)、openGauss系(og实测原生)、kingbase(实测)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM || dialect == DBType.HANA
				|| dialect == DBType.OPENGAUSS || dialect == DBType.GAUSSDB || dialect == DBType.MOGDB
				|| dialect == DBType.VASTBASE || dialect == DBType.OSCAR || dialect == DBType.STARDB
				|| dialect == DBType.KINGBASE || dialect == DBType.HIGHGO) {
			// update 2026-10-11 瀚高:add_months对裸字符串字面量报interval解析错(真库实测),字面量包cast(x as date);列/表达式保持原生透传
			if (dialect == DBType.HIGHGO && !args[0].trim().equals(FunctionUtils.castLiteralDate(args[0]).trim())) {
				return "add_months(" + FunctionUtils.castLiteralDate(args[0]) + "," + args[1] + ")";
			}
			// update 2026-10-11 oracle系(含dm/kingbase)对裸字符串字面量按NLS解析报ORA-01861,字面量参数包to_date渲染(真库实测);列/表达式保持原生透传
			if ((dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM
					|| dialect == DBType.KINGBASE || dialect == DBType.HIGHGO)
					&& !args[0].trim().equals(FunctionUtils.oracleDateLiteralExpr(args[0]).trim())) {
				return "add_months(" + FunctionUtils.oracleDateLiteralExpr(args[0]) + "," + args[1] + ")";
			}
			return super.IGNORE;
		}
		// mysql系:DATE_ADD(x, INTERVAL n MONTH)(月末钳制语义与oracle一致)
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE) {
			return "DATE_ADD(" + args[0] + ", INTERVAL " + args[1] + " MONTH)";
		}
		if (dialect == DBType.SQLSERVER) {
			return "DATEADD(month, " + args[1] + ", " + args[0] + ")";
		}
		// vanilla pg:make_interval支持表达式月数(真库实测),date+interval月末钳制同oracle
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14) {
			return "(" + args[0] + " + make_interval(months => " + args[1] + "))";
		}
		if (dialect == DBType.DB2) {
			return "(" + args[0] + " + " + args[1] + " MONTHS)";
		}
		if (dialect == DBType.CLICKHOUSE) {
			return "addMonths(" + args[0] + "," + args[1] + ")";
		}
		if (dialect == DBType.H2) {
			return "DATEADD('MONTH', " + args[1] + ", " + args[0] + ")";
		}
		// sqlite:月份修饰符不钳制月末(实测2026-01-31+1月=2026-03-03),不转换响亮保留
		return super.IGNORE;
	}
}