package org.sagacity.sqltoy.demo;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;
import org.sagacity.sqltoy.demo.domain.StaffInfo;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.utils.DBTransUtils;

import com.alibaba.druid.pool.DruidDataSourceFactory;

public class DBServiceTest {
	public void doDB() {
		try {
			SqlToyContext sqlToyContext = new SqlToyContext();
			sqlToyContext.setSqlResourcesDir("classpath:sqltoy/demo.sql.xml");
			sqlToyContext.initialize();
			Map<String, String> map = new HashMap<>();
			map.put(DruidDataSourceFactory.PROP_URL, "jdbc:mysql://10.10.10.134:3306/java20");
			// 设置驱动Driver
			map.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, "com.mysql.jdbc.Driver");
			// 设置用户名
			map.put(DruidDataSourceFactory.PROP_USERNAME, "root");
			// 设置密码
			map.put(DruidDataSourceFactory.PROP_PASSWORD, "123456");
			// 创建数据源
			DataSource dataSource = DruidDataSourceFactory.createDataSource(map);
			sqlToyContext.setDefaultDataSource(dataSource);
			// 框架默认提供了DefaultLightDaoImpl实现
			LightDao lightDao = new DefaultLightDaoImpl(sqlToyContext);
			// 非事务
			// lightDao.find("select * from staff_info where status=:status",
			// MapKit.map("status", "1"), StaffInfo.class);
			// 这里都是示意，请按实际逻辑编写
			Object result = DBTransUtils.doTrans(lightDao.getDataSource(), () -> {
				// 这里可以
				lightDao.updateByQuery(StaffInfo.class,
						EntityUpdate.create().set("sexType", "F").where("staffId=?").values("S0001"));
				return lightDao.find("select * from staff_info where status=:status", MapKit.map("status", "1"),
						StaffInfo.class);
			});
		} catch (Exception e) {

		}
	}
}
