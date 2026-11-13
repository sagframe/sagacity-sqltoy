package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;

/**
 * eql中自定义url的端点缓存:相同url(大小写不敏感)复用同一端点,连接池随之复用
 */
public class SqlToyContextElasticEndpointTest {

	@Test
	public void customUrlEndpointIsReused() {
		SqlToyContext context = new SqlToyContext();
		ElasticEndpoint first = context.getElasticEndpoint("http://127.0.0.1:9200/es");
		ElasticEndpoint second = context.getElasticEndpoint("HTTP://127.0.0.1:9200/es");
		assertSame(first, second, "同一url应复用端点,避免连接池无法复用/释放");
		assertNotSame(first, context.getElasticEndpoint("http://127.0.0.1:9201/es"));
	}
}
