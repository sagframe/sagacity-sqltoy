/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.impl;

import java.util.Date;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.SnowflakeIdWorker;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 基于twitter的分布式自增ID生成策略
 * @author zhongxuchen
 * @version v1.0,Date:2017年3月21日
 * @modify 2025-12-22 按照表名创建雪花算法实例
 */
public class SnowflakeIdGenerator implements IdGenerator {
	private static String DEFAULT_TABLE_NAME = "SQLTOY_SNOWFLAKE_GLOBAL_TABLE_NAME";

	private static IdGenerator me = new SnowflakeIdGenerator();

	private static SnowflakeIdWorker idWorker = null;

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
	 * @see org.sagacity.sqltoy.plugins.id.IdGenerator#getId(java.lang.String,
	 * java.lang.String, java.lang.Object[], int)
	 */
	@Override
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
			Date bizDate, String idJavaType, int length, int sequenceSize) {
		// 如果是多个应用，在一个节点服务器上(相同IP)，主键容易产生重复，要通过下面形式设置不同服务id
		// <32
		// java -Dsqltoy.snowflake.workerId=11
		// java -Dsqltoy.snowflake.dataCenterId=20
		if (null == idWorker) {
			idWorker = new SnowflakeIdWorker(SqlToyConstants.WORKER_ID, SqlToyConstants.DATA_CENTER_ID);
		}
		String realTableName = StringUtil.ifBlank(tableName, DEFAULT_TABLE_NAME);
		return SqlUtil.convertIdValueType(idWorker.nextId(realTableName), idJavaType);
	}

	// 实例化时增加初始化，避免多线程并发问题
	@Override
	public void initialize(SqlToyContext sqlToyContext) throws Exception {
		if (null == idWorker) {
			idWorker = new SnowflakeIdWorker(SqlToyConstants.WORKER_ID, SqlToyConstants.DATA_CENTER_ID);
		}
	}

}