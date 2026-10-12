package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @author zhongxuchen
 * @version v1.0,Date:2013-04-12
 * @project sagacity-sqltoy
 * @description 数据库判断空的处理逻辑函数转换
 * @modify Date:2013-04-12 填写修改说明
 */
public class Nvl extends IFunction {

	private static Pattern regex = Pattern.compile("(?i)\\W(nvl2|nvl|isnull|ifnull)\\(");

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return ALL;
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

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(int,
	 * java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase(Locale.ROOT);
		// update 2026-10-4 补oracle源nvl2(a,b,c):非oracle系目标转case when判非空取b否则c
		// (语义无损);oracle系原生透传;参数个数非3属误用,原样保留响亮报错
		if ("nvl2".equals(funLow)) {
			if (args.length != 3) {
				return super.IGNORE;
			}
			if (dialect == DBType.ORACLE || dialect == DBType.DM || dialect == DBType.OCEANBASE
					|| dialect == DBType.ORACLE11) {
				return super.IGNORE;
			}
			// update 2026-10-12 g8对裸null字面量的is not null判定报语法错(真库实测),
			// 包(null::lvarchar)定型后可用
			String nvl2Cond = (dialect == DBType.GBASE8S) ? g8NullLiteral(args[0]) : args[0];
			return "case when " + nvl2Cond + " is not null then " + args[1] + " else " + args[2] + " end";
		}
		// update 2026-10-4 单参判空:mysql的isnull(expr)为判空语义返回0/1(唯一合法的单参形态),
		// 原逻辑落到各库映射生成nvl(x)/coalesce(x)/ifnull(x)(均要求>=2参)目标库报参数个数错
		if (args.length == 1) {
			// update 2026-10-12 g8裸null字面量同nvl2(见上注释)
			String isnullCond = (dialect == DBType.GBASE8S) ? g8NullLiteral(args[0]) : args[0];
			return "case when " + isnullCond + " is null then 1 else 0 end";
		}
		if (dialect == DBType.SQLSERVER) {
			return wrapArgs("isnull", args);
		}
		// update 2026-9-9 补CLICKHOUSE(原生coalesce,此前落IGNORE原样输出nvl报函数不存在)
		// update 2026-9-14 补KINGBASE(KingbaseES基于PG,函数语法归PG系)
		// update 2026-10-12 GBASE8S移出本分支(原误归PG系):该引擎coalesce按CASE实现且
		// 对lvarchar列静默返回空串(真库实测非空列/空列均返回''而非默认值),nvl原生正确
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.DB2
				|| dialect == DBType.OPENGAUSS || dialect == DBType.STARDB || dialect == DBType.OSCAR
				|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
				|| dialect == DBType.H2 || dialect == DBType.CLICKHOUSE || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			return wrapArgs("coalesce", args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57 || dialect == DBType.DORIS
				|| dialect == DBType.STARROCKS) {
			// (单参已在上方统一判空处理,此处恒为2参)
			return wrapArgs("ifnull", args);
		}
		// update 2026-9-11 补hana(SPS08实测无NVL函数,报invalid name of function or procedure:
		// NVL;IFNULL/COALESCE原生支持,ifnull对geometry类型对同样可行)
		if (dialect == DBType.SQLITE || dialect == DBType.HANA) {
			return wrapArgs("ifnull", args);
		}
		if (dialect == DBType.ORACLE || dialect == DBType.DM || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE11
				// update 2026-10-12 补GBASE8S(Informix内核nvl原生,coalesce按CASE实现且
				// 对lvarchar静默返空,见上方coalesce分支注释);nvl对varchar参数返回varchar
				// 经该容器驱动读回空串,外层包::lvarchar定型(char参数场景保留尾部空格填充
				// 为char类型固有语义,真库实测)
				|| dialect == DBType.GBASE8S) {
			String nvlInner = wrapArgs("nvl", args);
			return (dialect == DBType.GBASE8S) ? nvlInner + "::lvarchar" : nvlInner;
		}
		// (H2已在coalesce分支处理,此处原重复分支已移除)
		return super.IGNORE;
	}

	/** update 2026-10-12 g8的is (not) null判定对裸null字面量报语法错,包(null::lvarchar)定型 */
	private static String g8NullLiteral(String arg) {
		return "null".equalsIgnoreCase(arg.trim()) ? "(null::lvarchar)" : arg;
	}

}
