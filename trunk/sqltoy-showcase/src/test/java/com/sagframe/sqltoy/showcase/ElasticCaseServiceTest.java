/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.CompanyInfoVO;

/**
 * @project sqltoy-showcase
 * @description 演示elasticsearch5.x+ 版本的使用
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ElasticCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class ElasticCaseServiceTest {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	/**
	 * 演示普通的查询
	 */
	@Test
	public void testSearch() {
		String sql = "es_find_company";
		List<CompanyInfoVO> result = (List<CompanyInfoVO>) sqlToyLazyDao.elastic().sql(sql)
				.resultType(CompanyInfoVO.class).find();
		for (CompanyInfoVO company : result) {
			System.err.println(JSON.toJSONString(company));
		}
	}

	/**
	 * 演示分页查询
	 */
	// @Test
	// public void testFindPage() {
	//
	// }
}
