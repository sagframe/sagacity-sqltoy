/**
 * 
 */
package org.sagacity.sqltoy.plugin.id;

import org.sagacity.sqltoy.plugin.IdGenerator;
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
	 * 获取对象单例
	 * 
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
	public Object getId(String tableName, String signature, Object relatedColValue, int jdbcType, int length)
			throws Exception {
		return IdUtil.getUUID();
	}

}
