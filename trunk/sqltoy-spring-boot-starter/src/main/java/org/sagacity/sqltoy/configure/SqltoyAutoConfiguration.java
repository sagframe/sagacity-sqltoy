package org.sagacity.sqltoy.configure;

import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.dao.impl.SqlToyLazyDaoImpl;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.service.impl.SqlToyCRUDServiceImpl;
import org.sagacity.sqltoy.utils.StringUtil;
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

	@Bean(name = "sqlToyContext", initMethod = "initialize", destroyMethod = "destroy")
	@ConditionalOnMissingBean
	SqlToyContext sqlToyContext() throws Exception {
		SqlToyContext sqlToyContext = new SqlToyContext();
		// sql 文件资源路径
		sqlToyContext.setSqlResourcesDir(properties.getSqlResourcesDir());
		if (properties.getSqlResources() != null && properties.getSqlResources().length > 0) {
			List<String> resList = new ArrayList<String>();
			for (String s : properties.getSqlResources()) {
				resList.add(s);
			}
			sqlToyContext.setSqlResources(resList);
		}
		// sql文件解析的编码格式,默认utf-8
		if (properties.getEncoding() != null) {
			sqlToyContext.setEncoding(properties.getEncoding());
		}
		// pojo 扫描路径
		if (properties.getPackagesToScan() != null) {
			sqlToyContext.setPackagesToScan(properties.getPackagesToScan());
		}

		// 特定pojo类加载
		if (properties.getAnnotatedClasses() != null) {
			sqlToyContext.setAnnotatedClasses(properties.getAnnotatedClasses());
		}

		if (properties.getBatchSize() != null) {
			sqlToyContext.setBatchSize(properties.getBatchSize());
		}

		if (properties.getPageFetchSizeLimit() != null) {
			sqlToyContext.setPageFetchSizeLimit(properties.getPageFetchSizeLimit());
		}

		if (properties.getScriptCheckIntervalSeconds() != null) {
			sqlToyContext.setScriptCheckIntervalSeconds(properties.getScriptCheckIntervalSeconds());
		}

		if (properties.getDelayCheckSeconds() != null) {
			sqlToyContext.setDelayCheckSeconds(properties.getDelayCheckSeconds());
		}

		// sql执行打印策略(默认为error时打印)
		if (properties.getDebug() != null) {
			sqlToyContext.setDebug(properties.getDebug());
		}
		if (properties.getPrintSqlStrategy() != null) {
			sqlToyContext.setPrintSqlStrategy(properties.getPrintSqlStrategy());
		}
		// sql执行超过多长时间则打印提醒(默认30秒)
		if (properties.getPrintSqlTimeoutMillis() != null) {
			sqlToyContext.setPrintSqlTimeoutMillis(properties.getPrintSqlTimeoutMillis());
		}

		// sql函数转换器
		if (properties.getFunctionConverts() != null) {
			sqlToyContext.setFunctionConverts(properties.getFunctionConverts());
		}

		if (properties.getTranslateConfig() != null) {
			sqlToyContext.setTranslateConfig(properties.getTranslateConfig());
		}
		// 数据库方言
		sqlToyContext.setDialect(properties.getDialect());
		sqlToyContext.setDialectProperties(properties.getDialectProperties());

		// 设置公共统一属性的处理器
		String unfiyHandler = properties.getUnifyFieldsHandler();
		if (StringUtil.isNotBlank(unfiyHandler)) {
			IUnifyFieldsHandler handler = (IUnifyFieldsHandler) Class.forName(unfiyHandler).getDeclaredConstructor()
					.newInstance();
			sqlToyContext.setUnifyFieldsHandler(handler);
		}

		// 设置elastic连接
		Elastic es = properties.getElastic();
		if (es != null && es.getEndpoints() != null && !es.getEndpoints().isEmpty()) {
			sqlToyContext.setDefaultElastic(es.getDefaultId());
			List<ElasticEndpoint> endpoints = new ArrayList<ElasticEndpoint>();
			for (ElasticConfig esconfig : es.getEndpoints()) {
				ElasticEndpoint ep = new ElasticEndpoint(esconfig.getUrl(), esconfig.getVersion());
				ep.setId(esconfig.getId());
				if (esconfig.getCharset() != null) {
					ep.setCharset(esconfig.getCharset());
				}
				if (esconfig.getRequestTimeout() != null) {
					ep.setRequestTimeout(esconfig.getRequestTimeout());
				}
				if (esconfig.getConnectTimeout() != null) {
					ep.setConnectTimeout(esconfig.getConnectTimeout());
				}
				if (esconfig.getSocketTimeout() != null) {
					ep.setSocketTimeout(esconfig.getSocketTimeout());
				}
				ep.setUsername(esconfig.getUsername());
				ep.setPassword(esconfig.getPassword());
				ep.setPath(esconfig.getPath());
				ep.setKeyStore(esconfig.getKeyStore());
				endpoints.add(ep);
			}
			sqlToyContext.setElasticEndpoints(endpoints);
		}
		// mongodb
		if (properties.getMongoFactoryName() != null) {
			sqlToyContext.setMongoFactoryName(properties.getMongoFactoryName());
		}
		if (properties.getDefaultDataSource() != null) {
			sqlToyContext.setDefaultDataSource(properties.getDefaultDataSource());
		}
		// sqlToyContext.initialize();
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
