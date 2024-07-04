package org.sagacity.sqltoy.plugins.datasource;

import javax.sql.DataSource;

import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 提供给开发者扩展获得数据源的方法
 * @author zhongxuchen
 * @version v1.0,Date:2021年4月13日
 */
public interface DataSourceSelector {
	/**
	 * @TODO 选择dataSource
	 * @param appContext        spring上下文
	 * @param pointDataSouce    方法调用时直接传递的数据源
	 * @param sqlDataSourceName sql中指定的数据源名称
	 * @param injectDataSource  dao中自动注入的数据源
	 * @param defaultDataSource sqltoy 默认的数据源
	 * @return
	 */
	public DataSource getDataSource(AppContext appContext, DataSource pointDataSouce, String sqlDataSourceName,
			DataSource injectDataSource, DataSource defaultDataSource);

	/**
	 * @TODO 提供通过名称获得数据库实例的扩展，便于一些dataSource插件特殊的封装方式无法用spring的getBean直接获得
	 * @param appContext
	 * @param dataSourceName
	 * @return
	 */
	public default DataSource getDataSourceBean(AppContext appContext, String dataSourceName) {
		if (StringUtil.isBlank(dataSourceName)) {
			return null;
		}
		if (appContext != null && appContext.containsBean(dataSourceName)) {
			return (DataSource) appContext.getBean(dataSourceName);
		}
		return null;
	}
}
