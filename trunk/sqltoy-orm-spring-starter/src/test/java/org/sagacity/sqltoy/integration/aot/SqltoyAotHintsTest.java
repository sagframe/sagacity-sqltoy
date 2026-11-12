package org.sagacity.sqltoy.integration.aot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GeneratedClasses;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.InMemoryGeneratedFiles;
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
		// functionConverts:default 展开为内置函数集,构建期按运行期同语义解析注册
		props.put("spring.sqltoy.functionConverts[0]", "default");
		// 人工登记的非 @Entity resultType DTO
		props.put("spring.sqltoy.nativeReflectionClasses[0]",
				"org.sagacity.sqltoy.integration.aot.entity.AotTestDto");
		// VO/DTO 较多时按包批量登记
		props.put("spring.sqltoy.nativeReflectionPackages[0]", "org.sagacity.sqltoy.integration.aot.dto");
		// updateSaveFetch(EntityUpdateCallback) 用实体:构建期预生成 EntityResultSetProxy 子类
		props.put("spring.sqltoy.nativeEntityProxies[0]",
				"org.sagacity.sqltoy.integration.aot.entity.AotTestEntity");

		RuntimeHints hints = applyProcessor(aotLikeBeanFactory(props));

		// 实体:按 packagesToScan 扫描注册,不依赖 SqlToyContext bean 实例化
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.integration.aot.entity.AotTestEntity"),
				"packagesToScan entity reflection hints missing");
		// update 2026-10-20 @Id(strategy="ulid")/@Id(generator=自定义类) 的生成器类注册
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.plugins.id.impl.ULIDGenerator"),
				"ULIDGenerator reflection hints missing (@Id strategy=ulid)");
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.integration.aot.entity.AotTestCustomGenerator"),
				"custom @Id generator reflection hints missing");
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.integration.aot.entity.AotTestDto"),
				"nativeReflectionClasses DTO reflection hints missing");
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.integration.aot.dto.AotTestOrderVO"),
				"nativeReflectionPackages VO reflection hints missing");
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.integration.aot.dto.AotTestUserDTO"),
				"nativeReflectionPackages DTO reflection hints missing");
		assertTrue(reflectionRegistered(hints,
				"org.sagacity.sqltoy.integration.aot.entity.AotTestEntity$$SqltoyEntityProxy"),
				"nativeEntityProxies generated proxy reflection hints missing");
		// 扩展类:按类名配置 + sqlFormater 别名展开
		assertTrue(reflectionRegistered(hints, AotTestFieldsHandler.class.getName()),
				"class-name configured extension missing");
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.plugins.formater.impl.DefaultSqlFormater"),
				"sqlFormater default alias expansion missing");
		// 集成类注册跟随 classpath 而非 cacheType 配置:caffeine 在本模块测试 classpath(provided),
		// 未配置 cacheType 也应注册翻译缓存管理器(支持运行期切换)
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.translate.cache.impl.TranslateCaffeineManager"),
				"caffeine translate cache manager missing (marker-based registration)");
		// 不存在的拦截器类跳过,不影响其他注册
		assertFalse(reflectionRegistered(hints, "com.sqltoy.NotExistSqlInterceptor"));
		// functionConverts default 展开的内置函数集已注册
		assertTrue(reflectionRegistered(hints, "org.sagacity.sqltoy.plugins.function.impl.Trim"),
				"functionConverts default set registration missing");

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
	void registersJsonProvidersByClasspathMarker() {
		// JsonProvider 注册跟随 classpath marker(与 JsonProviders.CANDIDATES 一致),
		// 覆盖运行期切换与探测降级;本模块测试 classpath 无 JSON 库(core 中为 provided+optional 不传递),
		// 故按 marker 存在性双向断言
		RuntimeHints hints = applyProcessor(aotLikeBeanFactory(new HashMap<>()));
		assertProviderRegistration(hints, "com.alibaba.fastjson2.JSON",
				"org.sagacity.sqltoy.plugins.json.FastjsonJsonProvider");
		assertProviderRegistration(hints, "tools.jackson.databind.ObjectMapper",
				"org.sagacity.sqltoy.plugins.json.Jackson3JsonProvider");
		assertProviderRegistration(hints, "com.fasterxml.jackson.databind.ObjectMapper",
				"org.sagacity.sqltoy.plugins.json.Jackson2JsonProvider");
	}

	private void assertProviderRegistration(RuntimeHints hints, String markerClass, String providerClass) {
		boolean markerPresent;
		try {
			Class.forName(markerClass);
			markerPresent = true;
		} catch (Throwable e) {
			markerPresent = false;
		}
		if (markerPresent) {
			assertTrue(reflectionRegistered(hints, providerClass),
					providerClass + " should be registered (marker " + markerClass + " present)");
		} else {
			assertFalse(reflectionRegistered(hints, providerClass),
					providerClass + " should not be registered (marker " + markerClass + " absent)");
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
		GeneratedFiles generatedFiles = new InMemoryGeneratedFiles();
		contribution.applyTo(new StubGenerationContext(hints, generatedFiles), null);
		return hints;
	}

	private static class StubGenerationContext implements GenerationContext {
		private final RuntimeHints hints;
		private final GeneratedFiles generatedFiles;

		StubGenerationContext(RuntimeHints hints, GeneratedFiles generatedFiles) {
			this.hints = hints;
			this.generatedFiles = generatedFiles;
		}

		@Override
		public GeneratedClasses getGeneratedClasses() {
			throw new UnsupportedOperationException("not used by sqltoy aot contribution");
		}

		@Override
		public GeneratedFiles getGeneratedFiles() {
			return generatedFiles;
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
