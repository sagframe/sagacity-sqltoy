package org.sagacity.sqltoy.solon.integration;

import java.util.Date;

import org.noear.redisx.RedisClient;
import org.noear.redisx.plus.RedisAtomic;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.integration.DistributeIdGenerator;

/**
 * 基于redisx的redis模式的分布式id生成器
 */
public class SolonRedisIdGenerator implements DistributeIdGenerator {
    /**
     * 全局ID的前缀符号,用于避免在redis中跟其它业务场景发生冲突
     */
    private final static String GLOBAL_ID_PREFIX = "SQLTOY_GL_ID:";
    private RedisClient client;

    @Override
    public long generateId(String key, int increment, Date expireTime) {
        String realKey = GLOBAL_ID_PREFIX.concat(key);
        RedisAtomic atomic = client.getAtomic(realKey);
        long result;
        if (increment < 1) {
            result = atomic.increment();
        } else {
            result = atomic.incrementBy(increment);
        }
        // 过期设置须在increment之后:首次调用时key由increment创建,提前pexpireAt对不存在的key是空操作
        if (expireTime != null) {
            client.open(s -> {
                s.jedis().pexpireAt(realKey, expireTime.getTime());
            });
        }
        return result;
    }

    @Override
    public void initialize(AppContext appContext) {
        client = appContext.getBean(RedisClient.class);
    }
}
