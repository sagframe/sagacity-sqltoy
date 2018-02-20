/**
 * 
 */
package org.sagacity.sqltoy.plugin.id;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyContext;
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
	private static RedisIdGenerator me = new RedisIdGenerator();

	/**
	 * 嵌入的日期匹配表达式
	 */
	private final static Pattern DF_REGEX = Pattern.compile("(?i)(\\@|\\$)(date|day|df)\\([\\w|\\W]*\\)");
	// private final static Pattern DF_REGEX_1 =
	// Pattern.compile("(?i)\\$(date|day|df)\\([\\w|\\W]*\\)");

	/**
	 * 全局ID的前缀符号,用于避免在redis中跟其它业务场景发生冲突
	 */
	private final static String GLOBAL_ID_PREFIX = "SQLTOY_GL_ID_";

	/**
	 * 获取对象单例
	 * 
	 * @return
	 */
	public static IdGenerator getInstance(SqlToyContext sqlToyContext) {
		if (me.getRedisTemplate() == null) {
			me.setRedisTemplate((RedisTemplate) sqlToyContext.getBean("redisTemplate"));
		}
		return me;
	}

	/**
	 * 日期格式
	 */
	private String dateFormat;

	private RedisTemplate redisTemplate;

	/**
	 * @param redisTemplate
	 *            the redisTemplate to set
	 */
	@Autowired(required = false)
	public void setRedisTemplate(RedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * @return the redisTemplate
	 */
	public RedisTemplate getRedisTemplate() {
		return redisTemplate;
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
				.concat(((relatedColValue == null) ? "" : relatedColValue.toString()));
		String realKey = key;
		// key 的格式如:PO@day(yyyyMMdd) 表示PO开头+yyyyMMdd格式
		if (key.indexOf("@") > 0 || key.indexOf("$") > 0) {
			Matcher m = DF_REGEX.matcher(key);
			if (m.find()) {
				String df = m.group();
				df = df.substring(df.indexOf("(") + 1, df.indexOf(")")).replaceAll("\'|\"", "").trim();
				// PO@day()格式,日期采用默认的2位年模式
				if (df.equals(""))
					df = "yyMMdd";
				realKey = key.substring(0, m.start()).concat(DateUtil.formatDate(new Date(), df))
						.concat(key.substring(m.end()));
			}
		} else if (dateFormat != null)
			realKey = key.concat(DateUtil.formatDate(new Date(), dateFormat));
		Long result = generate(realKey);
		return realKey + StringUtil.addLeftZero2Len("" + result, length - realKey.length());
	}

	/**
	 * @todo 根据key获取+1后的key值
	 * @param key
	 * @return
	 */
	public long generate(String key) {
		return generate(key, 1, null);
	}

	/**
	 * @todo 批量获取key值
	 * @param key
	 * @param increment
	 * @return
	 */
	public long generate(String key, int increment) {
		return generate(key, increment, null);
	}

	/**
	 * @todo 批量获取key值,并指定过期时间
	 * @param key
	 * @param increment
	 * @param expireTime
	 * @return
	 */
	public long generate(String key, int increment, Date expireTime) {
		RedisAtomicLong counter = new RedisAtomicLong(GLOBAL_ID_PREFIX.concat(key),
				redisTemplate.getConnectionFactory());
		// 设置过期时间
		if (expireTime != null)
			counter.expireAt(expireTime);
		// 设置提取多个数量
		if (increment > 1)
			return counter.addAndGet(increment);
		else
			return counter.incrementAndGet();
	}

	/**
	 * @param dateFormat
	 *            the dateFormat to set
	 */
	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}
}
