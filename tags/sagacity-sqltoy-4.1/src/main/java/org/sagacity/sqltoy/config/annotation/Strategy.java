/**
 * 
 */
package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy4.0
 * @description 分库分表的策略配置
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingDB.java,Revision:v1.0,Date:2017年11月5日
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Strategy {
	/**
	 * 分库分表策略
	 * 
	 * @return
	 */
	String name() default "";

	/**
	 * 决策字段(以哪几个字段值作为切分策略,一般主键或分类字段)
	 * 
	 * @return
	 */
	String[] fields() default {};

	/**
	 * 别名
	 * 
	 * @return
	 */
	String[] aliasNames() default {};

	/**
	 * 决策类型
	 * 
	 * @return
	 */
	String decisionType() default "";

}
