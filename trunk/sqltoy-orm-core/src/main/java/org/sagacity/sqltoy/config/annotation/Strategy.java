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
 * @description 分库分表的策略配置
 * @author zhongxuchen
 * @version v1.0,Date:2017年11月5日
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Strategy {
	// 分库分表策略
	String name() default "";

	// 决策字段(以哪几个字段值作为切分策略,一般主键或分类字段)
	String[] fields() default {};

	// 别名
	String[] aliasNames() default {};

	// 决策类型(预留)
	String decisionType() default "";

}
