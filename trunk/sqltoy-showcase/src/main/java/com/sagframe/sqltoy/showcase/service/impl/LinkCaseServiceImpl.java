/**
 * 
 */
package com.sagframe.sqltoy.showcase.service.impl;

import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import com.sagframe.sqltoy.showcase.service.LinkCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @project sqltoy-showcase
 * @description 链式操作演示
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:LinkCaseServiceImpl.java,Revision:v1.0,Date:2019年7月11日
 */
@Service("linkCaseService")
public class LinkCaseServiceImpl implements LinkCaseService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;
}
