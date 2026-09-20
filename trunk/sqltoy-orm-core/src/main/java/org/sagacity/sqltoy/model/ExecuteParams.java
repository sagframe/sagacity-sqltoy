package org.sagacity.sqltoy.model;

/**
 * update 2026-9-12 优化步骤3:执行级动态参数封装——将Dialect SPI方法上随需求演变
 * 陆续追加的调优型参数(fetchSize/maxRows/queryTimeout/lockMode/lockWaitTimeout/
 * timeout/autoCommit)统一收纳,方法身份的核心参数(上下文/配置/实体/集合/连接等)
 * 仍显式传递;后续新增的可选执行参数只扩充本类,不再逐方法加参。 各字段默认值即既有调用链的最常见取值,默认构造行为零变化。
 * 字段public直取+链式赋值(sqltoy内部模型同风格)。
 */
public class ExecuteParams {

	/** 结果集批量获取规模,0表示不设置 */
	public int fetchSize = 0;

	/** 最大提取行数,0表示不限制 */
	public int maxRows = 0;

	/**
	 * 查询超时(秒),null表示不额外设置(SqlToyConstants.defaultStatementTimeout的
	 * 全局statementTimeout仍按既有逻辑生效)
	 */
	public Integer queryTimeout;

	/**
	 * 锁模式(findBySql/updateFetch/load/loadAll),null表示不加锁子句
	 * (getLockSql对null直接返回空串的既有语义)
	 */
	public LockMode lockMode;

	/** 锁等待时长(秒),0表示无wait修饰(nowait/skip locked经lockMode表达) */
	public int lockWaitTimeout = 0;

	/**
	 * 存储过程执行超时(秒),null表示不设置(仅executeStore)
	 */
	public Integer timeout;

	/**
	 * 是否自动提交,null表示沿用连接当前事务语义(2017年引入的开发者自控机制, 既有调用链全部传null)
	 */
	public Boolean autoCommit;

	public static ExecuteParams of() {
		return new ExecuteParams();
	}

	public ExecuteParams fetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
		return this;
	}

	public ExecuteParams maxRows(int maxRows) {
		this.maxRows = maxRows;
		return this;
	}

	public ExecuteParams queryTimeout(Integer queryTimeout) {
		this.queryTimeout = queryTimeout;
		return this;
	}

	public ExecuteParams lockMode(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	public ExecuteParams lockWaitTimeout(int lockWaitTimeout) {
		this.lockWaitTimeout = lockWaitTimeout;
		return this;
	}

	public ExecuteParams timeout(Integer timeout) {
		this.timeout = timeout;
		return this;
	}

	public ExecuteParams autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}
}
