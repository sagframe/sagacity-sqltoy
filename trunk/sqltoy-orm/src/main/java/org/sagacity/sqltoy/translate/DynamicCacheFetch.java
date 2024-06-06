/**
 * 
 */
package org.sagacity.sqltoy.translate;

import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description 针对localCache提供动态从redis或数据库获取值并放入缓存的机制(目前还未实现)
 * @author zhongxuchen
 * @version v1.0, Date:2024年1月18日
 * @modify 2024年1月18日,修改说明
 */
public interface DynamicCacheFetch {
	// sid、properties
	// 两个属性是在sqltoy-translate.xml中的定义的，给接口扩展提供辅助，只有在local-translate且dynamic-cache="true"场景生效
	// <local-translate cache="" sid="" properties="" dynamic-cache="true"/>
	/**
	 * @TODO 取单个值的缓存数据
	 * @param cacheName
	 * @param sid        缓存定义中的sid便于给接口实现提供辅助标记(保留标记,一般为null可忽略)
	 * @param properties 缓存定义中的属性信息,如取哪几列值(保留属性，一般为null可忽略)
	 * @param key
	 * @return
	 */
	public Object[] getCache(String cacheName, String sid, String[] properties, String key);

	/**
	 * @TODO 获取多个key的数据
	 * @param cacheName
	 * @param sid        缓存定义中的sid便于给接口实现提供辅助标记(保留标记,一般为null可忽略)
	 * @param properties 缓存定义中的属性信息,如取哪几列值(保留属性，一般为null可忽略)
	 * @param keys
	 * @return
	 */
	public List<Object[]> getCache(String cacheName, String sid, String[] properties, String[] keys);
}
