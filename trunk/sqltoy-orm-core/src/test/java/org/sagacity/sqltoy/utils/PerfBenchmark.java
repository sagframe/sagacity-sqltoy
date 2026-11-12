package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;

/**
 * 本地性能基准:类名不匹配 surefire 默认扫描规则,不参与CI,
 * 需要时用 mvn -Dtest=PerfBenchmark test 单独执行
 * <p>
 * 覆盖两项待优化热点:
 * <li>SqlConfigParseUtils.processSql:多条件动态sql的参数归集(含#[]片段剔除与括号连接符清理)</li>
 * <li>BeanUtil.reflectListToBean:结果集到VO的映射计划构建</li>
 * </p>
 */
public class PerfBenchmark {

	private static final int WARMUP_ITERATIONS = 3000;

	public static class DemoVo {
		private int id;
		private String name;
		private BigDecimal amount;
		private Date createTime;
		private long qty;
		private double rate;
		private String remark;
		private Integer status;
		private Timestamp updateTime;
		private String code;
		private String type;
		private String orgId;

		public void setId(int id) {
			this.id = id;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}

		public void setCreateTime(Date createTime) {
			this.createTime = createTime;
		}

		public void setQty(long qty) {
			this.qty = qty;
		}

		public void setRate(double rate) {
			this.rate = rate;
		}

		public void setRemark(String remark) {
			this.remark = remark;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}

		public void setUpdateTime(Timestamp updateTime) {
			this.updateTime = updateTime;
		}

		public void setCode(String code) {
			this.code = code;
		}

		public void setType(String type) {
			this.type = type;
		}

		public void setOrgId(String orgId) {
			this.orgId = orgId;
		}
	}

	@Test
	public void sqlParse() throws Exception {
		StringBuilder sql = new StringBuilder("select * from t_order t where 1=1 ");
		String[] names = new String[40];
		Object[] values = new Object[40];
		for (int i = 0; i < 40; i++) {
			names[i] = "p" + i;
			values[i] = (i % 2 == 0) ? "v" + i : null;
			sql.append("#[and t.c").append(i).append("=:").append(names[i]).append("] ");
		}
		// 括号组:触发cleanDanglingConnectorInParens的and/or清理分支
		sql.append("#[and (t.g0=:").append(names[0]).append(" or t.h1=:").append(names[1]).append(")] ");
		String sqlStr = sql.toString();
		SqlToyResult check = SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
		if (check.getSql().contains("#[")) {
			throw new IllegalStateException("dynamic sql not processed");
		}
		for (int i = 0; i < WARMUP_ITERATIONS; i++) {
			SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
		}
		int iterations = 20000;
		long start = System.nanoTime();
		for (int i = 0; i < iterations; i++) {
			SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
		}
		print("sqlParse(41 fragments)", iterations, System.nanoTime() - start);
	}

