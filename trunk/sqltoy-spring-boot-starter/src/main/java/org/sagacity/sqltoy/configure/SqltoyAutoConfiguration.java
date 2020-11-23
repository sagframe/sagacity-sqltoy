package org.sagacity.sqltoy.configure;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.dao.impl.SqlToyLazyDaoImpl;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.datasource.ObtainDataSource;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.service.impl.SqlToyCRUDServiceImpl;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description sqltoy 自动配置类
 * @author wolf
 * @version v1.0,Date:2018年12月26日
 * @modify {Date:2020-2-20,完善配置支持es等,实现完整功能}
 */
@Configuration
@EnableConfigurationProperties(SqlToyContextProperties.class)
public class SqltoyAutoConfiguration {
	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private SqlToyContextProperties properties;

	//增加一个辅助校验,避免不少新用户将spring.sqltoy开头写成sqltoy.开头
	@Value("${sqltoy.sqlResourcesDir}")
	private String sqlResourcesDir;

	// 构建sqltoy上下文,并指定初始化方法和销毁方法
	@Bean(name = "sqlToyContext", initMethod = "initialize", destroyMethod = "destroy")
	@ConditionalOnMissingBean
	SqlToyContext sqlToyContext() throws Exception {
		if (StringUtil.isBlank(properties.getSqlResourcesDir()) && StringUtil.isNotBlank(sqlResourcesDir)) {
			throw new IllegalArgumentException(
					"请正确配置sqltoy配置,必须是spring.sqltoy开头,而不是sqltoy!\n例如: spring.sqltoy.sqlResourcesDir=classpath:com/sagframe/modules");
		}
		SqlToyContext sqlToyContext = new SqlToyContext();
		// sql 文件资源路径
		sqlToyContext.setSqlResourcesDir(properties.getSqlResourcesDir());
		if (properties.getSqlResources() != null && properties.getSqlResources().length > 0) {
			List<String> resList = new ArrayList<String>();
			for (String prop : properties.getSqlResources()) {
				resList.add(prop);
			}
			sqlToyContext.setSqlResources(resList);
		}
		// sql文件解析的编码格式,默认utf-8
		if (properties.getEncoding() != null) {
			sqlToyContext.setEncoding(properties.getEncoding());
		}

		// sqltoy 已经无需指定扫描pojo类,已经改为用的时候动态加载
		// pojo 扫描路径,意义不存在
		if (properties.getPackagesToScan() != null) {
			sqlToyContext.setPackagesToScan(properties.getPackagesToScan());
		}

		// 特定pojo类加载，意义已经不存在
		if (properties.getAnnotatedClasses() != null) {
			sqlToyContext.setAnnotatedClasses(properties.getAnnotatedClasses());
		}

		// 批量操作时(saveAll、updateAll)，每批次数量,默认200
		if (properties.getBatchSize() != null) {
			sqlToyContext.setBatchSize(properties.getBatchSize());
		}

		// 分页查询单页最大记录数量(默认50000)
		if (properties.getPageFetchSizeLimit() != null) {
			sqlToyContext.setPageFetchSizeLimit(properties.getPageFetchSizeLimit());
		}

		// sql 检测间隔时长(单位秒)
		if (properties.getScriptCheckIntervalSeconds() != null) {
			sqlToyContext.setScriptCheckIntervalSeconds(properties.getScriptCheckIntervalSeconds());
		}

		// 缓存、sql文件在初始化后延时多少秒开始检测
		if (properties.getDelayCheckSeconds() != null) {
			sqlToyContext.setDelayCheckSeconds(properties.getDelayCheckSeconds());
		}

		// 是否debug模式
		if (properties.getDebug() != null) {
			sqlToyContext.setDebug(properties.getDebug());
		}

		// sql执行打印策略(默认为error时打印)
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

		// 缓存翻译配置
		if (properties.getTranslateConfig() != null) {
			sqlToyContext.setTranslateConfig(properties.getTranslateConfig());
		}

		// 数据库保留字
		if (properties.getReservedWords() != null) {
			sqlToyContext.setReservedWords(properties.getReservedWords());
		}
		// 数据库方言
		sqlToyContext.setDialect(properties.getDialect());
		// sqltoy内置参数默认值修改
		sqlToyContext.setDialectConfig(properties.getDialectConfig());

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
			// 这里已经完成了当没有设置默认节点时将第一个节点作为默认节点
			sqlToyContext.setElasticEndpoints(endpoints);
		}
		// 设置默认数据库
		if (properties.getDefaultDataSource() != null) {
			if (applicationContext.containsBean(properties.getDefaultDataSource())) {
				sqlToyContext.setDefaultDataSource(
						(DataSource) applicationContext.getBean(properties.getDefaultDataSource()));
			}
		}

		// 自定义获取数据源的策略配置
		String obtainDataSource = properties.getObtainDataSource();
		if (StringUtil.isNotBlank(obtainDataSource)) {
			if (applicationContext.containsBean(obtainDataSource)) {
				sqlToyContext.setObtainDataSource((ObtainDataSource) applicationContext.getBean(obtainDataSource));
			} // 包名和类名称
			else if (obtainDataSource.contains(".")) {
				sqlToyContext.setObtainDataSource(
						(ObtainDataSource) Class.forName(obtainDataSource).getDeclaredConstructor().newInstance());
			}
		}

		// 自定义缓存实现管理器
		String translateCacheManager = properties.getTranslateCacheManager();
		if (StringUtil.isNotBlank(translateCacheManager)) {
			// 缓存管理器的bean名称
			if (applicationContext.containsBean(translateCacheManager)) {
				sqlToyContext.setTranslateCacheManager(
						(TranslateCacheManager) applicationContext.getBean(translateCacheManager));
			} // 包名和类名称
			else if (translateCacheManager.contains(".")) {
				sqlToyContext.setTranslateCacheManager((TranslateCacheManager) Class.forName(translateCacheManager)
						.getDeclaredConstructor().newInstance());
			}
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
