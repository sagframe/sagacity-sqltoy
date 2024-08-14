package org.sagacity.sqltoy.utils;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.sagacity.sqltoy.callback.TransactionHandler;
import org.sagacity.sqltoy.exception.DataAccessException;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 提供一个简易的事务控制器，供非spring等框架下使用或测试
 * 
 * @author zhongxuchen
 *
 */
public class DBTransUtils {
	// 通过ThreadLocal 来保存线程数据
	private static ThreadLocal<Connection> threadLocal = new TransmittableThreadLocal<Connection>();

	/**
	 * @TODO 执行事务
	 * @param dataSource
	 * @param transactionHandler
	 * @return
	 * @throws DataAccessException
	 */
	public static Object doTrans(DataSource dataSource, TransactionHandler transactionHandler)
			throws DataAccessException {
		Connection conn = null;
		try {
			// 获取连接，设置自动提交为false
			conn = dataSource.getConnection();
			conn.setAutoCommit(false);
			// 将连接放入当前线程，供后续业务通过现场获取连接，参见SimpleConnectFactory里面逻辑
			threadLocal.set(conn);
			// 通过反调具体执行开发者的业务逻辑，里面是一个代码块，可以是多次lightDao的操作行为
			Object result = transactionHandler.doTrans();
			// 提交
			conn.commit();
			return result;
		} catch (Exception e) {
			// 发生异常，事务回滚
			try {
				if (conn != null) {
					conn.rollback();
				}
			} catch (SQLException se) {

			}
			throw new DataAccessException(e);
		} finally {
			// 关闭连接
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (SQLException se) {

			}
			// 清除掉当前线程中的连接，这个非常关键
			threadLocal.remove();
		}
	}

	/**
	 * 获取当前线程下的连接
	 * 
	 * @return
	 */
	public static Connection getCurrentConnection() {
		return threadLocal.get();
	}
}
