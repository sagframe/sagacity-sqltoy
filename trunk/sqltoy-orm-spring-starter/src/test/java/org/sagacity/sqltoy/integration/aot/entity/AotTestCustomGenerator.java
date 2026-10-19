package org.sagacity.sqltoy.integration.aot.entity;

import java.util.Date;

import org.sagacity.sqltoy.plugins.id.IdGenerator;

/**
 * AOT 测试夹具:自定义主键生成器,供 AotTestCustomIdEntity 的 @Id(generator) 引用
 */
public class AotTestCustomGenerator implements IdGenerator {
	@Override
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
			Date bizDate, String idJavaType, int length, int sequenceSize) {
		return null;
	}
}
