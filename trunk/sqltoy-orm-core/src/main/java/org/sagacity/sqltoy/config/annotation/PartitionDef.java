package org.sagacity.sqltoy.config.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @project sagacity-sqltoy
 * @description 单个分区定义,配合Partition使用,描述分区名称和边界
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-18
 * @modify Date:2026-09-18 修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface PartitionDef {

	// 分区名称,如p202601
	String name();

	// 分区边界值或描述,如<2026-02-01、VALUES IN ('2026-01')、MAXVALUE
	String value() default "";

	// 分区所在表空间(oracle等)
	String tablespace() default "";

	// update 2026-10-13 分区上界(DB2表分区为STARTING..ENDING双边界区间,value为下界);单边界库(mysql系VALUES LESS THAN/pg系/oracle等)不使用,保持空串
	String endValue() default "";

	// update 2026-10-13 上界是否包含(DB2的ENDING默认INCLUSIVE,显式EXCLUSIVE须置false);仅endValue非空时有意义
	boolean endInclusive() default true;
}