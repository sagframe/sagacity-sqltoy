/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.util.LinkedHashMap;

/**
 * @project sagacity-sqltoy
 * @description 先进先出Map
 * @author zhongxuchen
 * @version v1.0, Date:2024年1月19日
 * @modify 2024年1月19日,修改说明
 */
public class FIFOMap<K, V> extends LinkedHashMap<K, V> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3684763841533693522L;
	private int capacity;

	public FIFOMap(int maxSize) {
		this.capacity = maxSize;
	}

	public FIFOMap(int initialCapacity, int maxSize) {
		super((initialCapacity > maxSize) ? maxSize : initialCapacity);
		this.capacity = maxSize;
	}

	/**
	 * @param initialCapacity
	 * @param maxSize
	 * @param accessOrder     是否频繁使用的放后面
	 */
	public FIFOMap(int initialCapacity, int maxSize, boolean accessOrder) {
		super((initialCapacity > maxSize) ? maxSize : initialCapacity, 0.75f, accessOrder);
		this.capacity = maxSize;
	}

	/**
	 * @param maxSize
	 * @param accessOrder 是否频繁使用的放后面
	 */
	public FIFOMap(int maxSize, boolean accessOrder) {
		super((maxSize > 128) ? 128 : maxSize, 0.75f, accessOrder);
		this.capacity = maxSize;
	}

	/**
	 * @param initialCapacity
	 * @param maxSize
	 * @param loadFactor
	 * @param accessOrder     是否频繁使用的放后面
	 */
	public FIFOMap(int initialCapacity, int maxSize, float loadFactor, boolean accessOrder) {
		super((initialCapacity > maxSize) ? maxSize : initialCapacity, loadFactor, accessOrder);
		this.capacity = maxSize;
	}

	@Override
	protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
		return size() > capacity;
	}
}
