package org.sagacity.sqltoy.plugins.datasource;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;

/**
 * @project sqltoy-orm
 * @description 提供给开发者扩展获得数据源的方法
 * @author zhongxuchen
 * @version v1.0,Date:2021年4月13日
 */
public interface DataSourceSelector {
	/**
	 * @TODO 选择dataSource
	 * @param applicationContext spring上下文
	 * @param pointDataSouce 方法调用时直接传递的数据源
	 * @param sqlDataSourceName sql中指定的数据源名称
	 * @param injectDataSource  dao中自动注入的数据源
	 * @param defaultDataSource sqltoy 默认的数据源
	 * @return
	 */
	public DataSource getDataSource(ApplicationContext applicationContext, DataSource pointDataSouce,
			String sqlDataSourceName, DataSource injectDataSource, DataSource defaultDataSource);
}
