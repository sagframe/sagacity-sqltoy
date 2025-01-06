package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sqltoy-orm
 * @description 针对pojo对象属性提供缓存翻译多注解配置
 * @author zhongxuchen
 * @version v1.0,Date:2025-1-2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Translates {
	Translate[] value();
}
