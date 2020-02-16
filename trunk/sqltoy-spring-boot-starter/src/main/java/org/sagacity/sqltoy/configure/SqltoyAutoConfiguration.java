package org.sagacity.sqltoy.configure;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.dao.impl.SqlToyLazyDaoImpl;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.service.impl.SqlToyCRUDServiceImpl;
import org.sagacity.sqltoy.utils.StringUtil;
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
	SqlToyContext sqlToyContext() throws Exception {
		SqlToyContext sqlToyContext = new SqlToyContext();
		BeanUtils.copyProperties(properties, sqlToyContext);
		if (properties.getPackagesToScan() != null) {
			sqlToyContext.setPackagesToScan(properties.getPackagesToScan());
		}
		if (properties.getBatchSize() != null) {
			sqlToyContext.setBatchSize(properties.getBatchSize());
		}
		sqlToyContext.setTranslateConfig(properties.getTranslateConfig());
		sqlToyContext.setDialect(properties.getDialect());
		sqlToyContext.setSqlResourcesDir(properties.getSqlResourcesDir());
		// 设置公共统一属性的处理器
		String unfiyHandler = properties.getUnifyFieldsHandler();
		if (StringUtil.isNotBlank(unfiyHandler)) {
			IUnifyFieldsHandler handler = (IUnifyFieldsHandler) Class.forName(unfiyHandler).getDeclaredConstructor()
					.newInstance();
			sqlToyContext.setUnifyFieldsHandler(handler);
		}
		// 设置elastic连接
		if (properties.getElastic() != null && properties.getElastic().getEndpoints() != null
				&& !properties.getElastic().getEndpoints().isEmpty()) {

		}
		// sqlToyContext.setElasticEndpoints(properties.getElastic());
		sqlToyContext.initialize();
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
