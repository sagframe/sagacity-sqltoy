package org.sagacity.sqltoy.lambda.toolkit;

/**
 * @description 实体属性与所属实体类的二元组,多表join场景通过lambda推断属性归属的实体
 * @param entityClass 属性所属的实体类
 * @param fieldName   实体属性名
 * @author zhongxuchen
 */
public record FieldNature(Class<?> entityClass, String fieldName) {
}
