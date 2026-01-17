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
	private int maxCapacity;
	// 最小初始化为1条
	private static int defaultInitCapacity = 1;
	// 最小是2条
	private static int defaultMax = 2;

	private static float defaultLoadFactor = 0.75f;

	public FIFOMap(int maxSize) {
		super((maxSize > 100) ? 16 : defaultInitCapacity, defaultLoadFactor, false);
		// 避免<2
		this.maxCapacity = Math.max(maxSize, defaultMax);
	}

	public FIFOMap(int initialCapacity, int maxSize) {
		super(Math.min(Math.max(initialCapacity, defaultInitCapacity), Math.max(maxSize, defaultMax)),
				defaultLoadFactor, false);
		// 避免<2
		this.maxCapacity = Math.max(maxSize, defaultMax);
	}

	/**
	 * @param initialCapacity
	 * @param maxSize
	 * @param accessOrder     是否频繁使用的放后面
	 */
	public FIFOMap(int initialCapacity, int maxSize, boolean accessOrder) {
		super(Math.min(Math.max(initialCapacity, defaultInitCapacity), Math.max(maxSize, defaultMax)),
				defaultLoadFactor, accessOrder);
		this.maxCapacity = Math.max(maxSize, defaultMax);
	}

	/**
	 * @param maxSize
	 * @param accessOrder 是否频繁使用的放后面
	 */
	public FIFOMap(int maxSize, boolean accessOrder) {
		super((maxSize > 100) ? 16 : defaultInitCapacity, defaultLoadFactor, accessOrder);
		this.maxCapacity = Math.max(maxSize, defaultMax);
	}

	/**
	 * @param initialCapacity 初始数量
	 * @param maxSize         最大数量
	 * @param loadFactor      加载因子，用于map扩容控制，比如0.8，即容量 100 的数组能装 80 个元素才扩容
	 * @param accessOrder     是否频繁使用的放后面
	 */
	public FIFOMap(int initialCapacity, int maxSize, float loadFactor, boolean accessOrder) {
		super(Math.min(Math.max(initialCapacity, defaultInitCapacity), Math.max(maxSize, defaultMax)),
				(loadFactor <= 0 || loadFactor > 1) ? defaultLoadFactor : loadFactor, accessOrder);
		this.maxCapacity = Math.max(maxSize, defaultMax);
	}

	@Override
	protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
		return size() > maxCapacity;
	}

	// 新增：暴露最大容量，方便使用者获取
	public int getMaxCapacity() {
		return maxCapacity;
	}
}