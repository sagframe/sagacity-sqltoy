/**
 * 
 */
package org.sagacity.sqltoy.plugin.function;

import org.sagacity.sqltoy.plugin.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 日期格式化
 * @author zhong
 * @version v1.0, Date:2019年9月9日
 * @modify 2019年9月9日,修改说明
 */
public class DateFormat extends IFunction {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		// TODO Auto-generated method stub
		return "";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IFunction#regex()
	 */
	@Override
	public String regex() {
		return "(?i)\\Wdate\\_format\\(";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IFunction#wrap(int, java.lang.String,
	 * boolean, java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		switch (dialect) {
		case DBType.ORACLE:
		case DBType.ORACLE12: {
			String format = args[1];
			// 日期
			format = format.replace("Y%", "yyyy").replace("y%", "yy").replace("m%", "MM").replace("d%", "dd");
			// 时间处理
			format = format.replace("%T", "hh24:mi:ss");
			format = format.replace("%H", "hh24").replace("%h", "hh").replace("%i", "mi").replace("%s", "ss");
			return "to_char(" + args[0] + "," + format + ")";
		}
		default:
			return null;
		}
	}

}
