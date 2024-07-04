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
 * @description 为DTO映射POJO提供别名映射额外配置(极特殊场景使用)
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-10
 * @modify 2020-8-10,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SqlToyFieldAlias {
	String value();
}
