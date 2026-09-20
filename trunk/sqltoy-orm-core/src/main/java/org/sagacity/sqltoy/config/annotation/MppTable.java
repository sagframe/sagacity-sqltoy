package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy
 * @description MPP分析型数据库(ClickHouse/Doris/StarRocks)的表引擎元数据,
 *              配合@Partition(分区)由quickvo依据数据库表定义自动产生,用于DDL生成
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 * @modify Date:2026-09-19 修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MppTable {

	/**
	 * 引擎:ClickHouse为MergeTree/ReplacingMergeTree/SummingMergeTree/AggregatingMergeTree等;
	 * Doris/StarRocks为OLAP
	 */
	String engine() default "";

	/**
	 * 引擎参数,如ReplacingMergeTree的版本列:engineArgs="ver";ClickHouse Replicated引擎的参宿亦放此处
	 */
	String engineArgs() default "";

	/**
	 * 键模型(Doris/StarRocks):UNIQUE/PRIMARY/DUPLICATE/AGGREGATE,配合orderBy列
	 */
	String keyModel() default "";

	/**
	 * 排序键:ClickHouse的ORDER BY列;Doris/StarRocks为KEY模型的键列
	 */
	String[] orderBy() default {};

	/**
	 * 分桶列(Doris/StarRocks的DISTRIBUTED BY HASH列)
	 */
	String[] distributedBy() default {};

	/**
	 * 分桶数,缺省-1表示未指定(由数据库自动决定)
	 */
	int buckets() default -1;

	/**
	 * 表属性,"key=value"形式:ClickHouse渲染为SETTINGS key=value;
	 * Doris/StarRocks渲染为PROPERTIES("key"="value")
	 */
	String[] properties() default {};
}
