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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.FieldSecureConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.demo.domain.DeviceOrderVO;
import org.sagacity.sqltoy.demo.domain.StaffInfo;
import org.sagacity.sqltoy.demo.vo.DataRange;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
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
		String lastUpdateTime = "2024-11-07 10:52:36.12345";

		BeanUtil.setProperty(dataRange, "lastUpdateTime", lastUpdateTime);

		DateUtil.parseLocalDateTime(lastUpdateTime, "yyyyMMdd HHmmss.SSSSSS");
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
}
