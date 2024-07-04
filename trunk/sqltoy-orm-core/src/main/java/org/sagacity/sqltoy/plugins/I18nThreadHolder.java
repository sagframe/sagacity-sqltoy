/**
 * 
 */
package org.sagacity.sqltoy.plugins;

/**
 * @project sagacity-sqltoy
 * @description 定义i18n国际化当前操作用户的方言线程保持容器
 * @author zhongxuchen
 * @version v1.0, Date:2022年10月5日
 * @modify 2022年10月5日,修改说明
 */
public class I18nThreadHolder {
	private static ThreadLocal<String> threadLocal = new ThreadLocal<String>();

	// 放入当前用户语言方言
	public static void put(String locale) {
		if (locale != null) {
			threadLocal.set(locale);
		}
	}

	// 请求结束要做清除
	public static void remove() {
		threadLocal.remove();
	}

	public static String getLocale() {
		return threadLocal.get();
	}
}
