/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.impl;

import java.time.LocalDate;
import java.util.Date;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.integration.DistributeIdGenerator;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.IdUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 基于redis的集中式主键生成策略
 * @author zhongxuchen
 * @version v1.0,Date:2018年1月30日
 * @modify Date:2019-1-24 {key命名策略改为SQLTOY_GL_ID:tableName:xxx 便于redis检索}
 */
public class RedisIdGenerator implements IdGenerator {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(RedisIdGenerator.class);

	/**
	 * 分布式id生成器
	 */
	private DistributeIdGenerator distributeIdGenerator;

	@Override
	public void initialize(SqlToyContext sqlToyContext) throws Exception {
		if (distributeIdGenerator == null) {
			distributeIdGenerator = (DistributeIdGenerator) Class.forName(sqlToyContext.getDistributeIdGeneratorClass())
					.getDeclaredConstructor().newInstance();
			distributeIdGenerator.initialize(sqlToyContext.getAppContext());
		}
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
		LocalDate bizLocalDate = (bizDate == null) ? null : DateUtil.asLocalDate(bizDate);
		return SqlUtil.convertIdValueType(
				IdUtil.getId(distributeIdGenerator, tableName, signature,
						MapKit.keys(relatedColumns).values(relatedColValue), bizLocalDate, length, sequenceSize),
				idJavaType);
	}
}
