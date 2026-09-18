package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlXMLConfigParse;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * 回归测试：(H4)保留字工具多批次put后正则与集合一致、并发put+convertSql无异常;
 * (H5)多线程并发解析xml片段(共享DocumentBuilderFactory竞争)无异常且结果正确
 */
public class ReservedWordsAndDomFactoryTest {

	@AfterEach
	public void resetWords() {
		// 还原为单批保留字,避免影响其他测试(ReservedWordsUtilsTest依赖默认词表行为)
		ReservedWordsUtilReflect.put("status");
	}

	@Test
	public void multiBatchPutKeepsRegexConsistentWithSet() {
		// 第一批
		ReservedWordsUtilReflect.put("status");
		// 第二批:修复前组合正则只含当次批次,第一批的status不再被convertSql匹配
		ReservedWordsUtilReflect.put("order");
		// 两个批次的词都应被识别为关键词
		assertTrue(ReservedWordsUtil.isKeyWord("status"));
		assertTrue(ReservedWordsUtil.isKeyWord("order"));
		// convertSql对两个批次的词都应转换(sqlserver下 [word] 保持 [word] 形式且被匹配处理)
		String sql = "select [status] as st, [order] as od from t";
		String result = ReservedWordsUtil.convertSql(sql, DBType.SQLSERVER);
		assertTrue(result.contains("[status]"), "实际:" + result);
		assertTrue(result.contains("[order]"), "实际:" + result);
		// mysql下转换为反引号
		String mysqlResult = ReservedWordsUtil.convertSql(sql, DBType.MYSQL);
		assertTrue(mysqlResult.contains("`status`"), "实际:" + mysqlResult);
		assertTrue(mysqlResult.contains("`order`"), "实际:" + mysqlResult);
	}

	@Test
	public void concurrentPutAndConvertNoError() throws Exception {
		int threads = 8;
		AtomicInteger errors = new AtomicInteger();
		CountDownLatch latch = new CountDownLatch(threads);
		for (int t = 0; t < threads; t++) {
			final int taskId = t;
			new Thread(() -> {
				try {
					for (int i = 0; i < 300; i++) {
						ReservedWordsUtilReflect.put("col" + taskId + "_" + i);
						String r = ReservedWordsUtil.convertSql("select [col" + taskId + "_" + i + "] from t",
								DBType.SQLSERVER);
						assertTrue(r != null);
					}
				} catch (Throwable e) {
					errors.incrementAndGet();
				} finally {
					latch.countDown();
				}
			}).start();
		}
		latch.await();
		assertEquals(0, errors.get(), "并发put+convertSql不应有异常");
	}

	@Test
	public void concurrentXmlSagmentParseNoError() throws Exception {
		String xml = "<sql id=\"concurrent-test\"><![CDATA[select * from t where id=:id]]></sql>";
		int threads = 8;
		int loops = 200;
		AtomicInteger errors = new AtomicInteger();
		CountDownLatch latch = new CountDownLatch(threads);
		List<Thread> threadList = new ArrayList<Thread>();
		for (int t = 0; t < threads; t++) {
			Thread thread = new Thread(() -> {
				try {
					for (int i = 0; i < loops; i++) {
						SqlToyConfig config = SqlXMLConfigParse.parseSagment(xml, "UTF-8", "mysql", "concurrent-test");
						// 解析结果必须正确,不能因builder状态串扰产生错乱文档
						assertTrue(config.getSql(null).contains("select * from t"), "sql内容错乱");
						assertEquals("concurrent-test", config.getId());
					}
				} catch (Throwable e) {
					errors.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
			thread.start();
			threadList.add(thread);
		}
		latch.await();
		for (Thread thread : threadList) {
			thread.join(10_000);
		}
		assertEquals(0, errors.get(), "并发xml片段解析不应有异常(共享非线程安全DocumentBuilderFactory)");
	}

	// 反射访问synchronized put,保持public API语义测试
	private static class ReservedWordsUtilReflect {
		static void put(String words) {
			ReservedWordsUtil.put(words);
		}
	}
}