	/**
	 * 片段数量与耗时的关系:用于判断是否存在O(n^2)级联扫描
	 */
	@Test
	public void sqlParseScaling() throws Exception {
		for (int count : new int[] { 1, 5, 10, 20, 40 }) {
			Object[] testCase = buildSqlCase(count);
			String sqlStr = (String) testCase[0];
			String[] names = (String[]) testCase[1];
			Object[] values = (Object[]) testCase[2];
			for (int i = 0; i < 2000; i++) {
				SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
			}
			int iterations = 3000;
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++) {
				SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
			}
			print("sqlParse(" + count + " fragments)", iterations, System.nanoTime() - start);
		}
	}

	private static Object[] buildSqlCase(int count) {
		StringBuilder sql = new StringBuilder("select * from t_order t where 1=1 ");
		String[] names = new String[count + 2];
		Object[] values = new Object[count + 2];
		for (int i = 0; i < count; i++) {
			names[i] = "p" + i;
			values[i] = (i % 2 == 0) ? "v" + i : null;
			sql.append("#[and t.c").append(i).append("=:").append(names[i]).append("] ");
		}
		names[count] = "g0";
		values[count] = "v0";
		names[count + 1] = "h1";
		values[count + 1] = "v1";
		sql.append("#[and (t.g0=:").append(names[count]).append(" or t.h1=:").append(names[count + 1]).append(")] ");
		return new Object[] { sql.toString(), names, values };
	}

	@Test
	public void beanMapping() throws Exception {
		int rowSize = 5000;
		List<Object[]> datas = buildRows(rowSize);
		int[] indexs = new int[12];
		for (int i = 0; i < 12; i++) {
			indexs[i] = i;
		}
		for (int i = 0; i < 20; i++) {
			BeanUtil.reflectListToBean(null, datas, indexs, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		}
		int iterations = 100;
		long start = System.nanoTime();
		List result = null;
		for (int i = 0; i < iterations; i++) {
			result = BeanUtil.reflectListToBean(null, datas, indexs, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		}
		if (result == null || result.size() != rowSize) {
			throw new IllegalStateException("unexpected mapping result");
		}
		print("beanMapping(5000 rows x 12 cols)", iterations, System.nanoTime() - start);
	}

	/**
	 * 单页小结果集:映射计划构建开销占比最高的场景
	 */
	@Test
	public void beanMappingSmallPage() throws Exception {
		int rowSize = 20;
		List<Object[]> datas = buildRows(rowSize);
		int[] indexs = new int[12];
		for (int i = 0; i < 12; i++) {
			indexs[i] = i;
		}
		for (int i = 0; i < 5000; i++) {
			BeanUtil.reflectListToBean(null, datas, indexs, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		}
		int iterations = 20000;
		long start = System.nanoTime();
		for (int i = 0; i < iterations; i++) {
			BeanUtil.reflectListToBean(null, datas, indexs, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		}
		print("beanMapping(20 rows x 12 cols)", iterations, System.nanoTime() - start);
	}

	private static final String[] COLUMNS = { "id", "name", "amount", "createTime", "qty", "rate", "remark", "status",
			"updateTime", "code", "type", "orgId" };
	private static final String[] COLUMN_TYPES = { "INTEGER", "VARCHAR", "DECIMAL", "TIMESTAMP", "BIGINT", "DOUBLE",
			"VARCHAR", "INTEGER", "TIMESTAMP", "VARCHAR", "VARCHAR", "VARCHAR" };

	private static List<Object[]> buildRows(int rowSize) {
		List<Object[]> datas = new ArrayList<Object[]>(rowSize);
		for (int i = 0; i < rowSize; i++) {
			datas.add(new Object[] { i, "name" + i, new BigDecimal(i * 1.5), new Date(), (long) i, i * 0.5,
					"remark" + i, i % 2, new Timestamp(System.currentTimeMillis()), "code" + i, "type" + i,
					"org" + i });
		}
		return datas;
	}

	/**
	 * 映射计划相关固定开销(方法匹配缓存key构造+字段注解扫描),用于衡量每次查询的固定成本
	 */
	@Test
	public void mappingPlanBuildCost() {
		for (int i = 0; i < 2000; i++) {
			BeanUtil.matchSetMethods(DemoVo.class, COLUMNS);
			BeanUtil.getClassFieldMap(DemoVo.class, COLUMNS);
		}
		int iterations = 50000;
		long start = System.nanoTime();
		for (int i = 0; i < iterations; i++) {
			BeanUtil.matchSetMethods(DemoVo.class, COLUMNS);
			BeanUtil.getClassFieldMap(DemoVo.class, COLUMNS);
		}
		print("mappingPlanBuild(12 props)", iterations, System.nanoTime() - start);
	}

	private static void print(String name, int iterations, long elapsedNanos) {
		double totalMs = elapsedNanos / 1000000.0;
		System.out.printf("[bench] %s: %.2f ms/op, %d ops in %.1f ms%n", name, totalMs / iterations, iterations,
				totalMs);
	}

	/**
	 * 供JFR采样使用:持续执行多条件动态sql解析
	 */
	public static void main(String[] args) throws Exception {
		Object[] testCase = buildSqlCase(40);
		String sqlStr = (String) testCase[0];
		String[] names = (String[]) testCase[1];
		Object[] values = (Object[]) testCase[2];
		long endTime = System.currentTimeMillis() + 30000;
		int count = 0;
		while (System.currentTimeMillis() < endTime) {
			SqlConfigParseUtils.processSql(sqlStr, names, values, "mysql");
			count++;
		}
		System.out.println("iterations=" + count);
	}
}
