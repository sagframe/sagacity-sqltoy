package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy
 * @description 索引定义
 * @author zhongxuchen
 * @version v1.0,Date:2023-07-12
 * @modify Date:2023-07-12,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Indexes {
	Index[] indexes();
}
