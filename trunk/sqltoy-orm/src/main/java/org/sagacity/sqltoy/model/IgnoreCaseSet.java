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
 * @author zhongxuchen
 *
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
		return super.contains(o.toString().toLowerCase());
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
		if (a == null) {
			return null;
		}
		return super.toArray(a);
	}

	@Override
	public boolean add(String e) {
		if (e == null) {
			return false;
		}
		return super.add(e.toLowerCase());
	}

	@Override
	public boolean remove(Object o) {
		if (o == null) {
			return true;
		}
		return super.remove(o.toString().toLowerCase());
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		if (c == null || c.isEmpty()) {
			return false;
		}
		List<String> tmp = new ArrayList<String>();
		Iterator iter = c.iterator();
		Object row;
		while (iter.hasNext()) {
			row = iter.next();
			if (row != null) {
				tmp.add(row.toString().toLowerCase());
			}
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
				tmp.add(row.toString().toLowerCase());
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
				tmp.add(row.toString().toLowerCase());
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
				tmp.add(row.toString().toLowerCase());
			}
		}
		return super.removeAll(tmp);
	}

	@Override
	public void clear() {
		super.clear();
	}

}
