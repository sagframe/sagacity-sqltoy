package org.sagacity.sqltoy.solon.translate.cache.impl;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.noear.solon.data.cache.CacheService;
import org.noear.solon.data.cache.LocalCacheService;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;

/**
 * 基于Solon CacheServices做的TranslateCache * @author 夜の孤城 * @since 1.5
 */
public class SolonTranslateCacheManager extends TranslateCacheManager {
	static final String prefix = "sqltoy.translate:";
	CacheService cacheService;
	// CacheService是扁平kv存储,无法按cacheName枚举条目,本地登记已put过的cacheName支撑hasCache语义
	private final Set<String> cacheNames = ConcurrentHashMap.newKeySet();

	public SolonTranslateCacheManager(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	/**
	 * sqltoy核心以Class.forName(..).getDeclaredConstructor().newInstance()反射实例化,
	 * 必须提供无参构造,缺省退化为本地缓存
	 */
	public SolonTranslateCacheManager() {
		this(new LocalCacheService());
	}

	private String buildKey(String cacheName, String cacheType) {
		// cacheType空白时条目键以cacheName兜底,与核心TranslateEhcacheManager的键规则一致
		String typeKey = (cacheType == null || cacheType.trim().isEmpty()) ? cacheName : cacheType;
		return prefix + cacheName + ":" + typeKey;
	}

	public void setCacheService(CacheService cacheService) {
		if (cacheService != null) {
			this.cacheService = cacheService;
		}
	}

	@Override
	public boolean hasCache(String cacheName) {
		return cacheNames.contains(cacheName);
	}

	@Override
	public HashMap<String, Object[]> getCache(String cacheName, String cacheType) {
		return (HashMap<String, Object[]>) cacheService.get(buildKey(cacheName, cacheType), HashMap.class);
	}

	@Override
	public void put(TranslateConfigModel cacheModel, String cacheName, String cacheType,
			HashMap<String, Object[]> cacheValue) {
		cacheService.store(buildKey(cacheName, cacheType), cacheValue, cacheModel.getKeepAlive());
		cacheNames.add(cacheName);
	}

	@Override
	public void clear(String cacheName, String cacheType) {
		cacheService.remove(buildKey(cacheName, cacheType));
	}

	@Override
	public boolean isStoreByValue(TranslateConfigModel cacheConfig) {
		// caffeine仅支持heap层(put忽略offHeap/diskSize配置),存储始终为by-reference,
		// 覆盖基类按配置判定的默认实现,避免无效的offheap配置导致误走整体复制路径
		return false;
	}

	@Override
	public boolean init() {
		return true;
	}

	@Override
	public void destroy() {

	}
}
