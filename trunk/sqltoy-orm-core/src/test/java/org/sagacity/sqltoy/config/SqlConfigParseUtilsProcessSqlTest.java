package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * SqlConfigParseUtils.processSql 的真实场景深度验证(该文件为sql解析核心):
 * <li>按真实sql形态(where/where 1=1/@if-else/括号组/字面量/update)逐类断言结果语义</li>
 * <li>去掉动态片段后:占位符与参数数量一致、参数顺序与位置一一对应</li>
 * <li>输出无悬挂连接符、括号配对、无"where and/or"等非法拼接</li>
 * <li>多次调用结果确定、并发调用互不影响</li>
 * </ul>
 */
public class SqlConfigParseUtilsProcessSqlTest {

	private static final Pattern DANGLING = Pattern.compile("(?i)(\\(\\s*(and|or)\\b|\\b(and|or)\\s*\\))");
	private static final Pattern BAD_WHERE = Pattern.compile("(?i)\\bwhere\\s+(and|or)\\b|\\bwhere\\s*\\)");
	private static final Pattern ARG = SqlConfigParseUtils.ARG_NAME_PATTERN;

	private static class Case {
		private final String name;
		private final String sql;
		private final String[] names;
		private final Object[] values;
		private final String[] contains;
		private final String[] notContains;

		private Case(String name, String sql, String[] names, Object[] values, String[] contains,
				String[] notContains) {
			this.name = name;
			this.sql = sql;
			this.names = names;
			this.values = values;
			this.contains = contains;
			this.notContains = notContains;
		}
	}

	private static Case of(String name, String sql, String[] names, Object[] values, String[] contains,
			String[] notContains) {
		return new Case(name, sql, names, values, contains, notContains);
	}

	// ============ 1. 真实形态逐类断言 ============

	@Test
	public void realisticShapesProduceExpectedSql() throws Exception {
		List<Case> cases = new ArrayList<Case>();
		cases.add(of("全部条件", "select * from t_order t where 1=1 #[and t.status=:status] #[and t.org=:org]",
				new String[] { "status", "org" }, new Object[] { "1", "O1" },
				new String[] { "t.status=?", "t.org=?" }, new String[0]));
		cases.add(of("首个条件为null", "select * from t_order t where 1=1 #[and t.status=:status] #[and t.org=:org]",
				new String[] { "status", "org" }, new Object[] { null, "O1" }, new String[] { "t.org=?" },
				new String[] { "t.status" }));
		cases.add(of("全部条件为null", "select * from t_order t where 1=1 #[and t.status=:status] #[and t.org=:org]",
				new String[] { "status", "org" }, new Object[] { null, null }, new String[] { "where 1=1" },
				new String[] { "t.status", "t.org" }));
		cases.add(of("where后无1=1全部剔除", "select * from t_order where #[and t.status=:status]",
				new String[] { "status" }, new Object[] { null }, new String[] { "from t_order" },
				new String[] { "where", "t.status" }));
		// 说明:preSql以"where 1=1"结尾时会剔除1=1并剥离开头的and/or(既有设计,此处锁定现状)
		cases.add(of("or片段", "select * from t_order where 1=1 #[or t.status=:status]",
				new String[] { "status" }, new Object[] { "1" }, new String[] { "t.status=?" },
				new String[] { "where 1=1", " or " }));
		cases.add(of("括号组内片段剔除", "select * from t where 1=1 and ( #[a=:a] or 1=1 )", new String[] { "a" },
				new Object[] { null }, new String[] { "1=1" }, new String[] { "( and", "( or" }));
		cases.add(of("字面量中的where 1=1不受影响",
				"select * from t where 1=1 and remark='where 1=1' #[and a=:a]", new String[] { "a" },
				new Object[] { null }, new String[] { "remark='where 1=1'" }, new String[0]));
		cases.add(of("update语句片段", "update t_order set c1=:c1 #[ , c2=:c2] where id=:id",
				new String[] { "c1", "c2", "id" }, new Object[] { "v1", "v2", 9 }, new String[] { "set c1=?" },
				new String[0]));

		for (Case testCase : cases) {
			SqlToyResult result = SqlConfigParseUtils.processSql(testCase.sql, testCase.names, testCase.values,
					"mysql");
			assertInvariants(testCase.name, result);
			for (String expected : testCase.contains) {
				assertTrue(result.getSql().contains(expected),
						"[" + testCase.name + "] 应包含:" + expected + ",实际:" + result.getSql());
			}
			for (String unexpected : testCase.notContains) {
				assertFalse(result.getSql().contains(unexpected),
						"[" + testCase.name + "] 不应包含:" + unexpected + ",实际:" + result.getSql());
			}
		}
	}

