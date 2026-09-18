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
	// update 2026-9-15 补listagg:原正则不含listagg,写listagg的SQL从不进入本转换
	// (mysql实测原样透传报FUNCTION listagg does not exist),三别名统一进入分派
	private static Pattern regex = Pattern.compile("(?i)\\W(group_concat|string_agg|listagg)\\(");
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
		// update 2026-9-15 listagg(expr,sep)与string_agg同为(表达式,分隔符)两参结构,
		// 归入两参形态解析(原listagg写法走group_concat多列解析,分隔符被误当拼接列)
		String funLow = functionName.toLowerCase(Locale.ROOT);
		boolean twoArgForm = "string_agg".equals(funLow) || "listagg".equals(funLow);
		String expr;
		String sign = "','";
		if (twoArgForm) {
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
		// update 2026-9-11
		// clickhouse:无group_concat/string_agg,以arrayStringConcat(groupArray(expr),sep)
		// 承担;多列拼接expr沿用||连接符(CH原生支持||字符串拼接),group_concat与string_agg两形态同构
		if (dbType == DBType.CLICKHOUSE) {
			return " arrayStringConcat(groupArray(" + expr + ")," + sign + ") ";
		}
		// update 2026-9-14 补KINGBASE(KingbaseES基于PG,函数语法归PG系):此前漏改导致group_concat不转换
		// 原样透传,目标库报函数不存在
		// update 2026-9-15 写法与目标库同名时原样透传,反向别名统一转换:
		// listagg写法→array_to_string(string_agg写法为pg原生透传)
		if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.OSCAR || dbType == DBType.STARDB
				|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE) {
			if ("string_agg".equals(funLow)) {
				return super.IGNORE;
			}
			// 原则上可以通过string_agg 但如果类型不是字符串就会报错
			return " array_to_string(ARRAY_AGG(" + expr + ")," + sign + ") ";
		}
		// update 2026-9-15 listagg写法统一转group_concat(原IGNORE透传,mysql系无listagg报函数不存在)
		if (dbType == DBType.MYSQL || dbType == DBType.TIDB || dbType == DBType.MYSQL57 || dbType == DBType.H2
				|| dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
			if (!twoArgForm) {
				return super.IGNORE;
			}
			return " group_concat(" + expr + " separator " + sign + ") ";
		}
		// update 2026-9-5 补充oracle系/DB2/sqlserver的listagg与STRING_AGG转换(此前缺失,原样输出在目标库非法)
		// update 2026-9-15
		// string_agg写法统一转listagg(原IGNORE透传,oracle无string_agg报ORA-00904);
		// listagg写法为oracle系原生,原样透传(保留用户自带的within group子句)
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.OCEANBASE) {
			if ("listagg".equals(funLow)) {
				return super.IGNORE;
			}
			return " listagg(" + expr + "," + sign + ") within group (order by null) ";
		}
		// update 2026-9-5 db2的listagg不接受order by null,直接省略within group子句
		if (dbType == DBType.DB2) {
			if ("listagg".equals(funLow)) {
				return super.IGNORE;
			}
			return " listagg(" + expr + "," + sign + ") ";
		}
		// 2026-9-11 hana 2.0 SPS04+提供STRING_AGG(expr,delimiter)两参形态(与sqlserver同名同构,
		// null值跳过语义一致;多列拼接expr走||连接符分支,hana原生支持||)
		// update 2026-9-15 listagg写法统一转string_agg(原IGNORE透传,sqlserver/hana无listagg)
		if (dbType == DBType.SQLSERVER || dbType == DBType.HANA) {
			if ("string_agg".equals(funLow)) {
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
