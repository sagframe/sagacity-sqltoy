/**
 * 
 */
package org.sagacity.sqltoy.plugins.sharding;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.integration.ConnectionFactory;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 检测sharding涉及到的数据库连接状况,动态调整权重
 * @author zhongxuchen
 * @version v1.0, Date:2019年9月10日
 */
public class IdleConnectionMonitor extends Thread {
	/**
	 * 定义日志
	 */
	private final Logger logger = LoggerFactory.getLogger(IdleConnectionMonitor.class);

	private Object[][] dataSourceWeightConfig;
	/**
	 * spring 上下文容器
	 */
	private AppContext appContext;

	private int[] weights;

	private Integer delaySeconds;

	private Integer intervalSeconds;

	private ConnectionFactory connectionFactory;

	public IdleConnectionMonitor(AppContext appContext, ConnectionFactory connectionFactory,
			Object[][] dataSourceWeightConfig, int[] weights, Integer delaySeconds, Integer intervalSeconds) {
		this.appContext = appContext;
		this.connectionFactory = connectionFactory;
		this.dataSourceWeightConfig = dataSourceWeightConfig;
		this.weights = weights;
		this.delaySeconds = delaySeconds;
		this.intervalSeconds = intervalSeconds;
		// daemon:无外部停止引用的场景下不阻止JVM退出
		setDaemon(true);
		setName("sqltoy-idle-connection-monitor");
	}

	@Override
	public void run() {
		boolean isRun = true;
		// 延时
		try {
			if (delaySeconds >= 1) {
				Thread.sleep(1000 * delaySeconds);
			}
		} catch (InterruptedException e) {
			isRun = false;
		}
		while (isRun) {
			int i = 0;
			for (Object[] dataBase : dataSourceWeightConfig) {
				// 每轮独立声明:上轮变量残留时finally会用本轮dataSource释放上一轮已归还的连接(双重归还事故)
				DataSource dataSource = null;
				Connection conn = null;
				PreparedStatement pst = null;
				ResultSet rs = null;
				try {
					dataSource = (DataSource) appContext.getBean(dataBase[0].toString());
					// 权重大于零且数据源不为null
					if (((Integer) dataBase[1]).intValue() > 0 && null != dataSource) {
						conn = connectionFactory.getConnection(dataSource);
						pst = conn.prepareStatement(DataSourceUtils.getValidateQuery(conn));
						// 设置全局statementTimeout，默认为null
						if (SqlToyConstants.defaultStatementTimeout != null
								&& SqlToyConstants.defaultStatementTimeout > 0) {
							pst.setQueryTimeout(SqlToyConstants.defaultStatementTimeout);
						}
						rs = pst.executeQuery();
						weights[i] = (Integer) dataBase[1];
					} else {
						weights[i] = 0;
					}
				} catch (Exception e) {
					logger.error("数据源:{}可用性检测失败,权重临时置0!", dataBase[0], e);
					weights[i] = 0;
				} finally {
					if (rs != null) {
						try {
							rs.close();
						} catch (SQLException e) {
							logger.error("close ResultSet 方法执行异常", e);
						}
					}
					if (pst != null) {
						try {
							pst.close();
						} catch (SQLException e) {
							logger.error("close PreparedStatement 方法执行异常", e);
						}
					}
					// 只归还本轮实际获取的连接,且用获取它的同一数据源
					if (conn != null && dataSource != null) {
						connectionFactory.releaseConnection(conn, dataSource);
					}
				}
				i++;
			}

			try {
				if (Thread.currentThread().isInterrupted()) {
					isRun = false;
				} else {
					// 设置检测间隔
					Thread.sleep(1000 * intervalSeconds);
				}
			} catch (InterruptedException e) {
				logger.warn("datasource sharding 可用性检测监测将终止!{}", e.getMessage(), e);
				isRun = false;
			}
		}
	}
}
