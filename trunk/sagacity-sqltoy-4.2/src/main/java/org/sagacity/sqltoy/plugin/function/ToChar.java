/**
 * 
 */
package org.sagacity.sqltoy.plugin.function;

import org.sagacity.sqltoy.plugin.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 将其它类型数据转换成字符串
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ToChar.java,Revision:v1.0,Date:2013-1-2
 */
public class ToChar extends IFunction {
	public String dialects() {
		return "";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String
	 * [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		switch (dialect) {
		case DBType.MYSQL:
		case DBType.MYSQL8:
			return "date_format(" + args[0] + "," + args[1] + ")";
		case DBType.ORACLE:
		case DBType.ORACLE12: {
			String format = args[1];
			format = format.replace("HH:mm:ss", "hh24:mi:ss");
			format = format.replace("HH.mm.ss", "hh24.mi.ss");
			return "to_char(" + args[0] + "," + format + ")";
		}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public String regex() {
		return "(?i)\\Wto\\_char\\(";
	}
}
