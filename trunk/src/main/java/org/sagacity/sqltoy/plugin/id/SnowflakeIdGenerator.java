/**
 * 
 */
package org.sagacity.sqltoy.plugin.id;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.plugin.IdGenerator;
import org.sagacity.sqltoy.utils.SnowflakeIdWorker;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * @project sagacity-sqltoy3.2
 * @description 基于twitter的分布式自增ID生成策略
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SnowflakeIdGenerator.java,Revision:v1.0,Date:2017年3月21日
 */
public class SnowflakeIdGenerator implements IdGenerator {

	private static SnowflakeIdWorker idWorker = null;

	private static IdGenerator me = new SnowflakeIdGenerator();

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
		if (idWorker == null) {
			idWorker = new SnowflakeIdWorker(SqlToyConstants.WORKER_ID, SqlToyConstants.DATA_CENTER_ID);
		}
		return SqlUtil.convertIdValueType(idWorker.nextId(), jdbcType);
	}
}
