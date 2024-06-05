package org.sagacity.sqltoy.solon.integration;

import org.noear.solon.Solon;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.data.cache.CacheService;
import org.sagacity.sqltoy.solon.DbManager;
import org.sagacity.sqltoy.solon.annotation.Db;
import org.sagacity.sqltoy.solon.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.solon.translate.cache.impl.SolonTranslateCacheManager;
import org.sagacity.sqltoy.SqlToyContext;

/**
 * 去除spring依赖，适配到Solon的Tran、Aop。TranslateCache默认设置为Solon CacheService
 * 实现Mapper接口功能
 *
 * @author 夜の孤城
 * @since 1.5
 * @since 1.8
 */
public class SqlToyPluginImpl implements Plugin {

    AppContext context;

    @Override
    public void start(AppContext context) throws Throwable {
        this.context = context;

        //尝试初始化 rdb
        SqlToyContextProperties properties = context.cfg().getBean("solon.sqltoy", SqlToyContextProperties.class);
        if (properties == null) {
            //old:
            properties = context.cfg().getBean("sqltoy", SqlToyContextProperties.class);
        }
        if (properties == null) {
            //def:
            properties = new SqlToyContextProperties();
        }


        if (Solon.cfg().isDebugMode()) {
            properties.setDebug(true);
        }

        SolonAppContext solonAppContext = new SolonAppContext(context);
        final SqlToyContext sqlToyContext = new SqlToyContextBuilder(properties, solonAppContext).build();

        if ("solon".equals(properties.getCacheType()) || properties.getCacheType() == null) {
            context.getWrapAsync(CacheService.class, bw -> {
                sqlToyContext.setTranslateCacheManager(new SolonTranslateCacheManager(bw.get()));
                try {
                    DbManager.setContext(sqlToyContext);
                    initSqlToy(sqlToyContext);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } else {
            DbManager.setContext(sqlToyContext);
            initSqlToy(sqlToyContext);
        }

        //新的注解包
        context.beanInjectorAdd(Db.class, new SqlToyDbInjector());
    }

    private void initSqlToy(SqlToyContext sqlToyContext) throws Exception {
        sqlToyContext.initialize();
        context.wrapAndPut(SqlToyContext.class, sqlToyContext);
    }

    @Override
    public void stop() throws Throwable {
        SqlToyContext sqlToyContext = context.getBean(SqlToyContext.class);
        sqlToyContext.destroy();
    }
}
