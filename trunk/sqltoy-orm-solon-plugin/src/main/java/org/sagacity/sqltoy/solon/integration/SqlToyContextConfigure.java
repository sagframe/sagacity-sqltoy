package org.sagacity.sqltoy.solon.integration;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.core.AppContext;
import org.noear.solon.data.cache.CacheService;
import org.noear.solon.data.cache.LocalCacheService;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.solon.DbManager;
import org.sagacity.sqltoy.solon.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.solon.translate.cache.impl.SolonTranslateCacheManager;

/**
 * SqlToyContext 配置
 *
 * @author noear 2025/12/7 created
 * @since 5.6
 */
@Configuration
public class SqlToyContextConfigure {
    @Bean
    public SqlToyContextProperties properties(AppContext context) {
        SqlToyContextProperties properties = context.cfg().toBean("solon.sqltoy", SqlToyContextProperties.class);
        if (properties == null) {
            //old:
            properties = context.cfg().toBean("sqltoy", SqlToyContextProperties.class);
        }
        if (properties == null) {
            //def:
            properties = new SqlToyContextProperties();
        }

        if (Solon.cfg().isDebugMode()) {
            properties.setDebug(true);
        }

        return properties;
    }

    @Condition(onBean = CacheService.class)
    @Bean
    public void cacheService(SolonTranslateCacheManager cacheManager,
                             CacheService cacheService) {
        cacheManager.setCacheService(cacheService);
    }

    @Bean
    public SolonTranslateCacheManager solonTranslateCacheManager() {
        return new SolonTranslateCacheManager(new LocalCacheService());
    }

    @Bean
    public SqlToyContext sqlToyContext(
            AppContext appContext,
            SqlToyContextProperties properties,
            SolonTranslateCacheManager solonTranslateCacheManager) throws Exception {

        SolonAppContext solonAppContext = new SolonAppContext(appContext);
        final SqlToyContext sqlToyContext = new SqlToyContextBuilder(properties, solonAppContext).build();

        if ("solon".equals(properties.getCacheType())) {
            sqlToyContext.setTranslateCacheManager(solonTranslateCacheManager);
            try {
                DbManager.setContext(sqlToyContext);
                sqlToyContext.initialize();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            DbManager.setContext(sqlToyContext);
            sqlToyContext.initialize();
        }

        return sqlToyContext;
    }
}