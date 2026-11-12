package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 数值截断的跨库适配(2026-11-10 补齐):同时匹配两种源写法—— MySQL 系的
 *              truncate(num,scale) 与标准/Oracle/PG/DB2/H2 系的 trunc(num,scale),
 *              函数匹配不只认 MySQL 拼写。MySQL 系(含 CH,CH 两函数皆原生)原生; Oracle/DM/PG
 *              系/DB2/H2 转同名 trunc;sqlserver 转 round(x,d,1)(第三参非零即截断); hana 转
 *              round(x,d,ROUND_DOWN);sqlite 的 round 无第三参暂不适配,原样透传响亮报错。
 *              仅适配数值语义:DDL 的 TRUNCATE TABLE 不带括号不会误命中。 要求两参(单参属误用原样报错)。
 * @author zhongxuchen
 * @version v1.1,Date:2026-11-10
 */
public class Truncate extends IFunction {

	// 同时匹配 truncate( 与 trunc(;truncate 列前防止其被 trunc 分支截断
	private static Pattern regex = Pattern.compile("(?i)\\W(truncate|trunc)\\(");

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
		// update 2026-11-10 只有"单引号且非数值"或"MySQL系双引号且非数值"的第二参才是日期单位
		// (如 Oracle 的 TRUNC(ts,'HH')):不能在本函数(同样匹配 trunc()被再次处理)里被改写成
		// truncate(ts,'HH')(MySQL 把 'HH' 当 0 位小数,静默截掉时分秒)。双引号在标准/PG/Oracle/
		// DB2/H2/CH/SQLServer/HANA 是标识符(列),单引号数值('2')是位数,二者都应正常走数值转换;
		// 但 MySQL 系默认非 ANSI_QUOTES,双引号是字符串字面量,非数值内容仍须按日期单位拦截
		if (isDateUnitArg(dialect, args[1].trim())) {
			return super.IGNORE;
		}
		return numericTrunc(dialect, functionName, args[0].trim(), args[1].trim());
	}

	/**
	 * @description update 2026-11-10 判断入参是否为引号字面量('...'或"..."):数值位数参数应为数值/占位/列,
	 *              引号字面量在此语境只可能是日期格式模型(如Oracle的TRUNC(ts,'HH')),不能当截断位数
	 * @param arg 已trim的入参
	 * @return true表示引号字面量
	 */
	public static boolean isQuotedLiteral(String arg) {
		if (arg == null || arg.length() < 2) {
			return false;
		}
		char first = arg.charAt(0);
		return (first == '\'' || first == '"') && arg.charAt(arg.length() - 1) == first;
	}

	/**
	 * @description update 2026-11-10 精确判定第二参是否为"日期单位"(而非数值位数): 单引号且内容非数值(如
	 *              'HH'/'MM')是日期单位,应拦截走日期逻辑;单引号数值('2')是位数。 双引号按目标方言分流:MySQL 系默认非
	 *              ANSI_QUOTES(双引号=字符串),非数值内容(如 "HH") 同样是日期单位,必须拦截——否则
	 *              truncate(ts,"HH") 把 "HH" 当 0 位小数,静默截成 2026.0 (MySQL 9.3 真库实测);标准
	 *              SQL/PG/Oracle/DB2/H2/CH/SQLServer/HANA 双引号是标识符
	 *              (列),放行数值转换(truncate(x,"col")→trunc(x,"col")/round(x,"col",1)
	 *              均为正确形态)。
	 * @param dialect 目标方言
	 * @param arg     已 trim 的第二参
	 * @return true 表示是日期单位;false 表示是位数/列/标识符,应继续数值转换
	 */
	public static boolean isDateUnitArg(int dialect, String arg) {
		if (arg == null || arg.length() < 2) {
			return false;
		}
		char first = arg.charAt(0);
		String inner = arg.substring(1, arg.length() - 1).trim();
		if (first == '"') {
			// MySQL 系:双引号是字符串字面量,非数值内容即日期单位;其余方言双引号是标识符(列)
			if (isMySqlFamily(dialect)) {
				return !inner.matches("[-+]?[0-9]+(\\.[0-9]+)?");
			}
			return false;
		}
		// 非引号(裸数值/列/占位符):不是日期单位
		if (first != '\'') {
			return false;
		}
		// 单引号:内容为数值则是位数,非数值才是日期单位
		return !inner.matches("[-+]?[0-9]+(\\.[0-9]+)?");
	}

	/**
	 * @param dialect 目标方言
	 * @return true 表示 MySQL 协议系(默认双引号按字符串字面量解释)
	 */
	private static boolean isMySqlFamily(int dialect) {
		return dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE;
	}

	/**
	 * @description 数值截断的方言映射(抽static供DateTrunc共用):
	 *              DateTrunc按单位归一失败识别出trunc(数值)用法后转发至此,使Oracle/PG系用户的
	 *              trunc(x,d)写法在MySQL系等目标库得到等价形态
	 * @param dialect      目标方言
	 * @param functionName 源函数名(truncate|trunc)
	 * @param num          数值表达式
	 * @param scale        截断位数表达式
	 * @return 目标方言形态;未适配方言返回null(响亮)
	 */
	public static String numericTrunc(int dialect, String functionName, String num, String scale) {
		String funLow = functionName.toLowerCase(Locale.ROOT);
		// MySQL 系/CH/OB(MySQL 模式):truncate 原生;CH 的 trunc 亦原生
		boolean mysqlFamily = dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS || dialect == DBType.OCEANBASE
				|| dialect == DBType.CLICKHOUSE;
		if (mysqlFamily) {
			// CH 两函数皆原生;其余 MySQL 系仅 truncate 原生,源 trunc 须改名 truncate
			if (dialect == DBType.CLICKHOUSE || "truncate".equals(funLow)) {
				return null;
			}
			return "truncate(" + num + "," + scale + ")";
		}
		// Oracle/DM/PG 系/DB2/H2:trunc 为原生数值截断函数
		boolean truncNative = dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM
				|| dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.OPENGAUSS
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.STARDB || dialect == DBType.OSCAR || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO || dialect == DBType.DB2 || dialect == DBType.H2;
		if (truncNative) {
			// 源 trunc 原生透传;源 truncate 须改名 trunc
			if ("trunc".equals(funLow)) {
				return null;
			}
			return "trunc(" + num + "," + scale + ")";
		}
		// sqlserver:round 第三参非零=截断(不去尾数进位),与 TRUNCATE 语义等价,与源函数名无关
		if (dialect == DBType.SQLSERVER) {
			return "round(" + num + "," + scale + ",1)";
		}
		// hana:round_mode 取 ROUND_DOWN(向零截断)
		if (dialect == DBType.HANA) {
			return "round(" + num + "," + scale + ",ROUND_DOWN)";
		}
		return null;
	}
}
