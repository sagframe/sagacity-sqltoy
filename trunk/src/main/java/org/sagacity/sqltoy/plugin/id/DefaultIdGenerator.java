/**
 * 
 */
package org.sagacity.sqltoy.plugin.id;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.plugin.IdGenerator;
import org.sagacity.sqltoy.utils.IdUtil;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * @project sqltoy-orm
 * @description 根据纳秒、机器IP地址后两位、2位随机数产生22位长唯一的字符串
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:DefaultGenerator.java,Revision:v1.0,Date:2012-6-4 上午10:12:48
 */
public class DefaultIdGenerator implements IdGenerator {
	private static IdGenerator me = new DefaultIdGenerator();

	/**
	 * 获取对象单例
	 * 
	 * @return
	 */
	public static IdGenerator getInstance() {
		return me;
	}

	/*
	 * (non-Javadoc)
	 * @see org.sagacity.sqltoy.plugin.IdGenerator#getId(java.lang.String, java.lang.String, java.lang.Object[], int)
	 */
	@Override
	public Object getId(String tableName, String signature, Object relatedColsValue, int jdbcType,int length) {
		return SqlUtil.convertIdValueType(IdUtil.getShortNanoTimeId(SqlToyConstants.SERVER_ID), jdbcType);
	}
}
