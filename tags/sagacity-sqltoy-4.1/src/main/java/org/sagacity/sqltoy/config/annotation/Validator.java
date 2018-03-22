/**
 * 
 */
package org.sagacity.sqltoy.config.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(TYPE)
/**
 * @project sagacity-sqltoy4.0
 * @description 数据验证
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Validator.java,Revision:v1.0,Date:2018年1月15日
 */
public @interface Validator {
	
}
