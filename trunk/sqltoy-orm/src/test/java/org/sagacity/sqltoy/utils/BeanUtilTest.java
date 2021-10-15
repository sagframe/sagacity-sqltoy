package org.sagacity.sqltoy.utils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.demo.vo.DataRange;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.demo.vo.TypeShowCase;
import org.sagacity.sqltoy.model.IgnoreCaseLinkedMap;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;

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
		//System.err.println(BeanUtil.isBaseDataType(new HashMap()));
	}
}
