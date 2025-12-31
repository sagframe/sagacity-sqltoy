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

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ParallelCallbackHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.ShardingConfig;
import org.sagacity.sqltoy.config.model.ShardingGroupModel;
import org.sagacity.sqltoy.config.model.ShardingModel;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.executor.DialectExecutor;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.ShardingResult;
import org.sagacity.sqltoy.plugins.sharding.ShardingUtils;

/**
 * @project sagacity-sqltoy
 * @description sqltoy对象集合相关操作、sharding策略分组以及并行提交相关数据库进行执行
 * @author zhongxuchen
 * @version v1.0,Date:2017年11月3日
 */
@SuppressWarnings("rawtypes")
public class ParallelUtils {
	private ParallelUtils() {
	}

	/**
	 * @todo 将集合进行根据sharding字段的值提取sharding策略并按照策略将集合分组，然后并行执行
	 * @param sqlToyContext
	 * @param entities
	 * @param wrapIdValue 是否需要事先主动给类似雪花算法、uuid等基于算法的id赋值
	 * @param notSharding
	 * @param dataSource
	 * @param parallelConfig
	 * @param handler
	 * @return
	 * @throws Exception
	 */
	public static List execute(final SqlToyContext sqlToyContext, List entities, boolean wrapIdValue,
			boolean notSharding, SqlType sqlType, DataSource dataSource, ParallelConfig parallelConfig,
			ParallelCallbackHandler handler) throws Exception {
		Class entityClass = entities.get(0).getClass();
		boolean isEntity = notSharding ? false : sqlToyContext.isEntity(entityClass);
		// 获取对象的媒体信息
		EntityMeta entityMeta = isEntity ? sqlToyContext.getEntityMeta(entityClass) : null;
		// 主键值需要提前按照主键策略赋予(sequence 和assign模式的不会实际执行赋值)
		if (wrapIdValue && isEntity) {
			ShardingUtils.assignPKs(sqlToyContext, entityMeta, entities);
		}
		// 将批量集合数据按sharding策略处理后的库和表组成的key分组
		Collection<ShardingGroupModel> shardingGroups = null;
		ShardingConfig shardingConfig = null;
		// notSharding只在batchUpdate场景,其它有分库分表策略优先,无分库分表且配置了并行,则开启集合拆分
		if (notSharding || ((entityMeta == null || entityMeta.getShardingConfig() == null) && parallelConfig != null)) {
			shardingGroups = splitSetParallel(entityMeta, entities, dataSource, parallelConfig);
			shardingConfig = new ShardingConfig();
			if (parallelConfig != null) {
				shardingConfig.setMaxConcurrents(parallelConfig.getMaxThreads());
				shardingConfig.setMaxWaitSeconds(parallelConfig.getMaxWaitSeconds());
			}
		} else {
			shardingGroups = ShardingUtils.groupShardings(sqlToyContext, entities, entityMeta, dataSource);
			shardingConfig = entityMeta.getShardingConfig();
		}
		// 单分组直接执行
		if (shardingGroups.size() == 1) {
			return handler.execute(sqlToyContext, shardingGroups.iterator().next());
		}
		SqlExecuteStat.debug("开启并行执行", "并行线程数:{},最大等待时长:{}秒", shardingGroups.size(),
				shardingConfig.getMaxWaitSeconds());
		// 开始多线程并行执行
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
		List<Future<ShardingResult>> futureResults = new ArrayList<Future<ShardingResult>>();
		Future<ShardingResult> future;
		for (ShardingGroupModel group : shardingGroups) {
			future = pool.submit(new DialectExecutor(sqlToyContext, group, handler));
			futureResults.add(future);
		}
		pool.shutdown();
		// 设置最大等待时长
		if (shardingConfig.getMaxWaitSeconds() > 0) {
			pool.awaitTermination(shardingConfig.getMaxWaitSeconds(), TimeUnit.SECONDS);
		} else {
			pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
		}
		// 提取各个线程返回的结果进行合并
		try {
			ShardingResult item;
			for (Future<ShardingResult> futureResult : futureResults) {
				item = futureResult.get();
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

	/**
	 * @todo 根据并行单个分组的记录量和并行度，切割集合，组织分组数据结构
	 * @param entityMeta
	 * @param entities
	 * @param dataSource
	 * @param parallelConfig
	 * @return
	 */
	private static Collection<ShardingGroupModel> splitSetParallel(EntityMeta entityMeta, List entities,
			DataSource dataSource, ParallelConfig parallelConfig) {
		Collection<ShardingGroupModel> shardingGroups = new ArrayList<ShardingGroupModel>();
		ShardingModel shardingModel = new ShardingModel();
		shardingModel.setDataSource(dataSource);
		if (entityMeta != null) {
			shardingModel.setTableName(entityMeta.getTableName());
		}
		int recordSize = entities.size();
		// 分组记录量
		int groupSize;
		// 并行设置为null,则全部记录为一个分组
		if (parallelConfig == null) {
			groupSize = recordSize;
		} else {
			// 设置合理的并行分组记录量
			if (recordSize % parallelConfig.getMaxThreads() == 0) {
				groupSize = recordSize / parallelConfig.getMaxThreads();
			} else {
				groupSize = (recordSize / parallelConfig.getMaxThreads()) + 1;
			}
			if (groupSize < parallelConfig.getGroupSize()) {
				groupSize = parallelConfig.getGroupSize();
			}
		}
		// 单个分组
		if (recordSize <= groupSize) {
			ShardingGroupModel groupModel = new ShardingGroupModel();
			groupModel.setEntities(entities);
			groupModel.setShardingModel(shardingModel);
			shardingGroups.add(groupModel);
		} else {
			int meter = 0;
			List subEntities = new ArrayList<>();
			for (Object item : entities) {
				subEntities.add(item);
				meter++;
				if (meter % groupSize == 0 || meter == recordSize) {
					ShardingGroupModel groupModel = new ShardingGroupModel();
					groupModel.setEntities(subEntities);
					groupModel.setShardingModel(shardingModel);
					shardingGroups.add(groupModel);
					subEntities = new ArrayList();
				}
			}
		}
		return shardingGroups;
	}
}
