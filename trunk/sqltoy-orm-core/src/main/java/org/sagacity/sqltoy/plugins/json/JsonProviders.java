package org.sagacity.sqltoy.plugins.json;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description JsonProvider 的解析与持有: 1) spring.sqltoy.jsonProvider 显式配置的实现类优先;
 *              2) 否则按 classpath 探测:fastjson2(兼容存量项目) -> jackson3 -> jackson2;
 *              3) 候选必须通过冒烟解析才会被采纳:库在 classpath 上并不等于可用(例如 jackson3 的 databind
 *              需要 jackson-annotations 2.20+,与 jackson2 共存时可能被仲裁成更旧的版本, 运行期抛
 *              NoSuchFieldError),此时记 WARN 并降级到下一个候选; 4) 没有任何可用实现时,触及 JSON
 *              列才抛出带明确指引的异常(而非 NoClassDefFoundError)。
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public final class JsonProviders {

	private static final Logger logger = LoggerFactory.getLogger(JsonProviders.class);

	/** 冒烟载荷:覆盖对象/嵌套对象/数组/布尔/数字,足以暴露依赖冲突与版本不兼容 */
	private static final String SMOKE_JSON = "{\"sqltoy\":1,\"nested\":{\"ok\":true},\"list\":[1,2]}";

	private static final String FASTJSON_IMPL = "org.sagacity.sqltoy.plugins.json.FastjsonJsonProvider";
	private static final String JACKSON3_IMPL = "org.sagacity.sqltoy.plugins.json.Jackson3JsonProvider";
	private static final String JACKSON2_IMPL = "org.sagacity.sqltoy.plugins.json.Jackson2JsonProvider";

	/**
	 * 探测候选:{marker 类名, 实现类名}。顺序即优先级:fastjson2 优先,因为 provider 抽象出现之前 就把 JSON 列跑起来的项目
	 * classpath 上一定有 fastjson2,保持它们的行为完全不变; 其后 jackson3(Spring Boot 4 默认)、jackson2。
	 * 全部用字符串:引用 Xxx.class 或 Xxx::new 都会在类加载/初始化阶段就要求对应的 JSON 库存在
	 */
	private static final String[][] CANDIDATES = { { "com.alibaba.fastjson2.JSON", FASTJSON_IMPL },
			{ "tools.jackson.databind.ObjectMapper", JACKSON3_IMPL },
			{ "com.fasterxml.jackson.databind.ObjectMapper", JACKSON2_IMPL } };

	/**
	 * 内置实现的简写别名(小写比较),配置时无需写全限定类名;自定义实现仍写全限定类名。 一律用字符串而非
	 * Xxx.class.getName():本类会在只装了部分 JSON 库的项目里被加载, 任何对具体实现类的静态引用都可能触发其类初始化并抛
	 * NoClassDefFoundError
	 */
	private static final Map<String, String> ALIASES = Map.of("fastjson", FASTJSON_IMPL, "fastjson2", FASTJSON_IMPL,
			"jackson2", JACKSON2_IMPL, "jackson-2", JACKSON2_IMPL, "jackson3", JACKSON3_IMPL, "jackson-3",
			JACKSON3_IMPL);

	private static volatile JsonProvider configured;
	private static volatile JsonProvider detected;

	private JsonProviders() {
	}

	/**
	 * 由 SqlToyContext 初始化时调用(配置项 spring.sqltoy.jsonProvider)。
	 * 取值支持两种写法:内置实现的简写别名(fastjson2 / jackson3 / jackson2), 或任意 JsonProvider
	 * 实现的全限定类名。 显式配置代表使用者的明确意图,因此不可用时直接失败并带上根因, 而不是静默回落到探测结果(那会让配置形同虚设且难以排查)。
	 */
	public static void configure(String providerClass) {
		if (providerClass == null || providerClass.trim().isEmpty()) {
			return;
		}
		String className = resolveClassName(providerClass);
		JsonProvider provider;
		try {
			provider = (JsonProvider) Class.forName(className).getDeclaredConstructor().newInstance();
		} catch (Throwable e) {
			throw new IllegalStateException("spring.sqltoy.jsonProvider [" + providerClass
					+ "] can not be instantiated: use a built-in alias (fastjson2 / jackson3 / jackson2) or the full"
					+ " class name of an org.sagacity.sqltoy.plugins.json.JsonProvider implementation!", e);
		}
		try {
			smokeTest(provider);
		} catch (Throwable e) {
			throw new IllegalStateException("spring.sqltoy.jsonProvider [" + providerClass
					+ "] is on the classpath but can not parse JSON, please check the version compatibility of the"
					+ " underlying JSON library!", e);
		}
		configured = provider;
		logger.info("sqltoy json provider configured: {} ({})", provider.name(), className);
	}

	/**
	 * 简写别名转全限定类名,非别名原样返回(视为自定义实现类名)
	 */
	private static String resolveClassName(String providerClass) {
		String trimmed = providerClass.trim();
		return ALIASES.getOrDefault(trimmed.toLowerCase(java.util.Locale.ROOT), trimmed);
	}

	/**
	 * 启动期调用(SqlToyContext.initialize):显式配置非法时快速失败; 仅缺可用 JSON 库时记 WARN 不阻断启动(不用
	 * JSON/ES 特性的项目无需任何 JSON 库)
	 */
	public static JsonProvider initAtStartup(String configuredClass) {
		configure(configuredClass);
		try {
			return get();
		} catch (IllegalStateException e) {
			logger.warn("no usable JSON provider on the classpath (fastjson2 / jackson3 / jackson2): JSON columns and"
					+ " the Elasticsearch integration stay unavailable until one is added; everything else is"
					+ " unaffected.");
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
		for (String[] candidate : CANDIDATES) {
			JsonProvider provider = probe(candidate[0], candidate[1]);
			if (provider != null) {
				return provider;
			}
		}
		throw new IllegalStateException(
				"JSON column support needs a usable JSON library on the classpath: add com.fasterxml.jackson.core:jackson-databind"
						+ " (or tools.jackson for Jackson 3, or com.alibaba.fastjson2:fastjson2), or point"
						+ " spring.sqltoy.jsonProvider at a custom org.sagacity.sqltoy.plugins.json.JsonProvider"
						+ " implementation. If a JSON library is already present, see the warnings above for why"
						+ " it was rejected.");
	}

	/**
	 * 库存在且能真正解析 JSON 才算可用;不可用时记 WARN 交回调用方降级。 marker 类不存在时静默跳过——项目只装一个 JSON
	 * 库是常态,不该为此刷 WARN。 实现类在 try 内部反射加载:这样即使它的静态初始化因缺库而抛 NoClassDefFoundError,
	 * 也只是降级到下一个候选,而不是让整个 SqlToyContext 启动失败
	 */
	private static JsonProvider probe(String markerClass, String providerClass) {
		if (!present(markerClass)) {
			return null;
		}
		try {
			JsonProvider provider = (JsonProvider) Class.forName(providerClass).getDeclaredConstructor().newInstance();
			smokeTest(provider);
			logger.info("sqltoy json provider detected from classpath: {}", provider.name());
			return provider;
		} catch (Throwable e) {
			logger.warn(
					"JSON library [{}] is on the classpath but its sqltoy provider [{}] can not parse JSON ({}),"
							+ " trying the next candidate; please check the version compatibility of the JSON library",
					markerClass, providerClass, e.toString());
			return null;
		}
	}

	/**
	 * 最小可用验证:对象解析、嵌套取值、类型化数组、序列化各走一遍
	 */
	private static void smokeTest(JsonProvider provider) {
		Map<String, Object> map = provider.parseObjectMap(SMOKE_JSON);
		if (map == null || ((Number) map.get("sqltoy")).intValue() != 1) {
			throw new IllegalStateException("unexpected object parse result: " + map);
		}
		Object nested = map.get("nested");
		if (!(nested instanceof Map) || !Boolean.TRUE.equals(((Map<?, ?>) nested).get("ok"))) {
			throw new IllegalStateException("unexpected nested parse result: " + nested);
		}
		List<Integer> typed = provider.parseArray("[1,2]", Integer.class);
		if (typed == null || typed.size() != 2 || typed.get(1).intValue() != 2) {
			throw new IllegalStateException("unexpected typed array parse result: " + typed);
		}
		String json = provider.toJSONString(map);
		if (json == null || !json.contains("sqltoy")) {
			throw new IllegalStateException("unexpected serialize result: " + json);
		}
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
