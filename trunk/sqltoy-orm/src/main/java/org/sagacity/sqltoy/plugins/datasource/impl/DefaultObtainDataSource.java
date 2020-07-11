package org.sagacity.sqltoy.plugins.datasource.impl;

import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.plugins.datasource.ObtainDataSource;
import org.springframework.context.ApplicationContext;

public class DefaultObtainDataSource implements ObtainDataSource {
	private DataSource dataSource;

	@Override
	public DataSource getDataSource(ApplicationContext applicationContext, DataSource defaultDataSource) {
		// 避免每次去查找
		if (this.dataSource != null) {
			return this.dataSource;
		}
		Map<String, DataSource> result = applicationContext.getBeansOfType(DataSource.class);
		// 只有一个dataSource,直接使用
		if (result.size() == 1) {
			this.dataSource = result.values().iterator().next();
		}
		// 通过sqltoyContext中定义的默认dataSource来获取
		if (this.dataSource == null) {
			this.dataSource = defaultDataSource;
		}
		if (this.dataSource == null && applicationContext.containsBean("dataSource")) {
			this.dataSource = (DataSource) applicationContext.getBean("dataSource");
		}
		return this.dataSource;
	}

}
