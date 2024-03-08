/**
 * 
 */
package org.sagacity.sqltoy.config.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy
 * @description 数据表记录版本注解，控制更新过程，如果当前版本大于更新版本抛出异常
 * @author zhongxuchen
 * @version v1.0, Date:2022年9月15日
 * @modify 2022年9月15日,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ TYPE, FIELD })
public @interface DataVersion {
	// 数据版本字段
	String field() default "";

	// 是否以日期开头:20220915001
	boolean startDate() default false;
}
