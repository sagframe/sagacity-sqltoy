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
 * @description 是否分区字段,MPP数据库涉及到，如StarRocks
 * @author zhongxuchen
 * @version v1.0, Date:2021-3-23
 * @modify 2021-3-23,修改说明
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PartitionKey {

}
