package org.sagacity.sqltoy.model;

/**
 * @project sqltoy-orm
 * @description 定义数据库记录锁的类型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:LockMode.java,Revision:v1.0,Date:2015年3月5日
 */
public enum LockMode {
	UPGRADE(1),

	/**
	 * Attempt to obtain an upgrade lock, using an Oracle-style
	 * <tt>select for update nowait</tt>. The semantics of this lock mode, once
	 * obtained, are the same as <tt>UPGRADE</tt>.
	 */
	UPGRADE_NOWAIT(2);

	private final int level;

	private LockMode(int level) {
		this.level = level;
	}

	public int value() {
		return this.level;
	}

	public String toString() {
		return Integer.toString(level);
	}
}
