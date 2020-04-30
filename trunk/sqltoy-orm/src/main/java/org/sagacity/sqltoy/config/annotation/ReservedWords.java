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
 * @description 标注哪些字段是保留字,从而产生sql时增加对应的符号
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ReservedWords.java,Revision:v1.0,Date:2020年4月30日
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReservedWords {
	/**
	 * 在vo类上指定哪些字段名称是保留字
	 * 
	 * @return
	 */
	String[] fields() default {};
}
