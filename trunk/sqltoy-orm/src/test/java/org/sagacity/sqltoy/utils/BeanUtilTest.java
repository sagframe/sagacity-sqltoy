package org.sagacity.sqltoy.utils;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.demo.vo.DataRange;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;

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

		Object[] result = BeanUtil.reflectBeanToAry(staff,
				new String[] { "staffId", "email", "dataRange.beginDate", "dataRange.enddate" }, null, null);
		for (Object tmp : result) {
			System.err.println(tmp);
		}
	}
}
