package org.sagacity.sqltoy.solon.integration.aot;

import org.noear.solon.aot.RuntimeNativeMetadata;
import org.noear.solon.aot.RuntimeNativeRegistrar;
import org.noear.solon.aot.hint.MemberCategory;
import org.noear.solon.core.AppContext;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.plugins.id.impl.*;
import org.sagacity.sqltoy.solon.configure.SqlToyContextProperties;

import java.util.stream.Stream;

/**
 * native注册类
 */
public class SqltoyRuntimeNativeRegistrar implements RuntimeNativeRegistrar {

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
                DefaultIdGenerator.class,
                NanoTimeIdGenerator.class,
                RedisIdGenerator.class,
                SnowflakeIdGenerator.class,
                UUIDGenerator.class
        ).forEach(x -> metadata.registerReflection(x, MemberCategory.values()));
        //sqltoy entity集合
        context.getBeanAsync(SqlToyContext.class, bean -> {
            bean.getEntityManager().getAllEntities().entrySet().forEach(entry -> {
                metadata.registerReflection(entry.getValue().getEntityClass(), MemberCategory.values());
            });
        });
    }
}
