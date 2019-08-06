/**
 * 
 */
package com.sagframe.sqltoy.showcase.service.impl;

import java.sql.Connection;

import javax.annotation.Resource;

import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.stereotype.Service;

import com.sagframe.sqltoy.showcase.service.InitDataService;
import com.sagframe.sqltoy.utils.ShowCaseUtils;

/**
 * @project sqltoy-showcase
 * @description 数据初始化
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:InitDataServiceImpl.java,Revision:v1.0,Date:2019年8月6日
 */
@Service("initDataService")
public class InitDataServiceImpl implements InitDataService {

	@Resource(name = "sqlToyLazyDao")
	private SqlToyLazyDao sqlToyLazyDao;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sagframe.sqltoy.showcase.service.InitDataService#createData(java.lang.
	 * String)
	 */
	@Override
	public void createData(String dataSqlFile) {
		// TODO Auto-generated method stub
		// 加载初始化数据脚本(最好手工执行数据初始化,便于演示缓存翻译功能)
		String realFile = StringUtil.isBlank(dataSqlFile) ? "classpath:/mock/initDataSql.sql" : dataSqlFile;
		final String sqlContent = ShowCaseUtils.loadFile(realFile, "UTF-8");
		if (StringUtil.isBlank(sqlContent))
			return;
		DataSourceUtils.processDataSource(sqlToyLazyDao.getSqlToyContext(), sqlToyLazyDao.getDataSource(),
				new DataSourceCallbackHandler() {
					@Override
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						// executeBatchSql可以根据数据库类型将大的sql字符进行分割循环执行
						SqlUtil.executeBatchSql(conn, sqlContent, 100, true);
					}
				});
	}

}
