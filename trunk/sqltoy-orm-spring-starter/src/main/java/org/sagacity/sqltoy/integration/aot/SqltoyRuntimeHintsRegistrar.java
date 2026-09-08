package org.sagacity.sqltoy.integration.aot;

import java.util.stream.Stream;

import org.sagacity.sqltoy.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.configure.SqlToyContextTaskPoolProperties;
import org.sagacity.sqltoy.plugins.id.impl.DefaultIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.NanoTimeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.RedisIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.SnowflakeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.UUIDGenerator;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Spring AOT 运行时 hints 注册：为配置属性类、主键生成器及缓存翻译相关资源注册反射与资源访问 hints
 *
 * @author limliu
 * @since 5.6
 */
class SqltoyRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Stream.of(
                "sqltoy-translate.xml",
                "translates/**",
                "sqltoy/**"
        ).forEach(x -> hints.resources().registerPattern(x));
        Stream.of(
                SqlToyContextProperties.class,
                SqlToyContextTaskPoolProperties.class,
                DefaultIdGenerator.class,
                NanoTimeIdGenerator.class,
                RedisIdGenerator.class,
                SnowflakeIdGenerator.class,
                UUIDGenerator.class
        ).forEach(x -> hints.reflection().registerType(x, MemberCategory.values()));
    }
}