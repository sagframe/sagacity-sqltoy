/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import org.sagacity.sqltoy.SqlToyThreadDataHolder;

/**
 * @project sagacity-sqltoy
 * @description 定义i18n国际化当前操作用户的方言线程保持容器
 * @author zhongxuchen
 * @version v1.0, Date:2022年10月5日
 * @modify 2024年12月6日,统一到SqlToyThreadDataHolder中处理
 */
// see SqlToyThreadDataHolder.setLanguage
@Deprecated
public class I18nThreadHolder {
	// 放入当前用户语言方言
	public static void put(String locale) {
		SqlToyThreadDataHolder.setLanguage(locale);
	}

	// 请求结束要做清除
	public static void remove() {
		SqlToyThreadDataHolder.clearLanguage();
	}

	public static String getLocale() {
		return SqlToyThreadDataHolder.getLanguage();
	}
}
