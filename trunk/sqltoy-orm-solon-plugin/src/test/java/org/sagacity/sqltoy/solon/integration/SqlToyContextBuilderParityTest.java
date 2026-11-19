package org.sagacity.sqltoy.solon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.solon.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.solon.configure.SqlToyContextTaskPoolProperties;

/**
 * solon插件参数补齐的锁定测试:jsonProvider配置透传与taskExecutor默认线程池构建
 * (此前solon侧仅targetPoolName生效,jsonProvider无配置入口、线程池其余8项参数配置无效)
 *
 * @author zhongxuchen
 * @version v1.0,Date:2026-11-16
 */
public class SqlToyContextBuilderParityTest {

	/**
	 * 无任何bean的AppContext桩:验证builder在不依赖solon容器时的纯装配逻辑
	 */
	private static class EmptyAppContext implements AppContext {
		@Override
		public boolean containsBean(String beanName) {
			return false;
		}

		@Override
		public Object getBean(String beanName) {
			return null;
		}

		@Override
		public <T> T getBean(Class<T> requiredType) {
			return null;
		}

		@Override
		public <T> Map<String, T> getBeansOfType(Class<T> type) {
			return Collections.emptyMap();
		}
	}

	@Test
	public void jsonProviderIsPassedThrough() throws Exception {
		SqlToyContextProperties properties = new SqlToyContextProperties();
		properties.setJsonProvider("com.demo.MyJsonProvider");
		SqlToyContext context = new SqlToyContextBuilder(properties, new EmptyAppContext()).build();
		assertEquals("com.demo.MyJsonProvider", context.getJsonProvider(),
				"solon.sqltoy.jsonProvider应透传到SqlToyContext");
	}

	@Test
	public void defaultTaskExecutorBuiltWhenTargetPoolNameUnset() throws Exception {
		SqlToyContextProperties properties = new SqlToyContextProperties();
		SqlToyContextTaskPoolProperties pool = new SqlToyContextTaskPoolProperties();
		pool.setThreadNamePrefix("demoPool");
		properties.setTaskExecutor(pool);
		SqlToyContext context = new SqlToyContextBuilder(properties, new EmptyAppContext()).build();
		Executor executor = context.getTaskExecutor();
		assertInstanceOf(SolonTaskExecutor.class, executor, "targetPoolName未配置时应按参数构建默认线程池(对齐spring未配置建池)");
		// 参数生效冒烟:任务可执行且线程名带配置前缀
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> threadName = new AtomicReference<>();
		executor.execute(() -> {
			threadName.set(Thread.currentThread().getName());
			latch.countDown();
		});
		assertTrue(latch.await(5, TimeUnit.SECONDS), "任务应在默认线程池中执行");
		assertTrue(threadName.get().startsWith("demoPool-"), "threadNamePrefix应生效,实际:" + threadName.get());
		((SolonTaskExecutor) executor).shutdown();
	}

	@Test
	public void explicitNoneFallsBackWithoutBuildingPool() throws Exception {
		SqlToyContextProperties properties = new SqlToyContextProperties();
		SqlToyContextTaskPoolProperties pool = new SqlToyContextTaskPoolProperties();
		pool.setTargetPoolName("none");
		properties.setTaskExecutor(pool);
		SqlToyContext context = new SqlToyContextBuilder(properties, new EmptyAppContext()).build();
		// 显式none与spring starter语义一致:不建专属池,回落core的taskExecutor默认值(commonPool的TTL包装)
		assertTrue(!(context.getTaskExecutor() instanceof SolonTaskExecutor),
				"显式targetPoolName=none时不应自建池,实际:" + context.getTaskExecutor());
	}

	@Test
	public void defaultTaskExecutorBuiltWhenTargetPoolNameBlank() throws Exception {
		SqlToyContextProperties properties = new SqlToyContextProperties();
		SqlToyContextTaskPoolProperties pool = new SqlToyContextTaskPoolProperties();
		pool.setTargetPoolName("");
		pool.setCorePoolSize(2);
		properties.setTaskExecutor(pool);
		SqlToyContext context = new SqlToyContextBuilder(properties, new EmptyAppContext()).build();
		assertInstanceOf(SolonTaskExecutor.class, context.getTaskExecutor(), "targetPoolName为空时同样构建默认线程池");
		((SolonTaskExecutor) context.getTaskExecutor()).shutdown();
	}

	@Test
	public void externalPoolNameResolvesWithoutBuilding() throws Exception {
		SqlToyContextProperties properties = new SqlToyContextProperties();
		SqlToyContextTaskPoolProperties pool = new SqlToyContextTaskPoolProperties();
		pool.setTargetPoolName("myExternalPool");
		properties.setTaskExecutor(pool);
		// 容器内存在同名线程池bean:core的getTaskExecutor按名称解析,不应被自建池覆盖
		Executor externalPool = Runnable::run;
		AppContext namedAppContext = new EmptyAppContext() {
			@Override
			public boolean containsBean(String beanName) {
				return "myExternalPool".equals(beanName);
			}

			@Override
			public Object getBean(String beanName) {
				return "myExternalPool".equals(beanName) ? externalPool : null;
			}
		};
		SqlToyContext context = new SqlToyContextBuilder(properties, namedAppContext).build();
		assertEquals(externalPool, context.getTaskExecutor(), "指定外部线程池名称时应按名称解析外部bean");
	}

	@Test
	public void externalPoolNameAbsentDoesNotBuildOwnPool() throws Exception {
		SqlToyContextProperties properties = new SqlToyContextProperties();
		SqlToyContextTaskPoolProperties pool = new SqlToyContextTaskPoolProperties();
		pool.setTargetPoolName("myExternalPool");
		properties.setTaskExecutor(pool);
		// 容器内不存在该bean:回落core的taskExecutor字段默认值(commonPool的TTL包装),
		// 插件不应越权自建池覆盖名称模式的解析语义
		SqlToyContext context = new SqlToyContextBuilder(properties, new EmptyAppContext()).build();
		assertTrue(!(context.getTaskExecutor() instanceof SolonTaskExecutor),
				"外部线程池bean不存在时不应自建池,实际:" + context.getTaskExecutor());
	}
}
