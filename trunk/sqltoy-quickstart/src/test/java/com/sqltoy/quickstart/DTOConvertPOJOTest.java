/**
 * 
 */
package com.sqltoy.quickstart;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sqltoy.quickstart.dto.StaffInfoVO;
import com.sqltoy.quickstart.pojo.StaffInfo;

/**
 * @project sqltoy-quickstart
 * @description 提供POJO和DTO相互转化的范例,用于接口服务类项目严格划分VO(DTO) 和POJO场景下
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020-8-10
 * @modify 2020-8-10,修改说明
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class DTOConvertPOJOTest {
	// 在SqlToyCRUDService和sqlToyLazyDao 以及SqlToySupportDao里面都有提供
	//
	@Autowired
	SqlToyLazyDao sqlToyLazyDao;

	@Test
	public void testVOToPO() {
		StaffInfoVO staffInfoVO = new StaffInfoVO();
		staffInfoVO.setStaffId("S2007");
		staffInfoVO.setStaffCode("S2007");
		staffInfoVO.setPostType("MASTER");
		staffInfoVO.setStaffName("测试员工9");
		staffInfoVO.setSexType("M");
		staffInfoVO.setEmail("test3@aliyun.com");
		staffInfoVO.setEntryDate(LocalDate.now());
		staffInfoVO.setStatus(1);
		staffInfoVO.setOrganId("100007");
		// staffInfoVO.setPhoto(FileUtil.readAsBytes("classpath:/mock/staff_photo.jpg"));
		staffInfoVO.setCountry("86");
		try {
			StaffInfo staffInfo = sqlToyLazyDao.convertType(staffInfoVO, StaffInfo.class);
			System.err.println(JSON.toJSONString(staffInfo));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Test
	public void testPOTOVO() {
		StaffInfo staffInfo = new StaffInfo();
		staffInfo.setStaffId("S2007");
		staffInfo.setStaffCode("S2007");
		staffInfo.setPost("MASTER");
		staffInfo.setStaffName("测试员工9");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test3@aliyun.com");
		staffInfo.setEntryDate(LocalDate.now());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("100007");
		// staffInfoVO.setPhoto(FileUtil.readAsBytes("classpath:/mock/staff_photo.jpg"));
		staffInfo.setCountry("86");
		try {
			StaffInfoVO staffInfoVO = sqlToyLazyDao.convertType(staffInfo, StaffInfoVO.class);
			System.err.println(JSON.toJSONString(staffInfoVO));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Test
	public void testVOToPOList() {
		List staffVOs = new ArrayList();
		StaffInfoVO staffInfoVO = new StaffInfoVO();
		staffInfoVO.setStaffId("S2007");
		staffInfoVO.setStaffCode("S2007");
		staffInfoVO.setPostType("MASTER");
		staffInfoVO.setStaffName("测试员工9");
		staffInfoVO.setSexType("M");
		staffInfoVO.setEmail("test3@aliyun.com");
		staffInfoVO.setEntryDate(LocalDate.now());
		staffInfoVO.setStatus(1);
		staffInfoVO.setOrganId("100007");
		staffInfoVO.setCountry("86");

		StaffInfoVO staffInfoVO1 = new StaffInfoVO();
		staffInfoVO1.setStaffId("S2008");
		staffInfoVO1.setStaffCode("S2008");
		staffInfoVO1.setPostType("MASTER");
		staffInfoVO1.setStaffName("测试员工8");
		staffInfoVO1.setSexType("M");
		staffInfoVO1.setEmail("test8@aliyun.com");
		staffInfoVO1.setEntryDate(LocalDate.now());
		staffInfoVO1.setStatus(1);
		staffInfoVO1.setOrganId("100008");
		staffInfoVO1.setCountry("86");
		staffVOs.add(staffInfoVO);
		staffVOs.add(staffInfoVO1);
		try {
			List<StaffInfo> staffInfos = sqlToyLazyDao.convertType(staffVOs, StaffInfo.class);
			System.err.println(JSON.toJSONString(staffInfos));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
