package org.sagacity.sqltoy.integration.impl;

import java.util.Date;

import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.integration.DistributeIdGenerator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;

/**
 * @project sagacity-sqltoy
 * @description 基于spring的redis模式的分布式id生成器
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public class SpringRedisIdGenerator implements DistributeIdGenerator {
	/**
	 * 全局ID的前缀符号,用于避免在redis中跟其它业务场景发生冲突
	 */
	private final static String GLOBAL_ID_PREFIX = "SQLTOY_GL_ID:";
	private RedisTemplate<?, ?> redisTemplate;

	@Override
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
		// 默认每次加1
		return counter.incrementAndGet();
	}

	/**
	 * 初始化
	 */
	@Override
	public void initialize(AppContext appContext) {
		// 这里比较特殊，不能根据类型获取(会获取到redisTemplate和stringRedisTemplate 两个)
		if (redisTemplate == null) {
			redisTemplate = (RedisTemplate) appContext.getBean("redisTemplate");
		}
	}

}
