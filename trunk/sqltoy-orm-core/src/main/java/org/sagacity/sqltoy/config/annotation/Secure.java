package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.sagacity.sqltoy.model.SecureType;

/**
 * @project sagacity-sqltoy
 * @description 字段加解密具体字段、类型、脱敏方式配置
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Secure {
	// 字段名称
	String field();

	// 安全类型
	SecureType secureType() default SecureType.ENCRYPT;

	// 来源字段
	String sourceField() default "";

	// 脱敏的安全码
	String maskCode() default "";

	// 保留头长度
	int headSize() default 0;

	// 保留尾部长度
	int tailSize() default 0;

	// 脱敏率
	int maskRate() default 0;
}
