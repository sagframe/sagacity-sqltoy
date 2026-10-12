package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description MySQL系find_in_set(str,逗号分隔串)的跨库适配:判断str是否为list中的完整元素。
 *              改写产物为boolean(pg系where必须boolean,不接受"非零整数即真"),函数后的
 *              ">0/>=1/!=0/<>=0"命中比较与"=0"未命中比较由suffix钩子连带消费改写, 裸真值形态(WHERE
 *              find_in_set(..)...)与显式比较形态两种惯用法全兼容。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class FindInSet extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(find_in_set)\\(");

	/**
	 * 函数闭括号后的比较符子句(裸操作符形态,suffixNeedsParen=false按命中长度消费)
	 */
	private static Pattern cmpPattern = Pattern.compile("\\s*(>\\s*0|>\\s*=\\s*1|!=\\s*0|<>\\s*0|=\\s*0)");

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.plugins.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return ALL;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.plugins.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public boolean suffixNeedsParen() {
		return false;
	}

	@Override
	public Pattern suffixPattern() {
		return cmpPattern;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.sagacity.sqltoy.plugins.function.IFunction#wrap(int,
	 * java.lang.String, boolean, java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		return wrapInternal(dialect, args, true);
	}

	@Override
	public String wrapWithSuffix(int dialect, String functionName, String suffix, String... args) {
		// "=0"反转命中口径,其余(>0/>=1/!=0/<>0)与裸真值同为命中口径(归一空白后判别)
		boolean hit = !"=0".equals(suffix.replaceAll("\\s", ""));
		return wrapInternal(dialect, args, hit);
	}

	/**
	 * 按方言组织"完整元素相等"的boolean表达式
	 *
	 * @param dialect 目标方言
	 * @param args    函数参数(str, list)
	 * @param hit     true=命中口径(strpos…>0),false=未命中口径(strpos…=0)
	 * @return 改写表达式;参数形态不完整或MySQL系原生返回null(原样保留)
	 */
	private String wrapInternal(int dialect, String[] args, boolean hit) {
		if (args == null || args.length < 2) {
			// 参数形态不完整,原样保留交由目标库响亮报错
			return super.IGNORE;
		}
		// find_in_set(str, list): str=待判断元素, list=逗号分隔集合串
		String str = args[0], list = args[1];
		// MySQL语法系原生支持,直接透传
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.TIDB
				|| dialect == DBType.OCEANBASE) {
			return super.IGNORE;
		}
		String cmp = hit ? ">0" : "=0";
		// PG系:strpos为原生精确子串定位(无通配符语义)。首尾补逗号钉住元素边界实现"完整元素相等"。
		// 不可用LIKE '%,str,%'形态:元素含_或%时通配符会误命中(如NODE_11误命中NODE_1,而
		// mq_consumer_nodes类节点清单恰以下划线+数字命名,是真实高频场景)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.OSCAR || dialect == DBType.STARDB
				|| dialect == DBType.MOGDB || dialect == DBType.VASTBASE || dialect == DBType.KINGBASE
				|| dialect == DBType.HIGHGO) {
			return "(strpos(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// 原生可用(乌龙排查实证instr('admin','d',1)=2,原注释"多参无对应"系误判,透传即生效)

		// MySQL语法系但无find_in_set的库(h2/doris/starrocks):concat包裹+instr精确子串
		// (instr(str,sub)在此三库的原生形态,见Instr适配器);||在MySQL语法系是OR,必须用concat
		if (dialect == DBType.H2 || dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			return "(instr(concat(','," + list + ",','), concat(','," + str + ",','))" + cmp + ")";
		}
		// Oracle语法系(oracle/oracle11/dm):instr(str,sub)同序原生+||拼接
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11 || dialect == DBType.DM) {
			return "(instr(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// sqlserver:无instr/||,用charindex(sub,str)与'+'拼接
		if (dialect == DBType.SQLSERVER) {
			return "(charindex(','+" + str + "+',', ','+" + list + "+',')" + cmp + ")";
		}
		// db2:locate(sub,str)针在前(与instr参数序相反)+||
		if (dialect == DBType.DB2) {
			return "(locate(','||" + str + "||',', ','||" + list + "||',')" + cmp + ")";
		}
		// hana:locate(str,sub)草垛在前(与db2/mysql的locate反序,见Instr适配器)+||
		if (dialect == DBType.HANA) {
			return "(locate(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// sqlite:instr(str,sub)+||
		if (dialect == DBType.SQLITE) {
			return "(instr(','||" + list + "||',', ','||" + str + "||',')" + cmp + ")";
		}
		// clickhouse:position(haystack,needle)逗号形态原生(见Instr适配器)+concat变参
		if (dialect == DBType.CLICKHOUSE) {
			return "(position(concat(','," + list + ",','), concat(','," + str + ",','))" + cmp + ")";
		}
		return super.IGNORE;
	}
}
