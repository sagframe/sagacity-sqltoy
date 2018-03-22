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
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:Entity.java,Revision:v1.0,Date:2012-5-25 下午1:28:46
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
	/**
	 * 表名
	 * 
	 * @return
	 */
	String tableName();

	/**
	 * 表对应schema
	 * 
	 * @return
	 */
	String schema() default "";

	/**
	 * 主键约束名称
	 * 
	 * @return
	 */
	String pk_constraint() default "";
}
