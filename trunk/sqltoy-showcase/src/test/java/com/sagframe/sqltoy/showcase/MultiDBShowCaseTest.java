/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import javax.annotation.Resource;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.model.SaveMode;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.StaffInfoVO;
import com.sagframe.sqltoy.utils.ShowCaseUtils;

/**
 * @project sqltoy-showcase
 * @description 多数据库同时交互演示范例
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:MultiDBShowCaseTest.java,Revision:v1.0,Date:2019年7月23日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class MultiDBShowCaseTest {
	@Resource(name = "sqlToyLazyDao")
	private SqlToyLazyDao sqlToyLazyDao;

	@Resource(name = "sqlToyLazyDaoShard1")
	private SqlToyLazyDao sqlToyLazyDaoShard1;

	@Resource(name = "sqlToyLazyDaoShard2")
	private SqlToyLazyDao sqlToyLazyDaoShard2;

	@Resource(name = "sharding1")
	private DataSource sharding1;

	@Resource(name = "sharding2")
	private DataSource sharding2;

	/**
	 * 创建一条员工记录
	 */
	// 项目中涉及多数据库场景的应用模式:通过定义多个lazyDao模式
	@Test
	public void saveStaffInfoByLazyDao() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715001");
		staffInfo.setStaffCode("S190715001");
		staffInfo.setStaffName("测试员工");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test@aliyun.com");
		staffInfo.setEntryDate(DateUtil.getDate());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		sqlToyLazyDao.saveOrUpdate(staffInfo);
		sqlToyLazyDaoShard1.saveOrUpdate(staffInfo);
		sqlToyLazyDaoShard2.saveOrUpdate(staffInfo);
	}

	/**
	 * 演示多数据库场景应用模式:通过链式操作直接传递数据库
	 */
	@Test
	public void saveStaffInfoByDB() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715001");
		staffInfo.setStaffCode("S190715001");
		staffInfo.setStaffName("测试员工");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test@aliyun.com");
		staffInfo.setEntryDate(DateUtil.getDate());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		sqlToyLazyDao.saveOrUpdate(staffInfo);
		sqlToyLazyDao.save().dataSource(sharding1).saveMode(SaveMode.UPDATE).one(staffInfo);
		sqlToyLazyDao.save().dataSource(sharding2).saveMode(SaveMode.UPDATE).one(staffInfo);
		// 多条记录,SaveMode 设置当记录存在时的是ignore还是update
		// sqlToyLazyDao.save().dataSource(sharding1).forceUpdateProps(new String[]
		// {""}).saveMode(SaveMode.UPDATE).many(entities);
	}
}
