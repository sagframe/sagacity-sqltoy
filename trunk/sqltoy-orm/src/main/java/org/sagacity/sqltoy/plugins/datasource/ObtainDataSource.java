/**
 * 
 */
package org.sagacity.sqltoy.plugins.datasource;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;

/**
 * @project sqltoy-orm
 * @description 提供一个由开发者自定义为Dao获取dataSource的策略扩展,便于极端场景下(多个数据源)问题应对
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version v1.0,Date:2020年7月11日
 */
public interface ObtainDataSource {
	/**
	 * @TOOD 提供给用户自行扩展获取数据源
	 * @param applicationContext
	 * @param defaultDataSource  sqlToyContext 上配置的默认数据源
	 * @param sqlDataSource      sqlId上配置的数据源名称，预览备用
	 * @return
	 */
	public DataSource getDataSource(ApplicationContext applicationContext, DataSource defaultDataSource,
			String sqlDataSource);
}
