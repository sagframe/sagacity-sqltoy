/**
 * 
 */
package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy
 * @description 提供one to one 的关联
 * @author zhongxuchen
 * @version v1.0, Date:2021-2-24
 * @modify 2021-2-24,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneToOne {
	/**
	 * 主表列名(many column)
	 * 
	 * @return
	 */
	String[] fields();

	/**
	 * 关联表对应对象的属性
	 * 
	 * @return
	 */
	String[] mappedFields();

	/**
	 * 是否级联删除
	 * 
	 * @return
	 */
	boolean delete() default false;

	/**
	 * 是否级联修改
	 * 
	 * @return
	 */
	boolean update() default false;
}
