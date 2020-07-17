/**
 * 
 */
package com.sqltoy.quickstart.service.impl;

import java.sql.Connection;

import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sqltoy.quickstart.service.InitDBService;

/**
 * @author zhong
 *
 */
@Service("initDBService")
public class InitDBServiceImpl implements InitDBService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	@Override
	public void initDatabase(String dataSqlFile) {
		// TODO Auto-generated method stub
		// 加载初始化数据脚本(最好手工执行数据初始化,便于演示缓存翻译功能)
		final String sqlContent = FileUtil.readFileAsString(dataSqlFile, "UTF-8");
		if (StringUtil.isBlank(sqlContent)) {
			return;
		}
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
