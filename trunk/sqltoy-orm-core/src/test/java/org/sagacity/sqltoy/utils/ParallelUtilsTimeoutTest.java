package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.ParallelConfig;

/**
 * 回归测试：maxWaitSeconds超时必须生效——超时即中断未完成任务并抛出异常,
 * 而不是忽略awaitTermination返回值后在future.get()上无限期阻塞
 */
public class ParallelUtilsTimeoutTest {

	@Test
	public void maxWaitSecondsTimeoutThrowsInsteadOfBlocking() {
		SqlToyContext context = new SqlToyContext();
		// groupSize builder下限为201,500条记录按201一组产生3个分组进入并行路径
		List<String> entities = new ArrayList<String>();
		for (int i = 0; i < 500; i++) {
			entities.add("row-" + i);
		}
		ParallelConfig parallelConfig = new ParallelConfig();
		parallelConfig.maxThreads(4);
		parallelConfig.groupSize(201);
		parallelConfig.maxWaitSeconds(1);
		long start = System.currentTimeMillis();
		assertThrows(DataAccessException.class, () -> ParallelUtils.execute(context, entities, false, true,
				SqlType.insert, null, parallelConfig, (sqlToyContext, group) -> {
					// 模拟数据库挂起且无statementTimeout的卡死任务
					Thread.sleep(10_000);
					return null;
				}));
		long cost = System.currentTimeMillis() - start;
		// 1秒超时即返回,而不是等任务跑完10秒(修复前awaitTermination返回值被忽略,get()无限期阻塞)
		assertTrue(cost < 8_000, "超时后应在约1秒抛出,实际耗时:" + cost + "ms");
	}
}