	@Test
	public void ifElseBranchesChooseOnlyOneBranch() throws Exception {
		// (jre8同步适配:6.0为文本块,改为等价字符串拼接)
		String sql = "select * from t_order where 1=1\n"
				+ "#[@if(:flag==1) #[and status=:status] #[and saleType is not :saleType] ]\n"
				+ "#[@elseif(:flag==2) and name like :name]\n" + "#[@else and orderType=:orderType]\n";
		String[] names = { "flag", "status", "name", "orderType", "saleType" };

		SqlToyResult first = SqlConfigParseUtils.processSql(sql, names,
				new Object[] { 1, 1, "张", "SALE", null }, "mysql");
		assertInvariants("@if分支1", first);
		assertTrue(first.getSql().contains("status=?"), first.getSql());
		assertFalse(first.getSql().contains("orderType"), first.getSql());
		assertFalse(first.getSql().contains("name like"), first.getSql());

		SqlToyResult second = SqlConfigParseUtils.processSql(sql, names,
				new Object[] { 2, 1, "张", "SALE", null }, "mysql");
		assertInvariants("@elseif分支", second);
		assertTrue(second.getSql().contains("name like ?"), second.getSql());
		assertFalse(second.getSql().contains("status=?"), second.getSql());
		assertFalse(second.getSql().contains("orderType"), second.getSql());

		SqlToyResult third = SqlConfigParseUtils.processSql(sql, names,
				new Object[] { 3, 1, "张", "SALE", null }, "mysql");
		assertInvariants("@else分支", third);
		assertTrue(third.getSql().contains("orderType=?"), third.getSql());
		assertFalse(third.getSql().contains("status=?"), third.getSql());
		assertFalse(third.getSql().contains("name like"), third.getSql());
	}

	@Test
	public void nestedIfElseProducesValidSql() throws Exception {
		// (jre8同步适配:6.0为文本块,改为等价字符串拼接)
		String sql = "select * from t_order where 1=1\n" + "#[@if(:flag==1)\n"
				+ "    #[@if(:operateType==1) and status=:status]\n"
				+ "    #[@elseif(:operateType==2) and saleType is not :saleType]\n"
				+ "    #[@else and saleType is :saleType]\n" + "]\n"
				+ "#[@elseif(:flag==2) and name like :name]\n" + "#[@else and orderType=:orderType]\n";
		String[] names = { "flag", "status", "name", "orderType", "saleType", "operateType" };
		SqlToyResult inner1 = SqlConfigParseUtils.processSql(sql, names,
				new Object[] { 1, 1, "张", "SALE", null, 1 }, "mysql");
		assertInvariants("嵌套if内部1", inner1);
		assertTrue(inner1.getSql().contains("status=?"), inner1.getSql());

		SqlToyResult inner2 = SqlConfigParseUtils.processSql(sql, names,
				new Object[] { 1, 1, "张", "SALE", null, 2 }, "mysql");
		assertInvariants("嵌套if内部2", inner2);
		// null参与"is not"条件时会落成is not null(既有行为)
		assertTrue(inner2.getSql().contains("saleType is not null"), inner2.getSql());
		assertFalse(inner2.getSql().contains("status=?"), inner2.getSql());

		SqlToyResult inner3 = SqlConfigParseUtils.processSql(sql, names,
				new Object[] { 1, 1, "张", "SALE", null, 3 }, "mysql");
		assertInvariants("嵌套if内部else", inner3);
		assertTrue(inner3.getSql().contains("saleType is null"), inner3.getSql());
	}

