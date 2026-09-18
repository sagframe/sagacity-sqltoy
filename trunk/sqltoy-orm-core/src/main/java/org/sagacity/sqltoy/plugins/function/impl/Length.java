package org.sagacity.sqltoy.plugins.function.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 提供不同数据库length函数的转换(主要是length和len之间的互换)
 * @author zhongxuchen
 * @version v1.0,Date:2015-10-19
 */
public class Length extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(length|lengthb|len|datalength|char_length)\\(");

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
	 * java.lang.String, boolean, java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (args == null || args.length == 0) {
			return super.IGNORE;
		}
		String funLow = functionName.toLowerCase(Locale.ROOT);
		if (dialect == DBType.SQLSERVER) {
			// update 2026-9-9 lengthb为字节语义映射DATALENGTH(原与datalength外的形态统一转len
			// 字符数,字节/字符语义静默互换)
			if ("datalength".equals(funLow) || "lengthb".equals(funLow)) {
				return wrapArgs("datalength", args);
			}
			return wrapArgs("len", args);
		}
		// update 2026-9-14
		// 补KINGBASE(KingbaseES基于PG,函数语法归PG系,length为字符数/octet_length为字节数)
		if (dialect == DBType.ORACLE || dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14
				|| dialect == DBType.DB2 || dialect == DBType.GAUSSDB || dialect == DBType.MOGDB
				|| dialect == DBType.VASTBASE || dialect == DBType.OPENGAUSS || dialect == DBType.STARDB
				|| dialect == DBType.OSCAR || dialect == DBType.OCEANBASE || dialect == DBType.DM
				|| dialect == DBType.ORACLE11 || dialect == DBType.KINGBASE) {
			// update 2026-9-9 按字节/字符语义拆分:原实现将datalength/char_length/len统一映射length,
			// oracle系的LENGTH为字符数,datalength(字节数)被静默转为字符数;Db2的LENGTH为字节数、
			// CHAR_LENGTH为字符数,与oracle/pg惯例相反,length/char_length原样透传会得到字节数
			boolean pgFamily = (dialect == DBType.POSTGRESQL || dialect == DBType.POSTGRESQL14
					|| dialect == DBType.GAUSSDB || dialect == DBType.MOGDB || dialect == DBType.VASTBASE
					|| dialect == DBType.OPENGAUSS || dialect == DBType.STARDB || dialect == DBType.KINGBASE);
			if (dialect == DBType.DB2) {
				// Db2:LENGTH/OCTET_LENGTH=字节数,CHAR_LENGTH=字符数
				if ("datalength".equals(funLow) || "lengthb".equals(funLow)) {
					return wrapArgs("octet_length", args);
				}
				if ("length".equals(funLow) || "char_length".equals(funLow) || "len".equals(funLow)) {
					return wrapArgs("char_length", args);
				}
				return wrapArgs(functionName, args);
			}
			// update 2026-9-5 PG系无lengthb函数,转为octet_length(字节长度);
			// oracle/DM/OCEANBASE/OSCAR原生支持lengthb,原样保留
			if (pgFamily && "lengthb".equals(funLow)) {
				return wrapArgs("octet_length", args);
			}
			// datalength为字节语义:oracle系(oracle/dm/oceanbase)映射LENGTHB,PG系映射octet_length;
			// OSCAR(神通)内核归属未实测,保守保持原length映射
			if ("datalength".equals(funLow)) {
				if (pgFamily) {
					return wrapArgs("octet_length", args);
				}
				if (dialect == DBType.OSCAR) {
					return wrapArgs("length", args);
				}
				return wrapArgs("lengthb", args);
			}
			// char_length/len为字符语义:oracle系无char_length函数,统一映射LENGTH(字符数)
			if ("char_length".equals(funLow) || "len".equals(funLow)) {
				return wrapArgs("length", args);
			}
			return wrapArgs(functionName, args);
		}
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57 || dialect == DBType.H2
				|| dialect == DBType.DORIS || dialect == DBType.STARROCKS) {
			if ("char_length".equals(funLow)) {
				return wrapArgs(functionName, args);
			}
			return wrapArgs("length", args);
		}
		// update 2026-9-15 补hana:hana无lengthb/len/datalength函数(真库实测lengthb原样透传
		// 报语法错误),LENGTH为字符数且无字节长度函数,lengthb/datalength统一映射LENGTH
		// (字节语义降级为字符数:ASCII数据同值,非ASCII存在差异属引擎能力边界)
		if (dialect == DBType.HANA) {
			return wrapArgs("length", args);
		}
		// update 2026-9-15 补sqlite:sqlite无lengthb/datalength(真库实测报no such function:
		// lengthb),LENGTH为字符数同样无字节长度函数,统一映射LENGTH(降级说明同hana)
		if (dialect == DBType.SQLITE) {
			return wrapArgs("length", args);
		}
		// update 2026-9-15 补clickhouse:CH无lengthb(真库实测报Function lengthb does not
		// exist);
		// CH的length()为字节数、lengthUTF8()为字符数,lengthb/datalength映射length(字节语义),
		// length/len/char_length映射lengthUTF8(字符语义,修正原样透传length在非ASCII下按字节计的偏差)
		if (dialect == DBType.CLICKHOUSE) {
			if ("lengthb".equals(funLow) || "datalength".equals(funLow)) {
				return wrapArgs("length", args);
			}
			return wrapArgs("lengthUTF8", args);
		}
		return super.IGNORE;
	}

}
