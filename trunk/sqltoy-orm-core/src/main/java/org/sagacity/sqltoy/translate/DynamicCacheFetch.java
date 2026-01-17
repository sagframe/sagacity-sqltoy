/**
 * 
 */
package org.sagacity.sqltoy.translate;

import java.util.Map;

import org.sagacity.sqltoy.integration.AppContext;

/**
 * @project sagacity-sqltoy
 * @description 针对localCache提供动态从redis或数据库获取值并放入缓存的机制
 *              <li>1、配置
 *              spring.sqltoy.dynamicCacheFetch=xxxx.xxx.DynamicCacheFetchImpl</li>
 *              <li>2、在sqltoy-translates.xml 缓存定义配置中增加:sid、properties
 *              可选，供开发者提供特定标记用
 *              <local-translate cache="缓存名称" sid="特定标记如sqlId" properties="属性信息"
 *              keep-alive="3600" dynamic-cache="true" dynamic-cache-maxSize=
 *              "100000"/></li>
 * @author zhongxuchen
 * @version v1.0, Date:2024年1月18日
 * @modify 2024年1月18日,修改说明
 */
public interface DynamicCacheFetch {
	/**
	 * 初始化方法,供提供类似redis、redisTemplate定义等
	 * 
	 * @param appContext
	 */
	public void initialize(AppContext appContext);

	// 配置说明
	// 第一步:在 sqltoy-translate.xml中定义一个local-translate
	// sid、properties 给接口扩展提供辅助，会传给取数据的接口
	// 启用动态获取缓存数据:dynamic-cache="true"
	// 参数:dynamic-cache-maxSize(默认10万)、dynamic-cache-initSize(默认1万)、dynamic-cache-loadFactor(默认0.75)
	// 正常只需要定义:dynamic-cache、dynamic-cache-maxSize
	// keep-alive:表示缓存自动销毁时间，单位秒，设置为负数，表示长久生效
	// <local-translate cache="" sid="" properties="" keep-alive="3600"
	// dynamic-cache="true" dynamic-cache-maxSize="100000"/>
	// 第二步: 定义DynamicCacheFetch的实现类，如果用到spring获取bean，initialize(AppContext
	// appContext)中传递了appContext.getBean(xxx)
	// 第三步: 在项目配置文件中，定义spring.sqltoy.dynamicCacheFetch=xxxx.DynamicCacheFetchImpl
	// 实现类(也可以直接是一个bean的名称）

	// 关联知识
	// 1、参见：org.sagacity.sqltoy.translate.cache.impl.FIFODynamicFetchCacheManager
	// 2、参见:org.sagacity.sqltoy.translate.TranslateManager的
	// public HashMap<String, FieldTranslateCacheHolder>
	// getTranslates(HashMap<String, FieldTranslate> translates) 194行
	// 3、参见：SqlToyContext.initialize()方法中默认实列化了467行:dynamicFecthCacheManager = new
	// FIFODynamicFetchCacheManager();
	// 4、参见：org.sagacity.sqltoy.utils.TranslateUtils

	/**
	 * @TODO 取单个值的缓存数据
	 * @param cacheName
	 * @param cacheType  类似数据字典的分组(使用缓存的地方传递<translate cache="skuDict" cache-type=
	 *                   "clothes">) 根据情况使用(非分组场景则为null)
	 * @param sid        缓存定义中的sid便于给接口实现提供辅助标记(保留标记,一般为null可忽略)
	 * @param properties 缓存定义中的属性信息,如取哪几列值(保留属性，一般为null可忽略)
	 * @param key
	 * @return
	 */
	public Object[] getCache(String cacheName, String cacheType, String sid, String[] properties, String key);

	/**
	 * @TODO 获取多个key的数据(预留功能，目前还未用到)
	 * @param cacheName
	 * @param cacheType  类似数据字典的分组(根据情况使用(非分组场景则为null))
	 * @param sid        缓存定义中的sid便于给接口实现提供辅助标记(保留标记,一般为null可忽略)
	 * @param properties 缓存定义中的属性信息,如取哪几列值(保留属性，一般为null可忽略)
	 * @param keys
	 * @return Map<key,Object[]> 返回Map避免key本身存在重复，解决key和数据的映射问题
	 */
	public Map<String, Object[]> getCache(String cacheName, String cacheType, String sid, String[] properties,
			String[] keys);
}