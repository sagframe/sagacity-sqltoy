/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import javax.annotation.Resource;

import org.junit.runner.RunWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.sagframe.sqltoy.SqlToyApplication;

/**
 * @project sqltoy-showcase
 * @description 多数据库同时交互演示范例
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:MultiDBShowCaseTest.java,Revision:v1.0,Date:2019年7月23日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class MultiDBShowCaseTest {
	@Resource(name = "sqlToyLazyDao")
	private SqlToyLazyDao sqlToyLazyDao;

	@Resource(name = "sqlToyLazyDaoShard1")
	private SqlToyLazyDao sqlToyLazyDaoShard1;

	@Resource(name = "sqlToyLazyDaoShard2")
	private SqlToyLazyDao sqlToyLazyDaoShard2;
}
