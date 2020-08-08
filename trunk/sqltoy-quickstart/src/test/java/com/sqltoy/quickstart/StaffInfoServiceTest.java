package com.sqltoy.quickstart;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.service.StaffInfoService;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
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

	@Autowired
	SqlToyCRUDService sqlToyCRUDService;

	// 常规情况下基于对象的操作也无需写service,统一使用SqlToyCRUDService即可
	// 因为常规对象操作即使自己写Service也是简单的转调
	@Test
	public void testSave() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S2007");
		staffInfo.setStaffCode("S2007");
		staffInfo.setStaffName("测试员工9");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test3@aliyun.com");
		staffInfo.setEntryDate(LocalDate.now());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("100007");
		staffInfo.setPhoto(FileUtil.readAsBytes("classpath:/mock/staff_photo.jpg"));
		staffInfo.setCountry("86");
		sqlToyCRUDService.save(staffInfo);
	}

	// sqltoy的update操作比较具有特色,字段值为null的不会参与变更,一次性交互精准变更，适合高并发场景避免先做load
	// 如需要对具体字段进行强制变更,则需通过forceUpdateProps来定义
	@Test
	public void testUpdate() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S2007");
		staffInfo.setEmail("test07@139.com");
		// 这里对照片进行强制修改
		sqlToyCRUDService.update(staffInfo, "photo");
	}

	// 既然update 不同于hibernate模式，那要实现hibernate模式的怎么做?
	// updateDeeply 就是深度修改的意思
	@Test
	public void testUpdateDeeply() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S2007");
		staffInfo.setEmail("test07@139.com");
		// 这里对照片进行强制修改
		sqlToyCRUDService.updateDeeply(staffInfo);
	}

	@Test
	public void testSaveOrUpdate() {
		List<StaffInfoVO> staffList = new ArrayList<StaffInfoVO>();
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S2007");
		staffInfo.setStaffCode("S2007");
		staffInfo.setStaffName("测试员工7");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test8@139.com");

		StaffInfoVO staffInfo1 = new StaffInfoVO();
		staffInfo1.setStaffId("S2008");
		staffInfo1.setStaffCode("S2008");
		staffInfo1.setStaffName("测试员工8");
		staffInfo1.setSexType("M");
		staffInfo1.setEmail("test8@aliyun.com");
		staffInfo1.setEntryDate(LocalDate.now());
		staffInfo1.setStatus(1);
		staffInfo1.setOrganId("100007");
		staffInfo1.setPhoto(FileUtil.readAsBytes("classpath:/mock/staff_photo.jpg"));
		staffInfo1.setCountry("86");

		staffList.add(staffInfo);
		staffList.add(staffInfo1);
		sqlToyCRUDService.saveOrUpdateAll(staffList);
	}

	@Test
	public void testDelete() {
		sqlToyCRUDService.delete(new StaffInfoVO("S2007"));
	}
	
	@Test
	public void testLoad() {
		StaffInfoVO staff=sqlToyCRUDService.load(new StaffInfoVO("S2007"));
	}

	@Test
	public void testQueryStaff() {
		PaginationModel pageModel = new PaginationModel();
		// 正常需设置pageNo和pageSize,默认值分别为1和10
		// pageModel.setPageNo(1);
		// pageModel.setPageSize(10);
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setBeginDate(LocalDate.parse("2019-01-01"));
		staffInfo.setEndDate(LocalDate.now());
		staffInfo.setStaffName("陈");
		PaginationModel<StaffInfoVO> result = staffInfoService.queryStaff(pageModel, staffInfo);
		for (StaffInfoVO row : result.getRows()) {
			System.err.println(JSON.toJSONString(row));
		}
	}

	// 演示updateFetch用法，同时也穿插了一个QueryExecutor中动态设置缓存翻译的示例
	// 1、查询取数据并锁定
	// 2、提取出来做逻辑判断
	// 3、对记录进行更新
	// 4、将更新后的结果返回
	@Test
	public void testUpdateFetch() {
		List<StaffInfoVO> result = staffInfoService.updateFetch();
		for (StaffInfoVO row : result) {
			System.err.println(JSON.toJSONString(row));
		}
	}
}
