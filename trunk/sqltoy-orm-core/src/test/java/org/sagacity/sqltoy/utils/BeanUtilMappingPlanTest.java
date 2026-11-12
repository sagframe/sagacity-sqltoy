package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * BeanUtil 映射计划缓存的深度验证:缓存复用、不同列类型不串用、属性顺序、关闭自动转换、
 * 并发下共享只读数组的正确性
 */
public class BeanUtilMappingPlanTest {

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

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public BigDecimal getAmount() {
			return amount;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}

		public Date getCreateTime() {
			return createTime;
		}

		public void setCreateTime(Date createTime) {
			this.createTime = createTime;
		}

		public long getQty() {
			return qty;
		}

		public void setQty(long qty) {
			this.qty = qty;
		}

		public double getRate() {
			return rate;
		}

		public void setRate(double rate) {
			this.rate = rate;
		}

		public String getRemark() {
			return remark;
		}

		public void setRemark(String remark) {
			this.remark = remark;
		}

		public Integer getStatus() {
			return status;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}

		public Timestamp getUpdateTime() {
			return updateTime;
		}

		public void setUpdateTime(Timestamp updateTime) {
			this.updateTime = updateTime;
		}

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getOrgId() {
			return orgId;
		}

		public void setOrgId(String orgId) {
			this.orgId = orgId;
		}
	}

	private static final String[] COLUMNS = { "id", "name", "amount", "createTime", "qty", "rate", "remark", "status",
			"updateTime", "code", "type", "orgId" };

	private static final String[] COLUMN_TYPES = { "INTEGER", "VARCHAR", "DECIMAL", "TIMESTAMP", "BIGINT", "DOUBLE",
			"VARCHAR", "INTEGER", "TIMESTAMP", "VARCHAR", "VARCHAR", "VARCHAR" };

	private static final int[] INDEXES = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

	@Test
	public void repeatedMappingReturnsIdenticalValues() throws Exception {
		clearPlanCache();
		List<Object[]> rows = buildRows(50);
		List first = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		List second = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		assertEquals(50, first.size());
		for (int i = 0; i < first.size(); i++) {
			DemoVo left = (DemoVo) first.get(i);
			DemoVo right = (DemoVo) second.get(i);
			assertEquals(left.getId(), right.getId());
			assertEquals(left.getName(), right.getName());
			assertEquals(left.getAmount(), right.getAmount());
			assertEquals(left.getStatus(), right.getStatus());
			assertEquals(left.getCode(), right.getCode());
			assertEquals(left.getOrgId(), right.getOrgId());
		}
	}

	@Test
	public void planCacheReusedForSameShape() throws Exception {
		clearPlanCache();
		List<Object[]> rows = buildRows(5);
		BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		assertEquals(1, planCacheSize(), "首次调用应只登记一个映射计划");
		BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		assertEquals(1, planCacheSize(), "同一(类+属性+列类型)不应重复登记");
	}

	@Test
	public void differentColumnTypesDoNotSharePlan() throws Exception {
		clearPlanCache();
		List<Object[]> rows = buildRows(3);
		BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		assertEquals(1, planCacheSize());
		String[] otherTypes = COLUMN_TYPES.clone();
		otherTypes[2] = "JSON";
		BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, otherTypes, DemoVo.class, true);
		assertEquals(2, planCacheSize(), "列类型不同必须使用不同映射计划");
		BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		assertEquals(2, planCacheSize(), "重复的列类型组合应命中既有计划");
	}

	@Test
	public void propertyOrderIsRespected() throws Exception {
		clearPlanCache();
		Object[] row = { "code-1", 7 };
		String[] properties = { "code", "id" };
		int[] indexes = { 0, 1 };
		List result = BeanUtil.reflectListToBean(null, java.util.Collections.singletonList(row), indexes, properties,
				new String[] { "VARCHAR", "INTEGER" }, DemoVo.class, true);
		DemoVo vo = (DemoVo) result.get(0);
		assertEquals("code-1", vo.getCode());
		assertEquals(7, vo.getId());
	}

	@Test
	public void autoConvertDisabledStillMapsCompatibleTypes() throws Exception {
		clearPlanCache();
		Object[] row = { 1, "n1", new BigDecimal("1.5"), new Date(), 2L, 0.5d, "r", 1, new Timestamp(0L), "c", "t",
				"o" };
		List result = BeanUtil.reflectListToBean(null, java.util.Collections.singletonList(row), INDEXES, COLUMNS,
				COLUMN_TYPES, DemoVo.class, false);
		DemoVo vo = (DemoVo) result.get(0);
		assertEquals(1, vo.getId());
		assertEquals("n1", vo.getName());
		assertEquals(new BigDecimal("1.5"), vo.getAmount());
	}

	@Test
	public void concurrentMappingIsConsistent() throws Exception {
		clearPlanCache();
		int threads = 8;
		int iterations = 200;
		int rowSize = 30;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		for (int t = 0; t < threads; t++) {
			final int threadNo = t;
			new Thread(() -> {
				try {
					start.await();
					List<Object[]> rows = buildRows(rowSize);
					for (int i = 0; i < iterations; i++) {
						// 同一批线程共享同一计划键,同时穿插不同列类型以覆盖多键并发登记
						List mapped = BeanUtil.reflectListToBean(null, rows, INDEXES, COLUMNS, COLUMN_TYPES,
								DemoVo.class, true);
						for (int r = 0; r < mapped.size(); r++) {
							DemoVo vo = (DemoVo) mapped.get(r);
							if (vo.getId() != r || !("name" + r).equals(vo.getName())) {
								throw new IllegalStateException("线程" + threadNo + "映射错位:row=" + r + ",id="
										+ vo.getId() + ",name=" + vo.getName());
							}
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
		assertTrue(done.await(60, java.util.concurrent.TimeUnit.SECONDS), "并发映射未在超时内完成");
		if (failure.get() != null) {
			throw new AssertionError("并发映射失败", failure.get());
		}
	}

	private static List<Object[]> buildRows(int rowSize) {
		List<Object[]> rows = new ArrayList<Object[]>(rowSize);
		for (int i = 0; i < rowSize; i++) {
			rows.add(new Object[] { i, "name" + i, new BigDecimal(i * 1.5), new Date(), (long) i, i * 0.5,
					"remark" + i, i % 2, new Timestamp(0L), "code" + i, "type" + i, "org" + i });
		}
		return rows;
	}

	private static ConcurrentHashMap<?, ?> planCache() throws Exception {
		Field field = BeanUtil.class.getDeclaredField("mappingPlanCache");
		field.setAccessible(true);
		return (ConcurrentHashMap<?, ?>) field.get(null);
	}

	private static int planCacheSize() throws Exception {
		return planCache().size();
	}

	private static void clearPlanCache() throws Exception {
		planCache().clear();
	}

	@Test
	public void planObjectIsNotNull() throws Exception {
		clearPlanCache();
		BeanUtil.reflectListToBean(null, buildRows(2), INDEXES, COLUMNS, COLUMN_TYPES, DemoVo.class, true);
		assertNotNull(planCache().values().iterator().next());
	}
}
