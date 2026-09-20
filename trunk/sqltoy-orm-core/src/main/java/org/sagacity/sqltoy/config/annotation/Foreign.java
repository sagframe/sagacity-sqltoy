package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy
 * @description 外键标记
 * @author zhongxuchen
 * @version v1.0,Date:2023-07-13
 * @modify Date:2023-07-13,修改说明
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

	// 删除参照动作,取值见ReferentialAction常量:0:CASCADE,1:RESTRICT(默认),2:SET_NULL,3:NO_ACTION,4:SET_DEFAULT
	int deleteRestict() default 1;

	// 修改参照动作,取值同deleteRestict,见ReferentialAction常量
	int updateRestict() default 1;
}
