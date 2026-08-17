/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * @project sqltoy-orm
 * @description 不区分大小写的字符串Set类型扩展
 * @author zhongxuchen
 * @version v1.0,Date:2018-8-1
 */
public class IgnoreCaseSet extends HashSet<String> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public int size() {
		return super.size();
	}

	@Override
	public boolean isEmpty() {
		return super.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		if (o == null) {
			return false;
		}
		return super.contains(o.toString().toLowerCase(java.util.Locale.ROOT));
	}

	@Override
	public Iterator<String> iterator() {
		return super.iterator();
	}

	@Override
	public Object[] toArray() {
		return super.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		// null入参按集合契约抛NPE,不再返回null推迟错误到调用方
		return super.toArray(a);
	}

	@Override
	public boolean add(String e) {
		if (e == null) {
			return false;
		}
		return super.add(e.toLowerCase(java.util.Locale.ROOT));
	}

	@Override
	public boolean remove(Object o) {
		if (o == null) {
			return false;
		}
		return super.remove(o.toString().toLowerCase(java.util.Locale.ROOT));
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		if (c == null || c.isEmpty()) {
			return true;
		}
		List<String> tmp = new ArrayList<String>();
		Iterator iter = c.iterator();
		Object row;
		while (iter.hasNext()) {
			row = iter.next();
			// 本集合不包含null元素,查询集合含null时必然不满足包含关系
			if (row == null) {
				return false;
			}
			tmp.add(row.toString().toLowerCase(java.util.Locale.ROOT));
		}
		return super.containsAll(tmp);
	}

	@Override
	public boolean addAll(Collection<? extends String> c) {
		if (c == null || c.isEmpty()) {
			return false;
		}
		List<String> tmp = new ArrayList<String>();
		Iterator iter = c.iterator();
		Object row;
		while (iter.hasNext()) {
			row = iter.next();
			if (row != null) {
				tmp.add(row.toString().toLowerCase(java.util.Locale.ROOT));
			}
		}
		return super.addAll(tmp);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		if (c == null || c.isEmpty()) {
			return false;
		}
		List<String> tmp = new ArrayList<String>();
		Iterator iter = c.iterator();
		Object row;
		while (iter.hasNext()) {
			row = iter.next();
			if (row != null) {
				tmp.add(row.toString().toLowerCase(java.util.Locale.ROOT));
			}
		}
		return super.retainAll(tmp);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		if (c == null || c.isEmpty()) {
			return false;
		}
		List<String> tmp = new ArrayList<String>();
		Iterator iter = c.iterator();
		Object row;
		while (iter.hasNext()) {
			row = iter.next();
			if (row != null) {
				tmp.add(row.toString().toLowerCase(java.util.Locale.ROOT));
			}
		}
		return super.removeAll(tmp);
	}

	@Override
	public void clear() {
		super.clear();
	}

}
