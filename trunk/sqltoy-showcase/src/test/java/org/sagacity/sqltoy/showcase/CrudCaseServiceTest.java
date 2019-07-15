/**
 * 
 */
package org.sagacity.sqltoy.showcase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.SqlToyApplication;
import org.sagacity.sqltoy.showcase.service.CrudCaseService;
import org.sagacity.sqltoy.showcase.vo.StaffInfoVO;
import org.sagacity.sqltoy.utils.ShowCaseUtils;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @project sqltoy-showcase
 * @description 请在此说明类的功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CrudCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class CrudCaseServiceTest {
	@Autowired
	private CrudCaseService crudCaseService;

	@Test
	public void saveStaffInfo() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffCode("S190715001");
		staffInfo.setStaffName("测试员工");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test@aliyun.com");
		staffInfo.setEntryDate(DateUtil.getNowTime());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		crudCaseService.saveStaffInfo(staffInfo);
	}
}
