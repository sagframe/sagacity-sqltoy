/**
 * 
 */
package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sqltoy-orm
 * @description 标志sqltoy实体对象
 * @author zhongxuchen
 * @version v1.0,Date:2012-5-25
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
	// 表名
	String tableName();

	// 表对应schema
	String schema() default "";
	
	//表注释
	String comment() default "";

	// 主键约束名称
	@Deprecated
	String pk_constraint() default "";
}
