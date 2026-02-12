package org.sagacity.sqltoy.translate.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @project sagacity-sqltoy
 * @description 解决缓存翻译，动态查询记录每条都执行查询的不足，具体原理
 *              <p>
 *              <li>1、针对存在动态捕获缓存的场景，且没有link和一个字段多次翻译的场景</li>
 *              <li>2、同时构建一个Map<cacheName,Set<String>>放入不存在key</li>
 *              <li>3、再取完完整结果后，一次性调用获取缓存，然后对集合进行批量翻译，并将新获取的数据放入缓存</li>
 *              </p>
 * @author zhongxuchen
 * @version v1.0,Date:2026年1月23日
 */
public class DynamicCacheHolder {
	/**
	 * 是否挂起
	 */
	private Set<String> pauseTranslateCaches = new HashSet<>();

	// private Map<String, Map<String, Object[]>> cacheMatchedDatas = new
	// HashMap<>();
	private Map<String, Set<String>> cacheNotMatchedKeys = new HashMap<>();

	private Map<String, String> cacheAndTypeForRealType = new HashMap<>();

	private Map<String, String> cacheAndTypeForRealMap = new HashMap<>();

	public DynamicCacheHolder() {
	}

	public DynamicCacheHolder(Map<String, String> cacheAndTypeForRealMap, Map<String, String> cacheAndTypeForRealType,
			String... cacheNames) {
		if (cacheAndTypeForRealMap != null && !cacheAndTypeForRealMap.isEmpty()) {
			this.cacheAndTypeForRealMap.putAll(cacheAndTypeForRealMap);
		}

		if (cacheAndTypeForRealType != null && !cacheAndTypeForRealType.isEmpty()) {
			this.cacheAndTypeForRealType.putAll(cacheAndTypeForRealType);
		}
		if (cacheNames != null && cacheNames.length > 0) {
			for (String cacheName : cacheNames) {
				pauseTranslateCaches.add(cacheName);
				// cacheMatchedDatas.put(cacheName, new HashMap<>());
				cacheNotMatchedKeys.put(cacheName, new HashSet<String>());
			}
		}
	}

	public String getRealCacheNameAndType(String cacheNameAndType) {
		return cacheAndTypeForRealMap.get(cacheNameAndType);
	}

	public String getRealCacheType(String cacheNameAndType) {
		return cacheAndTypeForRealType.get(cacheNameAndType);
	}

	/**
	 * 判断是否停止翻译
	 * 
	 * @param cacheNameAndType
	 * @return
	 */
	public boolean isPauseTranslate(String cacheNameAndType) {
		if (cacheNameAndType == null) {
			return false;
		}
		return pauseTranslateCaches.contains(cacheNameAndType);
	}

	public void addNotMatchedKey(String cacheNameAndType, String key) {
		cacheNotMatchedKeys.get(cacheNameAndType).add(key);
	}

	public Map<String, Set<String>> getCacheNotMatchedKeys() {
		return cacheNotMatchedKeys;
	}

	public String[] getNotMatchedKeys(String cacheNameAndType) {
		Set<String> notMatchedKeys = cacheNotMatchedKeys.get(cacheNameAndType);
		if (notMatchedKeys == null || notMatchedKeys.isEmpty()) {
			return null;
		}
		return notMatchedKeys.toArray(new String[0]);
	}
}
