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
 * @description 增加针对租户字段标志的注解
 * @author zhongxuchen
 * @version v1.0, Date:2022年9月15日
 * @modify 2022年9月15日,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = { ElementType.TYPE, ElementType.FIELD })
public @interface Tenant {
	// 租户字段
	String field() default "";
}
