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
import org.sagacity.sqltoy.plugins.id.impl.DefaultIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.NanoTimeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.RedisIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.SnowflakeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.UUIDGenerator;
import org.sagacity.sqltoy.plugins.json.JsonProvider;
import org.sagacity.sqltoy.plugins.json.JsonProviders;
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
 * update 2026-10-18 增强(6.0.8)：与 spring-starter 的 SqltoyBeanFactoryInitializationAotProcessor 对称，
 * 按实际配置动态注册 sql 文件、缓存翻译配置、加解密密钥的资源 pattern，为按类名配置的扩展类、
 * 条件依赖的集成实现类(SolonTranslateCacheManager/SolonMongoQuery/SolonRedisIdGenerator)
 * 及实际生效的 JsonProvider 实现类注册反射元数据。
 */
public class SqltoyRuntimeNativeRegistrar implements RuntimeNativeRegistrar {
    private static final Logger logger = LoggerFactory.getLogger(SqltoyRuntimeNativeRegistrar.class);

    /** 与 SqlToyContextBuilder.strSplitTrim 保持一致:多目录配置支持中英文逗号、分号分隔 */
    private static final String DIR_SPLIT_REGEX = "[,;，；]";

    @Override
    public void register(AppContext context, RuntimeNativeMetadata metadata) {
        //静态资源
        Stream.of(
                "sqltoy-translate.xml",
                "translates/.*",
                "sqltoy/.*"
        ).forEach(metadata::registerResourceInclude);
        //通用逻辑
        Stream.of(
                SqlToyContextProperties.class,
                SqlToyContextTaskPoolProperties.class,
                DefaultIdGenerator.class,
                NanoTimeIdGenerator.class,
                RedisIdGenerator.class,
                SnowflakeIdGenerator.class,
                UUIDGenerator.class
        ).forEach(x -> metadata.registerReflection(x, MemberCategory.values()));
        //XMLUtil.setAttributes 反射赋值的 xml 解析模型类(sql 结果处理器 + 缓存翻译配置)
        Stream.of(
                UnpivotModel.class,
                ColsChainRelativeModel.class,
                RowsChainRelativeModel.class,
                ReverseModel.class,
                TreeSortModel.class,
                DefaultConfig.class,
                TranslateConfigModel.class,
                CheckerConfigModel.class
        ).forEach(x -> metadata.registerReflection(x, MemberCategory.values()));
        //lambda 条件体系(6.0.8)经 writeReplace/SerializedLambda 序列化镜像提取属性元数据,
        //native 下 SerializedLambda 需要 java 序列化注册,否则 lambdaQuery/lambdaUpdate 全部不可用;
        //镜像类 org.sagacity.sqltoy.lambda.toolkit.SerializedLambda 是反序列化(resolveClass 重定向)的目标,同样需注册
        metadata.registerSerialization(SerializedLambda.class);
        metadata.registerSerialization(org.sagacity.sqltoy.lambda.toolkit.SerializedLambda.class);
        //sqltoy entity集合
        context.getBeanAsync(SqlToyContext.class, bean -> {
            bean.getEntityManager().getAllEntities().entrySet().forEach(entry -> {
                metadata.registerReflection(entry.getValue().getEntityClass(), MemberCategory.values());
            });
            //SqlToyContext 初始化后 JsonProviders 已完成显式配置或 classpath 探测,注册实际生效的实现类
            registerJsonProvider(metadata);
        });
        //按实际配置动态注册资源pattern与扩展类反射
        context.getBeanAsync(SqlToyContextProperties.class, properties -> {
            registerConfiguredResources(metadata, properties);
            registerExtensionClasses(metadata, properties);
            registerIntegrationClasses(metadata, properties);
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
     * 只登记 classpath 资源:剥离 classpath:/resource: 前缀,目录补 /.* 通配(solon 的资源 pattern 为正则形式);
     * 文件系统路径在 native 运行期可直接读取、无需进镜像,跳过
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
    }

    /**
     * 条件注册集成实现类:caffeine 翻译缓存管理器在 cacheType=caffeine 时于启动期反射实例化;
     * mongo/redis 集成实现在使用时反射实例化,仅在对应依赖在 classpath 上时注册,
     * 避免依赖缺失时 native 构建因类型不可解析而失败
     */
    private void registerIntegrationClasses(RuntimeNativeMetadata metadata, SqlToyContextProperties properties) {
        if ("caffeine".equalsIgnoreCase(properties.getCacheType())) {
            registerClassByName(metadata, "org.sagacity.sqltoy.solon.translate.cache.impl.SolonTranslateCacheManager");
        }
        if (present("com.mongodb.client.MongoClient")) {
            registerClassByName(metadata, "org.sagacity.sqltoy.solon.integration.SolonMongoQuery");
        }
        if (present("org.noear.redisx.RedisClient")) {
            registerClassByName(metadata, "org.sagacity.sqltoy.solon.integration.SolonRedisIdGenerator");
        }
        // PG 系扩展类型(json/jsonb/vector/geometry)经 DBProfile.PGobjectHolder 反射构造与读写 PGobject
        if (present("org.postgresql.util.PGobject")) {
            registerClassByName(metadata, "org.postgresql.util.PGobject");
        }
    }

    /**
     * 注册实际生效的 JsonProvider 实现类:使实现类及其引用的 JSON 库进入镜像,
     * native 运行期的 Class.forName 探测/实例化才可用;classpath 无可用 JSON 库时跳过
     */
    private void registerJsonProvider(RuntimeNativeMetadata metadata) {
        try {
            JsonProvider provider = JsonProviders.get();
            if (provider != null) {
                metadata.registerReflection(provider.getClass(), MemberCategory.values());
            }
        } catch (Throwable e) {
            logger.debug("sqltoy aot: no usable json provider, skip its reflection hints registration");
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
