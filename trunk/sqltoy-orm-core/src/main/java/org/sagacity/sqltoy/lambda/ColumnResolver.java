package org.sagacity.sqltoy.lambda;

/**
 * @description 属性名到数据库字段名的解析器,由执行侧提供:生产环境基于EntityMeta解析,
 *              单元测试可提供恒等解析
 * @author zhongxuchen
 */
@FunctionalInterface
public interface ColumnResolver {

	/**
	 * @param entityClass 实体类
	 * @param fieldName   实体属性名
	 * @return 对应的数据库字段名
	 */
	String columnName(Class<?> entityClass, String fieldName);
}
