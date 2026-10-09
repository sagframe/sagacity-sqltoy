package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 不同数据库substr函数的转化
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class SubStr extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(substr|substring)\\(");

	/**
	 * 本身就支持substr的数据库
	 */
	@Override
	public String dialects() {
		return ALL;
	}

	/**
	 * 匹配substr(xx，xx)函数的正则表达式
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	/**
	 * 针对不同数据库对如：substr(arg1,arg2,arg3)进行转换，框架自动将arg1和arg2等参数作为数组传进来
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		// update 2026-9-14 补KINGBASE(KingbaseES基于PG,函数语法归PG系)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.SQLSERVER || dialect == DBType.H2 || dialect == DBType.STARDB
				|| dialect == DBType.OSCAR || dialect == DBType.KINGBASE) {
			// update 2026-10-4 负起点字面量(mysql惯用substr(s,-n)取末n位)统一处理:
			// 两参转RIGHT(s,n);三参转case守卫形态(start=串长-n+1,串长不足n时取1,mysql语义)。
			// 原仅sqlserver两参做了RIGHT,pg系substring(s,-n)按"串首之前偏移"返回整串静默错值。
			// 表达式起点无法判断正负,原样保留交由目标库响亮报错
			String start = args[1].trim();
			if (start.startsWith("-") && start.substring(1).matches("\\d+")) {
				String n = start.substring(1);
				// sqlserver无length函数(为len),pg系/h2为length
				String lenFn = (dialect == DBType.SQLSERVER) ? "len" : "length";
				// update 2026-10-9 对齐mysql负起点契约:|n|超过串长时起点<1返回空串(原两参
				// RIGHT短串返回整串、三参守卫短串分支返回substring(s,1,len),均与mysql的''
				// 偏离且静默错值)
				if (args.length == 2) {
					return "case when " + lenFn + "(" + args[0] + ")>=" + n + " then RIGHT(" + args[0] + "," + n
							+ ") else '' end";
				}
				return "case when " + lenFn + "(" + args[0] + ")<" + n + " then '' else substring(" + args[0] + ","
						+ lenFn + "(" + args[0] + ")-" + n + "+1," + args[2] + ") end";
			}
			if (dialect == DBType.SQLSERVER && args.length == 2) {
				// update 2026-9-5 mysql惯用负数起点(substr(s,-2)=末2位):
				// sqlserver substring负起点语义不同,字面量负数转RIGHT(表达式无法判断,原样处理)
				return "substring(" + args[0] + "," + args[1] + ",len(" + args[0] + "))";
			}
			return wrapArgs("substring", args);
		}
		if (dialect == DBType.DB2) {
			// update 2026-10-4 两参负起点字面量转RIGHT(db2 substr起点须>=1,负起点报SQL0138);
			// 三参负起点走下方守卫仍为响亮报错,不静默错值
			String start = args[1].trim();
			if (args.length == 2 && start.startsWith("-") && start.substring(1).matches("\\d+")) {
				// update 2026-10-9 同pg系:|n|超串长时mysql返回空串,RIGHT短串返回整串须守卫
				String n = start.substring(1);
				return "case when length(" + args[0] + ")>=" + n + " then RIGHT(" + args[0] + "," + n + ") else '' end";
			}
			// update 2026-9-15 db2的substr长度越界直接报SQL0138(真库实测:substr('abcdef',3,100)、
			// substr('abcdef',10,1)均报错;长度0合法),与mysql/oracle的越界截断''语义不兼容;
			// 三参用CASE守卫长度(起点越界取0长度,否则取min(长度,剩余长度)),两参同理补长度守卫
			if (args.length == 3) {
				return "substr(" + args[0] + "," + args[1] + ",case when length(" + args[0] + ")<(" + args[1]
						+ ") then 0 else least(" + args[2] + ",length(" + args[0] + ")-(" + args[1] + ")+1) end)";
			}
			return "substr(" + args[0] + ",case when length(" + args[0] + ")<(" + args[1] + ") then 0 else " + args[1]
					+ " end,case when length(" + args[0] + ")<(" + args[1] + ") then 0 else length(" + args[0] + ")-("
					+ args[1] + ")+1 end)";
		}
		if (dialect == DBType.MYSQL || dialect == DBType.ORACLE || dialect == DBType.TIDB || dialect == DBType.DM
				|| dialect == DBType.OCEANBASE || dialect == DBType.ORACLE11 || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS) {
			return wrapArgs("substr", args);
		}
		// 表示不做修改
		// update 2026-10-4 hana的substr负起点为pg语义(真库实测substr('abcdef',-2)='abcdef'
		// 返回整串、substr('abcdef',-2,1)='a',与mysql取末n位/起点串长-n+1不兼容,静默错值),
		// 负起点字面量转RIGHT/CASE守卫(形态与substring分支一致);非负起点的substr为hana原生
		// (SUBSTRING别名),保持透传
		if (dialect == DBType.HANA) {
			String start = args[1].trim();
			if (start.startsWith("-") && start.substring(1).matches("\\d+")) {
				String n = start.substring(1);
				// update 2026-10-9 同pg系:|n|超串长时mysql返回空串,守卫短串分支
				if (args.length == 2) {
					return "case when length(" + args[0] + ")>=" + n + " then RIGHT(" + args[0] + "," + n
							+ ") else '' end";
				}
				return "case when length(" + args[0] + ")<" + n + " then '' else substring(" + args[0]
						+ ",length(" + args[0] + ")-" + n + "+1," + args[2] + ") end";
			}
			return super.IGNORE;
		}
		return super.IGNORE;
	}

	/**
	 * 是否存在参数，如：oracle中的sysdate 就不是一个函数模式
	 */
	public boolean hasArgs() {
		return true;
	}

}