	// ============ 2. 参数与占位符顺序必须严格对齐 ============

	@Test
	public void paramValuesStayAlignedAfterFragmentsRemoved() throws Exception {
		String sql = "select * from t where 1=1 #[and a=:a] #[and b=:b] #[and c=:c] #[and d=:d]";
		String[] names = { "a", "b", "c", "d" };
		// 各组合下:保留的条件与剩余参数必须保持原有先后顺序
		assertAligned(sql, names, new Object[] { null, null, null, null }, new String[0], new Object[0]);
		assertAligned(sql, names, new Object[] { "A", null, null, null }, new String[] { "a=?" },
				new Object[] { "A" });
		assertAligned(sql, names, new Object[] { null, "B", null, null }, new String[] { "b=?" },
				new Object[] { "B" });
		assertAligned(sql, names, new Object[] { null, null, "C", null }, new String[] { "c=?" },
				new Object[] { "C" });
		assertAligned(sql, names, new Object[] { null, null, null, "D" }, new String[] { "d=?" },
				new Object[] { "D" });
		assertAligned(sql, names, new Object[] { "A", null, "C", null }, new String[] { "a=?", "c=?" },
				new Object[] { "A", "C" });
		assertAligned(sql, names, new Object[] { null, "B", null, "D" }, new String[] { "b=?", "d=?" },
				new Object[] { "B", "D" });
		assertAligned(sql, names, new Object[] { "A", "B", "C", "D" },
				new String[] { "a=?", "b=?", "c=?", "d=?" }, new Object[] { "A", "B", "C", "D" });
	}

