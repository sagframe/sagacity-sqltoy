/**
 * 
 */
package org.sagacity.sqltoy.plugins.id;

import java.util.Date;

import org.sagacity.sqltoy.SqlToyContext;

/**
 * @project sqltoy-orm
 * @description 定义主键产生器接口,自定义产生器必须实现getId()方法
 * @author zhongxuchen
 * @version v1.0,Date:2012-6-4
 */
public interface IdGenerator {
	/**
	 * @todo <b>返回id</b>
	 * @param tableName       为特殊的主键生成策略预留表名
	 * @param signature       识别符号
	 * @param relatedColValue 关联字段的值
	 * @param bizDate         当前日期
	 * @param idJavaType      主键数据类型
	 * @param length          主键长度
	 * @param sequenceSize     流水号长度,如:20210709[001] 后三位为流水
	 * @return
	 */
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
			Date bizDate, String idJavaType, int length, int sequenceSize);

	public default void initialize(SqlToyContext sqlToyContext) throws Exception {

	};
}
