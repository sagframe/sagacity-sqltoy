package org.sagacity.sqltoy.plugins.datasource.impl;

import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.plugins.datasource.ObtainDataSource;
import org.springframework.context.ApplicationContext;

/**
 * @project sqltoy-orm
 * @description 提供默认获取数据源的策略(单一数据源直接返回)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DefaultObtainDataSource.java,Revision:v1.0,Date:2020年7月11日
 */
public class DefaultObtainDataSource implements ObtainDataSource {
	private DataSource dataSource;

	@Override
	public DataSource getDataSource(ApplicationContext applicationContext, DataSource defaultDataSource) {
		// 避免每次去查找(适用于固定数据源场景)
		if (this.dataSource != null) {
			return this.dataSource;
		}
		Map<String, DataSource> result = applicationContext.getBeansOfType(DataSource.class);
		// 只有一个dataSource,直接使用
		if (result.size() == 1) {
			this.dataSource = result.values().iterator().next();
		}
		// 非单一数据源,通过sqltoyContext中定义的默认dataSource来获取
		if (this.dataSource == null) {
			this.dataSource = defaultDataSource;
		}
		// 理论上应该先获取primary的数据源,目前不知道如何获取
		// 多数据源情况下没有指定默认dataSource则返回名称为dataSource的数据源
		if (this.dataSource == null && applicationContext.containsBean("dataSource")) {
			this.dataSource = (DataSource) applicationContext.getBean("dataSource");
		}
		return this.dataSource;
	}

}
