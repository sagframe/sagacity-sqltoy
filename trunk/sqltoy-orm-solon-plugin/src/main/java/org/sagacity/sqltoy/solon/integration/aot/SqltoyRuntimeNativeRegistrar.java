package org.sagacity.sqltoy.solon.integration.aot;

import java.lang.invoke.SerializedLambda;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.noear.solon.aot.RuntimeNativeMetadata;
import org.noear.solon.aot.RuntimeNativeRegistrar;
import org.noear.solon.aot.hint.MemberCategory;
import org.noear.solon.core.AppContext;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.ReverseModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.id.impl.DefaultIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.NanoTimeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.RedisIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.SnowflakeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.UUIDGenerator;
import org.sagacity.sqltoy.solon.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.solon.configure.SqlToyContextTaskPoolProperties;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * native注册类
 * <p>
 * update 2026-10-18 增强(6.0.8)：与 spring-starter 的
 * SqltoyBeanFactoryInitializationAotProcessor 对称， 按实际配置动态注册 sql
 * 文件、缓存翻译配置、加解密密钥的资源 pattern，为按类名配置的扩展类、
 * 条件依赖的集成实现类(SolonTranslateCacheManager/SolonMongoQuery/SolonRedisIdGenerator)
 * 及实际生效的 JsonProvider 实现类注册反射元数据。
 */
public class SqltoyRuntimeNativeRegistrar implements RuntimeNativeRegistrar {
	private static final Logger logger = LoggerFactory.getLogger(SqltoyRuntimeNativeRegistrar.class);

	/** 与 SqlToyContextBuilder.strSplitTrim 保持一致:多目录配置支持中英文逗号、分号分隔 */
	private static final String DIR_SPLIT_REGEX = "[,;，；]";

	/**
	 * 扩展类型(json/jsonb/vector/geometry等)经反射访问的驱动专属类,仅在对应驱动位于classpath时注册,
	 * 清单与spring侧SqltoyBeanFactoryInitializationAotProcessor.EXT_TYPE_DRIVER_CLASSES一致:
	 * PG系PGobject同构类5个(postgresql及stardb等兜底/opengauss与mogdb/vastbase/gaussdb/kingbase
	 * KBobject)、 mssql-jdbc的Geometry与13.x Vector、oracle的OracleTypes
	 */
	private static final String[] EXT_TYPE_DRIVER_CLASSES = { "org.postgresql.util.PGobject",
			"org.opengauss.util.PGobject", "cn.com.vastbase.util.PGobject", "com.huawei.gaussdb.jdbc.util.PGobject",
			"com.kingbase8.util.KBobject", "com.microsoft.sqlserver.jdbc.Geometry", "microsoft.sql.Vector",
			"microsoft.sql.Vector$VectorDimensionType", "oracle.jdbc.OracleTypes" };

	@Override
	public void register(AppContext context, RuntimeNativeMetadata metadata) {
		// 静态资源
		Stream.of("sqltoy-translate.xml", "translates/.*", "sqltoy/.*").forEach(metadata::registerResourceInclude);
		// 通用逻辑
		Stream.of(SqlToyContextProperties.class, SqlToyContextTaskPoolProperties.class, DefaultIdGenerator.class,
				NanoTimeIdGenerator.class, RedisIdGenerator.class, SnowflakeIdGenerator.class, UUIDGenerator.class)
				.forEach(x -> metadata.registerReflection(x, MemberCategory.values()));
		// XMLUtil.setAttributes 反射赋值的 xml 解析模型类(sql 结果处理器 + 缓存翻译配置)
		Stream.of(UnpivotModel.class, ColsChainRelativeModel.class, RowsChainRelativeModel.class, ReverseModel.class,
				TreeSortModel.class, DefaultConfig.class, TranslateConfigModel.class, CheckerConfigModel.class)
				.forEach(x -> metadata.registerReflection(x, MemberCategory.values()));
		// lambda 条件体系(6.0.8)经 writeReplace/SerializedLambda 序列化镜像提取属性元数据,
		// native 下 SerializedLambda 需要 java 序列化注册,否则 lambdaQuery/lambdaUpdate 全部不可用;
		// 镜像类 org.sagacity.sqltoy.lambda.toolkit.SerializedLambda 是反序列化(resolveClass
		// 重定向)的目标,同样需注册
		metadata.registerSerialization(SerializedLambda.class);
		metadata.registerSerialization(org.sagacity.sqltoy.lambda.toolkit.SerializedLambda.class);
		// sqltoy entity集合
		context.getBeanAsync(SqlToyContext.class, bean -> {
			bean.getEntityManager().getAllEntities().entrySet().forEach(entry -> {
				metadata.registerReflection(entry.getValue().getEntityClass(), MemberCategory.values());
			});
		});
		// 按实际配置动态注册资源pattern与扩展类反射
		context.getBeanAsync(SqlToyContextProperties.class, properties -> {
			registerConfiguredResources(metadata, properties);
			registerExtensionClasses(metadata, properties);
			registerIntegrationClasses(metadata, properties);
			registerJsonProvider(metadata);
		});
	}

