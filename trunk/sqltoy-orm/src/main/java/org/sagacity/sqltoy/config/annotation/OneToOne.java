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
	// 主表列名(many column)
	String[] fields() default {};

	// 关联表对应对象的属性
	String[] mappedFields();

	// 是否级联删除
	boolean delete() default false;

	// 加载自定义sql
	String load() default "";

	// 定制级联修改保存对子表的操作语句
	String update() default "";

	// 级联子表不为null的字段(for hiberarchy)
	String notNullField() default "";
}
