package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 针对mysql数据库字符连接函数concat在其它数据库中的函数转换
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class Concat extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wconcat\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return super.ALL;
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
		if (args == null || args.length < 2) {
			return super.IGNORE;
		}
		// update 2026-9-14 sqlite 3.44+已原生提供concat,但两参以上仍统一转||拼接(统一拼接形态与null传播语义不变,转换保留仅作澄清);
		// DB2/oracle系(含OCEANBASE,与Nvl/Now等函数的oracle系归属保持一致)、hana、kingbase的concat仅支持两参,
		// 超出支持的参数个数统一转||拼接(sqlite从两参起转,oracle/db2/oceanbase/hana/kingbase三参起转)。
		// OB边界实测:||产物适用于OB oracle模式租户;OB CE mysql模式租户把||当逻辑或求值,'a'||'-'||'b'静默返回0(比报错更危险),
		// mysql模式租户应配dialect=mysql走mysql转换链(concat原生多参),verify工程即此配置。
		// hana:CONCAT仅两参(SPS08实测三参报wrong number of arguments),三参起转||拼接(||原生支持且null按空串处理同oracle系)。
		// kingbase:KingbaseES V9(默认oracle兼容模式)真库实测sys.concat(text,text)两参遮蔽pg_catalog.concat(VARIADIC "any"),
		// 三参起报"function sys.concat(text, unknown, varchar) does not exist"(concat_ws因sys无同名函数不受影响),三参起转||拼接
		// (||实测将null按空串处理,与concat跳过null语义一致)。
		// 已知边界(同ConcatWs):mysql的concat任一参数为NULL时整体返回NULL,oracle/db2的||将NULL视为空串、sqlite传播NULL,
		// 参数可能为NULL时跨库结果存在差异,需严格一致请显式coalesce/ifnull包裹参数

		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DB2 || dialect == DBType.SQLITE
				|| dialect == DBType.OCEANBASE || dialect == DBType.HANA || dialect == DBType.KINGBASE) {
			if (dialect != DBType.SQLITE && args.length < 3) {
				return super.IGNORE;
			}
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < args.length; i++) {
				if (i > 0) {
					result.append("||");
				}
				result.append(args[i].replace("\\'", "''"));
			}
			return result.toString();
		}
		return super.IGNORE;
	}
}