/**
 * 
 */
package org.sagacity.sqltoy;

import java.util.HashMap;
import java.util.Map;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供额外的线程传递数据的方式，便于应用层和sqltoy核心层进行数据交互
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ThreadContext.java,Revision:v1.0,Date:2015年6月12日
 */
public class ThreadContext {
	private static ThreadLocal<Map<String, Object>> threadLocal = new ThreadLocal<Map<String, Object>>();

	public static void setMap(Map<String, Object> map) {
		threadLocal.set(map);
	}

	public static void put(String key, Object value) {
		Map<String, Object> threadMap = threadLocal.get();
		if (threadMap == null) {
			threadMap = new HashMap<String, Object>();
			threadLocal.set(threadMap);
		}
		threadMap.put(key, value);
	}

	public static Object getValue(String key) {
		Map<String, Object> threadMap = threadLocal.get();
		if (null != threadMap)
			return threadMap.get(key);
		else
			return null;
	}

	public static Map<String, Object> getMap() {
		return threadLocal.get();
	}

	public static void destroy() {
		threadLocal.remove();
	}
}
