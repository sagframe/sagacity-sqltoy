/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 转换to_date函数
 * @author zhongxuchen
 * @version v1.0,Date:2013-1-2
 */
public class ToDate extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(to_date|date)\\(");

	@Override
	public String dialects() {
		return "oracle,dm,mysql,sqlserver,h2";
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
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.SQLSERVER) {
			if (args != null && args.length == 1) {
				if (args[0].length() > 12) {
					return "convert(datetime," + args[0] + ")";
				}
				return "convert(date," + args[0] + ")";
			}
		}
		if (dialect == DBType.ORACLE || dialect == DBType.ORACLE11) {
            if (args != null) {
                if (args.length > 1) {
                    return wrapArgs("to_date", args);
                } else {
                    if (args[0].length() > 12) {
                        return "to_date(" + args[0] + ",'yyyy-MM-dd HH:mm:ss')";
                    }else {
                        return "to_date(" + args[0] + ",'yyyy-MM-dd')";
                    }
                }
            }
        }
        if (dialect == DBType.H2) {
            if (args != null) {
                if (args != null && args.length == 1) {
                    if (args[0].length() > 12) {
                        return "formatdatetime(" + args[0] + ",'yyyy-MM-dd HH:mm:ss')";
                    }else {
                        return "formatdatetime(" + args[0] + ",'yyyy-MM-dd')";
                    }
                }
            }
        }
		// 表示不做修改
		return super.IGNORE;
	}
}
