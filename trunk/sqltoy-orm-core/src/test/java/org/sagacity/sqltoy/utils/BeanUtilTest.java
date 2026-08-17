package org.sagacity.sqltoy.utils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.FieldSecureConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.demo.domain.DeviceOrderVO;
import org.sagacity.sqltoy.demo.domain.StaffInfo;
import org.sagacity.sqltoy.demo.vo.DataRange;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.demo.vo.Student;
import org.sagacity.sqltoy.demo.vo.TypeShowCase;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.model.MaskType;
import org.sagacity.sqltoy.model.SaveMode;
import org.sagacity.sqltoy.model.SecureType;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;

public class BeanUtilTest {

	// 测试多级反射
	@Test
	public void testMultLevelReflect() {
		StaffInfoVO staff = new StaffInfoVO();
		staff.setEmail("zhongxuchen@gmail.com");
		staff.setStaffId("S001");
		DataRange dataRange = new DataRange();
		dataRange.setBeginDate(DateUtil.getDate("2020-10-01"));
		dataRange.setEndDate(LocalDate.now());
		staff.setDataRange(dataRange);

		HashMap params = new HashMap();
		params.put("companyId", "C0001");
		params.put("companyName", "xxx企业集团");
		staff.setParams(params);
		Object[] result = BeanUtil.reflectBeanToAry(staff, new String[] { "staffId", "email", "dataRange.beginDate",
				"dataRange.enddate", "params.companyId", "params.companyName" }, null, null);
		for (Object tmp : result) {
			System.err.println(tmp);
		}
	}

	@Test
	public void testMultLevelMapReflect() {
		StaffInfoVO staff = new StaffInfoVO();
		staff.setEmail("zhongxuchen@gmail.com");
		staff.setStaffId("S001");
		DataRange dataRange = new DataRange();
		dataRange.setBeginDate(DateUtil.getDate("2020-10-01"));
		dataRange.setEndDate(LocalDate.now());
		staff.setDataRange(dataRange);

		HashMap params = new HashMap();
		params.put("companyId", "C0001");
		params.put("companyName", "xxx企业集团");
		staff.setParams(params);
		Map map = new IgnoreKeyCaseMap();
		map.put("staff", staff);
		Object[] result = BeanUtil
				.reflectBeanToAry(map,
						new String[] { "staff.staffid", "staff.email", "staff.dataRange.beginDate",
								"staff.dataRange.enddate", "staff.params.companyId", "staff.params.companyName" },
						null, null);
		for (Object tmp : result) {
			System.err.println(tmp);
		}
	}

