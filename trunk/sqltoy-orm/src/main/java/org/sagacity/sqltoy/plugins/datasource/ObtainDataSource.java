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
 * @version id:AbstractGetDataSource.java,Revision:v1.0,Date:2020年7月11日
 */
public interface ObtainDataSource {
	public DataSource getDataSource(ApplicationContext applicationContext, DataSource defaultDataSource);
}
