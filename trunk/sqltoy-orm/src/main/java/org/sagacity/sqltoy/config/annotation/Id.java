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
 * @description 关于数据库表主键的注解定义
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:Id.java,Revision:v1.0,Date:2012-5-25 下午1:52:53
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
	// 主键产生策略，默认为手工赋予
	String strategy() default "assign";

	// 对应sequence name
	String sequence() default "";

	// 主键产生类
	String generator() default "";
}
