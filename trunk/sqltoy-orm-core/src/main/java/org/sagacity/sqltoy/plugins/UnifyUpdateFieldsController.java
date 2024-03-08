/**
 * 
 */
package org.sagacity.sqltoy.plugins;

/**
 * @project sagacity-sqltoy
 * @description 提供干预统一更新字段的功能开启和关闭，针对一些特殊场景，表中的最后修改人、修改时间不希望通过统一处理
 * @author zhongxuchen
 * @version v1.0, Date:2022年9月10日
 * @modify 2022年9月10日,修改说明
 */
public class UnifyUpdateFieldsController {
	// 通过ThreadLocal 来保存进程数据
	private static ThreadLocal<Boolean> threadLocal = new ThreadLocal<Boolean>();

	/**
	 * 取消统一更新字段处理
	 */
	public static void stop() {
		threadLocal.set(true);
	}

	// 恢复统一更新字段处理
	public static void resume() {
		threadLocal.remove();
		threadLocal.set(null);
	}

	/**
	 * @TODO 判断是否关闭了统一更新字段
	 * @return
	 */
	public static boolean useUnifyFields() {
		Boolean cancalUnify = threadLocal.get();
		if (cancalUnify != null && cancalUnify) {
			return false;
		}
		return true;
	}
}
