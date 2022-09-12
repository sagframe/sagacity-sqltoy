package org.sagacity.sqltoy.integration.impl;

import java.util.Map;

import org.sagacity.sqltoy.integration.AppContext;
import org.springframework.context.ApplicationContext;

/**
 * @project sagacity-sqltoy
 * @description 基于spring的bean管理，主要用于获取Bean实例
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public class SpringAppContext implements AppContext {
	private ApplicationContext context;

	public SpringAppContext() {
	}

	public SpringAppContext(ApplicationContext context) {
		this.context = context;
	}

	@Override
	public boolean containsBean(String beanName) {
		return context.containsBean(beanName);
	}

	@Override
	public Object getBean(String beanName) {
		return context.getBean(beanName);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) {
		return context.getBean(requiredType);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> type) {
		return context.getBeansOfType(type);
	}

	public void setContext(ApplicationContext context) {
		this.context = context;
	}

}
