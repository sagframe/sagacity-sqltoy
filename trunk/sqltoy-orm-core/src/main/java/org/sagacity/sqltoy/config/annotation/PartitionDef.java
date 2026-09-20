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
}
