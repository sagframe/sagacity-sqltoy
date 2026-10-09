package org.sagacity.sqltoy.plugins.function;

import java.util.regex.Pattern;

/**
 * @project sagacity-sqltoy
 * @description 定义不同数据函数转换接口，为加载sql文件时将不同数据库的函数转换成目标数据库的函数 写法
 * @author zhongxuchen
 * @version v1.0,Date:2013-01-02
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
	 * 函数适配的数据库方言,用逗号分隔,返回空或null表示适配所有数据库
	 * 
	 * @return
	 */
	public abstract String dialects();

	/**
	 * 函数匹配表达式
	 * 
	 * @return
	 */
	public abstract Pattern regex();

	/**
	 * 函数转换,通过不同方言重新组织当前的函数
	 *
	 * @param dialect      数据库方言
	 * @param functionName 函数名称
	 * @param hasArgs      函数中是否含参数
	 * @param args         函数中的参数
	 * @return
	 */
	public abstract String wrap(int dialect, String functionName, boolean hasArgs, String... args);

	/**
	 * update 2026-10-4 尾部子句钩子:函数调用闭括号之后紧邻的附属子句(如聚合的
	 * within group (order by …)),返回null表示本函数不消费尾部子句(默认)。
	 * pattern须以\(*结尾且以\s*开头承担间隔空白;框架在字面量掩码串上探测防误伤,
	 * 命中后按平衡括号消费整个子句并交由 {@link #wrapWithSuffix} 处理
	 *
	 * @return 尾部子句的匹配pattern, null表示不处理
	 */
	public Pattern suffixPattern() {
		return null;
	}

	/**
	 * update 2026-10-4 尾部子句钩子命中时的处理入口,仅当 {@link #suffixPattern()}
	 * 非null且实际命中时被调用;suffix为原始文本(自子句起始至平衡闭括号,含leading空白)。
	 * 返回null表示连同子句原样保留(同名原生透传的安全兜底)
	 *
	 * @param dialect      数据库方言
	 * @param functionName 函数名称
	 * @param suffix       尾部子句原文
	 * @param args         函数中的参数
	 * @return
	 */
	public String wrapWithSuffix(int dialect, String functionName, String suffix, String... args) {
		return IGNORE;
	}

	/**
	 * 提供默认的函数加工拼接方式实现
	 * 
	 * @param functionName 函数名称
	 * @param args         函数中的参数如ifnull(name)这里就是name
	 * @return
	 */
	protected String wrapArgs(String functionName, String... args) {
		StringBuilder result = new StringBuilder(functionName);
		result.append("(");
		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; i++) {
				if (i > 0) {
					result.append(",");
				}
				result.append(args[i]);
			}
		}
		return result.append(")").toString();
	}
}
