/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.sql.Connection;
import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.utils.ShowCaseUtils;

/**
 * @project sqltoy-boot-showcase
 * @description 演示sql查询
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:QueryCaseTest.java,Revision:v1.0,Date:2019年7月15日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class QueryCaseTest {
	@Resource(name = "sqlToyLazyDao")
	private SqlToyLazyDao sqlToyLazyDao;

	/**
	 * 加载数据字典、订单等模拟数据sql文件
	 */
	@Before
	public void initData() {
		final String sqlContent = ShowCaseUtils.loadFile("classpath:/initDataSql.sql", "UTF-8");
		if (StringUtil.isBlank(sqlContent))
			return;
		DataSourceUtils.processDataSource(sqlToyLazyDao.getSqlToyContext(), sqlToyLazyDao.getDataSource(),
				new DataSourceCallbackHandler() {
					@Override
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// executeBatchSql可以根据数据库类型将大的sql字符进行分割循环执行
						SqlUtil.executeBatchSql(conn, sqlContent, 100, null);
					}
				});
	}

	/**
	 * 根据对象加载数据
	 */
	@Test
	public void loadByEntity() {

	}

	/**
	 * 普通sql加载对象
	 */
	@Test
	public void loadBySql() {

	}

	/**
	 * 唯一性验证
	 */
	@Test
	public void unique() {

	}

	/**
	 * 普通sql查询
	 */
	@Test
	public void findBySql() {
		List result = (List) sqlToyLazyDao.findBySql("biz_test", null);
		System.err.println(JSON.toJSONString(result));
	}

	/**
	 * 分页查询
	 */
	@Test
	public void findPage() {

	}

	/**
	 * 取前多少条记录
	 */
	@Test
	public void findTop() {

	}

	/**
	 * 查询随机记录
	 */
	@Test
	public void findByRandom() {

	}
}
