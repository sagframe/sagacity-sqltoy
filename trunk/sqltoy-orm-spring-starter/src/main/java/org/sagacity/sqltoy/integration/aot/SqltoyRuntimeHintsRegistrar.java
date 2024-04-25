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