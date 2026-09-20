package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy
 * @description 表分区策略元数据,标注在实体类上,描述分区方式和分区键,
 *              由quickvo依据数据库分区定义自动产生;配合字段级@PartitionKey使用
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-18
 * @modify Date:2026-09-18 修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Partition {

	/**
	 * 分区策略:RANGE(范围)|LIST(列表)|HASH(哈希)|RANGE_COLUMNS|LIST_COLUMNS
	 */
	String strategy();

	/**
	 * 分区键列(支持多列复合分区键);缺省时自动收集字段级@PartitionKey标记的字段
	 */
	String[] columns() default {};

	/**
	 * 分区表达式原文,如mysql的TO_DAYS(createTime)、postgresql的RANGE (trade_date)
	 */
	String expression() default "";

	/**
	 * 分区明细定义(可选,描述每个分区的名称和边界)
	 */
	PartitionDef[] partitions() default {};
}
