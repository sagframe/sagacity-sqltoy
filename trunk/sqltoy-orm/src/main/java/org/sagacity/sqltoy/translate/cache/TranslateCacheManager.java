package org.sagacity.sqltoy.translate.cache;

import java.util.HashMap;

import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * @project sagacity-sqltoy
 * @description translate 翻译缓存管理接口定义，为基于其他缓存框架的实现提供接口规范
 * @author zhongxuchen
 * @version v1.0,Date:2013-4-14
 */
public abstract class TranslateCacheManager {
	protected HashMap<String, TranslateConfigModel> translateMap = new HashMap<String, TranslateConfigModel>();

	/**
	 * 缓存管理器名称
	 */
	private String name;

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @todo 从缓存中获取翻译的hashMap 集合数据
	 * @param cacheName
	 * @param cacheType (默认为null，针对诸如数据字典类型的，对应字典类型)
	 * @return
	 */
	public abstract HashMap<String, Object[]> getCache(String cacheName, String cacheType);

	/**
	 * @todo 将数据放入缓存
	 * @param cacheConfig
	 * @param cacheName
	 * @param cacheType   (默认为null，针对诸如数据字典类型的，对应字典类型)
	 * @param cacheValue
	 */
	public abstract void put(TranslateConfigModel cacheModel, String cacheName, String cacheType,
			HashMap<String, Object[]> cacheValue);

	/**
	 * @todo 清空缓存
	 * @param cacheName
	 * @param cacheType (默认为null，针对诸如数据字典类型的，对应字典类型)
	 */
	public abstract void clear(String cacheName, String cacheType);

	/**
	 * 初始化(便于扩展实例启动一些处理逻辑)
	 */
	public abstract boolean init();

	/**
	 * 销毁
	 */
	public abstract void destroy();

	public void setTranslateMap(HashMap<String, TranslateConfigModel> translateMap) {
		this.translateMap = translateMap;
	}

}
