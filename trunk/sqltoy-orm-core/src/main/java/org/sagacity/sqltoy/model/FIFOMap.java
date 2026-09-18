package org.sagacity.sqltoy.model;

import java.util.LinkedHashMap;

/**
 * @project sagacity-sqltoy
 * @description 先进先出Map
 * @author zhongxuchen
 * @version v1.0,Date:2024-01-19
 * @modify Date:2024-01-19,修改说明
 */
public class FIFOMap<K, V> extends LinkedHashMap<K, V> {
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

	// update 2026-9-8 增加线程安全:该Map用于缓存翻译的动态缓存(见FIFODynamicFetchCacheManager),
	// 被结果集翻译热路径多线程并发get/put;accessOrder=true时get也会将节点移到尾部(结构性修改),
	// LinkedHashMap非线程安全,并发读写可致条目丢失、链表损坏甚至死循环。put内部回调的
	// removeEldestEntry/size为synchronized可重入调用,无死锁风险
	@Override
	public synchronized V get(Object key) {
		return super.get(key);
	}

	@Override
	public synchronized V put(K key, V value) {
		return super.put(key, value);
	}

	@Override
	public synchronized V remove(Object key) {
		return super.remove(key);
	}

	@Override
	public synchronized int size() {
		return super.size();
	}

	@Override
	public synchronized void clear() {
		super.clear();
	}

	// 新增：暴露最大容量，方便使用者获取
	public int getMaxCapacity() {
		return maxCapacity;
	}
}
