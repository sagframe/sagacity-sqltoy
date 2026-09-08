package org.sagacity.sqltoy.integration.aot;

import org.sagacity.sqltoy.SqlToyContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Spring AOT 处理器：构建期为全部已注册实体类注册反射 hints，保障 GraalVM Native Image 下 ORM 反射可用
 *
 * @author limliu
 * @since 5.6
 */
class SqltoyBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {
    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        return (context, code) -> {
            RuntimeHints hints = context.getRuntimeHints();
            SqlToyContext sqlToyContext = beanFactory.getBean(SqlToyContext.class);
            sqlToyContext.getEntityManager().getAllEntities().entrySet().forEach(entry -> {
                hints.reflection().registerType(entry.getValue().getEntityClass(), MemberCategory.values());
            });
        };
    }
}
