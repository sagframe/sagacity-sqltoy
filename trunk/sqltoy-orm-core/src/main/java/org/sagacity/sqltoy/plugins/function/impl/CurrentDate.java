package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 当前日期(不带时间)函数的跨库转换。update 2026-10-4 新增本函数类:
 *         统一口径为"本地时区的今天零点日期"(典型用途 create_time >= CURRENT_DATE);
 *         覆盖mysql的curdate()/current_date()与标准裸关键字current_date两种源形态。
 *         已知边界:裸关键字位于整条SQL末尾无后继字符时\W不命中(与sysdate同款既有限制,
 *         实际SQL不出现);pg系对mysql习惯的current_date()带括号形态不转换(响亮报错,
 *         该写法仅mysql合法)。
 */
public class CurrentDate extends IFunction {

	// \W前缀须涵盖全部分支(裸关键字current_date\W同样要求前导非单词字符),否则匹配自token
	// 中部起始,替换会残留首字母(实测产出cTRUNC/cdate)
	private static Pattern regex = Pattern.compile("(?i)\\W((curdate|current_date)\\(|current_date\\W)");

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
		// update 2026-10-4 mysql系两种形态原生(CURDATE()/CURRENT_DATE),同名透传
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			return super.IGNORE;
		}
		// update 2026-10-4 oracle系的CURRENT_DATE带时间部分(会话时区),与"零点日期"口径
		// 不一致(同条件在mysql的CURDATE下为今天零点),统一转TRUNC(CURRENT_DATE)截断到
		// 零点并保留会话时区(实例/会话时区不一致的部署不受影响)
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM
				|| dialect == DBType.OCEANBASE) {
			return "TRUNC(CURRENT_DATE)";
		}
		if (dialect == DBType.SQLSERVER) {
			return "CAST(GETDATE() AS date)";
		}
		// update 2026-10-4 db2对CURRENT_DATE注册专名透传即可;mysql习惯的curdate转db2惯用的
		// 带空格关键字形态CURRENT DATE
		if (dialect == DBType.DB2) {
			return "curdate".equals(functionName.toLowerCase(Locale.ROOT)) ? "CURRENT DATE" : super.IGNORE;
		}
		// update 2026-10-4 sqlite的CURRENT_DATE关键字为UTC墙钟(东八区晚8小时才算"今天"),
		// 与Now的localtime策略一致取本地墙钟
		if (dialect == DBType.SQLITE) {
			return "date('now','localtime')";
		}
		if (dialect == DBType.CLICKHOUSE) {
			// ch原生支持CURRENT_DATE关键字透传;mysql习惯的curdate转原生today()(Date类型)
			return "curdate".equals(functionName.toLowerCase(Locale.ROOT)) ? "today()" : super.IGNORE;
		}
		// pg系/h2/hana原生支持CURRENT_DATE:同名透传;mysql习惯的curdate()转CURRENT_DATE
		if ("curdate".equals(functionName.toLowerCase(Locale.ROOT))) {
			return "CURRENT_DATE";
		}
		return super.IGNORE;
	}
}
