/**
 * 
 */
package org.sagacity.sqltoy.executor;

import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ParallelCallbackHandler;
import org.sagacity.sqltoy.model.ShardingGroupModel;
import org.sagacity.sqltoy.model.ShardingResult;

/**
 * @project sagacity-sqltoy4.0
 * @description 数据库方言并行执行器
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DialectExecutor.java,Revision:v1.0,Date:2017年11月3日
 */
public class DialectExecutor implements Callable<ShardingResult> {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LogManager.getLogger(getClass());

	/**
	 * sqltoy上下文
	 */
	private SqlToyContext sqltoyContext = null;

	/**
	 * sharding分组模型
	 */
	private ShardingGroupModel shardingGroupModel;

	/**
	 * 并行反调处理器
	 */
	private ParallelCallbackHandler handler;

	public DialectExecutor(SqlToyContext sqltoyContext, ShardingGroupModel shardingGroupModel,
			ParallelCallbackHandler handler) {
		this.sqltoyContext = sqltoyContext;
		this.shardingGroupModel = shardingGroupModel;
		this.handler = handler;
	}

	/**
	 * 任务的具体过程，一旦任务传给ExecutorService的submit方法，则该方法自动在一个线程上执行。
	 * 
	 * @return
	 */
	public ShardingResult call() {
		String dataSourceName = shardingGroupModel.getShardingModel().getDataSourceName();
		String tableName = shardingGroupModel.getShardingModel().getTableName();
		ShardingResult result = new ShardingResult();
		// 异常捕获掉,确保其他节点可以正常执行
		try {
			result.setRows(handler.execute(sqltoyContext, shardingGroupModel));
		} catch (Exception e) {
			e.printStackTrace();
			result.setSuccess(false);
			result.setMessage(
					"分库分表执行,DataSource节点:" + dataSourceName + ",table=" + tableName + " 发生异常:" + e.getMessage());
			logger.error("分库分表执行,DataSource节点:{},table={} 发生异常:{}", dataSourceName, tableName, e.getMessage());
		}
		return result;
	}
}
