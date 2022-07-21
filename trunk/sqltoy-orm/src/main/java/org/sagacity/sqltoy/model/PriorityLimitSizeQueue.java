package org.sagacity.sqltoy.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * 固定长度优先队列模型
 * @author zhongxuchen
 *
 * @param <T>
 */
public class PriorityLimitSizeQueue<T> implements Queue<T> {

	private Queue<T> queue;

	/**
	 * 默认最大长度
	 */
	private int limit = 1000;

	public PriorityLimitSizeQueue(int limit) {
		this.limit = limit;
		this.queue = new PriorityQueue<T>();
	}

	public PriorityLimitSizeQueue(int limit, Comparator<T> comparator) {
		this.limit = limit;
		this.queue = new PriorityQueue<T>(comparator);
	}

	@Override
	public boolean offer(T e) {
		// 如果超出长度,入队时,先出队
		if (queue.size() >= limit) {
			queue.poll();
		}
		return queue.offer(e);
	}

	@Override
	public int size() {
		return queue.size();
	}

	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return this.queue.contains(o);
	}

	@Override
	public Iterator<T> iterator() {
		return queue.iterator();
	}

	@Override
	public Object[] toArray() {
		return queue.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return queue.toArray(a);
	}

	@Override
	public boolean remove(Object o) {
		return queue.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return queue.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		if (c == null || c.isEmpty()) {
			return true;
		}
		Iterator<?> iter = c.iterator();
		while (iter.hasNext()) {
			if (queue.size() >= limit) {
				// 如果超出长度,入队时,先出队
				queue.poll();
			}
			queue.offer((T) iter.next());
		}
		return true;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return queue.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return queue.retainAll(c);
	}

	@Override
	public void clear() {
		queue.clear();
	}

	@Override
	public boolean add(T e) {
		// 如果超出长度,入队时,先出队
		if (queue.size() >= limit) {
			queue.poll();
		}
		return queue.offer(e);
	}

	@Override
	public T remove() {
		return queue.remove();
	}

	@Override
	public T poll() {
		return queue.poll();
	}

	@Override
	public T element() {
		return queue.element();
	}

	@Override
	public T peek() {
		return queue.peek();
	}

}