	private void assertAligned(String sql, String[] names, Object[] values, String[] expectedConditions,
			Object[] expectedValues) throws Exception {
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, names, values, "mysql");
		assertInvariants("参数对齐", result);
		assertEquals(expectedConditions.length * 1,
				StringUtil.matchCnt(result.getSql(), ARG, 0), "占位符数量不符:" + result.getSql());
		Object[] params = result.getParamsValue() == null ? new Object[0] : result.getParamsValue();
		assertEquals(expectedValues.length, params.length, "参数数量不符:" + result.getSql());
		for (int i = 0; i < expectedValues.length; i++) {
			assertEquals(expectedValues[i], params[i], "第" + i + "个参数错位:" + result.getSql());
		}
		for (String condition : expectedConditions) {
			assertTrue(result.getSql().contains(condition), "缺少条件:" + condition + ",实际:" + result.getSql());
		}
	}

	// ============ 3. 边界与压力 ============

	@Test
	public void sqlWithoutDynamicFragmentsIsReturnedUnchanged() throws Exception {
		String sql = "select * from t where a=1 and b='x'";
		SqlToyResult result = SqlConfigParseUtils.processSql(sql, new String[0], new Object[0], "mysql");
		assertEquals(sql, result.getSql());
		assertTrue(result.getParamsValue() == null || result.getParamsValue().length == 0);
	}

	@Test
	public void manyFragmentsKeepInvariantsAndCompleteQuickly() throws Exception {
		int fragmentCount = 200;
		StringBuilder sql = new StringBuilder("select * from t_big t where 1=1 ");
		String[] names = new String[fragmentCount];
		Object[] values = new Object[fragmentCount];
		Random random = new Random(20261118L);
		for (int i = 0; i < fragmentCount; i++) {
			names[i] = "p" + i;
			values[i] = random.nextBoolean() ? null : ("v" + i);
			sql.append("#[and t.c").append(i).append("=:").append(names[i]).append("] ");
		}
		long start = System.currentTimeMillis();
		SqlToyResult result = SqlConfigParseUtils.processSql(sql.toString(), names, values, "mysql");
		long cost = System.currentTimeMillis() - start;
		assertInvariants("200片段", result);
		assertTrue(cost < 2000, "200片段解析耗时异常:" + cost + "ms");
	}

	@Test
	public void processSqlIsDeterministicAndStateless() throws Exception {
		String sql = "select * from t_order where 1=1 #[and a=:a] #[and b=:b] #[and c=:c]";
		String[] names = { "a", "b", "c" };
		Object[] values = { "A", null, "C" };
		SqlToyResult first = SqlConfigParseUtils.processSql(sql, names, values, "mysql");
		for (int i = 0; i < 100; i++) {
			SqlToyResult again = SqlConfigParseUtils.processSql(sql, names, values, "mysql");
			assertEquals(first.getSql(), again.getSql(), "重复解析结果不稳定");
			assertEquals(first.getParamsValue().length, again.getParamsValue().length);
		}
		// 入参数组不应被解析过程篡改
		assertEquals(3, values.length);
		assertEquals("A", values[0]);
		assertEquals(null, values[1]);
		assertEquals("C", values[2]);
	}

	@Test
	public void concurrentParsingIsConsistent() throws Exception {
		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		for (int t = 0; t < threads; t++) {
			final int threadNo = t;
			new Thread(() -> {
				try {
					start.await();
					for (int i = 0; i < 300; i++) {
						StringBuilder sql = new StringBuilder("select * from t").append(threadNo)
								.append(" where 1=1 ");
						sql.append("#[and a=:a] #[and b=:b]");
						SqlToyResult result = SqlConfigParseUtils.processSql(sql.toString(),
								new String[] { "a", "b" }, new Object[] { (i % 2 == 0) ? null : "A", "B" }, "mysql");
						assertInvariants("并发", result);
						if (i % 2 == 0) {
							assertFalse(result.getSql().contains("a=?"), result.getSql());
							assertTrue(result.getSql().contains("b=?"), result.getSql());
						} else {
							assertTrue(result.getSql().contains("a=?"), result.getSql());
						}
					}
				} catch (Throwable e) {
					failure.compareAndSet(null, e);
				} finally {
					done.countDown();
				}
			}).start();
		}
		start.countDown();
		assertTrue(done.await(60, TimeUnit.SECONDS), "并发解析超时");
		if (failure.get() != null) {
			throw new AssertionError("并发解析失败", failure.get());
		}
	}

	// ============ 通用后置条件 ============

	private static void assertInvariants(String caseName, SqlToyResult result) {
		String sql = result.getSql();
		assertFalse(sql.contains("#["), "[" + caseName + "] 残留动态标记:" + sql);
		assertFalse(DANGLING.matcher(sql).find(), "[" + caseName + "] 存在悬挂连接符:" + sql);
		assertFalse(BAD_WHERE.matcher(sql).find(), "[" + caseName + "] 存在非法where拼接:" + sql);
		assertTrue(parenthesesBalanced(sql), "[" + caseName + "] 括号不配对:" + sql);
		int argCount = StringUtil.matchCnt(sql, ARG, 0);
		int paramCount = result.getParamsValue() == null ? 0 : result.getParamsValue().length;
		assertEquals(paramCount, argCount, "[" + caseName + "] 占位符与参数数量不一致:" + sql);
	}

	private static boolean parenthesesBalanced(String sql) {
		int depth = 0;
		boolean inQuote = false;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c == '\'') {
				inQuote = !inQuote;
			}
			if (inQuote) {
				continue;
			}
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth < 0) {
					return false;
				}
			}
		}
		return depth == 0;
	}
}
