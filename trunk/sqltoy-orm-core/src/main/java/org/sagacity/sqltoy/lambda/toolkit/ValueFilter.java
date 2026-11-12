package org.sagacity.sqltoy.lambda.toolkit;

import java.util.Objects;

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

	/**
	 * @description 忽略指定哨兵值(同时忽略null):用于XML filters中-1、"全部"等约定,
	 *              如<code>valueFilter(ValueFilter.ignore(-1))</code>
	 * @param ignored 需要忽略的哨兵值
	 * @return 值过滤器
	 */
	static ValueFilter ignore(Object... ignored) {
		return value -> {
			if (value == null) {
				return false;
			}
			if (ignored != null) {
				for (Object item : ignored) {
					if (Objects.equals(value, item)) {
						return false;
					}
				}
			}
			return true;
		};
	}
}
