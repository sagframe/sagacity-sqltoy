package org.sagacity.sqltoy.dialect.executor;

import java.util.concurrent.Callable;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ParallelCallbackHandler;
import org.sagacity.sqltoy.config.model.ShardingGroupModel;
import org.sagacity.sqltoy.model.ShardingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 数据库方言并行执行器(ParallelUtils将数据按策略分组后并行执行,DialectExecutor作为一个并行单元)
 * @author zhongxuchen
 * @version v1.0,Date:2017-11-03
 */
public class DialectExecutor implements Callable<ShardingResult> {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(DialectExecutor.class);

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
	@Override
	public ShardingResult call() {
		String dataSourceName = shardingGroupModel.getShardingModel().getDataSourceName();
		String tableName = shardingGroupModel.getShardingModel().getTableName();
		ShardingResult result = new ShardingResult();
		// 异常捕获掉,确保其他线程可以正常执行
		try {
			logger.debug("execute sharding, dataSource node:{}, table={}", dataSourceName, tableName);
			result.setRows(handler.execute(sqltoyContext, shardingGroupModel));
		} catch (Exception e) {
			SqlExecuteStat.error(e);
			result.setSuccess(false);
			result.setMessage("execute sharding, DataSource node:" + dataSourceName + ",table=" + tableName
					+ " occurred exception:" + e.getMessage());
			logger.error("execute sharding, dataSource node:{}, table={} error occurred:{}", dataSourceName, tableName,
					e.getMessage());
		} finally {
			SqlExecuteStat.destroy();
		}
		return result;
	}
}
