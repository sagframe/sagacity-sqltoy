package org.sagacity.sqltoy.lambda.toolkit;

/**
 * @description 条件值过滤策略,决定一个值是否参与sql组装(默认策略:值为null时该条件被跳过)。
 *              与旧版全局静态过滤器不同,本策略为wrapper实例级配置,通过
 *              <code>lambdaQuery.valueFilter(...)</code>设置
 * @author zhongxuchen
 */
@FunctionalInterface
public interface ValueFilter {

	/**
	 * @param value 条件值
	 * @return true表示该值参与sql组装
	 */
	boolean test(Object value);

	/**
	 * 默认策略:过滤null值
	 */
	ValueFilter NON_NULL = value -> value != null;
}
