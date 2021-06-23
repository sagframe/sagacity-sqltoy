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

	//dto
	public String dtoClassName;

	public String[] dtoProps;


	public Method[] dtoSetMethods;

	public Method[] dtoGetMethods;

	//pojo entity
	public String pojoClassName;
	public String[] pojoProps;

	public Method[] pojoSetMethods;

	public Method[] pojoGetMethods;

}
