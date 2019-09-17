/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @project sagacity-sqltoy4.0
 * @description 重构HashMap让key值始终是小写,存储和提取时key都是小写
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:IgnoreCaseLinkedMap.java,Revision:v1.0,Date:2017年11月7日
 */
@SuppressWarnings("unchecked")
public class IgnoreCaseLinkedMap<K, V> extends LinkedHashMap<K, V> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 391326207592902507L;

	/**
	 * @TODO key转小写
	 * @param key
	 * @return
	 */
	private Object toLowCaseKey(Object key) {
		return (key != null && key instanceof String) ? key.toString().toLowerCase() : key;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.LinkedHashMap#get(java.lang.Object)
	 */
	@Override
	public V get(Object key) {
		return super.get(toLowCaseKey(key));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashMap#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V put(K key, V value) {
		return super.put((K) toLowCaseKey(key), value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashMap#putAll(java.util.Map)
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> map) {
		if (map == null || map.isEmpty())
			return;
		Iterator<?> iter = map.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<K, V> entry = (Map.Entry<K, V>) iter.next();
			super.put((K) toLowCaseKey(entry.getKey()), entry.getValue());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashMap#remove(java.lang.Object)
	 */
	@Override
	public V remove(Object key) {
		return super.remove(toLowCaseKey(key));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashMap#putIfAbsent(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V putIfAbsent(K key, V value) {
		return super.putIfAbsent((K) toLowCaseKey(key), value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashMap#replace(java.lang.Object, java.lang.Object,
	 * java.lang.Object)
	 */
	@Override
	public boolean replace(K key, V value1, V value2) {
		return super.replace((K) toLowCaseKey(key), value1, value2);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashMap#replace(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V replace(K key, V value) {
		return super.replace((K) toLowCaseKey(key), value);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashMap#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(Object key) {
		return super.containsKey(toLowCaseKey(key));
	}
}
