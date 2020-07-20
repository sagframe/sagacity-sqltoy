package com.sqltoy.quickstart;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.model.PaginationModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.service.StaffInfoService;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * 
 * @project sqltoy-quickstart
 * @description 通过员工信息表来演示常规的crud
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月17日
 * @modify 2020年7月17日,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class StaffInfoServiceTest {

	@Autowired
	StaffInfoService staffInfoService;

	@Test
	public void testSave() {

	}

	@Test
	public void testUpdate() {

	}

	@Test
	public void testSaveOrUpdate() {

	}

	@Test
	public void testQueryStaff() {
		PaginationModel pageModel = new PaginationModel();
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setBeginDate(LocalDate.parse("2019-01-01"));
		staffInfo.setEndDate(LocalDate.now());
		staffInfo.setStaffName("陈");
		PaginationModel<StaffInfoVO> result = staffInfoService.queryStaff(pageModel, staffInfo);
		for (StaffInfoVO row : result.getRows()) {
			System.err.println(JSON.toJSONString(row));
		}
	}
}
