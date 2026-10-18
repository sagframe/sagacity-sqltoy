package org.sagacity.sqltoy.lambda;

import java.io.Serializable;
import java.util.function.Function;

/**
 * @description 支持序列化的Function,方法引用形式使用(如<code>User::getStaffName</code>),
 *              是lambda条件体系中提取实体属性名的基础
 * @param <T> 实体类型
 * @param <R> 属性类型
 * @author zhongxuchen
 */
@FunctionalInterface
public interface LambdaFn<T, R> extends Function<T, R>, Serializable {
}
