package org.sagacity.sqltoy.configure;

import static java.lang.System.err;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.dao.impl.LightDaoImpl;
import org.sagacity.sqltoy.dao.impl.SqlToyLazyDaoImpl;
import org.sagacity.sqltoy.integration.ConnectionFactory;
import org.sagacity.sqltoy.integration.impl.SpringAppContext;
import org.sagacity.sqltoy.integration.impl.SpringConnectionFactory;
import org.sagacity.sqltoy.plugins.FilterHandler;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.OverTimeSqlHandler;
import org.sagacity.sqltoy.plugins.SqlInterceptor;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.sagacity.sqltoy.plugins.datasource.DataSourceSelector;
import org.sagacity.sqltoy.plugins.formater.SqlFormater;
import org.sagacity.sqltoy.plugins.secure.DesensitizeProvider;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.service.impl.SqlToyCRUDServiceImpl;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.alibaba.ttl.threadpool.TtlExecutors;

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

	// 增加一个辅助校验,避免不少新用户将spring.sqltoy开头写成sqltoy.开头
	@Value("${sqltoy.sqlResourcesDir:}")
	private String sqlResourcesDir;

	/**
	 * 当配置不存在或不为none的时候实例化
	 * 
	 * @return
	 */
	@ConditionalOnExpression("#{''.equals(environment.getProperty('spring.sqltoy.taskExecutor.targetPoolName', ''))}")
	@Bean(name = "sqltoyOrmTaskExecutor", destroyMethod = "shutdown", initMethod = "initialize")
	public ThreadPoolTaskExecutor sqltoyOrmTaskExecutor() {
		SqlToyContextTaskPoolProperties taskPoolProperties = properties.getTaskExecutor();
		ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
		pool.setThreadNamePrefix(taskPoolProperties.getThreadNamePrefix());
		// 线程池维护线程的最少数量
		pool.setCorePoolSize(taskPoolProperties.getCorePoolSize());
		// 线程池维护线程的最大数量
		pool.setMaxPoolSize(taskPoolProperties.getMaxPoolSize());
		// 线程池所使用的缓冲队列
		pool.setQueueCapacity(taskPoolProperties.getQueueCapacity());
		// 线程池维护线程所允许的空闲时间
		pool.setKeepAliveSeconds(taskPoolProperties.getKeepAliveSeconds());
		// 决定使用ThreadPool的shutdown()还是shutdownNow()方法来关闭，默认为false
		pool.setWaitForTasksToCompleteOnShutdown(taskPoolProperties.getWaitForTasksToCompleteOnShutdown());
		// pool.setContinueScheduledExecutionAfterException();
		pool.setAwaitTerminationSeconds(taskPoolProperties.getAwaitTerminationSeconds());
		// 如果添加到线程池失败，那么主线程会自己去执行该任务，不会等待线程池中的线程去执行。
		pool.setRejectedExecutionHandler(taskPoolProperties.getRejectedExecutionHandler());
		return pool;
	}

	@ConditionalOnExpression("#{''.equals(environment.getProperty('spring.sqltoy.taskExecutor.targetPoolName', ''))}")
	@Bean(name = "ttlSqltoyOrmTaskExecutor")
	public Executor ttlSqltoyOrmTaskExecutor(@Qualifier("sqltoyOrmTaskExecutor") ThreadPoolTaskExecutor taskExecutor) {
		return TtlExecutors.getTtlExecutor(taskExecutor);
	}

	// 构建sqltoy上下文,并指定初始化方法和销毁方法
	@Bean(name = "sqlToyContext", initMethod = "initialize", destroyMethod = "destroy")
	@ConditionalOnMissingBean
	SqlToyContext sqlToyContext(
			@Value("${spring.sqltoy.taskExecutor.targetPoolName:ttlSqltoyOrmTaskExecutor}") String taskExecutorName)
			throws Exception {
		// 用辅助配置来校验是否配置错误
		if (StringUtil.isBlank(properties.getSqlResourcesDir()) && StringUtil.isNotBlank(sqlResourcesDir)) {
			throw new IllegalArgumentException(
					"请检查sqltoy配置,是spring.sqltoy作为前缀,而不是sqltoy!\n正确范例: spring.sqltoy.sqlResourcesDir=classpath:com/sagframe/modules");
		}
		SqlToyContext sqlToyContext = new SqlToyContext();

		// --------5.2 变化的地方----------------------------------
		// 注意appContext注入非常关键(必须设置)
		SpringAppContext appContext = new SpringAppContext(applicationContext);
		sqlToyContext.setAppContext(appContext);

		// 设置默认spring的connectFactory(不设置也会自动默认)
		sqlToyContext.setConnectionFactory(new SpringConnectionFactory());

		// 分布式id产生器实现类(不设置也会自动默认)
		sqlToyContext.setDistributeIdGeneratorClass("org.sagacity.sqltoy.integration.impl.SpringRedisIdGenerator");

		// 针对Caffeine缓存指定实现类型(不设置也会自动默认)
		sqlToyContext
				.setTranslateCaffeineManagerClass("org.sagacity.sqltoy.translate.cache.impl.TranslateCaffeineManager");
		// 注入spring的默认mongoQuery实现类(不设置也会自动默认)
		sqlToyContext.setMongoQueryClass("org.sagacity.sqltoy.integration.impl.SpringMongoQuery");
		// --------end 5.2 -----------------------------------------

		// 当发现有重复sqlId时是否抛出异常，终止程序执行
		sqlToyContext.setBreakWhenSqlRepeat(properties.isBreakWhenSqlRepeat());
		// 是否自动创建或更新表
		sqlToyContext.setAutoDDL(properties.getAutoDDL());
		// 开放设置默认单页记录数量
		sqlToyContext.setDefaultPageSize(properties.getDefaultPageSize());
		sqlToyContext.setDefaultPageOffset(properties.isDefaultPageOffset());
		// map 类型结果label是否自动转驼峰处理
		if (properties.getHumpMapResultTypeLabel() != null) {
			sqlToyContext.setHumpMapResultTypeLabel(properties.getHumpMapResultTypeLabel());
		}
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
		// 默认数据库fetchSize
		if (properties.getFetchSize() > 0) {
			sqlToyContext.setFetchSize(properties.getFetchSize());
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

		// 指定需要进行产品化跨数据库查询验证的数据库
		if (properties.getRedoDataSources() != null) {
			sqlToyContext.setRedoDataSources(properties.getRedoDataSources());
		}
		// 数据库方言
		sqlToyContext.setDialect(properties.getDialect());
		// sqltoy内置参数默认值修改
		sqlToyContext.setDialectConfig(properties.getDialectConfig());

		// update 2021-01-18 设置缓存类别,默认ehcache
		sqlToyContext.setCacheType(properties.getCacheType());
		sqlToyContext.setExecuteSqlBlankToNull(properties.isExecuteSqlBlankToNull());
		// 是否拆分merge into 为updateAll 和 saveAllIgnoreExist 两步操作(1、seata分布式事务不支持merge)
		sqlToyContext.setSplitMergeInto(properties.isSplitMergeInto());
		// getMetaData().getColumnLabel(i) 结果做大小写处理策略
		if (null != properties.getColumnLabelUpperOrLower()) {
			sqlToyContext.setColumnLabelUpperOrLower(properties.getColumnLabelUpperOrLower().toLowerCase());
		}
		sqlToyContext.setSecurePrivateKey(properties.getSecurePrivateKey());
		sqlToyContext.setSecurePublicKey(properties.getSecurePublicKey());
		// 修改多少条记录做特别提示
		sqlToyContext.setUpdateTipCount(properties.getUpdateTipCount());
		if (properties.getOverPageToFirst() != null) {
			sqlToyContext.setOverPageToFirst(properties.getOverPageToFirst());
		}
		// 设置公共统一属性的处理器
		String unfiyHandler = properties.getUnifyFieldsHandler();
		if (StringUtil.isNotBlank(unfiyHandler)) {
			try {
				IUnifyFieldsHandler handler = null;
				// 类
				if (unfiyHandler.contains(".")) {
					handler = (IUnifyFieldsHandler) Class.forName(unfiyHandler).getDeclaredConstructor().newInstance();
				} // spring bean名称
				else if (applicationContext.containsBean(unfiyHandler)) {
					handler = (IUnifyFieldsHandler) applicationContext.getBean(unfiyHandler);
					if (handler == null) {
						throw new ClassNotFoundException("项目中未定义unifyFieldsHandler=" + unfiyHandler + " 对应的bean!");
					}
				}
				if (handler != null) {
					sqlToyContext.setUnifyFieldsHandler(handler);
				}
			} catch (ClassNotFoundException cne) {
				err.println("------------------- 错误提示 ------------------------------------------- ");
				err.println("spring.sqltoy.unifyFieldsHandler=" + unfiyHandler + " 对应类不存在,错误原因:");
				err.println("--1.您可能直接copy了参照项目的配置文件,但没有将具体的类也同步copy过来!");
				err.println("--2.如您并不需要此功能，请将配置文件中注释掉spring.sqltoy.unifyFieldsHandler");
				err.println("------------------------------------------------");
				cne.printStackTrace();
				throw cne;
			}
		}

		// 设置elastic连接
		Elastic es = properties.getElastic();
		if (es != null && es.getEndpoints() != null && !es.getEndpoints().isEmpty()) {
			sqlToyContext.setDefaultElastic(es.getDefaultId());
			List<ElasticEndpoint> endpoints = new ArrayList<ElasticEndpoint>();
			for (ElasticConfig esconfig : es.getEndpoints()) {
				ElasticEndpoint ep = new ElasticEndpoint(esconfig.getUrl(), esconfig.getSqlPath());
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
				ep.setAuthCaching(esconfig.isAuthCaching());
				ep.setUsername(esconfig.getUsername());
				ep.setPassword(esconfig.getPassword());
				ep.setKeyStore(esconfig.getKeyStore());
				ep.setKeyStorePass(esconfig.getKeyStorePass());
				ep.setKeyStoreSelfSign(esconfig.isKeyStoreSelfSign());
				ep.setKeyStoreType(esconfig.getKeyStoreType());
				endpoints.add(ep);
			}
			// 这里已经完成了当没有设置默认节点时将第一个节点作为默认节点
			sqlToyContext.setElasticEndpoints(endpoints);
		}
		// 设置默认数据库
		if (properties.getDefaultDataSource() != null) {
			sqlToyContext.setDefaultDataSourceName(properties.getDefaultDataSource());
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

		// 自定义typeHandler
		String typeHandler = properties.getTypeHandler();
		if (StringUtil.isNotBlank(typeHandler)) {
			if (applicationContext.containsBean(typeHandler)) {
				sqlToyContext.setTypeHandler((TypeHandler) applicationContext.getBean(typeHandler));
			} // 包名和类名称
			else if (typeHandler.contains(".")) {
				sqlToyContext.setTypeHandler(
						(TypeHandler) Class.forName(typeHandler).getDeclaredConstructor().newInstance());
			}
		}

		// 自定义数据源选择器
		String dataSourceSelector = properties.getDataSourceSelector();
		if (StringUtil.isNotBlank(dataSourceSelector)) {
			if (applicationContext.containsBean(dataSourceSelector)) {
				sqlToyContext
						.setDataSourceSelector((DataSourceSelector) applicationContext.getBean(dataSourceSelector));
			} // 包名和类名称
			else if (dataSourceSelector.contains(".")) {
				sqlToyContext.setDataSourceSelector(
						(DataSourceSelector) Class.forName(dataSourceSelector).getDeclaredConstructor().newInstance());
			}
		}

		// 自定义数据库连接获取和释放的接口实现
		String connectionFactory = properties.getConnectionFactory();
		if (StringUtil.isNotBlank(connectionFactory)) {
			if (applicationContext.containsBean(connectionFactory)) {
				sqlToyContext.setConnectionFactory((ConnectionFactory) applicationContext.getBean(connectionFactory));
			} // 包名和类名称
			else if (connectionFactory.contains(".")) {
				sqlToyContext.setConnectionFactory(
						(ConnectionFactory) Class.forName(connectionFactory).getDeclaredConstructor().newInstance());
			}
		}

		// 自定义字段安全实现器
		String fieldsSecureProvider = properties.getFieldsSecureProvider();
		if (StringUtil.isNotBlank(fieldsSecureProvider)) {
			if (applicationContext.containsBean(fieldsSecureProvider)) {
				sqlToyContext.setFieldsSecureProvider(
						(FieldsSecureProvider) applicationContext.getBean(fieldsSecureProvider));
			} // 包名和类名称
			else if (fieldsSecureProvider.contains(".")) {
				sqlToyContext.setFieldsSecureProvider((FieldsSecureProvider) Class.forName(fieldsSecureProvider)
						.getDeclaredConstructor().newInstance());
			}
		}

		// 自定义字段脱敏处理器
		String desensitizeProvider = properties.getDesensitizeProvider();
		if (StringUtil.isNotBlank(desensitizeProvider)) {
			if (applicationContext.containsBean(desensitizeProvider)) {
				sqlToyContext
						.setDesensitizeProvider((DesensitizeProvider) applicationContext.getBean(desensitizeProvider));
			} // 包名和类名称
			else if (desensitizeProvider.contains(".")) {
				sqlToyContext.setDesensitizeProvider((DesensitizeProvider) Class.forName(desensitizeProvider)
						.getDeclaredConstructor().newInstance());
			}
		}

		// 自定义sql中filter处理器
		String customFilterHandler = properties.getCustomFilterHandler();
		if (StringUtil.isNotBlank(customFilterHandler)) {
			if (applicationContext.containsBean(customFilterHandler)) {
				sqlToyContext.setCustomFilterHandler((FilterHandler) applicationContext.getBean(customFilterHandler));
			} // 包名和类名称
			else if (customFilterHandler.contains(".")) {
				sqlToyContext.setCustomFilterHandler(
						(FilterHandler) Class.forName(customFilterHandler).getDeclaredConstructor().newInstance());
			}
		}

		// 自定义sql执行超时处理器
		String overTimeSqlHandler = properties.getOverTimeSqlHandler();
		if (StringUtil.isNotBlank(overTimeSqlHandler)) {
			if (applicationContext.containsBean(overTimeSqlHandler)) {
				sqlToyContext
						.setOverTimeSqlHandler((OverTimeSqlHandler) applicationContext.getBean(overTimeSqlHandler));
			} // 包名和类名称
			else if (customFilterHandler.contains(".")) {
				sqlToyContext.setOverTimeSqlHandler(
						(OverTimeSqlHandler) Class.forName(overTimeSqlHandler).getDeclaredConstructor().newInstance());
			}
		}

		//---- sqltoy默认的规则，即:先判断是否包含beanName，然后判断是否是包路径再new 构造
		// 自定义sql拦截处理器
		String[] sqlInterceptors = properties.getSqlInterceptors();
		if (null != sqlInterceptors && sqlInterceptors.length > 0) {
			List<SqlInterceptor> sqlInterceptorList = new ArrayList<SqlInterceptor>();
			for (String interceptor : sqlInterceptors) {
				// 优先检查beanName
				if (applicationContext.containsBean(interceptor)) {
					sqlInterceptorList.add((SqlInterceptor) applicationContext.getBean(interceptor));
				} // 包名和类名称
				else if (interceptor.contains(".")) {
					sqlInterceptorList
							.add(((SqlInterceptor) Class.forName(interceptor).getDeclaredConstructor().newInstance()));
				}
			}
			sqlToyContext.setSqlInterceptors(sqlInterceptorList);
		}

		// 自定义sql格式化器
		String sqlFormater = properties.getSqlFormater();
		if (StringUtil.isNotBlank(sqlFormater)) {
			// 提供简化配置
			if (sqlFormater.equalsIgnoreCase("default") || sqlFormater.equalsIgnoreCase("defaultFormater")
					|| sqlFormater.equalsIgnoreCase("defaultSqlFormater")) {
				sqlFormater = "org.sagacity.sqltoy.plugins.formater.impl.DefaultSqlFormater";
			}
			if (applicationContext.containsBean(sqlFormater)) {
				sqlToyContext.setSqlFormater((SqlFormater) applicationContext.getBean(sqlFormater));
			} // 包名和类名称
			else if (sqlFormater.contains(".")) {
				sqlToyContext.setSqlFormater(
						(SqlFormater) Class.forName(sqlFormater).getDeclaredConstructor().newInstance());
			}
		}
		// 自定义线程池
		if (StringUtil.isNotBlank(taskExecutorName)) {
			sqlToyContext.setTaskExecutorName(taskExecutorName);
		}
		return sqlToyContext;
	}

	/**
	 * 5.2 版本要注入sqlToyContext
	 * 
	 * @return 返回预定义的通用Dao实例
	 */
	@Bean(name = "sqlToyLazyDao")
	@ConditionalOnMissingBean
	SqlToyLazyDao sqlToyLazyDao(SqlToyContext sqlToyContext) {
		SqlToyLazyDaoImpl lazyDao = new SqlToyLazyDaoImpl();
		lazyDao.setSqlToyContext(sqlToyContext);
		return lazyDao;
	}

	@Bean(name = "lightDao")
	@ConditionalOnMissingBean
	LightDao lightDao(SqlToyContext sqlToyContext) {
		LightDaoImpl lightDao = new LightDaoImpl();
		lightDao.setSqlToyContext(sqlToyContext);
		return lightDao;
	}

	/**
	 * 5.2 版本要注入sqlToyLazyDao
	 * 
	 * @return 返回预定义的通用CRUD service实例
	 */
	@Bean(name = "sqlToyCRUDService")
	@ConditionalOnMissingBean
	SqlToyCRUDService sqlToyCRUDService(SqlToyLazyDao sqlToyLazyDao) {
		SqlToyCRUDServiceImpl sqlToyCRUDService = new SqlToyCRUDServiceImpl();
		sqlToyCRUDService.setSqlToyLazyDao(sqlToyLazyDao);
		return sqlToyCRUDService;
	}
}
