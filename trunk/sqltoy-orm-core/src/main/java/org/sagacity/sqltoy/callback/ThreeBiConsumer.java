package org.sagacity.sqltoy.callback;

/**
 * @project sagacity-sqltoy
 * @description 三个入参的函数式回调接口，弥补JDK只提供BiConsumer(两个入参)的不足
 * @author zhongxuchen
 * @version v1.0,Date:2025-01-23
 */
@FunctionalInterface
public interface ThreeBiConsumer<T1, T2, T3> {

	/**
	 * 执行回调处理
	 *
	 * @param t  第一个参数
	 * @param t2 第二个参数
	 * @param t3 第三个参数
	 */
	void accept(T1 t, T2 t2, T3 t3);
}
