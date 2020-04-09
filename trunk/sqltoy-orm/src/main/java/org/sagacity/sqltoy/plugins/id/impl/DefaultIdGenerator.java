/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.impl;

import java.util.Date;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.IdUtil;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * @project sqltoy-orm
 * @description 格式:13位当前毫秒+6位纳秒+3位主机ID 构成的22位不重复的ID
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:DefaultGenerator.java,Revision:v1.0,Date:2012-6-4 上午10:12:48
 */
public class DefaultIdGenerator implements IdGenerator {
	private static IdGenerator me = new DefaultIdGenerator();

	/**
	 * @TODO 获取对象单例
	 * @return
	 */
	public static IdGenerator getInstance() {
		return me;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IdGenerator#getId(java.lang.String,
	 * java.lang.String, java.lang.Object[], int)
	 */
	@Override
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColsValue,
			Date bizDate, String idJavaType, int length, int sequencSize) {
		return SqlUtil.convertIdValueType(IdUtil.getShortNanoTimeId(SqlToyConstants.SERVER_ID), idJavaType);
	}
}
