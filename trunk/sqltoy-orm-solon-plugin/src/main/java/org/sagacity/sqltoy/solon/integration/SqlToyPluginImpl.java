package org.sagacity.sqltoy.solon.integration;

import org.noear.solon.aot.RuntimeNativeRegistrar;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.runtime.NativeDetector;
import org.noear.solon.core.util.ClassUtil;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.solon.annotation.Db;
import org.sagacity.sqltoy.solon.integration.aot.SqltoyRuntimeNativeRegistrar;

/**
 * 去除spring依赖，适配到Solon的Tran、Aop。TranslateCache默认设置为Solon CacheService
 * 实现Mapper接口功能
 *
 * @author 夜の孤城
 * @since 1.5
 * @since 1.8
 */
public class SqlToyPluginImpl implements Plugin {
    private AppContext context;

    @Override
    public void start(AppContext context) throws Throwable {
        // aot
        if (NativeDetector.isAotRuntime() && ClassUtil.hasClass(() -> RuntimeNativeRegistrar.class)) {
            context.wrapAndPut(SqltoyRuntimeNativeRegistrar.class);
        }
        this.context = context;

        context.beanMake(SqlToyContextConfigure.class);
        context.beanInjectorAdd(Db.class, new SqlToyDbInjector());
    }

    @Override
    public void stop() throws Throwable {
        SqlToyContext sqlToyContext = context.getBean(SqlToyContext.class);

        if (sqlToyContext != null) {
            sqlToyContext.destroy();
        }
    }
}
