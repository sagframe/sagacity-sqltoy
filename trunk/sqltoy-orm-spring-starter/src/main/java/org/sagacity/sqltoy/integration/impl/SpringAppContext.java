package org.sagacity.sqltoy.integration.impl;

import java.util.Map;

import org.sagacity.sqltoy.integration.AppContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @project sagacity-sqltoy
 * @description 基于spring的bean管理，主要用于获取Bean实例
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public class SpringAppContext implements AppContext, ApplicationContextAware {
	private ApplicationContext applicationContext;

	public SpringAppContext() {
	}

	public SpringAppContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public boolean containsBean(String beanName) {
		return applicationContext.containsBean(beanName);
	}

	@Override
	public Object getBean(String beanName) {
		return applicationContext.getBean(beanName);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) {
		return applicationContext.getBean(requiredType);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> type) {
		return applicationContext.getBeansOfType(type);
	}

	@Deprecated
	public void setContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * @param applicationContext the applicationContext to set
	 */
	@Autowired
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

}
