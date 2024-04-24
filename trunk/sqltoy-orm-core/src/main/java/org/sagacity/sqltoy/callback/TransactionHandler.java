package org.sagacity.sqltoy.callback;

/**
 * 提供一个反调函数，供非spring等框架型项目下执行事务
 * 
 * @author zhongxuchen
 */
@FunctionalInterface
public interface TransactionHandler {
	/**
	 * 执行业务逻辑，事务在反调前后进行管理 () -> { lightDao.xxx(); lightDao.find() }
	 * 
	 * @return
	 */
	public Object doTrans();
}
