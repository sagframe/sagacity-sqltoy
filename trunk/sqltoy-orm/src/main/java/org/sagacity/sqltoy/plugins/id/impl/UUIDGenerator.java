/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.impl;

import java.util.Date;

import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.IdUtil;

/**
 * @project sqltoy-orm
 * @description 产生32位UUID字符串
 * @author renfei.chen $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:UUIDGenerator.java,Revision:v1.0,Date:2012-6-4 上午10:11:58 $
 */
public class UUIDGenerator implements IdGenerator {
	private static IdGenerator me = new UUIDGenerator();

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
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
			Date bizDate, String idJavaType, int length, int sequencSize) {
		return IdUtil.getUUID();
	}

}