	@Test
	public void testMultLevelMapListReflect() {
		StaffInfoVO staff = new StaffInfoVO();
		staff.setEmail("zhongxuchen@gmail.com");
		staff.setStaffId("S001");
		DataRange dataRange = new DataRange();
		dataRange.setBeginDate(DateUtil.getDate("2020-10-01"));
		dataRange.setEndDate(LocalDate.now());
		staff.setDataRange(dataRange);

		HashMap params = new HashMap();
		params.put("companyId", "C0001");
		params.put("companyName", "xxx企业集团");
		staff.setParams(params);
		Map map = new HashMap();
		map.put("staff", staff);
		List<Map> listMap = new ArrayList<Map>();
		listMap.add(map);
		List result = null;
		try {
			result = BeanUtil
					.reflectBeansToList(listMap,
							new String[] { "staff.staffid", "staff.email", "staff.dataRange.beginDate",
									"staff.dataRange.enddate", "staff.params.companyId", "staff.params.companyName" },
							null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (Object tmp : result) {
			System.err.println(tmp);
		}
	}

	@Test
	public void testMapListReflect() {
		HashMap params = new HashMap();
		params.put("staff.companyId", "C0001");
		params.put("companyName", "xxx企业集团");
		List<Map> listMap = new ArrayList<Map>();
		listMap.add(params);
		List result = null;
		try {
			result = BeanUtil.reflectBeansToList(listMap, new String[] { "staff.companyid", "companyName" }, null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (Object tmp : result) {
			System.err.println(tmp);
		}
	}

	@Test
	public void testTypeName() {
		System.err.println(DateUtil.formatDate(LocalDate.now(), "MMM dd,yyyy", Locale.US));
		System.err.println(byte[].class.getName());
		System.err.println(byte[].class.getTypeName());
		System.err.println(SaveMode.class.getTypeName());
	}

	@Test
	public void testMap() {
		HashMap params = new HashMap();
		params.put("companyId", "C0001");
		params.put("companyName", null);
		IgnoreKeyCaseMap map = new IgnoreKeyCaseMap(params);
		System.err.println(((Map) map).get("companyId"));
	}

	/**
	 * 显示java pojo的所有类型名称
	 */
	@Test
	public void testFullTypeName() {
		TypeShowCase showCase = new TypeShowCase();
		System.err.println("[" + showCase.getCharValue() + "][" + " ".charAt(0) + "]");
		System.err.println("[" + showCase.getByteType() + "][" + Byte.valueOf("0").byteValue() + "]");
		Method[] methods = TypeShowCase.class.getMethods();
		for (Method method : methods) {
			if (method.getParameterTypes().length > 0) {
				System.err.println(method.getParameterTypes()[0].getTypeName());
			}
		}

	}

	@Test
	public void testLinkedMap() {
		IgnoreCaseLinkedMap<String, Object> realDataMap = new IgnoreCaseLinkedMap<String, Object>();
		realDataMap.put("chen", null);
		System.err.println(realDataMap.get("chend1"));
	}

	@Test
	public void testBaseType() {
		StaffInfo staff = new StaffInfo() {
			{
				country = "china";
				createBy = "S0001";
			}
		};
		System.err.println("{{}}实例化得到的class=" + staff.getClass().getName());
		System.err.println("通过BeanUtil处理后得到的=" + BeanUtil.getEntityClass(staff.getClass()).getName());
		DeviceOrderVO order = new DeviceOrderVO() {
			{
				setSaler("ssss");
			}
		};
		System.err.println(BeanUtil.getEntityClass(order.getClass()).getName());
		DataRange da = new DataRange() {
			{
				setBeginDate(LocalDate.now());
			}
		};
		System.err.println("{{}}实例化得到的class=" + da.getClass().getName());
		System.err.println(BeanUtil.getEntityClass(da.getClass()).getName());
	}

	@Test
	public void testReflect() {
		StaffInfoVO staff = new StaffInfoVO();
		staff.setEmail("zhongxuchen@gmail.com");
		staff.setStaffId("S001");
		staff.setResType(1);
		try {
			System.err.println(
					JSON.toJSONString(BeanUtil.reflectBeanToAry(staff, new String[] { "resType", "staffId" })));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void testBeanWrapper() {
		List<StaffInfoVO> staffInfos = new ArrayList<StaffInfoVO>();

		StaffInfoVO staff = new StaffInfoVO();
		staff.setEmail("zhongxuchen@gmail.com");
		staff.setStaffId("S001");
		staff.setResType(1);

		StaffInfoVO staff1 = new StaffInfoVO();
		staff1.setEmail("zhongxuchen@gmail.com");
		staff1.setStaffId("S001");
		staff1.setResType(1);

		staffInfos.add(staff);
		staffInfos.add(staff1);
		BeanWrapper.create().names("staffName").values("陈").mappingSet(staffInfos);

		System.err.println(JSON.toJSONString(staffInfos));

	}

	@Test
	public void testType() {
		System.err.println(BeanUtil.isBaseDataType(Array.class));
		System.err.println(BeanUtil.isBaseDataType(int.class));
		System.err.println(BeanUtil.isBaseDataType(Map.class));
		System.err.println(BeanUtil.isBaseDataType(List.class));
		System.err.println(DataRange.class.getSuperclass().getName());
	}

	@Test
	public void testEnum() {
		MaskType type = MaskType.ADDRESS;
		try {
			System.err.println(MaskType.values());
			System.err.println(((Enum) type).name());
			System.err.println(((Enum) type).ordinal());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Test
	public void testClone() {
		Translate translate = new Translate("dictKey");
		translate.setColumn("id");
		translate.setIndex(5);

		Translate cloneValue = translate.clone();
		cloneValue.setIndex(3);
		System.err.println(translate.getExtend().index);
		System.err.println(cloneValue.getExtend().index);
	}

	@Test
	public void testParall() {
		ExecutorService pool = null;
		try {
			pool = Executors.newFixedThreadPool(2);
			// 查询总记录数量
			pool.submit(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < 10; i++) {
						System.err.println("--------" + i);
						try {
							Thread.currentThread().sleep(100);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			});
			for (int i = 0; i < 10; i++) {
				System.err.println("#######" + i);
				try {
					Thread.currentThread().sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			pool.shutdown();
			pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("并行查询执行错误:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}
	}

	@Test
	public void testParall2() {
		ExecutorService pool = null;
		try {
			pool = Executors.newFixedThreadPool(2);
			// 查询总记录数量
			CompletableFuture countCompletableFuture = CompletableFuture.runAsync(() -> {
				System.err.println("@@@@@@@@@@@@@@@@2");
			}, pool);
			// 获取记录
			CompletableFuture dataCompletableFuture = CompletableFuture.runAsync(() -> {
				System.err.println("---------------2");
			}, pool);
			pool.shutdown();
			pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("并行查询执行错误:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}
	}

	@Test
	public void testParall3() {
		ExecutorService pool = null;
		try {
			pool = Executors.newFixedThreadPool(2);
			// 查询总记录数量
			pool.submit(new Runnable() {
				@Override
				public void run() {
					// System.err.println("@@@@@@@@@@@@@@@@1");
				}
			});
			// 获取记录
			pool.submit(new Runnable() {
				@Override
				public void run() {
					// System.err.println("---------------1");
				}
			});
			pool.shutdown();
			pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("并行查询执行错误:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}

		try {
			pool = Executors.newFixedThreadPool(2);
			// 查询总记录数量
			CompletableFuture countCompletableFuture = CompletableFuture.runAsync(() -> {
				System.err.println("@@@@@@@@@@@@@@@@2");
			}, pool);
			// 获取记录
			CompletableFuture dataCompletableFuture = CompletableFuture.runAsync(() -> {
				System.err.println("---------------2");
			}, pool);
			pool.shutdown();
			pool.awaitTermination(SqlToyConstants.PARALLEL_MAXWAIT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException("并行查询执行错误:" + e.getMessage(), e);
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}
	}

	@Test
	public void testMethodType() {
		StaffInfoVO staff = new StaffInfoVO();
		Method method = BeanUtil.matchSetMethods(StaffInfoVO.class, "dataRangeList")[0];
		System.err.println(method.getParameterTypes()[0]);
		System.err.println(method.getParameterTypes()[0].equals(List.class));
		System.err.println(((ParameterizedType) method.getGenericParameterTypes()[0]).getRawType());
		System.err.println(((ParameterizedType) method.getGenericParameterTypes()[0]).getActualTypeArguments()[0]);
		System.err.println(method.getGenericParameterTypes()[0] instanceof ParameterizedType);
		System.err.println(method.getGenericParameterTypes()[0].equals(List.class));
	}

	@Test
	public void testMethodType2() {
		StaffInfoVO staff = new StaffInfoVO();
		List<DataRange> list = new ArrayList<DataRange>();
		DataRange range = new DataRange();
		list.add(range);
		staff.setDataRangeList(list);
		Method method = BeanUtil.matchSetMethods(StaffInfoVO.class, "items")[0];
		System.err.println(method.getParameterTypes()[0]);
		System.err.println(method.getParameterTypes()[0].equals(List.class));
		System.err.println(method.getGenericParameterTypes()[0] instanceof ParameterizedType);
		System.err.println(method.getGenericParameterTypes()[0] == null);
		Object value = staff.getDataRangeList();
	}

	@Test
	public void testSetValue() {
		FieldSecureConfig config = new FieldSecureConfig("", null, null, null, 4, 10, 5);
		if (!SecureType.ENCRYPT.equals(config.getSecureType())) {
			System.err.println("[" + config.getSecureType() + "]");
		}
		if (!config.getSecureType().equals(SecureType.ENCRYPT)) {
			System.err.println("[" + config.getSecureType() + "]");
		}
	}

	@Test
	public void testSetValue1() {
		// 2024-11-07 10:52:36.12345
		DataRange dataRange = new DataRange();
		String lastUpdateTime = "2024-11-07 10:52:36.123454";

		BeanUtil.setProperty(dataRange, "lastUpdateTime", lastUpdateTime);

		DateUtil.parseLocalDateTime(lastUpdateTime, "yyyy-MM-dd HH:mm:ss.SSSSSS");
	}

	@Test
	public void testGetValues() {
		String jsonString = "[{\"name\":\"张三\",\"id\":\"10001\"},{\"name\":\"李四\",\"id\":\"10002\"}]";
		JSONArray jsonArray = JSON.parseArray(jsonString);
		System.err.println("jsonArray instanceof:" + (jsonArray instanceof List));
		Object[] resultObjects = BeanUtil.reflectBeanToAry(
				MapKit.keys("itemList", "staffId").values(jsonArray, "S0001"), "itemList.name", "staffId");

		System.err.println(JSON.toJSONString(resultObjects[0]));
	}

	@Test
	public void testFillSql() {
		String sql = "select * from where t.create_time BETWEEN ? AND ?";
		System.err.println(SqlExecuteStat.fitSqlParams(sql,
				new Object[] { LocalDateTime.now().plusDays(-10), LocalDateTime.now() }, DBType.ORACLE));
		sql = "select * from where t.create_time> ? AND t.create_time< ?";
		System.err.println(SqlExecuteStat.fitSqlParams(sql,
				new Object[] { LocalDateTime.now().plusDays(-10), LocalDateTime.now() }, DBType.ORACLE));
		sql = "select * from where t.create_time>= ? AND t.create_time< ?";
		System.err.println(SqlExecuteStat.fitSqlParams(sql,
				new Object[] { LocalDateTime.now().plusDays(-10), LocalDateTime.now() }, DBType.ORACLE));
		sql = "select * from where trunc(?,'year')>= 10 AND t.create_time< ?";
		System.err.println(SqlExecuteStat.fitSqlParams(sql,
				new Object[] { LocalDateTime.now().plusDays(-10), LocalDateTime.now() }, DBType.ORACLE));
	}

	@Test
	public void testRecord() {
		Student student = new Student("S0001", "chenrenfei", 24, LocalDate.now());
		System.err.println(JSON.toJSONString(BeanUtil.reflectBeanToAry(student, "age", "id")));
		List<Object[]> dataSet = new ArrayList();
		dataSet.add(new Object[] { "S0001", "chrenfei", 32, LocalDate.now() });
		dataSet.add(new Object[] { "S0002", "zhangsan", 31, LocalDate.now() });
		System.err.println(JSON.toJSONString(BeanUtil.reflectListToBean(null, dataSet,
				new String[] { "id", "name", "age", "birthDay", "tel" }, null, Student.class)));
	}

	// ==================== 并发重构方法测试 ====================

	@Test
	public void testGetEnumValue() {
		// 带getValue()的枚举 → 返回value值
		Assertions.assertEquals("address", BeanUtil.getEnumValue(MaskType.ADDRESS));
		Assertions.assertEquals("tel", BeanUtil.getEnumValue(MaskType.TEL));
		Assertions.assertEquals("name", BeanUtil.getEnumValue(MaskType.NAME));
		// null → 返回null
		Assertions.assertNull(BeanUtil.getEnumValue(null));
		// getValue()返回int类型的枚举
		Assertions.assertEquals(0, BeanUtil.getEnumValue(SaveMode.APPEND));
		Assertions.assertEquals(2, BeanUtil.getEnumValue(SaveMode.IGNORE));
	}

	@Test
	public void testGetEnumValuePlainEnum() {
		// 无自定义属性的普通枚举 → 返回name()
		Assertions.assertEquals("NEW", BeanUtil.getEnumValue(Thread.State.NEW));
		Assertions.assertEquals("RUNNABLE", BeanUtil.getEnumValue(Thread.State.RUNNABLE));
	}

	@Test
	public void testNewEnumInstance() {
		// 通过value匹配
		Assertions.assertEquals(MaskType.ADDRESS, BeanUtil.newEnumInstance("address", MaskType.class));
		Assertions.assertEquals(MaskType.TEL, BeanUtil.newEnumInstance("tel", MaskType.class));
		// 大小写不敏感匹配value
		Assertions.assertEquals(MaskType.ADDRESS, BeanUtil.newEnumInstance("ADDRESS", MaskType.class));
		// 通过int型value匹配
		Assertions.assertEquals(SaveMode.IGNORE, BeanUtil.newEnumInstance("2", SaveMode.class));
		Assertions.assertEquals(SaveMode.APPEND, BeanUtil.newEnumInstance("0", SaveMode.class));
		// null key → 返回null
		Assertions.assertNull(BeanUtil.newEnumInstance(null, MaskType.class));
		// 不存在的value → 返回null
		Assertions.assertNull(BeanUtil.newEnumInstance("xyz", MaskType.class));
	}

	@Test
	public void testNewEnumInstancePlainEnum() {
		// 无value方法的普通枚举 → 按name匹配
		Assertions.assertEquals(Thread.State.NEW, BeanUtil.newEnumInstance("NEW", Thread.State.class));
		Assertions.assertEquals(Thread.State.RUNNABLE, BeanUtil.newEnumInstance("runnable", Thread.State.class));
		// 不存在的name → 返回null
		Assertions.assertNull(BeanUtil.newEnumInstance("BLOCKED2", Thread.State.class));
	}

	@Test
	public void testSetProperty() {
		StaffInfoVO staff = new StaffInfoVO();
		// 类型完全一致
		BeanUtil.setProperty(staff, "staffId", "S001");
		Assertions.assertEquals("S001", staff.getStaffId());
		// 类型转换: String → Integer
		BeanUtil.setProperty(staff, "resType", "5");
		Assertions.assertEquals(Integer.valueOf(5), staff.getResType());
		// 类型转换: String → LocalDate
		DataRange dataRange = new DataRange();
		BeanUtil.setProperty(dataRange, "beginDate", "2024-01-15");
		Assertions.assertEquals(LocalDate.of(2024, 1, 15), dataRange.getBeginDate());
		// 类型转换: String → LocalDateTime
		BeanUtil.setProperty(dataRange, "lastUpdateTime", "2024-06-01 10:30:00");
		Assertions.assertEquals(LocalDateTime.of(2024, 6, 1, 10, 30, 0), dataRange.getLastUpdateTime());
	}

	@Test
	public void testSetPropertyInvalid() {
		StaffInfoVO staff = new StaffInfoVO();
		// 不存在的属性 → 抛出RuntimeException
		Assertions.assertThrows(RuntimeException.class, () -> {
			BeanUtil.setProperty(staff, "nonExistentField", "value");
		});
	}

	@Test
	public void testGetProperty() {
		StaffInfoVO staff = new StaffInfoVO();
		staff.setStaffId("S001");
		staff.setEmail("test@test.com");
		staff.setResType(3);
		// 获取存在的属性
		Assertions.assertEquals("S001", BeanUtil.getProperty(staff, "staffId"));
		Assertions.assertEquals("test@test.com", BeanUtil.getProperty(staff, "email"));
		Assertions.assertEquals(Integer.valueOf(3), BeanUtil.getProperty(staff, "resType"));
		// 不存在的属性 → 返回null
		Assertions.assertNull(BeanUtil.getProperty(staff, "nonExistentField"));
	}

	@Test
	public void testGetPropertyMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		Assertions.assertEquals("value1", BeanUtil.getProperty(map, "key1"));
		Assertions.assertNull(BeanUtil.getProperty(map, "missingKey"));
	}

	@Test
	public void testGetComplexProperty() {
		StaffInfoVO staff = new StaffInfoVO();
		staff.setStaffId("S001");
		List items = new ArrayList();
		items.add("item0");
		items.add("item1");
		staff.setItems(items);
		// 普通属性
		Assertions.assertEquals("S001", BeanUtil.getComplexProperty(staff, "staffId"));
		// 数组索引语法
		Assertions.assertEquals("item0", BeanUtil.getComplexProperty(staff, "items[0]"));
		Assertions.assertEquals("item1", BeanUtil.getComplexProperty(staff, "items[1]"));
		// 超出索引范围 → 返回null
		Assertions.assertNull(BeanUtil.getComplexProperty(staff, "items[5]"));
		// 不存在的属性 → 返回null
		Assertions.assertNull(BeanUtil.getComplexProperty(staff, "nonExistentField"));
	}

	@Test
	public void testGetComplexPropertyMap() {
		Map<String, Object> map = new HashMap<>();
		Object[] arr = { "a", "b", "c" };
		map.put("letters", arr);
		// Map + 数组索引
		Assertions.assertEquals("b", BeanUtil.getComplexProperty(map, "letters[1]"));
		Assertions.assertEquals("a", BeanUtil.getComplexProperty(map, "letters[0]"));
		// 超出范围 → null
		Assertions.assertNull(BeanUtil.getComplexProperty(map, "letters[10]"));
		// Map普通取值
		Assertions.assertArrayEquals(arr, (Object[]) BeanUtil.getComplexProperty(map, "letters"));
	}

	@Test
	public void testConcurrentAccess() throws Exception {
		int threadCount = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			final int idx = i;
			futures.add(CompletableFuture.runAsync(() -> {
				// getEnumValue
				Assertions.assertEquals("address", BeanUtil.getEnumValue(MaskType.ADDRESS));
				Assertions.assertEquals("NEW", BeanUtil.getEnumValue(Thread.State.NEW));
				// newEnumInstance
				Assertions.assertEquals(MaskType.TEL, BeanUtil.newEnumInstance("tel", MaskType.class));
				Assertions.assertEquals(Thread.State.NEW, BeanUtil.newEnumInstance("NEW", Thread.State.class));
				// setProperty
				StaffInfoVO s = new StaffInfoVO();
				BeanUtil.setProperty(s, "staffId", "S" + idx);
				BeanUtil.setProperty(s, "resType", String.valueOf(idx));
				// getProperty
				Assertions.assertEquals("S" + idx, BeanUtil.getProperty(s, "staffId"));
				Assertions.assertEquals(Integer.valueOf(idx), BeanUtil.getProperty(s, "resType"));
				// getComplexProperty
				List items = new ArrayList();
				items.add("x" + idx);
				s.setItems(items);
				Assertions.assertEquals("x" + idx, BeanUtil.getComplexProperty(s, "items[0]"));
				latch.countDown();
			}, pool));
		}
		// 如有任一线程抛异常,CompletableFuture.allOf会抛出ExecutionException
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
		pool.shutdown();
		pool.awaitTermination(10, TimeUnit.SECONDS);
		Assertions.assertEquals(0, latch.getCount());
	}

}
