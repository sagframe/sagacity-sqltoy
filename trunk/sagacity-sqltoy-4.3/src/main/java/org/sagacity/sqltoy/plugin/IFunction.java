/**
 * 
 */
package org.sagacity.sqltoy.plugin;

/**
 * @project sqltoy-orm
 * @description 定义不同数据函数转换接口，为加载sql文件时将不同数据库的函数转换成目标数据库的函数 写法
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:IFunction.java,Revision:v1.0,Date:2013-1-2
 */
public abstract class IFunction {
	public abstract String dialects();

	public abstract String regex();

	/**
	 * @TODO 函数转换
	 * @param dialect
	 * @param functionName
	 * @param hasArgs
	 * @param args
	 * @return
	 */
	public abstract String wrap(int dialect, String functionName, boolean hasArgs, String... args);

	/**
	 * @todo 提供默认的函数加工拼接方式实现
	 * @param functionName
	 * @param args
	 * @return
	 */
	protected String wrapArgs(String functionName, String... args) {
		StringBuilder result = new StringBuilder(functionName);
		result.append("(");
		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; i++) {
				if (i > 0)
					result.append(",");
				result.append(args[i]);
			}
		}
		return result.append(")").toString();
	}
}
