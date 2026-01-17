package org.sagacity.sqltoy.translate.cache;

import java.util.HashMap;

import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * @project sqltoy-orm
 * @description 动态获取缓存数据的缓存管理器接口定义，以便提供给开发者自行实现，框架提供默认实现
 * @author zhongxuchen
 * @version v1.0,Date:2026年1月16日
 */
public interface DynamicFecthCacheManager {
	/**
	 * 初始化，可以用于构建一个定时检测器，清理缓存
	 */
	public void initialize();

	/**
	 * 注意:cacheModel.getHeep() 内存堆存放数量目前用于最大存放数据量
	 * 
	 * @param cacheModel
	 * @param cacheType
	 * @return
	 */
	public HashMap<String, Object[]> getDynamicCache(TranslateConfigModel cacheModel, String cacheType);

	/**
	 * 销毁
	 */
	public void destroy();

	/**
	 * 清除缓存
	 * 
	 * @param cacheName
	 * @param cacheType
	 */
	public void clear(String cacheName, String cacheType);

	/**
	 * 是否已经创建对应的缓存(即是否被首次使用)，用于缓存数据后台更新检测
	 * 
	 * @param cacheName
	 * @return
	 */
	public boolean hasCache(String cacheName);
}
