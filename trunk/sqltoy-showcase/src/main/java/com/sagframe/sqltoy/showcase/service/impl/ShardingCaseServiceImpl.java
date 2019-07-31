/**
 * 
 */
package com.sagframe.sqltoy.showcase.service.impl;

import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import com.sagframe.sqltoy.showcase.service.ShardingCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @project sqltoy-boot-showcase
 * @description 分库分表操作演示
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingCaseServiceImpl.java,Revision:v1.0,Date:2019年7月15日
 */
@Service("shardingCaseService")
public class ShardingCaseServiceImpl implements ShardingCaseService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;
	
}
