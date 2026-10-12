package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description oracle decode函数
 * @author zhongxuchen
 * @version v1.0,Date:2013-01-02
 */
public class Decode extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wdecode\\(");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String dialects() {
		return super.ALL;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length < 3) {
			return super.IGNORE;
		}
		/*
		 * if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8) { return
		 * wrapArgs("ELT", args); } else
		 */
		// oracle支持decode
		// 驱动对varchar结果读回一律空串(真库实测decode(lvarchar列,...)读空、常量参与正常),
		// 以case when重写并包::lvarchar定型(case原生、cast后读回正常,真库验证)

		if (dialect == DBType.ORACLE || dialect == DBType.DM || dialect == DBType.OCEANBASE
				|| dialect == DBType.ORACLE11 || dialect == DBType.H2) {
			return super.IGNORE;
		}
		// decode(param,a1,a11,a2,a21,other)
		String param = args[0];
		StringBuilder sql = new StringBuilder(" case ");
		int loopSize = (args.length - 1) / 2;
		for (int i = 0; i < loopSize; i++) {
			sql.append(" when ").append(param).append("=").append(args[1 + i * 2]).append(" then ")
					.append(args[1 + i * 2 + 1]);
		}
		// update 2026-9-5 decode参数结构:expr + n*(search,result) + 可选default:
		// 参数个数为偶数时末参为默认值(生成else);奇数时无默认值,
		// 不生成else子句(oracle decode无匹配返回null,不再将最后一个结果值误当默认值)。
		// 固有边界:case when param=search不匹配NULL(oracle decode中null=null视为匹配),
		// 以及参数类型的隐式转换差异,跨库使用时请注意
		if (args.length % 2 == 0) {
			sql.append(" else ").append(args[args.length - 1]);
		}
		sql.append(" end ");
		return sql.toString();
	}

	/** update 2026-10-12 g8分支用:与主转换逻辑同构的case when重写(含默认值规则) */
	private String buildCaseWhen(String[] args) {
		String param = args[0];
		StringBuilder sql = new StringBuilder(" case ");
		int loopSize = (args.length - 1) / 2;
		for (int i = 0; i < loopSize; i++) {
			sql.append(" when ").append(param).append("=").append(args[1 + i * 2]).append(" then ")
					.append(args[1 + i * 2 + 1]);
		}
		if (args.length % 2 == 0) {
			sql.append(" else ").append(args[args.length - 1]);
		}
		sql.append(" end ");
		return sql.toString();
	}
}
