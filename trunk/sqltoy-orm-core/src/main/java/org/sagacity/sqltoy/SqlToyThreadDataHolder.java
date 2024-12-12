package org.sagacity.sqltoy;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * @project sagacity-sqltoy
 * @description sqltoy全局的线程值持有者(整合I18nThreadHolder和UnifyUpdateFieldsController)
 * @author zhongxuchen
 * @version v1.0, Date:2024-12-06
 */
public class SqlToyThreadDataHolder {
	/**
	 * 当前语言，主要用于字典国际化处理
	 */
	private static ThreadLocal<String> i18nThreadLocal = new TransmittableThreadLocal<String>();

	// 是否启用统一字段处理中修改行为(一些业务数据不需要强制对修改人、修改时间做强制覆盖)
	private static ThreadLocal<Boolean> unifyUpdateFields = new TransmittableThreadLocal<Boolean>();

	// 放入当前用户语言方言
	public static void setLanguage(String locale) {
		if (locale != null) {
			i18nThreadLocal.set(locale);
		}
	}

	public static String getLanguage() {
		return i18nThreadLocal.get();
	}

	/**
	 * 取消统一更新字段处理
	 */
	public static void stopUnifyUpdate() {
		unifyUpdateFields.set(true);
	}

	/**
	 * @TODO 判断是否关闭了统一更新字段
	 * @return
	 */
	public static boolean useUnifyFields() {
		Boolean cancalUnify = unifyUpdateFields.get();
		if (cancalUnify != null && cancalUnify) {
			return false;
		}
		return true;
	}

	// 清除语言
	public static void clearLanguage() {
		i18nThreadLocal.remove();
		i18nThreadLocal.set(null);
	}

	// 恢复统一更新字段处理
	public static void resumeUnifyUpdate() {
		unifyUpdateFields.remove();
		unifyUpdateFields.set(null);
	}

	public static void clearAll() {
		clearLanguage();
		resumeUnifyUpdate();
	}
}
