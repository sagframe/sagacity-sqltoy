package org.sagacity.sqltoy.integration.aot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.json.JsonProvider;
import org.sagacity.sqltoy.plugins.json.JsonProviders;
import org.springframework.aot.generate.GeneratedClasses;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * SqltoyBeanFactoryInitializationAotProcessor 单元测试:模拟 AOT 构建期的 beanFactory 形态
 * (refreshForAotProcessing 只注册 environment 单例,不注册 BeanPostProcessor、不预实例化单例,
 * @ConfigurationProperties bean 不可依赖),验证贡献从 Environment 绑定 spring.sqltoy 配置并注册
 * 实体扫描、动态资源 pattern、扩展类与集成类反射 hints(6.0.8 GraalVM 增强)
 */
public class SqltoyAotHintsTest {

	/** 扩展类夹具:按类名配置的扩展点只需可被 Class.forName 加载即可注册反射 hints */
	public static class AotTestFieldsHandler {
	}

	@Test
	void bindsPropertiesFromEnvironmentAndRegistersHints() {
		Map<String, Object> props = new HashMap<>();
		props.put("spring.sqltoy.packagesToScan", "org.sagacity.sqltoy.integration.aot.entity");
		// 多目录:中英文逗号、分号分隔
		props.put("spring.sqltoy.sqlResourcesDir", "classpath:com/sqltoy/aot-test，classpath:sqltoy-ext");
		props.put("spring.sqltoy.sqlResources[0]", "classpath:com/sqltoy/explicit.sql.xml");
		props.put("spring.sqltoy.translateConfig", "classpath:translate-test/my-translate.xml");
		props.put("spring.sqltoy.securePrivateKey", "classpath:mock/aot_private.key");
		props.put("spring.sqltoy.securePublicKey", "classpath:mock/aot_public.key");
		props.put("spring.sqltoy.unifyFieldsHandler", AotTestFieldsHandler.class.getName());
		// bean 名称(不含".")不按类名注册,由 Spring AOT 自行保障
		props.put("spring.sqltoy.typeHandler", "aotTestTypeHandlerBean");
		// 简化别名展开为内置实现类
		props.put("spring.sqltoy.sqlFormater", "default");
		// 不存在的类:warn 跳过而非中断 AOT 构建
		props.put("spring.sqltoy.sqlInterceptors[0]", "com.sqltoy.NotExistSqlInterceptor");
		props.put("spring.sqltoy.cacheType", "caffeine");

		RuntimeHints hints = applyProcessor(aotLikeBeanFactory(props));

		// 实体:按 packagesToScan 扫描注册,不依赖 SqlToyContext bean 实例化
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.integration.aot.entity.AotTestEntity"),
				"packagesToScan entity reflection hints missing");
		// 扩展类:按类名配置 + sqlFormater 别名展开 + cacheType=caffeine 的启动期反射集成类
		assertTrue(reflectionRegistered(hints, AotTestFieldsHandler.class.getName()),
				"class-name configured extension missing");
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.plugins.formater.impl.DefaultSqlFormater"),
				"sqlFormater default alias expansion missing");
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.translate.cache.impl.TranslateCaffeineManager"),
				"caffeine translate cache manager missing");
		// 不存在的拦截器类跳过,不影响其他注册
		assertFalse(reflectionRegistered(hints, "com.sqltoy.NotExistSqlInterceptor"));

		// 动态资源 pattern:目录补 /** 通配、显式文件与密钥按原路径登记
		Set<String> patterns = resourcePatterns(hints);
		assertTrue(patterns.contains("com/sqltoy/aot-test/**"), "sqlResourcesDir pattern missing: " + patterns);
		assertTrue(patterns.contains("sqltoy-ext/**"), "multi-dir split pattern missing: " + patterns);
		assertTrue(patterns.contains("com/sqltoy/explicit.sql.xml"), "sqlResources file pattern missing: " + patterns);
		assertTrue(patterns.contains("translate-test/my-translate.xml"), "translateConfig pattern missing: " + patterns);
		assertTrue(patterns.contains("mock/aot_private.key"), "securePrivateKey pattern missing: " + patterns);
		assertTrue(patterns.contains("mock/aot_public.key"), "securePublicKey pattern missing: " + patterns);
	}

	@Test
	void skipsGracefullyWithoutEnvironment() {
		// starter 在 classpath 上但 sqltoy 自动配置未生效(无 environment 单例)时不得中断 AOT 构建
		RuntimeHints hints = applyProcessor(new DefaultListableBeanFactory());
		assertNotNull(hints);
	}

	@Test
	void registersEffectiveJsonProvider() {
		// 与处理器相同的解析路径:classpath 无可用 JSON 库时返回 null,无需注册
		JsonProvider provider = JsonProviders.initAtStartup(null);
		RuntimeHints hints = applyProcessor(aotLikeBeanFactory(new HashMap<>()));
		if (provider != null) {
			assertTrue(reflectionRegistered(hints, provider.getClass().getName()),
					"effective json provider reflection hints missing");
		}
	}

	/**
	 * 构造 AOT 构建期形态的 beanFactory:仅注册 environment 单例(与 refreshForAotProcessing 一致)
	 */
	private DefaultListableBeanFactory aotLikeBeanFactory(Map<String, Object> props) {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("sqltoy-aot-test", props));
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("environment", environment);
		return beanFactory;
	}

	private RuntimeHints applyProcessor(ConfigurableListableBeanFactory beanFactory) {
		SqltoyBeanFactoryInitializationAotProcessor processor = new SqltoyBeanFactoryInitializationAotProcessor();
		BeanFactoryInitializationAotContribution contribution = processor.processAheadOfTime(beanFactory);
		assertNotNull(contribution, "aot contribution should always be returned");
		RuntimeHints hints = new RuntimeHints();
		// 贡献只消费 GenerationContext.getRuntimeHints(),代码生成设施在本测试中不使用
		contribution.applyTo(new StubGenerationContext(hints), null);
		return hints;
	}

	private static class StubGenerationContext implements GenerationContext {
		private final RuntimeHints hints;

		StubGenerationContext(RuntimeHints hints) {
			this.hints = hints;
		}

		@Override
		public GeneratedClasses getGeneratedClasses() {
			throw new UnsupportedOperationException("not used by sqltoy aot contribution");
		}

		@Override
		public GeneratedFiles getGeneratedFiles() {
			throw new UnsupportedOperationException("not used by sqltoy aot contribution");
		}

		@Override
		public RuntimeHints getRuntimeHints() {
			return hints;
		}

		@Override
		public GenerationContext withName(String name) {
			return this;
		}
	}

	private static boolean reflectionRegistered(RuntimeHints hints, String className) {
		return hints.reflection().typeHints().map(TypeHint::getType).anyMatch(ref -> ref.getName().equals(className));
	}

	private static Set<String> resourcePatterns(RuntimeHints hints) {
		return hints.resources().resourcePatternHints().flatMap(rp -> rp.getIncludes().stream())
				.map(include -> include.getPattern()).collect(Collectors.toSet());
	}
}
