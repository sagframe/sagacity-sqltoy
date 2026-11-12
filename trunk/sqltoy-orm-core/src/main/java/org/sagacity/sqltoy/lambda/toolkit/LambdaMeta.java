package org.sagacity.sqltoy.lambda.toolkit;

/**
 * @description lambda表达式解析出的元信息
 * @author zhongxuchen
 */
public interface LambdaMeta {

	/**
	 * @return lambda表达式实现方法的名称
	 */
	String getImplMethodName();

	/**
	 * @return 声明该实现方法的实体类型
	 */
	Class<?> getInstantiatedClass();
}
