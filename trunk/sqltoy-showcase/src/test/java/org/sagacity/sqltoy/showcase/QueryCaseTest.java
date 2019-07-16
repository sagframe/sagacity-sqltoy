/**
 * 
 */
package org.sagacity.sqltoy.showcase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.SqlToyApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @project sqltoy-boot-showcase
 * @description 演示sql查询
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:QueryCaseTest.java,Revision:v1.0,Date:2019年7月15日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class QueryCaseTest {

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

	}

	/**
	 * 分页查询
	 */
	@Test
	public void findPage() {

	}

	/**
	 * 查询随机记录
	 */
	@Test
	public void findByRandom() {

	}
}
