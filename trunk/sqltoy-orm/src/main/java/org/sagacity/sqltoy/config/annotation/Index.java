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
 * @description 单个索引定义
 * @author zhongxuchen
 * @version v1.0, Date:2023年7月12日
 * @modify 2023年7月12日,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Index {
	// 索引名称
	String name();

	// 是否唯一索引
	boolean isUnique() default false;

	// 索引列
	String[] columns();
}
