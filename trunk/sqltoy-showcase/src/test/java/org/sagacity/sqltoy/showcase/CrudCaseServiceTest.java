/**
 * 
 */
package org.sagacity.sqltoy.showcase;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.SqlToyApplication;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.showcase.vo.StaffInfoVO;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.ShowCaseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @project sqltoy-showcase
 * @description 普通的CRUD操作演示，无需额外写service方法，由sqltoy提供SqlToyCRUDServcie提供默认的操作即可
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CrudCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class CrudCaseServiceTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	@Resource(name = "sqlToyLazyDaoShard1")
	private SqlToyLazyDao sqlToyLazyDaoShard1;
	
	@Resource(name = "sqlToyLazyDaoShard2")
	private SqlToyLazyDao sqlToyLazyDaoShard2;

	/**
	 * 创建一条员工记录
	 */
	@Test
	public void saveStaffInfo() {
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
		sqlToyCRUDService.saveOrUpdate(staffInfo);
		
		sqlToyLazyDaoShard1.saveOrUpdate(staffInfo);
		
		sqlToyLazyDaoShard2.saveOrUpdate(staffInfo);
	}

	/**
	 * 修改员工记录信息
	 */
	@Test
	public void updateStaffInfo() {
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
		sqlToyCRUDService.update(staffInfo);
	}

	/**
	 * 对员工信息特定字段进行强制修改
	 */
	@Test
	public void forceUpdate() {

	}

	@Test
	public void saveOrUpdate() {

	}

	@Test
	public void saveOrUpdateAll() {

	}

	@Test
	public void saveAll() {

	}

	@Test
	public void delete() {

	}

	@Test
	public void deleteAll() {

	}

	@Test
	public void load() {

	}

	@Test
	public void loadAll() {

	}
}
