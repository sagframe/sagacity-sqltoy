/**
 * 
 */
package org.sagacity.sqltoy.plugins.function;

import java.util.regex.Pattern;

/**
 * @project sqltoy-orm
 * @description 定义不同数据函数转换接口，为加载sql文件时将不同数据库的函数转换成目标数据库的函数 写法
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:IFunction.java,Revision:v1.0,Date:2013-1-2
 */
public abstract class IFunction {

	/**
	 * 表示支持全部数据库类型
	 */
	public final String ALL = "";

	/**
	 * 返回null,表示忽视函数转换处理，原样输出
	 */
	public final String IGNORE = null;

	/**
	 * @todo 函数适配的数据库方言,用逗号分隔,返回空或null表示适配所有数据库
	 * @return
	 */
	public abstract String dialects();

	/**
	 * @todo 函数匹配表达式
	 * @return
	 */
	public abstract Pattern regex();

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
