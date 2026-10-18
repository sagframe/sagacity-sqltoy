package org.sagacity.sqltoy.integration.aot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sagacity.sqltoy.config.EntityScanner;
import org.sagacity.sqltoy.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.plugins.json.JsonProvider;
import org.sagacity.sqltoy.plugins.json.JsonProviders;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Spring AOT 处理器：构建期注册 sqltoy 运行所需的反射与资源 hints，保障 GraalVM Native Image 下 ORM 可用
 * <p>
 * update 2026-10-18 重写(6.0.8)：AOT 构建(refreshForAotProcessing)期间容器不注册 BeanPostProcessor、
 * 不预实例化单例，此阶段 getBean 得到的 @ConfigurationProperties bean 是未绑定的空实例，SqlToyContext
 * 也会以空配置被初始化——原先依赖 getAllEntities() 快照的实现因此注册不到任何实体 hints。
 * 现改为从 Environment(prepareBeanFactory 阶段已注册为单例)直接绑定配置，并按配置扫描实体：
 * 1.实体反射 hints(与 EntityManager.initialize 的启动扫描语义一致;实体为即用即加载，native 场景必须
 * 配置 spring.sqltoy.packagesToScan 或 annotatedClasses)；
 * 2.按配置动态注册 sql 文件、缓存翻译配置、加解密密钥的 classpath 资源 pattern(SqltoyRuntimeHintsRegistrar
 * 内置的固定 pattern 只覆盖 sqltoy/**、translates/** 等约定目录，而 native 运行期 sql 文件按资源列表加载，
 * 文件必须先进镜像)；
 * 3.为按类名配置的扩展类注册反射 hints(unifyFieldsHandler、typeHandler、sqlInterceptors 等运行期经
 * Class.forName 反射实例化，约定同 SqltoyAutoConfiguration:含"."视为类名，否则为 bean 名称由 Spring AOT 覆盖)；
 * 4.条件注册运行期反射实例化的集成实现类(caffeine 翻译缓存管理器、mongo 查询、redis 分布式 ID)，
 * 并按与运行期相同的解析路径注册实际生效的 JsonProvider 实现类。
 *
 * @author limliu
 * @since 5.6
 */
class SqltoyBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {
	private static final Logger logger = LoggerFactory.getLogger(SqltoyBeanFactoryInitializationAotProcessor.class);

	/** 与 SqltoyAutoConfiguration.strSplitTrim 保持一致:多目录配置支持中英文逗号、分号分隔 */
	private static final String DIR_SPLIT_REGEX = "[,;，；]";

	/**
	 * 扩展类型(json/jsonb/vector/geometry等)经反射访问的驱动专属类,仅在对应驱动位于classpath时注册:
	 * PG系PGobject同构类5个(写路径DataSourceUtils.resolvePGobjectHolder按URL scheme反射构造+读路径
	 * ResultUtils.extObjectPgType反射getType,PGobject不能跨驱动setObject故每驱动一个同构类):
	 * postgresql及stardb等PG系兜底/opengauss与mogdb/vastbase/gaussdb/kingbase(KBobject小写o);
	 * mssql-jdbc的Geometry(parse/serialize/deserialize/STAsText句柄)与13.x的Vector;
	 * oracle的OracleTypes类型常量。
	 * 无需注册:oracle.sql.TIMESTAMP(BeanUtil直接强转调用timestampValue/dateValue)、
	 * oracle与mssql的vector/json对象(按类名识别后走toString)、JTS(静态引用可达)、
	 * postgis-jdbc的PGgeometry(JtsGeometryCodec全程instanceof/toString无反射)
	 */
	private static final String[] EXT_TYPE_DRIVER_CLASSES = { "org.postgresql.util.PGobject",
			"org.opengauss.util.PGobject", "cn.com.vastbase.util.PGobject", "com.huawei.gaussdb.jdbc.util.PGobject",
			"com.kingbase8.util.KBobject", "com.microsoft.sqlserver.jdbc.Geometry", "microsoft.sql.Vector",
			"microsoft.sql.Vector$VectorDimensionType", "oracle.jdbc.OracleTypes" };

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return (context, code) -> {
			RuntimeHints hints = context.getRuntimeHints();
			SqlToyContextProperties properties = bindProperties(beanFactory);
			registerEntities(hints, properties);
			registerConfiguredResources(hints.resources(), properties);
			registerExtensionClasses(hints, properties);
			registerIntegrationClasses(hints, properties);
			registerJsonProvider(hints, properties);
		};
	}

	/**
	 * AOT 构建期 @ConfigurationProperties bean 不会被容器绑定,直接从 Environment 绑定(与运行期绑定结果一致)
	 */
	private SqlToyContextProperties bindProperties(ConfigurableListableBeanFactory beanFactory) {
		Environment environment = beanFactory.getBeanProvider(Environment.class).getIfAvailable();
		if (environment == null) {
			return new SqlToyContextProperties();
		}
		return Binder.get(environment).bind("spring.sqltoy", SqlToyContextProperties.class)
				.orElseGet(SqlToyContextProperties::new);
	}

	/**
	 * 实体反射 hints:与 EntityManager.initialize 的扫描语义一致;实体为即用即加载，
	 * native 场景需配置 spring.sqltoy.packagesToScan(或 annotatedClasses)供构建期扫描
	 */
	private void registerEntities(RuntimeHints hints, SqlToyContextProperties properties) {
		Set<Class<?>> entities = new LinkedHashSet<>();
		if (properties.getPackagesToScan() != null) {
			for (String pkg : properties.getPackagesToScan()) {
				if (StringUtil.isBlank(pkg)) {
					continue;
				}
				try {
					List<Class<?>> scanned = EntityScanner.scanEntityClasses(pkg.trim(), true, "UTF-8");
					if (scanned != null) {
						entities.addAll(scanned);
					}
				} catch (Throwable e) {
					logger.warn("sqltoy aot: entity scan failed for package [{}], entities may be incomplete!", pkg,
							e);
				}
			}
		}
		if (properties.getAnnotatedClasses() != null) {
			for (String className : properties.getAnnotatedClasses()) {
				if (StringUtil.isBlank(className)) {
					continue;
				}
				try {
					Class<?> entityClass = Class.forName(className.trim());
					if (EntityScanner.isSqlToyEntity(entityClass)) {
						entities.add(entityClass);
					}
				} catch (Throwable e) {
					logger.warn("sqltoy aot: annotated class [{}] can not be loaded!", className);
				}
			}
		}
		for (Class<?> entityClass : entities) {
			hints.reflection().registerType(entityClass, MemberCategory.values());
		}
	}

	/**
	 * 按配置动态注册 classpath 资源 pattern:sql 文件目录、显式 sql 文件列表、缓存翻译配置、字段加解密密钥
	 */
	private void registerConfiguredResources(ResourceHints resources, SqlToyContextProperties properties) {
		if (StringUtil.isNotBlank(properties.getSqlResourcesDir())) {
			for (String dir : properties.getSqlResourcesDir().split(DIR_SPLIT_REGEX)) {
				registerClasspathResource(resources, dir, true);
			}
		}
		if (properties.getSqlResources() != null) {
			for (String sqlResource : properties.getSqlResources()) {
				registerClasspathResource(resources, sqlResource, false);
			}
		}
		if (StringUtil.isNotBlank(properties.getTranslateConfig())) {
			for (String item : properties.getTranslateConfig().split(DIR_SPLIT_REGEX)) {
				// 与 SqltoyAutoConfiguration 的翻译配置解析规则一致:.xml 结尾视为文件,否则视为目录
				registerClasspathResource(resources, item, !item.trim().endsWith(".xml"));
			}
		}
		registerClasspathResource(resources, properties.getSecurePrivateKey(), false);
		registerClasspathResource(resources, properties.getSecurePublicKey(), false);
	}

	/**
	 * 只登记 classpath 资源:剥离 classpath: 前缀,目录补 /** 通配;
	 * 文件系统路径在 native 运行期可直接读取、无需进镜像,跳过
	 */
	private void registerClasspathResource(ResourceHints resources, String path, boolean directory) {
		if (StringUtil.isBlank(path)) {
			return;
		}
		String trimmed = path.trim();
		if (trimmed.startsWith("classpath*:")) {
			trimmed = trimmed.substring("classpath*:".length());
		} else if (trimmed.startsWith("classpath:")) {
			trimmed = trimmed.substring("classpath:".length());
		} else if (trimmed.startsWith("file:") || trimmed.startsWith("/") || trimmed.startsWith("\\")
				|| (trimmed.length() > 1 && trimmed.charAt(1) == ':')) {
			// file: 前缀、unix 绝对路径、windows 盘符路径均为文件系统资源
			return;
		}
		while (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		if (trimmed.isEmpty()) {
			return;
		}
		if (directory && !trimmed.contains("*")) {
			trimmed = (trimmed.endsWith("/") ? trimmed : trimmed + "/") + "**";
		}
		resources.registerPattern(trimmed);
	}

	/**
	 * 为按类名配置的扩展点注册反射 hints,清单与 SqltoyAutoConfiguration 中 Class.forName 实例化的属性一一对应
	 */
	private void registerExtensionClasses(RuntimeHints hints, SqlToyContextProperties properties) {
		List<String> candidates = new ArrayList<>(Arrays.asList(properties.getUnifyFieldsHandler(),
				properties.getTranslateCacheManager(), properties.getFirstBizCodeTrace(), properties.getTypeHandler(),
				properties.getDataSourceSelector(), properties.getConnectionFactory(),
				properties.getFieldsSecureProvider(), properties.getDesensitizeProvider(),
				properties.getCustomFilterHandler(), properties.getOverTimeSqlHandler(),
				properties.getDialectDDLGenerator(), properties.getDynamicCacheFetch(),
				properties.getDynamicFecthCacheManager()));
		// sqlFormater 支持简化别名,展开逻辑与 SqltoyAutoConfiguration 保持一致
		String sqlFormater = properties.getSqlFormater();
		if (StringUtil.isNotBlank(sqlFormater)) {
			if (sqlFormater.equalsIgnoreCase("default") || sqlFormater.equalsIgnoreCase("defaultFormater")
					|| sqlFormater.equalsIgnoreCase("defaultSqlFormater")) {
				candidates.add("org.sagacity.sqltoy.plugins.formater.impl.DefaultSqlFormater");
			} else {
				candidates.add(sqlFormater);
			}
		}
		if (properties.getSqlInterceptors() != null) {
			candidates.addAll(Arrays.asList(properties.getSqlInterceptors()));
		}
		for (String candidate : candidates) {
			// 运行期约定:含"."才视为类名反射实例化,否则为 spring bean 名称(由 Spring AOT 自行保障)
			if (StringUtil.isNotBlank(candidate) && candidate.contains(".")) {
				registerClassByName(hints, candidate.trim());
			}
		}
	}

	/**
	 * 条件注册集成实现类:caffeine 翻译缓存管理器在 cacheType=caffeine 时于启动期反射实例化;
	 * mongo/redis 集成实现在使用时反射实例化,仅在对应依赖在 classpath 上时注册,
	 * 避免依赖缺失时 native 构建因类型不可解析而失败
	 */
	private void registerIntegrationClasses(RuntimeHints hints, SqlToyContextProperties properties) {
		if ("caffeine".equalsIgnoreCase(properties.getCacheType())) {
			registerClassByName(hints, "org.sagacity.sqltoy.translate.cache.impl.TranslateCaffeineManager");
		}
		if (present("com.mongodb.client.MongoClient")) {
			registerClassByName(hints, "org.sagacity.sqltoy.integration.impl.SpringMongoQuery");
		}
		if (present("org.springframework.data.redis.core.RedisTemplate")) {
			registerClassByName(hints, "org.sagacity.sqltoy.integration.impl.SpringRedisIdGenerator");
		}
		// 扩展类型反射访问的驱动专属类(PG系含vastbase/opengauss/mogdb/gaussdb/kingbase衍生品,非仅org.postgresql)
		for (String driverClass : EXT_TYPE_DRIVER_CLASSES) {
			if (present(driverClass)) {
				registerClassByName(hints, driverClass);
			}
		}
	}

	/**
	 * 注册实际生效的 JsonProvider 实现类:走与 SqlToyContext.initialize 相同的 initAtStartup 解析路径
	 * (显式配置的别名/类名优先,否则 classpath 探测),使实现类及其引用的 JSON 库进入镜像,
	 * native 运行期的 Class.forName 探测/实例化才可用;classpath 无可用 JSON 库时跳过(JSON 特性本就不可用)
	 */
	private void registerJsonProvider(RuntimeHints hints, SqlToyContextProperties properties) {
		try {
			JsonProvider provider = JsonProviders.initAtStartup(properties.getJsonProvider());
			if (provider != null) {
				hints.reflection().registerType(provider.getClass(), MemberCategory.values());
			}
		} catch (Throwable e) {
			logger.warn("sqltoy aot: json provider [{}] can not be resolved, its reflection hints are not registered!",
					properties.getJsonProvider());
		}
	}

	private void registerClassByName(RuntimeHints hints, String className) {
		try {
			hints.reflection().registerType(Class.forName(className), MemberCategory.values());
		} catch (Throwable e) {
			logger.warn("sqltoy aot: class [{}] can not be loaded, its reflection hints are not registered!",
					className);
		}
	}

	private static boolean present(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (Throwable e) {
			return false;
		}
	}
}
