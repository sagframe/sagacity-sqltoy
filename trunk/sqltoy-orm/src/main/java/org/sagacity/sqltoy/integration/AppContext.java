package org.sagacity.sqltoy.integration;

import java.util.Map;

/**
 * @project sagacity-sqltoy
 * @description 构建一个应用上下文接口，适配spring和其他框架
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public interface AppContext {
	/**
	 * @TODO 判断容器种是否存在相应的bean
	 * @param beanName
	 * @return
	 */
	public boolean containsBean(String beanName);

	/**
	 * @TODO 根据名称获取bean的实例
	 * @param beanName
	 * @return
	 */
	public Object getBean(String beanName);

	/**
	 * @TODO 根据类型获取bean的实例
	 * @param <T>
	 * @param requiredType
	 * @return
	 */
	public <T> T getBean(Class<T> requiredType);

	/**
	 * @TODO 根据类型获取所有相关bean
	 * @param <T>
	 * @param type
	 * @return
	 */
	public <T> Map<String, T> getBeansOfType(Class<T> type);
}
