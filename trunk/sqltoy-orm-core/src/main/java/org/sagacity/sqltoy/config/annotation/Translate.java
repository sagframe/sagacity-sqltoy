package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sqltoy-orm
 * @description 针对pojo对象属性提供缓存翻译注解配置
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-15
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Repeatable(value=Translates.class)
public @interface Translate {
	// 缓存名称
	String cacheName();

	// 缓存类型(一般适用于类似数据字典场景，对应字典类别)
	String cacheType() default "";

	// 缓存对应的列,默认为1
	int cacheIndex() default 1;

	// key来源字段，比如organId
	String keyField();

	// 适用于key1,key2,key3 场景分割翻译
	String split() default "";

	// 针对split分割翻译后结果拼接字符串定义
	String join() default "";

	// add 2024-12-29 example: orderType==PO
	String where() default "";

	// 未匹配的模板,默认:[${value}]未定义,${value} 引用key值
	String uncached() default "";
}