	/**
	 * 按配置动态注册 classpath 资源 pattern:sql 文件目录、显式 sql 文件列表、缓存翻译配置、字段加解密密钥
	 */
	private void registerConfiguredResources(RuntimeNativeMetadata metadata, SqlToyContextProperties properties) {
		if (StringUtil.isNotBlank(properties.getSqlResourcesDir())) {
			for (String dir : properties.getSqlResourcesDir().split(DIR_SPLIT_REGEX)) {
				registerClasspathResource(metadata, dir, true);
			}
		}
		if (properties.getSqlResources() != null) {
			for (String sqlResource : properties.getSqlResources()) {
				registerClasspathResource(metadata, sqlResource, false);
			}
		}
		if (StringUtil.isNotBlank(properties.getTranslateConfig())) {
			for (String item : properties.getTranslateConfig().split(DIR_SPLIT_REGEX)) {
				// 与 SqlToyContextBuilder 的翻译配置解析规则一致:.xml 结尾视为文件,否则视为目录
				registerClasspathResource(metadata, item, !item.trim().endsWith(".xml"));
			}
		}
		registerClasspathResource(metadata, properties.getSecurePrivateKey(), false);
		registerClasspathResource(metadata, properties.getSecurePublicKey(), false);
	}

	/**
	 * 只登记 classpath 资源:剥离 classpath:/resource: 前缀,目录补 /.* 通配(solon 的资源 pattern
	 * 为正则形式); 文件系统路径在 native 运行期可直接读取、无需进镜像,跳过
	 */
	private void registerClasspathResource(RuntimeNativeMetadata metadata, String path, boolean directory) {
		if (StringUtil.isBlank(path)) {
			return;
		}
		String trimmed = path.trim();
		if (trimmed.startsWith("classpath*:")) {
			trimmed = trimmed.substring("classpath*:".length());
		} else if (trimmed.startsWith("classpath:")) {
			trimmed = trimmed.substring("classpath:".length());
		} else if (trimmed.startsWith("resource:")) {
			trimmed = trimmed.substring("resource:".length());
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
			trimmed = (trimmed.endsWith("/") ? trimmed : trimmed + "/") + ".*";
		}
		metadata.registerResourceInclude(trimmed);
	}

