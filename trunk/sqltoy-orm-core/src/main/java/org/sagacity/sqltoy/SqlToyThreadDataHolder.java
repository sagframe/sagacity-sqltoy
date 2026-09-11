package org.sagacity.sqltoy;

import org.sagacity.sqltoy.model.DBProfile;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * @project sagacity-sqltoy
 * @description sqltoy全局的线程值持有者(整合I18nThreadHolder和UnifyUpdateFieldsController)
 * @author zhongxuchen
 * @version v1.0,Date:2024-12-06
 */
public class SqlToyThreadDataHolder {
	/**
	 * 当前语言，主要用于字典国际化处理
	 */
	private static ThreadLocal<String> i18nThreadLocal = new TransmittableThreadLocal<String>();

	/**
	 * 计数器
	 */
	private static ThreadLocal<Integer> counterThreadLocal = new TransmittableThreadLocal<Integer>();

	// 是否启用统一字段处理中修改行为(一些业务数据不需要强制对修改人、修改时间做强制覆盖)
	private static ThreadLocal<Boolean> unifyUpdateFields = new TransmittableThreadLocal<Boolean>();

	/**
	 * 自由场景(预留)
	 */
	private static ThreadLocal<Integer> freeSceneThreadLocal = new TransmittableThreadLocal<Integer>();

	/**
	 * 当前连接的数据库特征档案(processDataSource探测时设置,含dbType/主版本/dialect),
	 * 供运行期按数据库版本及特征分派的场景读取(如DB2 11.5 GSE与12.1+内置空间引擎分派)
	 */
	private static ThreadLocal<DBProfile> dbProfile = new TransmittableThreadLocal<DBProfile>();

	// 放入当前用户语言方言
	public static void setLanguage(String locale) {
		if (locale != null) {
			i18nThreadLocal.set(locale);
		}
	}

	public static String getLanguage() {
		return i18nThreadLocal.get();
	}

	public static void setFreeScene(Integer scene) {
		freeSceneThreadLocal.set(scene == null ? 0 : scene);
	}

	public static Integer getFreeScene() {
		return freeSceneThreadLocal.get();
	}

	/**
	 * 取消统一更新字段处理
	 */
	public static void stopUnifyUpdate() {
		unifyUpdateFields.set(true);
	}

	// 设置计数器
	public static void setCounter(Integer counter) {
		counterThreadLocal.set(counter == null ? 0 : counter);
	}

	public static Integer incrementCounterAndGet() {
		Integer count = counterThreadLocal.get();
		if (count != null) {
			counterThreadLocal.set(count + 1);
		}
		return count;
	}

	// update 2026-9-9 各clear方法去除remove()后的set(null):set(null)会重新插入null值entry,
	// 使remove清理失效(池化线程残留entry,TransmittableThreadLocal场景还会向子线程传播null项)
	public static void clearCounter() {
		counterThreadLocal.remove();
	}

	/**
	 * 判断是否关闭了统一更新字段
	 * 
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
	}

	// 恢复统一更新字段处理
	public static void resumeUnifyUpdate() {
		unifyUpdateFields.remove();
	}

	public static void clearFreeScene() {
		freeSceneThreadLocal.remove();
	}

	public static Integer getActuallyDBType() {
		DBProfile profile = dbProfile.get();
		return (profile == null) ? null : profile.getDbType();
	}

	public static void clearActuallyDBType() {
		dbProfile.remove();
	}

	public static void setDBProfile(DBProfile profile) {
		dbProfile.set(profile);
	}

	public static DBProfile getDBProfile() {
		return dbProfile.get();
	}

	public static void clearDBProfile() {
		dbProfile.remove();
	}

	public static void clearAll() {
		clearLanguage();
		resumeUnifyUpdate();
		clearFreeScene();
	}
}
