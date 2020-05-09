/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.impl;

import java.util.Date;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;

/**
 * @project sagacity-sqltoy4.0
 * @description 基于redis的集中式主键生成策略
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:RedisIdGenerator.java,Revision:v1.0,Date:2018年1月30日
 * @Modification Date:2019-1-24 {key命名策略改为SQLTOY_GL_ID:tableName:xxx 便于redis检索}
 */
public class RedisIdGenerator implements IdGenerator {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(RedisIdGenerator.class);
	private static RedisIdGenerator me = new RedisIdGenerator();

	/**
	 * 全局ID的前缀符号,用于避免在redis中跟其它业务场景发生冲突
	 */
	private final static String GLOBAL_ID_PREFIX = "SQLTOY_GL_ID:";

	private RedisTemplate<?, ?> redisTemplate;

	/**
	 * 日期格式
	 */
	private String dateFormat;

	/**
	 * @todo 获取对象单例
	 * @param sqlToyContext
	 * @return
	 */
	public static IdGenerator getInstance(SqlToyContext sqlToyContext) {
		if (me.getRedisTemplate() == null) {
			Object template = sqlToyContext.getBean("redisTemplate");
			if (template == null) {
				logger.error("RedisIdGenerator 未定义redisTemplate!");
			} else {
				me.setRedisTemplate((RedisTemplate<?, ?>) template);
			}
		}
		return me;
	}

	/**
	 * @param redisTemplate the redisTemplate to set
	 */
	// 目前AutoWired 不起作用(没有用spring来托管),因此在getInstance时进行自动获取
	@Autowired(required = false)
	@Qualifier(value = "redisTemplate")
	public void setRedisTemplate(RedisTemplate<?, ?> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * @return the redisTemplate
	 */
	public RedisTemplate<?, ?> getRedisTemplate() {
		return redisTemplate;
	}

	public boolean hasRedisTemplate() {
		if (redisTemplate != null) {
			return true;
		}
		return false;
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
		String key = (signature == null ? "" : signature);
		// 主键生成依赖业务的相关字段值
		IgnoreKeyCaseMap<String, Object> keyValueMap = new IgnoreKeyCaseMap<String, Object>();
		if (relatedColumns != null && relatedColumns.length > 0) {
			for (int i = 0; i < relatedColumns.length; i++) {
				keyValueMap.put(relatedColumns[i], relatedColValue[i]);
			}
		}
		// 替换signature中的@df() 和@case()等宏表达式
		String realKey = MacroUtils.replaceMacros(key, keyValueMap);
		// 没有宏
		if (realKey.equals(key)) {
			// 长度够放下6位日期 或没有设置长度且流水长度小于6,则默认增加一个6位日期作为前置
			if ((length <= 0 && sequencSize < 6) || (length - realKey.length() > 6)) {
				Date realBizDate = (bizDate == null ? new Date() : bizDate);
				realKey = realKey
						.concat(DateUtil.formatDate(realBizDate, (dateFormat == null) ? "yyMMdd" : dateFormat));
			}
		}
		// 参数替换
		if (!keyValueMap.isEmpty()) {
			realKey = MacroUtils.replaceParams(realKey, keyValueMap);
		}
		// 结合redis计数取末尾几位顺序数
		Long result;

		// update 2019-1-24 key命名策略改为SQLTOY_GL_ID:tableName:xxx 便于redis检索
		if (tableName != null) {
			result = generateId(realKey.equals("") ? tableName : tableName.concat(":").concat(realKey));
		} else {
			result = generateId(realKey);
		}
		return realKey.concat(
				StringUtil.addLeftZero2Len("" + result, (sequencSize > 0) ? sequencSize : length - realKey.length()));
	}

	/**
	 * @todo 根据key获取+1后的key值
	 * @param key
	 * @return
	 */
	public long generateId(String key) {
		return generateId(key, 1, null);
	}

	/**
	 * @todo 批量获取key值
	 * @param key
	 * @param increment
	 * @return
	 */
	public long generateId(String key, int increment) {
		return generateId(key, increment, null);
	}

	/**
	 * @todo 批量获取key值,并指定过期时间
	 * @param key
	 * @param increment
	 * @param expireTime
	 * @return
	 */
	public long generateId(String key, int increment, Date expireTime) {
		RedisAtomicLong counter = new RedisAtomicLong(GLOBAL_ID_PREFIX.concat(key),
				redisTemplate.getConnectionFactory());
		// 设置过期时间
		if (expireTime != null) {
			counter.expireAt(expireTime);
		}
		// 设置提取多个数量
		if (increment > 1) {
			return counter.addAndGet(increment);
		}
		return counter.incrementAndGet();
	}

	/**
	 * @param dateFormat the dateFormat to set
	 */
	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}
}
