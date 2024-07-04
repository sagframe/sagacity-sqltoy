package org.sagacity.sqltoy.model;

/**
 * @project sqltoy-orm
 * @description 定义数据库记录锁的类型
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月5日
 */
public enum LockMode {
	// 锁记录且等待之前的事务完成
	// updateFetch默认使用upgrade
	UPGRADE(1),

	// 有其它事务锁记录则抛出异常
	UPGRADE_NOWAIT(2),

	// 跳过被其它事务锁的记录
	// 如单号1、2、3，其中2被其它事务锁住，则查询结果只包含1、3
	UPGRADE_SKIPLOCK(3);

	private final int level;

	private LockMode(int level) {
		this.level = level;
	}

	public int value() {
		return this.level;
	}

	@Override
	public String toString() {
		return Integer.toString(level);
	}
}
