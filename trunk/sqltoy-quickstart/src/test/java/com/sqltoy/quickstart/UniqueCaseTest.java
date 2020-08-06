/**
 * 
 */
package com.sqltoy.quickstart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * @project sqltoy-quickstart
 * @description 演示唯一性验证
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020-8-6
 * @modify 2020-8-6,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class UniqueCaseTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;
	
	// 演示之前需要先执行InitDataBaseTest中的初始化数据库
	// 如果是复核字段验证唯一性，用法举例即传多个属性名称
	// sqlToyCRUDService.isUnique(staffInfo, "staffCode","sexType");

	/*
	    *   唯一性验证的原理:
	 * 1、根据验证属性去数据库检索
	 * 2、当返回0条表示数据库中不存在，直接返回true
	 * 3、当>1,表示多条直接返回false (说明有脏数据)
	 * 4、当=1，主键值不同则表示已经存在返回false，当主键值相同表示是对自身修改返回true
	 */
	/**
	 * 存在但是主键值一致，表示对现有记录进行修改，返回true
	 */
	@Test
	public void testUniqueTrue() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S0006");
		staffInfo.setStaffCode("S0006");
		Boolean result = sqlToyCRUDService.isUnique(staffInfo, "staffCode");
		System.err.println(result);
	}

	/**
	 * 完全不存在，返回true
	 */
	@Test
	public void testUniqueTrue1() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		// staffInfo.setStaffId("S0006");
		staffInfo.setStaffCode("S0016");
		Boolean result = sqlToyCRUDService.isUnique(staffInfo, "staffCode");
		System.err.println(result);
	}

	/**
	 * 无主键值，表示新增记录，数据存在则表示重复，返回false
	 */
	@Test
	public void testUniqueFalse() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffCode("S0006");
		Boolean result = sqlToyCRUDService.isUnique(staffInfo, "staffCode");
		System.err.println(result);
	}
}