	/**
	 * 为按类名配置的扩展点注册反射元数据,清单与 SqlToyContextBuilder 中 Class.forName 实例化的属性一一对应
	 */
	private void registerExtensionClasses(RuntimeNativeMetadata metadata, SqlToyContextProperties properties) {
		List<String> candidates = new ArrayList<>(Arrays.asList(properties.getUnifyFieldsHandler(),
				properties.getTranslateCacheManager(), properties.getFirstBizCodeTrace(), properties.getTypeHandler(),
				properties.getDataSourceSelector(), properties.getConnectionFactory(),
				properties.getFieldsSecureProvider(), properties.getDesensitizeProvider(),
				properties.getCustomFilterHandler(), properties.getOverTimeSqlHandler(),
				properties.getDialectDDLGenerator(), properties.getDynamicCacheFetch(),
				properties.getDynamicFecthCacheManager()));
		// sqlFormater 支持简化别名,展开逻辑与 SqlToyContextBuilder 保持一致
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
			// 运行期约定:含"."才视为类名反射实例化,否则为 bean 名称(由 solon aot 自行保障)
			if (StringUtil.isNotBlank(candidate) && candidate.contains(".")) {
				registerClassByName(metadata, candidate.trim());
			}
		}
		// functionConverts:bean 创建期 FunctionUtils 对解析出的每个函数类(含 default 展开的内置集)
		// Class.forName 实例化,与运行期同语义解析后逐个注册,否则配置了 functionConverts 的应用 native 启动即失败
		if (properties.getFunctionConverts() != null) {
			for (String functionName : FunctionUtils.resolveFunctionClassNames(properties.getFunctionConverts())) {
				registerClassByName(metadata, functionName);
			}
		}
	}

	/**
	 * 条件注册集成实现类:注册跟随 classpath 而非构建期配置——cacheType/jsonProvider 均支持运行期切换, native
	 * 镜像构建期冻结,按构建期配置注册会让"运行期切到另一实现"因未注册而 Class.forName 失败;
	 * SolonTranslateCacheManager 为本插件类且 solon-data 恒在 classpath(有无参构造供
	 * cacheType=caffeine 路径反射实例化),无条件注册;mongo/redis 集成实现在对应依赖在 classpath 上时注册,
	 * 避免依赖缺失时 native 构建因类型不可解析而失败
	 */
	private void registerIntegrationClasses(RuntimeNativeMetadata metadata, SqlToyContextProperties properties) {
		registerClassByName(metadata, "org.sagacity.sqltoy.solon.translate.cache.impl.SolonTranslateCacheManager");
		if (present("com.mongodb.client.MongoClient")) {
			registerClassByName(metadata, "org.sagacity.sqltoy.solon.integration.SolonMongoQuery");
		}
		if (present("org.noear.redisx.RedisClient")) {
			registerClassByName(metadata, "org.sagacity.sqltoy.solon.integration.SolonRedisIdGenerator");
		}
		// 扩展类型反射访问的驱动专属类(PG系含vastbase/opengauss/mogdb/gaussdb/kingbase衍生品,非仅org.postgresql)
		for (String driverClass : EXT_TYPE_DRIVER_CLASSES) {
			if (present(driverClass)) {
				registerClassByName(metadata, driverClass);
			}
		}
	}

	/**
	 * JsonProvider 注册跟随 classpath:三个内置实现凡对应 JSON 库 marker 在 classpath 即全部注册 (marker
	 * 与 JsonProviders.CANDIDATES 一致)——solon 侧无 jsonProvider 配置项,运行期纯靠探测,
	 * 探测/降级目标必须已注册;注册使实现类及其引用的 JSON 库进入镜像,native 运行期 Class.forName 才可用
	 */
	private void registerJsonProvider(RuntimeNativeMetadata metadata) {
		if (present("com.alibaba.fastjson2.JSON")) {
			registerClassByName(metadata, "org.sagacity.sqltoy.plugins.json.FastjsonJsonProvider");
		}
		if (present("tools.jackson.databind.ObjectMapper")) {
			registerClassByName(metadata, "org.sagacity.sqltoy.plugins.json.Jackson3JsonProvider");
		}
		if (present("com.fasterxml.jackson.databind.ObjectMapper")) {
			registerClassByName(metadata, "org.sagacity.sqltoy.plugins.json.Jackson2JsonProvider");
		}
	}

	private void registerClassByName(RuntimeNativeMetadata metadata, String className) {
		try {
			metadata.registerReflection(Class.forName(className), MemberCategory.values());
		} catch (Throwable e) {
			logger.warn("sqltoy aot: class [{}] can not be loaded, its reflection metadata is not registered!",
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
