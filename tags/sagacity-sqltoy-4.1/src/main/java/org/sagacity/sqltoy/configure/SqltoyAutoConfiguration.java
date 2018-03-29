package org.sagacity.sqltoy.configure;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.dao.impl.SqlToyLazyDaoImpl;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.service.impl.SqlToyCRUDServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sqltoy 自动配置类
 * 
 * @author wolf
 *
 */
@Configuration
@ConditionalOnClass(SqlToyLazyDaoImpl.class)
@EnableConfigurationProperties(SqlToyContextProperties.class)
public class SqltoyAutoConfiguration {

	@Autowired
	SqlToyContextProperties properties;

	@Bean(name = "sqlToyContext")
	@ConditionalOnMissingBean
	SqlToyContext sqlToyContext() {
		SqlToyContext sqlToyContext = new SqlToyContext();
		BeanUtils.copyProperties(properties, sqlToyContext);
		sqlToyContext.setCacheManager(properties.getCacheManager());
		sqlToyContext.setPackagesToScan(properties.getPackagesToScan());
		sqlToyContext.setTranslateConfig(properties.getTranslateConfig());
		sqlToyContext.setBatchSize(properties.getBatchSize());
		sqlToyContext.setSqlResourcesDir(properties.getSqlResourcesDir());
		sqlToyContext.setUnifyFieldsHandler(properties.getUnifyFieldsHandler());
		sqlToyContext.setElasticEndpoints(properties.getElasticEndpoints());
		sqlToyContext.setTranslateCacheManagers(properties.getTranslateCacheManagers());
		try {
			sqlToyContext.initialize();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sqlToyContext;
	}

	/**
	 * 
	 * @return 返回预定义的通用Dao实例
	 */
	@Bean(name = "sqlToyLazyDao")
	@ConditionalOnMissingBean
	SqlToyLazyDao sqlToyLazyDao() {
		return new SqlToyLazyDaoImpl();
	}

	/**
	 * 
	 * @return 返回预定义的通用CRUD service实例
	 */
	@Bean(name = "sqlToyCRUDService")
	@ConditionalOnMissingBean
	SqlToyCRUDService sqlToyCRUDService() {
		return new SqlToyCRUDServiceImpl();
	}

}
