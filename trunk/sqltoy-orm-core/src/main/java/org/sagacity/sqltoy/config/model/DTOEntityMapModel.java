/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * @project sagacity-sqltoy
 * @description DTO(或VO)和POJO 属性映射关系模型,便于放入缓存快速提取
 * @author zhongxuchen
 * @version v1.0, Date:2020年8月8日
 * @modify 2020年8月8日,修改说明
 */
public class DTOEntityMapModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8851171361533403505L;

	// from
	public String fromClassName;

	public String[] fromProps;

	public Method[] fromGetMethods;

	// target
	public String targetClassName;
	public String[] targetProps;

	public Method[] targetSetMethods;
}
