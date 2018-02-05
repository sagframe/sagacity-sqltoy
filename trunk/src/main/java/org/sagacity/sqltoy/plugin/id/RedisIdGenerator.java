/**
 * 
 */
package org.sagacity.sqltoy.plugin.id;

import java.util.Date;

import org.sagacity.sqltoy.plugin.IdGenerator;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;

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

	private RedisTemplate redisTemplate;

	/**
	 * @param redisTemplate
	 *            the redisTemplate to set
	 */
	@Autowired(required = false)
	public void setRedisTemplate(RedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
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
		String key = (signature == null ? "" : signature)
				+ ((relatedColValue == null) ? "" : relatedColValue.toString())
				+ (dateFormat == null ? "" : DateUtil.parse(new Date(), dateFormat));
		int increment = 1;
		Long result = generate(key, increment);
		return key + StringUtil.addLeftZero2Len("" + result, length - key.length());
	}

	private long generate(String key, int increment) {
		RedisAtomicLong counter = new RedisAtomicLong(key, redisTemplate.getConnectionFactory());
		return counter.addAndGet(increment);
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
