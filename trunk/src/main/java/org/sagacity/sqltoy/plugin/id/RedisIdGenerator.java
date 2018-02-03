/**
 * 
 */
package org.sagacity.sqltoy.plugin.id;

import java.util.Date;

import org.sagacity.sqltoy.plugin.IdGenerator;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sagacity-sqltoy4.0
 * @description 基于redis的集中式主键生成策略
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:RedisIdGenerator.java,Revision:v1.0,Date:2018年1月30日
 */
public class RedisIdGenerator implements IdGenerator {

	/**
	 * 日期格式
	 */
	private String dateFormat = "yyMMdd";

	/**
	 * url地址
	 */
	private String redisUrl;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IdGenerator#getId(java.lang.String,
	 * java.lang.String, java.lang.Object[], int)
	 */
	@Override
	public Object getId(String tableName, String signature, Object relatedColValue, int jdbcType, int length) {
		String key = (signature == null ? "" : signature)
				+ ((relatedColValue == null) ? "" : relatedColValue.toString())
				+ (dateFormat == null ? "" : DateUtil.parse(new Date(), dateFormat));
		return null;
	}

	/**
	 * @return the dateFormat
	 */
	public String getDateFormat() {
		return dateFormat;
	}

	/**
	 * @param dateFormat
	 *            the dateFormat to set
	 */
	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	/**
	 * @return the redisUrl
	 */
	public String getRedisUrl() {
		return redisUrl;
	}

	/**
	 * @param redisUrl
	 *            the redisUrl to set
	 */
	public void setRedisUrl(String redisUrl) {
		this.redisUrl = redisUrl;
	}

}
