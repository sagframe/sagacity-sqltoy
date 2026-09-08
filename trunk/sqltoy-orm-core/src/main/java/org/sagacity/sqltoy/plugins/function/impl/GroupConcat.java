package org.sagacity.sqltoy.plugins.function.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 转换group_concat 分组拼接函数 在不同数据库中的实现
 * @author zhongxuchen
 * @version v1.0,Date:2019-10-21
 */
public class GroupConcat extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(group_concat|string_agg)\\(");
	private static Pattern separtorPattern = Pattern.compile("\\Wseparator\\W");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dbType, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		// update 2026-9-5 重构参数解析:group_concat与string_agg参数结构不同,分别处理
		// (string_agg(expr,sep)第二参为分隔符;group_concat为多列concat语义+可选separator关键字),
		// 修复group_concat多列拼接(a, b [separator '-'])转换时丢失列的问题
		boolean isStringAgg = "string_agg".equals(functionName.toLowerCase(Locale.ROOT));
		String expr;
		String sign = "','";
		if (isStringAgg) {
			expr = args[0];
			sign = (args.length > 1) ? args[1] : "','";
		} else {
			List<String> segments = new ArrayList<String>();
			for (int i = 0; i < args.length; i++) {
				String tmp = args[i];
				int matchIndex = StringUtil.matchIndex(tmp.toLowerCase(Locale.ROOT), separtorPattern);
				if (matchIndex > 0) {
					// "\\Wseparator\\W" 表达式长度11
					sign = tmp.substring(matchIndex + 11).trim();
					// separator之前的残留表达式一并纳入拼接
					if (tmp.substring(0, matchIndex).trim().length() > 0) {
						segments.add(tmp.substring(0, matchIndex));
					}
				} else {
					segments.add(tmp);
				}
			}
			// mysql目标的逗号为concat语义;转到其他数据库时按目标库的拼接方式重组
			// (pg/oracle/db2系为||,sqlserver为+)
			String joiner;
			if (dbType == DBType.SQLSERVER) {
				joiner = " + ";
			} else if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57
					|| dbType == DBType.H2 || dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
				joiner = ",";
			} else {
				joiner = "||";
			}
			expr = String.join(joiner, segments);
		}
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.OSCAR || dbType == DBType.STARDB
				|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE) {
			if (isStringAgg) {
				return super.IGNORE;
			}
			// 原则上可以通过string_agg 但如果类型不是字符串就会报错
			return " array_to_string(ARRAY_AGG(" + expr + ")," + sign + ") ";
		}
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57 || dbType == DBType.H2
				|| dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
			if (!isStringAgg) {
				return super.IGNORE;
			}
			return " group_concat(" + expr + " separator " + sign + ") ";
		}
		// update 2026-9-5 补充oracle系/DB2/sqlserver的listagg与STRING_AGG转换(此前缺失,原样输出在目标库非法)
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.OCEANBASE) {
			if (isStringAgg || "listagg".equals(functionName.toLowerCase(Locale.ROOT))) {
				return super.IGNORE;
			}
			return " listagg(" + expr + "," + sign + ") within group (order by null) ";
		}
		// update 2026-9-5 db2的listagg不接受order by null,直接省略within group子句
		if (dbType == DBType.DB2) {
			if (isStringAgg || "listagg".equals(functionName.toLowerCase(Locale.ROOT))) {
				return super.IGNORE;
			}
			return " listagg(" + expr + "," + sign + ") ";
		}
		if (dbType == DBType.SQLSERVER) {
			if (isStringAgg) {
				return super.IGNORE;
			}
			return " string_agg(" + expr + "," + sign + ") ";
		}
		// update 2026-9-6
		// 补sqlite:无group_concat(separator)关键字语法,以group_concat(expr,sep)两参形态
		if (dbType == DBType.SQLITE) {
			return " group_concat(" + expr + "," + sign + ") ";
		}
		return super.IGNORE;
	}

}
