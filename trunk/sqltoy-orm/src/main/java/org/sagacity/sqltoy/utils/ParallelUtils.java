/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ParallelCallbackHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.ShardingConfig;
import org.sagacity.sqltoy.executor.DialectExecutor;
import org.sagacity.sqltoy.model.ShardingGroupModel;
import org.sagacity.sqltoy.model.ShardingResult;
import org.sagacity.sqltoy.plugins.sharding.ShardingUtils;

/**
 * @project sagacity-sqltoy4.0
 * @description sqltoy对象集合相关操作sharding策略分组以及并行提交相关数据库进行执行
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ParallelUtils.java,Revision:v1.0,Date:2017年11月3日
 */
@SuppressWarnings("rawtypes")
public class ParallelUtils {
	/**
	 * @todo 将集合进行根据sharding字段的值提取sharding策略并按照策略将集合分组，然后并行执行
	 * @param sqlToyContext
	 * @param entities
	 * @param wrapIdValue
	 * @param dataSource
	 * @param handler
	 * @return
	 * @throws Exception
	 */
	public static List execute(final SqlToyContext sqlToyContext, List entities, boolean wrapIdValue,
			DataSource dataSource, ParallelCallbackHandler handler) throws Exception {
		// 获取对象的媒体信息
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// 主键值需要提前按照主键策略赋予(sequence 和assign模式的不会实际执行赋值)
		if (wrapIdValue) {
			ShardingUtils.assignPKs(sqlToyContext, entityMeta, entities);
		}
		// 将批量集合数据按sharding策略处理后的库和表组成的key分组
		Collection<ShardingGroupModel> shardingGroups = ShardingUtils.groupShardings(sqlToyContext, entities,
				entityMeta, dataSource);
		// 单分组直接执行
		if (shardingGroups.size() == 1) {
			return handler.execute(sqlToyContext, shardingGroups.iterator().next());
		}

		// 开始多线程并行执行
		ShardingConfig shardingConfig = entityMeta.getShardingConfig();
		List results = new ArrayList();
		// 并行线程数量
		int threads = shardingGroups.size();
		// 是否全局异常回滚
		boolean globalRollback = shardingConfig.isGlobalRollback();
		// 如果额外策略配置了线程数量,则按照指定的线程数量执行
		if (threads > shardingConfig.getMaxConcurrents() && shardingConfig.getMaxConcurrents() > 1) {
			threads = shardingConfig.getMaxConcurrents();
		}
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<Future<ShardingResult>> futureResult = new ArrayList<Future<ShardingResult>>();
		for (final ShardingGroupModel group : shardingGroups) {
			Future<ShardingResult> future = pool.submit(new DialectExecutor(sqlToyContext, group, handler));
			futureResult.add(future);
		}
		pool.shutdown();
		// 设置最大等待时长
		if (shardingConfig.getMaxWaitSeconds() > 0) {
			pool.awaitTermination(shardingConfig.getMaxWaitSeconds(), TimeUnit.SECONDS);
		}
		// 提取各个线程返回的结果进行合并
		try {
			for (Future<ShardingResult> future : futureResult) {
				ShardingResult item = future.get();
				// 全局异常则抛出,让事务进行全部回滚。
				if (item != null && !item.isSuccess() && globalRollback) {
					throw new RuntimeException(item.getMessage());
				}
				if (item != null && item.getRows() != null && !item.getRows().isEmpty()) {
					results.addAll(item.getRows());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			pool.shutdownNow();
		}
		return results;
	}

}
