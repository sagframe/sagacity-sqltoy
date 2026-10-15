package org.sagacity.sqltoy.plugins.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description JsonProvider 的解析与持有:
 *              1) spring.sqltoy.jsonProvider 显式配置的实现类优先;
 *              2) 否则按 classpath 探测:fastjson2(兼容存量项目) -> jackson(Spring Boot 默认自带);
 *              3) 两者都不存在时,触及 JSON 列才抛出带明确指引的异常(而非 NoClassDefFoundError)。
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public final class JsonProviders {

	private static final Logger logger = LoggerFactory.getLogger(JsonProviders.class);

	private static volatile JsonProvider configured;
	private static volatile JsonProvider detected;

	private JsonProviders() {
	}

	/**
	 * 由 SqlToyContext 初始化时调用(配置项 spring.sqltoy.jsonProvider)
	 */
	public static void configure(String providerClass) {
		if (providerClass == null || providerClass.trim().isEmpty()) {
			return;
		}
		try {
			configured = (JsonProvider) Class.forName(providerClass.trim()).getDeclaredConstructor().newInstance();
			logger.info("sqltoy json provider configured: {}", configured.name());
		} catch (Exception e) {
			throw new IllegalStateException("spring.sqltoy.jsonProvider class [" + providerClass
					+ "] can not be instantiated, it must implement org.sagacity.sqltoy.plugins.json.JsonProvider!", e);
		}
	}

	/**
	 * 启动期调用(SqlToyContext.initialize):显式配置非法时快速失败;
	 * 仅缺 JSON 库时记 WARN 不阻断启动(不用 JSON/ES 特性的项目无需任何 JSON 库)
	 */
	public static JsonProvider initAtStartup(String configuredClass) {
		configure(configuredClass);
		try {
			return get();
		} catch (IllegalStateException e) {
			logger.warn("no JSON provider on the classpath (fastjson2 / jackson3 / jackson2): JSON columns and the"
					+ " Elasticsearch integration stay unavailable until one is added; everything else is unaffected.");
			return null;
		}
	}

	public static JsonProvider get() {
		if (configured != null) {
			return configured;
		}
		if (detected == null) {
			synchronized (JsonProviders.class) {
				if (detected == null) {
					detected = detect();
				}
			}
		}
		return detected;
	}

	private static JsonProvider detect() {
		// fastjson2 first: every project that made JSON columns work before the provider
		// abstraction exists has fastjson2 on its classpath — keep their behavior identical;
		// then jackson 3 (Spring Boot 4 default), then jackson 2
		if (present("com.alibaba.fastjson2.JSON")) {
			logger.info("sqltoy json provider detected from classpath: fastjson2");
			return new FastjsonJsonProvider();
		}
		if (present("tools.jackson.databind.ObjectMapper")) {
			logger.info("sqltoy json provider detected from classpath: jackson3");
			return new Jackson3JsonProvider();
		}
		if (present("com.fasterxml.jackson.databind.ObjectMapper")) {
			logger.info("sqltoy json provider detected from classpath: jackson2");
			return new Jackson2JsonProvider();
		}
		throw new IllegalStateException(
				"JSON column support needs a JSON library on the classpath: add com.fasterxml.jackson.core:jackson-databind"
						+ " (or tools.jackson for Jackson 3, or com.alibaba.fastjson2:fastjson2), or point"
						+ " spring.sqltoy.jsonProvider at a custom org.sagacity.sqltoy.plugins.json.JsonProvider implementation.");
	}

	private static boolean present(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (Throwable e) {
			return false;
		}
	}
}
