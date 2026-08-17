package org.sagacity.sqltoy.translate.cache;

import java.util.HashMap;

import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * @project sagacity-sqltoy
 * @description translate 翻译缓存管理接口定义，为基于其他缓存框架的实现提供接口规范
 * @author zhongxuchen
 * @version v1.0,Date:2013-4-14
 */
public abstract class TranslateCacheManager {
	protected IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap = new IgnoreKeyCaseMap<String, TranslateConfigModel>();

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
	 * @todo 判断是否存在相关缓存
	 * @param cacheName
	 * @return
	 */
	public abstract boolean hasCache(String cacheName);

	/**
	 * @todo 从缓存中获取翻译的hashMap 集合数据
	 * @param cacheName
	 * @param cacheType (默认为null，针对诸如数据字典类型的，对应字典类型)
	 * @return
	 */
	public abstract HashMap<String, Object[]> getCache(String cacheName, String cacheType);

	/**
	 * @todo 将数据放入缓存
	 * @param cacheModel
	 * @param cacheName
	 * @param cacheType  (默认为null，针对诸如数据字典类型的，对应字典类型)
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

	/**
	 * @TODO 缓存存储是否为按值(by-value)语义：offHeap/diskSize是缓存配置的通用存储声明，
	 *       配置了堆外/磁盘层的缓存，其堆外/磁盘保存的是序列化副本，原地修改heap层返回的map引用
	 *       不会同步副本(堆内条目被驱逐后从副本读回会丢增量)，此类缓存的增量更新须整体复制后
	 *       经put同步多层存储；未配置(纯heap)为by-reference，原地put直接生效且零拷贝开销。
	 *       <p>
	 *       覆盖指引：尊重offHeap/diskSize配置建堆外/磁盘存储的实现(如ehcache)继承默认判定即正确；
	 *       不支持/忽略这两个配置、永远只有heap层的实现(如caffeine)应覆盖返回false
	 * @param cacheConfig 缓存配置
	 * @return true表示按值存储，增量更新应走复制-整体替换
	 */
	public boolean isStoreByValue(TranslateConfigModel cacheConfig) {
		return cacheConfig != null && (cacheConfig.getOffHeap() > 0 || cacheConfig.getDiskSize() > 0);
	}

	public void setTranslateMap(IgnoreKeyCaseMap<String, TranslateConfigModel> translateMap) {
		this.translateMap = translateMap;
	}
}
