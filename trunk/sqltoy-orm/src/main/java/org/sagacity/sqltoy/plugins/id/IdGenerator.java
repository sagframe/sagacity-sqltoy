/**
 * 
 */
package org.sagacity.sqltoy.plugins.id;

import java.util.Date;

/**
 * @project sqltoy-orm
 * @description 定义主键产生器接口,自定义产生器必须实现getId()方法
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:IdGenerator.java,Revision:v1.0,Date:2012-6-4 上午10:08:15
 */
public interface IdGenerator {
	/**
	 * @todo <b>返回id</b>
	 * @param tableName       为特殊的主键生成策略预留表名
	 * @param signature       识别符号
	 * @param relatedColValue 关联字段的值
	 * @param bizDate
	 * @param idJavaType
	 * @param length
	 * @param sequencSize
	 * @return
	 */
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
			Date bizDate, String idJavaType, int length, int sequencSize);
}
