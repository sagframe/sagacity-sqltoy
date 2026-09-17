package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 针对不同数据库字符串indexOf 函数的不同用法转换
 * @author zhongxuchen
 * @version v1.0,Date:2013-03-21
 */
public class Instr extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(instr|charindex|position)\\(");

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
		String[] realArgs;
		String funLow = functionName.toLowerCase(Locale.ROOT);
		if ("position".equals(funLow)) {
			realArgs = args[0].split("(?i)\\sin\\s");
		} else {
			realArgs = args;
		}
		StringBuilder result = new StringBuilder();
		if (dialect == DBType.SQLSERVER) {
			if ("charindex".equals(funLow)) {
				return super.IGNORE;
			}
			// update 2026-9-5 charindex不支持occurrence参数(instr第4参),
			// 静默丢弃会改变语义,保持原样交由目标库报错(响亮失败优于静默错误结果)
			if (realArgs.length > 3) {
				return super.IGNORE;
			}
			result.append("charindex(");
			if ("position".equals(funLow)) {
				result.append(realArgs[0]).append(",").append(realArgs[1]);
			} else {
				result.append(realArgs[1]).append(",").append(realArgs[0]);
				if (realArgs.length > 2) {
					result.append(",").append(realArgs[2]);
				}
			}
			return result.append(")").toString();
		}
		// update 2026-9-9 Db2 LUW无INSTR函数(LOCATE/POSSTR承担),原分支对instr原样透传会报
		// 函数不存在;LOCATE(search,source[,start])参数语义与mysql的locate一致,统一转locate
		if (dialect == DBType.DB2) {
			if ("position".equals(funLow)) {
				// position(sub in str):realArgs=[sub,str]
				return "locate(" + realArgs[0] + "," + realArgs[1] + ")";
			}
			if ("charindex".equals(funLow)) {
				// charindex(sub,str[,start]):参数序与locate一致
				return wrapArgs("locate", realArgs);
			}
			// instr(str,sub[,pos[,occurrence]]):第4参occurrence无locate对应形态,原样保留交由目标库报错
			if (realArgs.length > 3) {
				return super.IGNORE;
			}
			result.append("locate(").append(realArgs[1]).append(",").append(realArgs[0]);
			if (realArgs.length > 2) {
				result.append(",").append(realArgs[2]);
			}
			return result.append(")").toString();
		}
		if (dialect == DBType.MYSQL || dialect == DBType.ORACLE || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.TIDB || dialect == DBType.ORACLE11 || dialect == DBType.MYSQL57
				|| dialect == DBType.H2 || dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			// update 2026-9-15 补starrocks的position形态:starrocks真库实测不支持position(sub in str)
			// 关键字语法(Unexpected input 'in'),starrocks原生instr(str,sub)参数序与instr一致,直接换名
			if ("position".equals(funLow) && dialect == DBType.STARROCKS) {
				return "instr(" + realArgs[1] + "," + realArgs[0] + ")";
			}
			if ("instr".equals(funLow)) {
				// update 2026-9-5 三参instr(str,sub,pos)在mysql无此语法,转locate(sub,str,pos);
				// 四参(occurrence)mysql的locate同样不支持,原样保留交由目标库报错
				if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57
						|| dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
					if (realArgs.length == 3) {
						return "locate(" + realArgs[1] + "," + realArgs[0] + "," + realArgs[2] + ")";
					}
				}
				return super.IGNORE;
			}
			// position mysql、h2也支持 update 2021-11-11
			if (dialect == DBType.MYSQL || dialect == DBType.MYSQL57 || dialect == DBType.H2 || dialect == DBType.DORIS
					|| dialect == DBType.STARROCKS) {
				if ("position".equals(funLow)) {
					return super.IGNORE;
				}
			}
			result.append("instr(").append(realArgs[1]).append(",").append(realArgs[0]);
			if (realArgs.length > 2) {
				result.append(",").append(realArgs[2]);
			}
			if (realArgs.length > 3) {
				result.append(",").append(realArgs[3]);
			}
			return result.append(")").toString();
		}
		// update 2026-9-14 补KINGBASE(KingbaseES基于PG,函数语法归PG系)
		if (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14 || dialect == DBType.GAUSSDB
				|| dialect == DBType.OPENGAUSS || dialect == DBType.OSCAR || dialect == DBType.STARDB
				|| dialect == DBType.MOGDB || dialect == DBType.VASTBASE || dialect == DBType.KINGBASE) {
			if ("position".equals(funLow)) {
				return super.IGNORE;
			}
			if (realArgs.length == 2) {
				result.append("position(");
				if ("charindex".equals(funLow)) {
					result.append(realArgs[0]).append(" in ").append(realArgs[1]);
				} else {
					result.append(realArgs[1]).append(" in ").append(realArgs[0]);
				}
				return result.append(")").toString();
			}
			// update 2026-9-15 补三参instr(str,sub,pos):pg系position(strpos)无起始位参数,
			// 原样透传报function instr does not exist(pg真库实测);以strpos(substr(str,pos),sub)
			// 组合表达"从pos起搜索"(命中则加pos-1偏移,未命中为0)
			if (realArgs.length == 3) {
				return "(case when strpos(substr(" + realArgs[0] + "," + realArgs[2] + ")," + realArgs[1]
						+ ")=0 then 0 else strpos(substr(" + realArgs[0] + "," + realArgs[2] + ")," + realArgs[1]
						+ ")+(" + realArgs[2] + ")-1 end)";
			}
		}
		// update 2026-9-11 clickhouse:无instr函数,position(haystack,needle)逗号形态且参数序
		// 与instr(str,substr)一致;3/4参的出现次数语义CH无对应,原样保留交目标库响亮报错
		// update 2026-9-15 补charindex写法:charindex原生参数序(sub,str)与CH的position
		// (haystack,needle)相反,须对调(真库实测未对调时恒为0)
		if (dialect == DBType.CLICKHOUSE) {
			if ("position".equals(funLow)) {
				return super.IGNORE;
			}
			if (realArgs.length == 2) {
				if ("charindex".equals(funLow)) {
					return "position(" + realArgs[1] + "," + realArgs[0] + ")";
				}
				return "position(" + realArgs[0] + "," + realArgs[1] + ")";
			}
		}
		// update 2026-9-11 hana:无instr函数,LOCATE(<string>,<substring>[,<start>])参数序与
		// instr(str,sub[,pos])一致(haystack在前,注意与db2/mysql的locate(needle,haystack)相反);
		// 第4参occurrence无locate对应形态,原样保留交由目标库报错
		// update 2026-9-15 hana真库实测并无标准SQL的POSITION(sub in str)形态(报invalid
		// name of function or procedure: POSITION,与此前注释假设不符),统一转LOCATE(str,sub)
		if (dialect == DBType.HANA) {
			if ("position".equals(funLow)) {
				return "locate(" + realArgs[1] + "," + realArgs[0] + ")";
			}
			if (realArgs.length > 3) {
				return super.IGNORE;
			}
			if ("charindex".equals(funLow)) {
				// charindex(sub,str[,start]):参数序对调转locate(str,sub[,start])
				result.append("locate(").append(realArgs[1]).append(",").append(realArgs[0]);
				if (realArgs.length > 2) {
					result.append(",").append(realArgs[2]);
				}
				return result.append(")").toString();
			}
			return wrapArgs("locate", realArgs);
		}
		// update 2026-9-15 补sqlite:sqlite无position形态(真库实测position('cd' in name)把
		// in name解析为IN子查询报no such table),instr(str,sub)为sqlite原生仅两参;
		// 三参instr(str,sub,pos)以"substr后移起点+instr+偏移"组合表达;charindex(sub,str)参数序对调
		if (dialect == DBType.SQLITE) {
			if ("position".equals(funLow)) {
				return "instr(" + realArgs[1] + "," + realArgs[0] + ")";
			}
			if ("charindex".equals(funLow) && realArgs.length == 2) {
				return "instr(" + realArgs[1] + "," + realArgs[0] + ")";
			}
			if (realArgs.length == 3) {
				return "(case when instr(substr(" + realArgs[0] + "," + realArgs[2] + ")," + realArgs[1]
						+ ")=0 then 0 else instr(substr(" + realArgs[0] + "," + realArgs[2] + ")," + realArgs[1] + ")+("
						+ realArgs[2] + ")-1 end)";
			}
			return super.IGNORE;
		}
		return super.IGNORE;
	}
}
