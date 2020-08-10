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
 * @description 为sqltoy分层提供DTO(或VO)对应POJO映射提供注解标记
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-10
 * @modify 2020-8-10,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SqlToyDTO {
	Class entity();
}
