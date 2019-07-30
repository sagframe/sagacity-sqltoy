/**
 * 
 */
package com.sagframe.sqltoy.showcase.service.impl;

import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import com.sagframe.sqltoy.showcase.service.ElasticCaseService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @project sqltoy-boot-showcase
 * @description 演示elasticsearch 的查询操作
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ElasticCaseServiceImpl.java,Revision:v1.0,Date:2019年7月15日
 */
public class ElasticCaseServiceImpl implements ElasticCaseService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;
}
