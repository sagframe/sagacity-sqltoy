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
 * @project sagacity-sqltoy
 * @description 产生26位顺序id;15位:yyMMddHHmmssSSS+6位纳秒+2位(线程Id+随机数)+3位主机ID
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:OrderIdGenerator.java,Revision:v1.0,Date:2017年9月27日
 */
public class NanoTimeIdGenerator implements IdGenerator {
	private static IdGenerator me = new NanoTimeIdGenerator();

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
		return SqlUtil.convertIdValueType(IdUtil.getNanoTimeId(SqlToyConstants.SERVER_ID), idJavaType);
	}

}
