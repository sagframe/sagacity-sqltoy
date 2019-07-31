/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.StaffInfoVO;
import com.sagframe.sqltoy.utils.ShowCaseUtils;

/**
 * @project sqltoy-showcase
 * @description 请在此说明类的功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class ShardingCaseServiceTest {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	@Test
	public void testHashSharding() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715001");
		staffInfo.setStaffCode("S190715001");
		staffInfo.setStaffName("测试员工");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test@aliyun.com");
		staffInfo.setEntryDate(DateUtil.getNowTime());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		sqlToyLazyDao.saveOrUpdate(staffInfo);
		
		StaffInfoVO staffInfo2 = new StaffInfoVO();
		staffInfo.setStaffId("S190715002");
		staffInfo.setStaffCode("S190715002");
		staffInfo.setStaffName("测试员工2");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test1@aliyun.com");
		staffInfo.setEntryDate(DateUtil.getNowTime());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		sqlToyLazyDao.saveOrUpdate(staffInfo2);
	}
}
