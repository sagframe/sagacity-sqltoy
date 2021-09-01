package org.sagacity.sqltoy.utils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.demo.vo.DataRange;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
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
		Map map=new IgnoreKeyCaseMap();
		map.put("staff", staff);
		Object[] result = BeanUtil.reflectBeanToAry(map, new String[] { "staff.staffId", "staff.email", "staff.dataRange.beginDate",
				"staff.dataRange.enddate", "staff.params.companyId", "staff.params.companyName" }, null, null);
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
		IgnoreKeyCaseMap map=new IgnoreKeyCaseMap(params);
		System.err.println(((Map)map).get("companyId"));
	}
}
