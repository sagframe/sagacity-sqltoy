/**
 * 
 */
package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sqltoy-orm
 * @description 主键被其他表关联的机制
 * @author zhongxuchen
 * @version v1.0,Date:2012-7-30
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneToMany {
	// 主表列名(many column)
	String[] fields() default {};

	// 关联表对应对象的属性
	String[] mappedFields();

	// 加载自定义sql
	String load() default "";

	// 是否级联删除
	boolean delete() default false;

	// 排序
	String orderBy() default "";

	// 定制级联修改保存对子表的操作语句
	String update() default "";

}
