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
 * @description sqltoy entity 对象字段注解定义
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:Column.java,Revision:v1.0,Date:2012-5-25 下午1:53:05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
	// 数据类型
	int type();

	// 字段名称
	String name();

	// 是否为空
	boolean nullable() default true;

	// 长度
	long length() default 0;

	// 数字类型的总长度
	int precision() default 0;

	// 小数位长度
	int scale() default 0;

	//是否是关键词
	boolean keyword() default false;

	// 默认值
	String defaultValue() default "";

	// 是否自增
	boolean autoIncrement() default false;
}
