/**
 * 
 */
package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy4.0
 * @description 数据库分库分表注解
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Sharding.java,Revision:v1.0,Date:2017年11月5日
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Sharding {

	// 最大等待秒数,小于等于0表示不限制
	int maxWaitSeconds() default -1;

	// 最大并行数,默认为10,用于批量操作时可以同时向多个数据源同时执行
	// 实际会按照数据源的多少进行并行,同时考虑CPU的核心数量
	int maxConcurrents() default 10;

	// 分库策略
	Strategy db() default @Strategy();

	// 分表策略
	Strategy table() default @Strategy();

	// 在增加、修改、删除操作是个别节点发生异常，事务回滚策略是整体还是局部(默认为全局)
	boolean is_global_rollback() default true;
}
