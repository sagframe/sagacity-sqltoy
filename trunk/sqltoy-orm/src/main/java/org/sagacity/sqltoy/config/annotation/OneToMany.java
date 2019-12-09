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
 * @description 主键被其他表关联的机制
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:OneToMany.java,Revision:v1.0,Date:2012-7-30
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneToMany {
	/**
	 * 列名(one column)
	 * 
	 * @return
	 */
	String[] fields();

	/**
	 * 关联表的字段(many column)
	 * 
	 * @return
	 */
	String[] mappedColumns();

	/**
	 * 关联表对应对象的属性
	 * 
	 * @return
	 */
	String[] mappedFields();

	/**
	 * 是否自动加载
	 * 
	 * @return
	 */
	String load() default "";

	/**
	 * 是否级联删除
	 * 
	 * @return
	 */
	boolean delete() default true;

	/**
	 * 定制级联修改保存对子表的操作语句
	 * 
	 * @return
	 */
	String update() default "";

	/**
	 * 关联的表
	 * 
	 * @return
	 */
	String mappedTable();

}
