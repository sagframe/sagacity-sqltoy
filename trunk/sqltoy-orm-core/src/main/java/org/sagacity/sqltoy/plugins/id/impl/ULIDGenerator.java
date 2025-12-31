package org.sagacity.sqltoy.plugins.id.impl;

import java.util.Date;

import org.sagacity.sqltoy.plugins.id.IdGenerator;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

/**
 * @project sqltoy-orm
 * @description 产生26位ULID字符串
 * @author zhongxuchen
 * @version v1.0,Date:2025-12-23
 */
public class ULIDGenerator implements IdGenerator {
	private static IdGenerator me = new ULIDGenerator();

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
		Ulid ulid = UlidCreator.getUlid();
		return ulid.toString();
	}
}
