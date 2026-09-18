package org.sagacity.sqltoy.solon.integration;

import java.util.HashMap;
import java.util.Map;

import org.noear.solon.core.AppContext;

/**
 * 基于 Solon AppContext 实现的 sqltoy IoC 集成上下文，为 sqltoy 提供Bean的查找与获取能力
 *
 * @author noear
 * @since 5.6
 */
public class SolonAppContext implements org.sagacity.sqltoy.integration.AppContext {
    AppContext context;

    public SolonAppContext(AppContext context) {
        this.context = context;
    }

    @Override
    public boolean containsBean(String s) {
        return context.hasWrap(s);
    }

    @Override
    public Object getBean(String s) {
        return context.getBean(s);
    }

    @Override
    public <T> T getBean(Class<T> aClass) {
        return context.getBean(aClass);
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> aClass) {
        Map<String, T> beans = new HashMap<>();

        context.beanForeach(beanWrap -> {
            if (aClass.isInstance(beanWrap.get())) {
                beans.put(beanWrap.name(), beanWrap.get());
            }
        });
        return beans;
    }
}
