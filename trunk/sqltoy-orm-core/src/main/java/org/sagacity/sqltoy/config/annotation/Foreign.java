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
 * @description 外键标记
 * @author zhongxuchen
 * @version v1.0, Date:2023年7月13日
 * @modify 2023年7月13日,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Foreign {

	// 约束名称
	String constraintName() default "";

	// 外键表名称
	String table();

	// 外键表的字段
	String field();

	// 删除级联
	int deleteRestict() default 1;

	// 修改级联约束
	int updateRestict() default 1;
}
