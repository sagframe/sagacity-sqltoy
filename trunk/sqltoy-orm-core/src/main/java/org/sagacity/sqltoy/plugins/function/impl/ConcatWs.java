package org.sagacity.sqltoy.plugins.function.impl;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 针对mysql数据库字符连接函数concat_ws在其它数据库中的函数转换
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class ConcatWs extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wconcat_ws\\(");

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
		// update 2026-9-5 补充OCEANBASE(oracle模式无concat_ws)/DB2/sqlite的||拼接转换
		// (此前仅oracle系处理,这些库原样输出concat_ws非法);
		// update 2026-9-11 补hana(SPS08实测无CONCAT_WS函数,||拼接原生支持且null按空串处理同oracle系)
		// update 2026-9-15 修复null参数语义:原a||sep||b朴素拼接,参数为null时留下悬挂分隔符
		// (oracle真库实测concat_ws('-',name,nul_col)得'abcdef-'而非'abcdef');改为逐段case when
		// 跳过null参数及其分隔符,且前序参数全为null时当前段不加分隔符,与mysql concat_ws契约一致:
		// ('a',null,'b')='a-b'、(null,'b')='b'、(null,'a','b')='a-b'、('a','','b')='a--b'(空串非null不跳过)
		// update 2026-9-15 补clickhouse:新版CH(26.8实测)的concat_ws已改为null传播
		// (concat_ws('-','abc',NULL)返回NULL,与mysql跳过null语义漂移),一并转||拼接
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.OCEANBASE
				|| dialect == DBType.DB2 || dialect == DBType.SQLITE || dialect == DBType.HANA
				|| dialect == DBType.CLICKHOUSE) {
			String split = args[0].replace("\\'", "''");
			// update 2026-9-15 db2的coalesce(:param,'')会把绑定参数类型推断为''的VARCHAR(0),
			// 绑定实际值报-302字符串超长(真库实测),兜底空串显式CAST定型;oracle/hana/sqlite
			// 的''语义无此问题,统一同形态无副作用(oracle的''即null,coalesce(arg,null)=arg)
			String emptyFallback = (dialect == DBType.DB2) ? "CAST('' AS VARCHAR(4000))" : "''";
			// 首参null以空串兜底(db2/sqlite的||传播null)
			StringBuilder result = new StringBuilder("coalesce(").append(args[1].replace("\\'", "''")).append(",")
					.append(emptyFallback).append(")");
			// 前序参数集合(判定前序是否全为null:全null时当前段不加分隔符)。
			// update 2026-9-15 修复前序coalesce链构建漏参:原实现单前序直用表达式,多前序时
			// StringBuilder未含全部前序参数(4参oracle实测ORA-00938),改为显式集合构建;
			// oracle的coalesce至少两参数,单前序直接用表达式
			java.util.List<String> prev = new ArrayList<String>();
			prev.add(args[1].replace("\\'", "''"));
			for (int i = 2; i < args.length; i++) {
				String arg = args[i].replace("\\'", "''");
				String prevIsNull = (prev.size() == 1) ? (prev.get(0) + " is null")
						: ("coalesce(" + String.join(",", prev) + ") is null");
				result.append("||case when ").append(arg).append(" is null then '' when ").append(prevIsNull)
						.append(" then ").append(arg).append(" else ").append(split).append("||").append(arg)
						.append(" end");
				prev.add(arg);
			}
			return result.toString();
		} else if (dialect == DBType.DM) {
			String splitStr = args[0].trim();
			// dm concat_ws不支持双引号包装分割符号
			if (splitStr.startsWith("\"") && splitStr.endsWith("\"")) {
				args[0] = "'" + splitStr.substring(1, splitStr.length() - 1) + "'";
				return wrapArgs("concat_ws", args);
			}
		}
		// update 2026-9-15 补sqlserver/h2两参形态:h2的concat_ws同样要求至少2个值参数
		// (真库实测两参报语法错误);两参concat_ws(sep,x)语义为单值(x为null则空串),
		// 用concat(x,'')承担(各库concat均要求≥2参恰好满足,null按空串语义一致)
		if ((dialect == DBType.SQLSERVER || dialect == DBType.H2) && args.length == 2) {
			return "concat(" + args[1].replace("\\'", "''") + ",'')";
		}
		return super.IGNORE;
	}

}
