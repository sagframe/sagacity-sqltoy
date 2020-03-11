/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.model.PaginationModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.CompanyInfoVO;

/**
 * @project sqltoy-showcase
 * @description 演示elasticsearch5.x+ 版本的使用,sql请安装elasticsearch-sql插件
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ElasticCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class ElasticCaseServiceTest {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	/**
	 * 演示普通的查询
	 */
	@Test
	public void testSqlSearch() {
		// elasticsearch-sql https://github.com/NLPchina/elasticsearch-sql
		String sql = "es_find_company";
		List<CompanyInfoVO> result = (List<CompanyInfoVO>) sqlToyLazyDao.elastic().sql(sql)
				.resultType(CompanyInfoVO.class).find();
		for (CompanyInfoVO company : result) {
			System.err.println(JSON.toJSONString(company));
		}
	}

	/**
	 * 演示分页查询，基于sql分页请使用elasticsearch-sql插件
	 */
	@Test
	public void testSqlFindPage() {
		// elasticsearch-sql https://github.com/NLPchina/elasticsearch-sql
		String sql = "es_find_company_page";
		PaginationModel pageModel = new PaginationModel();
		PaginationModel result = (PaginationModel) sqlToyLazyDao.elastic().sql(sql).resultType(CompanyInfoVO.class)
				.findPage(pageModel);
		System.err.println("resultCount=" + result.getRecordCount());
		for (CompanyInfoVO company : (List<CompanyInfoVO>) result.getRows()) {
			System.err.println(JSON.toJSONString(company));
		}
	}

	@Test
	public void testJsonSearch() {
		String sql = "sys_elastic_test_json";
		String[] paramNames = { "companyTypes" };
		Object[] paramValues = { new Object[] { "1", "2" } };

		List<CompanyInfoVO> result = (List<CompanyInfoVO>) sqlToyLazyDao.elastic().sql(sql).names(paramNames)
				.values(paramValues).resultType(CompanyInfoVO.class).find();
		for (CompanyInfoVO company : result) {
			System.err.println(JSON.toJSONString(company));
		}
	}

	@Test
	public void testJsonFindPage() {
		String sql = "sys_elastic_test_json";
		String[] paramNames = { "companyTypes" };
		Object[] paramValues = { new Object[] { "1", "2" } };
		PaginationModel pageModel = new PaginationModel();
		PaginationModel result = (PaginationModel) sqlToyLazyDao.elastic().sql(sql).names(paramNames)
				.values(paramValues).resultType(CompanyInfoVO.class).findPage(pageModel);
		System.err.println("resultCount=" + result.getRecordCount());
		for (CompanyInfoVO company : (List<CompanyInfoVO>) result.getRows()) {
			System.err.println(JSON.toJSONString(company));
		}
	}
}
