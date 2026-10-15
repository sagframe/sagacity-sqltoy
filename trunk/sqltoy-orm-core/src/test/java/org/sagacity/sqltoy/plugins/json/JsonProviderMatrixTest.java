package org.sagacity.sqltoy.plugins.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.JSONTypeUtil;

/**
 * @project sagacity-sqltoy
 * @description JsonProvider SPI 的多轮矩阵验证。core 的 test classpath 同时具备 fastjson2 /
 *              jackson3 / jackson2 三个库,因此可以在同一 JVM 内横向对比:
 *              <li>Round1 解析优先级与显式配置</li>
 *              <li>Round2 包装层契约(JsonObject/JsonArray 的 Map/List 语义)</li>
 *              <li>Round3 外部 JSON 库序列化包装对象的回归护栏</li>
 *              <li>Round4 跨 provider 语义等价(含已知的小数表示法差异)</li>
 *              <li>Round5 ES 响应载荷形状(hits.total 必须是 java.util.Map)</li>
 *              <li>Round6 JSONTypeUtil 端到端(JSON 列 -> Java 类型)</li>
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class JsonProviderMatrixTest {

	/** 探测顺序即此顺序:fastjson2(兼容存量) -> jackson3(Boot 4 默认) -> jackson2 */
	private static final List<JsonProvider> ALL = List.of(new FastjsonJsonProvider(), new Jackson3JsonProvider(),
			new Jackson2JsonProvider());

	private static final String NESTED_JSON = """
			{"staffId":"S001","staffName":"Alice","age":30,"score":92.5,"active":true,
			 "organ":{"organId":"O01","organName":"RD"},
			 "tags":["a","b","c"],
			 "orders":[{"orderId":1,"amount":100},{"orderId":2,"amount":200}]}
			""";

	/** 不含小数:用于跨 provider 的深度相等比较(小数表示法各家不同,见 Round4) */
	private static final String FLAT_JSON = """
			{"staffId":"S001","staffName":"Alice","age":30,"active":true,
			 "organ":{"organId":"O01","organName":"RD"},
			 "tags":["a","b","c"],
			 "orders":[{"orderId":1,"amount":100},{"orderId":2,"amount":200}]}
			""";

	/** ES 7 的真实响应形状:hits.total 是 {value,relation} 对象而非数字 */
	private static final String ES_RESPONSE = """
			{"took":3,"timed_out":false,
			 "hits":{"total":{"value":7,"relation":"eq"},"max_score":1.0,
			         "hits":[{"_index":"staff","_id":"S001","_score":1.0,
			                  "_source":{"staffId":"S001","staffName":"Alice"}},
			                 {"_index":"staff","_id":"S002","_score":0.8,
			                  "_source":{"staffId":"S002","staffName":"Bob"}}]},
			 "aggregations":{"by_organ":{"buckets":[{"key":"O01","doc_count":4},
			                                       {"key":"O02","doc_count":3}]}}}
			""";

	@BeforeEach
	void resetBefore() throws Exception {
		resetProviderState();
	}

	@AfterEach
	void resetAfter() throws Exception {
		// 静态状态是 JVM 级共享的,交还给后续测试类时必须清空
		resetProviderState();
	}

	private static void resetProviderState() throws Exception {
		for (String fieldName : new String[] { "configured", "detected" }) {
			Field field = JsonProviders.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(null, null);
		}
	}

	private static boolean onClasspath(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	// ==================================================================================
	// Round 1:解析优先级与显式配置
	// ==================================================================================
	@Nested
	@DisplayName("Round1: provider 解析优先级与显式配置")
	class Resolution {

		@Test
		@DisplayName("三个库同时存在时探测到 fastjson2(存量项目行为不变)")
		void detectPrefersFastjson2WhenAllPresent() {
			assertTrue(onClasspath("com.alibaba.fastjson2.JSON"), "本测试要求 fastjson2 在 test classpath 上");
			assertTrue(onClasspath("tools.jackson.databind.ObjectMapper"), "本测试要求 jackson3 在 test classpath 上");
			assertTrue(onClasspath("com.fasterxml.jackson.databind.ObjectMapper"),
					"本测试要求 jackson2 在 test classpath 上");
			assertEquals("fastjson2", JsonProviders.get().name());
		}

		@Test
		@DisplayName("显式配置压过 classpath 探测")
		void explicitConfigBeatsDetection() {
			// fastjson2 在 classpath 上且探测优先级最高,配置 jackson2 必须仍然胜出
			JsonProviders.configure(Jackson2JsonProvider.class.getName());
			assertEquals("jackson2", JsonProviders.get().name());
		}

		@Test
		@DisplayName("三个 provider 都能被显式选中")
		void anyProviderCanBeConfigured() throws Exception {
			for (JsonProvider provider : ALL) {
				resetProviderState();
				JsonProviders.configure(provider.getClass().getName());
				assertEquals(provider.name(), JsonProviders.get().name());
			}
		}

		@Test
		@DisplayName("空配置被忽略,回落到探测")
		void blankConfigFallsBackToDetection() {
			JsonProviders.configure(null);
			JsonProviders.configure("");
			JsonProviders.configure("   ");
			assertEquals("fastjson2", JsonProviders.get().name());
		}

		@Test
		@DisplayName("配置了不存在的类:快速失败并给出指引")
		void unknownProviderClassFailsFast() {
			IllegalStateException e = assertThrows(IllegalStateException.class,
					() -> JsonProviders.configure("org.sagacity.sqltoy.NoSuchJsonProvider"));
			assertTrue(e.getMessage().contains("spring.sqltoy.jsonProvider"), e.getMessage());
			assertTrue(e.getMessage().contains(JsonProvider.class.getName()), e.getMessage());
		}

		@Test
		@DisplayName("配置了未实现 JsonProvider 的类:快速失败")
		void nonProviderClassFailsFast() {
			assertThrows(IllegalStateException.class, () -> JsonProviders.configure(String.class.getName()));
		}

		@Test
		@DisplayName("启动期初始化:有库时返回 provider")
		void initAtStartupReturnsProvider() {
			JsonProvider provider = JsonProviders.initAtStartup(null);
			assertNotNull(provider);
			assertEquals("fastjson2", provider.name());
			assertEquals("fastjson2", JsonProviders.initAtStartup(null).name());
		}

		@Test
		@DisplayName("启动期初始化:显式配置非法必须抛出,不能被 WARN 吞掉")
		void initAtStartupPropagatesInvalidConfig() {
			assertThrows(IllegalStateException.class,
					() -> JsonProviders.initAtStartup("org.sagacity.sqltoy.NoSuchJsonProvider"));
		}

		@Test
		@DisplayName("探测结果被缓存,不重复探测")
		void detectionIsCached() {
			assertSame(JsonProviders.get(), JsonProviders.get());
		}

		@Test
		@DisplayName("配置后 get() 稳定返回同一实例")
		void configuredProviderIsStable() {
			JsonProviders.configure(Jackson3JsonProvider.class.getName());
			assertSame(JsonProviders.get(), JsonProviders.get());
			assertEquals("jackson3", JsonProviders.get().name());
		}

		@Test
		@DisplayName("配置支持简写别名:fastjson2 / jackson3 / jackson2(大小写、连字符、空格不敏感)")
		void shortAliasesAreAccepted() throws Exception {
			Map<String, String> cases = new LinkedHashMap<>();
			cases.put("fastjson2", "fastjson2");
			cases.put("FASTJSON2", "fastjson2");
			cases.put("  fastjson ", "fastjson2");
			cases.put("jackson2", "jackson2");
			cases.put("Jackson-2", "jackson2");
			cases.put("jackson3", "jackson3");
			cases.put("JACKSON-3", "jackson3");
			for (Map.Entry<String, String> entry : cases.entrySet()) {
				resetProviderState();
				JsonProviders.configure(entry.getKey());
				assertEquals(entry.getValue(), JsonProviders.get().name(), "别名 [" + entry.getKey() + "] 未生效");
			}
		}

		@Test
		@DisplayName("别名与全限定类名等价")
		void aliasEqualsFullClassName() throws Exception {
			resetProviderState();
			JsonProviders.configure("jackson3");
			Class<?> byAlias = JsonProviders.get().getClass();
			resetProviderState();
			JsonProviders.configure(Jackson3JsonProvider.class.getName());
			assertEquals(byAlias, JsonProviders.get().getClass());
		}

		@Test
		@DisplayName("无法识别的取值:报错信息要列出可用别名与接口名")
		void unrecognizedValueListsAliases() {
			IllegalStateException e = assertThrows(IllegalStateException.class, () -> JsonProviders.configure("gson"));
			String message = e.getMessage();
			assertTrue(message.contains("spring.sqltoy.jsonProvider"), message);
			assertTrue(message.contains("fastjson2"), message);
			assertTrue(message.contains("jackson3"), message);
			assertTrue(message.contains("jackson2"), message);
			assertTrue(message.contains(JsonProvider.class.getName()), message);
		}

		@Test
		@DisplayName("显式配置了一个解析即抛错的 provider:启动就失败,保留根因,且不留半配置状态")
		void brokenExplicitProviderFailsFast() {
			IllegalStateException e = assertThrows(IllegalStateException.class,
					() -> JsonProviders.configure(BrokenProvider.class.getName()));
			assertTrue(e.getMessage().contains("spring.sqltoy.jsonProvider"), e.getMessage());
			assertTrue(e.getMessage().contains("version compatibility"), e.getMessage());
			// 根因必须被保留,否则又回到难以定位的 NoSuchFieldError
			assertInstanceOf(NoSuchFieldError.class, e.getCause(), "根因应被保留");
			// 配置失败后 configured 不能被污染,仍回落到探测结果
			assertEquals("fastjson2", JsonProviders.get().name());
		}

		@Test
		@DisplayName("探测阶段:库在 classpath 上但不可用时返回 null 以触发降级")
		void probeRejectsUnusableLibrary() throws Exception {
			java.lang.reflect.Method probe = probeMethod();
			// marker 存在但 provider 一解析就抛错:必须被拒绝(这正是 jackson-annotations 版本
			// 被仲裁成过旧版本时的形态),从而降级到下一个候选
			assertNull(probe.invoke(null, "java.lang.String", BrokenProvider.class.getName()));
			// marker 不存在:静默跳过,不刷 WARN
			assertNull(probe.invoke(null, "com.not.exists.Marker", Jackson2JsonProvider.class.getName()));
			// 实现类不存在:同样降级而不是抛出
			assertNull(probe.invoke(null, "java.lang.String", "org.sagacity.sqltoy.plugins.json.NoSuchImpl"));
			// 可用的候选正常返回
			JsonProvider accepted = (JsonProvider) probe.invoke(null, "com.fasterxml.jackson.databind.ObjectMapper",
					Jackson2JsonProvider.class.getName());
			assertNotNull(accepted);
			assertEquals("jackson2", accepted.name());
		}

		@Test
		@DisplayName("实现类静态初始化就失败时,probe 必须降级而不是让 SqlToyContext 启动失败")
		void probeSurvivesProviderClassInitializationFailure() throws Exception {
			// 回归护栏:曾用 Jackson3JsonProvider::new 作方法引用传参,构造器方法引用在传参时
			// 就会触发实现类初始化,其静态 MAPPER 字段引用 tools.jackson,在没装 jackson3 的
			// 项目里直接抛 NoClassDefFoundError,且发生在进入 probe 之前——守卫与 try 全部失效。
			// 现在实现类一律在 try 内部按类名反射加载,该场景必须安全降级
			assertNull(probeMethod().invoke(null, "java.lang.String", ExplodingOnInitProvider.class.getName()));
			// 降级后仍能拿到可用 provider,整体探测不受影响
			assertEquals("fastjson2", JsonProviders.get().name());
		}

		private java.lang.reflect.Method probeMethod() throws Exception {
			java.lang.reflect.Method probe = JsonProviders.class.getDeclaredMethod("probe", String.class,
					String.class);
			probe.setAccessible(true);
			return probe;
		}
	}

	// ==================================================================================
	// Round 2:包装层契约
	// ==================================================================================
	@Nested
	@DisplayName("Round2: JsonObject/JsonArray 包装层契约")
	class WrapperContract {

		@Test
		@DisplayName("包装对象就是 java.util.Map / java.util.List(框架内 instanceof 判断的依据)")
		void wrappersArePlainMapAndList() {
			for (JsonProvider provider : ALL) {
				JsonObject object = provider.object(NESTED_JSON);
				JsonArray array = provider.array("[1,2,3]");
				String tag = provider.name();
				assertInstanceOf(Map.class, object, tag);
				assertInstanceOf(List.class, array, tag);
				// 继承抽象基类,任何 JSON 库都会把它们当容器而非 bean 处理
				assertTrue(object instanceof java.util.AbstractMap, tag);
				assertTrue(array instanceof java.util.AbstractList, tag);
			}
		}

		@Test
		@DisplayName("类型化取值器在三个 provider 上语义一致")
		void typedGettersAgree() {
			for (JsonProvider provider : ALL) {
				JsonObject object = provider.object(NESTED_JSON);
				String tag = provider.name();
				assertEquals("S001", object.getString("staffId"), tag);
				assertEquals("Alice", object.getString("staffName"), tag);
				// 数字取字符串:与 fastjson 的历史行为对齐
				assertEquals("30", object.getString("age"), tag);
				assertEquals(Integer.valueOf(30), object.getInteger("age"), tag);
				assertEquals(Long.valueOf(30L), object.getLong("age"), tag);
				assertEquals(30, object.getIntValue("age"), tag);
				assertEquals(30L, object.getLongValue("age"), tag);
				assertEquals(Boolean.TRUE, object.getBoolean("active"), tag);
				assertTrue(object.getBooleanValue("active"), tag);
				assertTrue(object.containsKey("score"), tag);
				assertFalse(object.containsKey("nope"), tag);
			}
		}

		@Test
		@DisplayName("字符串数字可转 Long,缺失键返回 null / 0 / false")
		void lenientConversionAndMissingKey() {
			for (JsonProvider provider : ALL) {
				JsonObject object = provider.object("{\"code\":\"123\",\"flag\":\"true\"}");
				String tag = provider.name();
				assertEquals(Long.valueOf(123L), object.getLong("code"), tag);
				assertEquals(Integer.valueOf(123), object.getInteger("code"), tag);
				assertEquals(Boolean.TRUE, object.getBoolean("flag"), tag);
				assertNull(object.getString("missing"), tag);
				assertNull(object.getInteger("missing"), tag);
				assertNull(object.getLong("missing"), tag);
				assertNull(object.getBoolean("missing"), tag);
				assertEquals(0, object.getIntValue("missing"), tag);
				assertEquals(0L, object.getLongValue("missing"), tag);
				assertFalse(object.getBooleanValue("missing"), tag);
				assertNull(object.getJSONObject("missing"), tag);
				assertNull(object.getJSONArray("missing"), tag);
			}
		}

		@Test
		@DisplayName("嵌套对象与数组可逐层导航")
		void nestedNavigation() {
			for (JsonProvider provider : ALL) {
				JsonObject root = provider.object(NESTED_JSON);
				String tag = provider.name();
				assertEquals("O01", root.getJSONObject("organ").getString("organId"), tag);
				JsonArray tags = root.getJSONArray("tags");
				assertEquals(3, tags.size(), tag);
				assertEquals("a", tags.getString(0), tag);
				assertFalse(tags.isEmpty(), tag);
				JsonArray orders = root.getJSONArray("orders");
				assertEquals(2, orders.size(), tag);
				assertEquals(1, orders.getJSONObject(0).getIntValue("orderId"), tag);
				assertEquals(200, orders.getJSONObject(1).getIntValue("amount"), tag);
				// 原生嵌套节点本身就是 Map,这是 instanceof java.util.Map 判断成立的前提
				assertInstanceOf(Map.class, orders.get(0), tag);
			}
		}

		@Test
		@DisplayName("写入时自动拆包嵌套 wrapper,避免 Map 里再套 JsonObject")
		void mutationUnwrapsNestedWrappers() {
			JsonObject object = new JsonObject();
			object.put("child", new JsonObject().fluentPut("k", "v"));
			object.put("array", new JsonArray().append(1).append(2));
			assertInstanceOf(Map.class, object.get("child"));
			assertFalse(object.get("child") instanceof JsonObject, "写入时应已拆包");
			assertInstanceOf(List.class, object.get("array"));
			assertFalse(object.get("array") instanceof JsonArray, "写入时应已拆包");
			// put 返回自身以支持链式
			assertSame(object, object.put("another", 1));
			assertEquals(3, object.size());
			Object removed = object.remove("child");
			assertInstanceOf(Map.class, removed);
			assertEquals("v", ((Map<?, ?>) removed).get("k"));
			assertFalse(object.containsKey("child"));
		}

		@Test
		@DisplayName("JsonArray.add 遵守 List 契约返回 boolean,链式追加用 append")
		void addReturnsBooleanAppendReturnsArray() {
			JsonArray array = new JsonArray();
			assertTrue(array.add("x"));
			assertSame(array, array.append("y"));
			assertEquals(2, array.size());
			assertEquals(List.of("x", "y"), new ArrayList<>(array));
			assertArrayEquals(new Object[] { "x", "y" }, array.toArray());
		}

		@Test
		@DisplayName("unwrap 暴露底层容器,of() 对 null 与已包装对象幂等")
		void unwrapAndFactoryIdempotence() {
			for (JsonProvider provider : ALL) {
				JsonObject object = provider.object(NESTED_JSON);
				JsonArray array = provider.array("[1,2]");
				String tag = provider.name();
				assertNotNull(object.unwrap(), tag);
				assertEquals(object.size(), object.unwrap().size(), tag);
				assertSame(object, JsonObject.of(object), tag);
				assertNull(JsonObject.of(null), tag);
				assertNotNull(array.unwrap(), tag);
				assertSame(array, JsonArray.of(array), tag);
				assertNull(JsonArray.of(null), tag);
			}
			// 构造器对 null 容器兜底成空集合而不是 NPE
			assertEquals(0, new JsonObject(null).size());
			assertEquals(0, new JsonArray(null).size());
		}

		@Test
		@DisplayName("entrySet/keySet/values 等 Map 视图可用(AbstractMap 派生)")
		void mapViewsWork() {
			JsonObject object = new Jackson2JsonProvider().object("{\"a\":1,\"b\":2}");
			assertEquals(Set.of("a", "b"), object.keySet());
			assertEquals(2, object.values().size());
			assertEquals(2, object.entrySet().size());
			assertEquals(2, new LinkedHashMap<>(object).size());
		}

		@Test
		@DisplayName("toJavaList 把节点集合转成目标类型")
		void toJavaListConvertsElements() {
			JsonProviders.configure(Jackson2JsonProvider.class.getName());
			JsonArray array = new Jackson2JsonProvider().array(
					"[{\"staffId\":\"S001\",\"staffName\":\"Alice\"},{\"staffId\":\"S002\",\"staffName\":\"Bob\"}]");
			List<Staff> staff = array.toJavaList(Staff.class);
			assertEquals(2, staff.size());
			assertEquals("S001", staff.get(0).getStaffId());
			assertEquals("Bob", staff.get(1).getStaffName());
		}
	}

	// ==================================================================================
	// Round 3:外部 JSON 库序列化包装对象(回归护栏)
	// ==================================================================================
	@Nested
	@DisplayName("Round3: 外部 JSON 库序列化 wrapper 必须是容器而非 bean")
	class ExternalSerialization {

		@Test
		@DisplayName("jackson2 序列化 JsonObject 得到 JSON 对象,不泄漏 empty/unwrap 等 getter")
		void jackson2SerializesObjectWrapperAsContainer() throws Exception {
			JsonObject object = new Jackson2JsonProvider().object("{\"a\":1,\"b\":\"x\"}");
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			String json = mapper.writeValueAsString(object);
			assertFalse(json.contains("empty"), "曾被当 bean 序列化写出 empty 字段:" + json);
			assertFalse(json.contains("unwrap"), json);
			Map<?, ?> back = mapper.readValue(json, Map.class);
			assertEquals(Set.of("a", "b"), back.keySet());
			assertEquals(1, ((Number) back.get("a")).intValue());
			assertEquals("x", back.get("b"));
		}

		@Test
		@DisplayName("jackson2 序列化 JsonArray 得到 JSON 数组")
		void jackson2SerializesArrayWrapperAsContainer() throws Exception {
			JsonArray array = new Jackson2JsonProvider().array("[1,2,3]");
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			String json = mapper.writeValueAsString(array);
			assertTrue(json.startsWith("[") && json.endsWith("]"), json);
			assertEquals(List.of(1, 2, 3), mapper.readValue(json, List.class));
		}

		@Test
		@DisplayName("jackson3 序列化 wrapper 同样得到容器")
		void jackson3SerializesWrapperAsContainer() {
			JsonObject object = new Jackson3JsonProvider().object("{\"a\":1}");
			tools.jackson.databind.ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();
			String json = mapper.writeValueAsString(object);
			assertFalse(json.contains("empty"), json);
			assertEquals(1, ((Number) mapper.readValue(json, Map.class).get("a")).intValue());
		}

		@Test
		@DisplayName("fastjson2 序列化 wrapper 同样得到容器")
		void fastjson2SerializesWrapperAsContainer() {
			JsonObject object = new FastjsonJsonProvider().object("{\"a\":1,\"n\":{\"b\":2}}");
			String json = com.alibaba.fastjson2.JSON.toJSONString(object);
			assertFalse(json.contains("empty"), json);
			assertEquals(1, com.alibaba.fastjson2.JSON.parseObject(json).getIntValue("a"));
			assertEquals(2, com.alibaba.fastjson2.JSON.parseObject(json).getJSONObject("n").getIntValue("b"));
		}

		@Test
		@DisplayName("wrapper 自身的 toJSONString/toString 产出合法 JSON")
		void wrapperToJsonStringIsValidJson() {
			JsonProviders.configure(Jackson2JsonProvider.class.getName());
			Jackson2JsonProvider provider = new Jackson2JsonProvider();
			JsonObject object = provider.object(NESTED_JSON);
			String json = object.toJSONString();
			assertEquals("S001", provider.object(json).getString("staffId"));
			assertEquals(json, object.toString());
			JsonArray array = provider.array("[1,2]");
			assertEquals("[1,2]", array.toJSONString());
			assertEquals(array.toJSONString(), array.toString());
		}
	}

	// ==================================================================================
	// Round 4:跨 provider 语义等价
	// ==================================================================================
	@Nested
	@DisplayName("Round4: 三个 provider 对同一份 JSON 的理解必须一致")
	class CrossProviderEquivalence {

		@Test
		@DisplayName("对象图逐层深度相等(不含小数的载荷)")
		void allProvidersAgreeOnObjectGraph() {
			Map<String, Object> baseline = null;
			String baselineName = null;
			for (JsonProvider provider : ALL) {
				Map<String, Object> parsed = provider.parseObjectMap(FLAT_JSON);
				if (baseline == null) {
					baseline = parsed;
					baselineName = provider.name();
					continue;
				}
				assertEquals(baseline, parsed, baselineName + " 与 " + provider.name() + " 解析结果不一致");
			}
			assertNotNull(baseline);
			assertEquals("S001", baseline.get("staffId"));
			assertInstanceOf(Map.class, baseline.get("organ"));
			assertInstanceOf(List.class, baseline.get("tags"));
			assertInstanceOf(List.class, baseline.get("orders"));
		}

		@Test
		@DisplayName("已知差异:小数在 fastjson2 是 BigDecimal、在 jackson 是浮点数,取值须走 Number")
		void decimalRepresentationDiffersByProvider() {
			Map<String, Object> fast = new FastjsonJsonProvider().parseObjectMap("{\"score\":92.5}");
			assertInstanceOf(BigDecimal.class, fast.get("score"), "fastjson2 默认用 BigDecimal 表示小数");
			for (JsonProvider provider : List.of(new Jackson3JsonProvider(), new Jackson2JsonProvider())) {
				Object score = provider.parseObjectMap("{\"score\":92.5}").get("score");
				assertInstanceOf(Number.class, score, provider.name());
				assertEquals(92.5, ((Number) score).doubleValue(), 1e-9, provider.name());
			}
			// 数值语义一致:统一按 Number 取值即可屏蔽差异
			assertEquals(92.5, ((Number) fast.get("score")).doubleValue(), 1e-9);
			// 包装视图的取值器同样屏蔽了该差异
			assertEquals(92L, new FastjsonJsonProvider().object("{\"score\":92.5}").getLong("score"));
			assertEquals("92.5", new FastjsonJsonProvider().object("{\"score\":92.5}").getString("score"));
		}

		@Test
		@DisplayName("数组解析结果一致")
		void allProvidersAgreeOnArray() {
			for (JsonProvider provider : ALL) {
				List<Object> parsed = provider.parseArray("[1,2,{\"k\":\"v\"}]");
				String tag = provider.name();
				assertEquals(3, parsed.size(), tag);
				assertEquals(1, ((Number) parsed.get(0)).intValue(), tag);
				assertEquals("v", ((Map<?, ?>) parsed.get(2)).get("k"), tag);
			}
		}

		@Test
		@DisplayName("POJO 绑定结果一致")
		void allProvidersAgreeOnPojoBinding() {
			String json = "{\"staffId\":\"S001\",\"staffName\":\"Alice\",\"age\":30,\"active\":true}";
			for (JsonProvider provider : ALL) {
				Staff staff = provider.parseObject(json, Staff.class);
				String tag = provider.name();
				assertEquals("S001", staff.getStaffId(), tag);
				assertEquals("Alice", staff.getStaffName(), tag);
				assertEquals(30, staff.getAge(), tag);
				assertTrue(staff.isActive(), tag);
			}
		}

		@Test
		@DisplayName("未知属性不报错(与 fastjson 的宽松绑定对齐)")
		void unknownPropertiesAreTolerated() {
			String json = "{\"staffId\":\"S001\",\"totallyUnknown\":{\"deep\":[1,2]},\"another\":\"x\"}";
			for (JsonProvider provider : ALL) {
				Staff staff = assertDoesNotThrow(() -> provider.parseObject(json, Staff.class), provider.name());
				assertEquals("S001", staff.getStaffId(), provider.name());
			}
		}

		@Test
		@DisplayName("元素类型化数组解析一致")
		void allProvidersAgreeOnTypedArray() {
			String json = "[{\"staffId\":\"S001\"},{\"staffId\":\"S002\"}]";
			for (JsonProvider provider : ALL) {
				List<Staff> list = provider.parseArray(json, Staff.class);
				assertEquals(2, list.size(), provider.name());
				assertEquals("S002", list.get(1).getStaffId(), provider.name());
			}
		}

		@Test
		@DisplayName("序列化同一 POJO 的产物语义相等(键顺序不保证,故比较解析后的 Map)")
		void toJSONStringIsSemanticallyEqual() {
			Staff staff = new Staff();
			staff.setStaffId("S001");
			staff.setStaffName("Alice");
			staff.setAge(30);
			staff.setActive(true);
			Map<String, Object> baseline = null;
			for (JsonProvider provider : ALL) {
				Map<String, Object> parsed = provider.parseObjectMap(provider.toJSONString(staff));
				if (baseline == null) {
					baseline = parsed;
					continue;
				}
				assertEquals(baseline, parsed, provider.name());
			}
			assertNotNull(baseline);
			assertEquals("S001", baseline.get("staffId"));
			assertEquals(30, ((Number) baseline.get("age")).intValue());
		}

		@Test
		@DisplayName("convert 把已解析节点转成目标类型")
		void convertNodeToType() {
			for (JsonProvider provider : ALL) {
				Object node = provider.parseObjectMap("{\"staffId\":\"S009\",\"staffName\":\"Cathy\"}");
				Staff staff = provider.convert(node, Staff.class);
				assertEquals("S009", staff.getStaffId(), provider.name());
				assertEquals("Cathy", staff.getStaffName(), provider.name());
			}
		}

		@Test
		@DisplayName("parseObjectNative/parseArrayNative 返回的原生对象也是 Map/List")
		void nativeResultsAreStillContainers() {
			for (JsonProvider provider : ALL) {
				assertInstanceOf(Map.class, provider.parseObjectNative("{\"a\":1}"), provider.name());
				assertInstanceOf(List.class, provider.parseArrayNative("[1]"), provider.name());
			}
		}
	}

	// ==================================================================================
	// Round 5:ES 响应载荷形状
	// ==================================================================================
	@Nested
	@DisplayName("Round5: ES 响应载荷(曾触发 NumberFormatException 的形状)")
	class ElasticPayloadShape {

		@Test
		@DisplayName("hits.total 是 java.util.Map,value 可按 Number 取出")
		void hitsTotalIsJavaUtilMap() {
			for (JsonProvider provider : ALL) {
				JsonObject hits = provider.object(ES_RESPONSE).getJSONObject("hits");
				String tag = provider.name();
				assertNotNull(hits, tag);
				Object total = hits.get("total");
				// 框架内一律用 instanceof java.util.Map 判断,不能依赖 JsonObject 具体类型
				assertInstanceOf(Map.class, total, "hits.total 必须是 Map:" + tag);
				Map<?, ?> totalMap = (Map<?, ?>) total;
				assertEquals(7, ((Number) totalMap.get("value")).intValue(), tag);
				assertEquals("eq", totalMap.get("relation"), tag);
				// 经包装视图访问同样成立
				assertEquals(7, hits.getJSONObject("total").getIntValue("value"), tag);
				assertEquals("eq", hits.getJSONObject("total").getString("relation"), tag);
			}
		}

		@Test
		@DisplayName("hits.hits 是 java.util.List,逐条 _source 可导航")
		void hitsArrayIsJavaUtilList() {
			for (JsonProvider provider : ALL) {
				JsonObject hits = provider.object(ES_RESPONSE).getJSONObject("hits");
				String tag = provider.name();
				assertInstanceOf(List.class, hits.get("hits"), tag);
				JsonArray rows = hits.getJSONArray("hits");
				assertEquals(2, rows.size(), tag);
				assertEquals("S001", rows.getJSONObject(0).getString("_id"), tag);
				assertEquals("Bob", rows.getJSONObject(1).getJSONObject("_source").getString("staffName"), tag);
			}
		}

		@Test
		@DisplayName("聚合 buckets 可导航,doc_count 求和正确")
		void aggregationBucketsNavigable() {
			for (JsonProvider provider : ALL) {
				JsonArray buckets = provider.object(ES_RESPONSE).getJSONObject("aggregations")
						.getJSONObject("by_organ").getJSONArray("buckets");
				String tag = provider.name();
				assertEquals(2, buckets.size(), tag);
				int sum = 0;
				for (int i = 0; i < buckets.size(); i++) {
					sum += buckets.getJSONObject(i).getIntValue("doc_count");
				}
				assertEquals(7, sum, tag);
				assertEquals("O01", buckets.getJSONObject(0).getString("key"), tag);
			}
		}

		@Test
		@DisplayName("用 wrapper 构造的 ES 请求体能被三个 provider 正确序列化并还原")
		void requestBodyBuiltWithWrapperSerializes() {
			for (JsonProvider provider : ALL) {
				JsonObject term = new JsonObject().fluentPut("status", 1);
				JsonObject must = new JsonObject().fluentPut("term", term);
				JsonObject bool = new JsonObject().fluentPut("must", new JsonArray().append(must));
				JsonObject body = new JsonObject().fluentPut("query", bool).fluentPut("size", 10);
				String tag = provider.name();
				JsonObject back = provider.object(provider.toJSONString(body));
				assertEquals(10, back.getIntValue("size"), tag);
				assertEquals(1, back.getJSONObject("query").getJSONArray("must").getJSONObject(0)
						.getJSONObject("term").getIntValue("status"), tag);
			}
		}
	}

	// ==================================================================================
	// Round 6:JSONTypeUtil 端到端(JSON 列 -> Java 类型)
	// ==================================================================================
	@Nested
	@DisplayName("Round6: JSONTypeUtil 在三个 provider 下行为一致")
	class JsonTypeUtilEndToEnd {

		private Object convert(String javaTypeName, Class<?> genericType, Object jdbcValue) throws Exception {
			return JSONTypeUtil.jsonToJavaType(JdbcTypes.JSON, javaTypeName, genericType, jdbcValue);
		}

		@Test
		@DisplayName("null 值与字符串目标类型")
		void nullAndStringTarget() throws Exception {
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				assertNull(convert("java.lang.String", null, null), tag);
				assertEquals("{\"a\":1}", convert("java.lang.String", null, "{\"a\":1}"), tag);
			}
		}

		@Test
		@DisplayName("Map 家族:Map/HashMap/LinkedHashMap/TreeMap")
		void mapFamily() throws Exception {
			String json = "{\"staffId\":\"S001\",\"age\":30}";
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				assertEquals("S001", ((Map<?, ?>) convert("java.util.Map", null, json)).get("staffId"), tag);
				assertInstanceOf(Map.class, convert("java.util.HashMap", null, json), tag);
				assertInstanceOf(LinkedHashMap.class, convert("java.util.LinkedHashMap", null, json), tag);
				assertInstanceOf(TreeMap.class, convert("java.util.TreeMap", null, json), tag);
				// TreeMap 键有序
				assertEquals(List.of("age", "staffId"),
						new ArrayList<>(((TreeMap<?, ?>) convert("java.util.TreeMap", null, json)).keySet()), tag);
			}
		}

		@Test
		@DisplayName("List 家族:带与不带泛型")
		void listFamily() throws Exception {
			String json = "[{\"staffId\":\"S001\"},{\"staffId\":\"S002\"}]";
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				List<?> raw = (List<?>) convert("java.util.List", null, json);
				assertEquals(2, raw.size(), tag);
				assertInstanceOf(Map.class, raw.get(0), tag);
				List<?> typed = (List<?>) convert("java.util.List", Staff.class, json);
				assertInstanceOf(Staff.class, typed.get(0), tag);
				assertEquals("S002", ((Staff) typed.get(1)).getStaffId(), tag);
				assertInstanceOf(List.class, convert("java.util.ArrayList", Staff.class, json), tag);
			}
		}

		@Test
		@DisplayName("Set 家族:去重与泛型转换")
		void setFamily() throws Exception {
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				assertEquals(2, ((Set<?>) convert("java.util.Set", null, "[\"a\",\"b\",\"a\"]")).size(), tag);
				Set<?> typed = (Set<?>) convert("java.util.HashSet", Staff.class,
						"[{\"staffId\":\"S001\"},{\"staffId\":\"S001\"}]");
				assertEquals(1, typed.size(), tag);
				assertInstanceOf(Staff.class, typed.iterator().next(), tag);
			}
		}

		@Test
		@DisplayName("Collection 目标类型")
		void collectionTarget() throws Exception {
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				List<?> typed = (List<?>) convert("java.util.Collection", Staff.class, "[{\"staffId\":\"S001\"}]");
				assertEquals(1, typed.size(), provider.name());
				assertInstanceOf(Staff.class, typed.get(0), provider.name());
			}
		}

		@Test
		@DisplayName("POJO 与原生 jsonobject/jsonarray 目标类型")
		void pojoAndNativeTargets() throws Exception {
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				Staff staff = (Staff) convert(Staff.class.getName(), null, "{\"staffId\":\"S001\",\"age\":30}");
				assertEquals("S001", staff.getStaffId(), tag);
				assertEquals(30, staff.getAge(), tag);
				assertInstanceOf(Map.class, convert("jsonobject", null, "{\"a\":1}"), tag);
				assertInstanceOf(List.class, convert("jsonarray", null, "[1,2]"), tag);
			}
		}

		@Test
		@DisplayName("数组目标类型:基础类型与对象数组")
		void arrayTargets() throws Exception {
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				assertArrayEquals(new int[] { 1, 2, 3 }, (int[]) convert(int[].class.getName(), null, "[1,2,3]"), tag);
				assertArrayEquals(new long[] { 1L, 2L },
						(long[]) convert(long[].class.getName(), null, "[1,2]"), tag);
				assertArrayEquals(new double[] { 1.5D, 2.5D },
						(double[]) convert(double[].class.getName(), null, "[1.5,2.5]"), tag);
				assertArrayEquals(new boolean[] { true, false },
						(boolean[]) convert(boolean[].class.getName(), null, "[true,false]"), tag);
				Staff[] staff = (Staff[]) convert(Staff[].class.getName(), null,
						"[{\"staffId\":\"S001\"},{\"staffId\":\"S002\"}]");
				assertEquals(2, staff.length, tag);
				assertEquals("S002", staff[1].getStaffId(), tag);
				// 空数组不能 NPE
				assertEquals(0, ((int[]) convert(int[].class.getName(), null, "[]")).length, tag);
			}
		}

		@Test
		@DisplayName("无法加载的目标类型返回 null 交回框架,不抛异常")
		void unloadableTypeReturnsNull() throws Exception {
			JsonProviders.configure(Jackson2JsonProvider.class.getName());
			assertNull(convert("com.not.exists.Whatever", null, "{\"a\":1}"));
		}

		@Test
		@DisplayName("JDBC 值形态归一:byte[] / 带引号标量 / Map(clickhouse 形态)")
		void jdbcValueShapes() throws Exception {
			String json = "{\"staffId\":\"S001\"}";
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				// byte[]:驱动以字节返回 JSON 文本
				assertEquals("S001",
						((Map<?, ?>) convert("java.util.Map", null, json.getBytes(StandardCharsets.UTF_8)))
								.get("staffId"),
						tag);
				// h2 的 json 列以 setString 绑定时会整体再包一层引号,需对称剥除
				String quoted = "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
				assertEquals(json, JSONTypeUtil.unwrapJsonStringScalar(quoted), tag);
				assertEquals(json, JSONTypeUtil.unwrapJsonStringScalar(json), "无引号时应原样返回:" + tag);
				assertEquals("S001", ((Map<?, ?>) convert("java.util.Map", null, quoted)).get("staffId"), tag);
				// clickhouse 动态 JSON 列 getObject 直接给 Map,需重新序列化成标准 JSON 文本
				Map<String, Object> nativeMap = new LinkedHashMap<>();
				nativeMap.put("staffId", "S001");
				assertEquals("S001", ((Map<?, ?>) convert("java.util.Map", null, nativeMap)).get("staffId"), tag);
			}
		}

		@Test
		@DisplayName("toJSONString:String 直通,其余走 provider")
		void toJSONStringPassthrough() {
			for (JsonProvider provider : ALL) {
				JsonProviders.configure(provider.getClass().getName());
				String tag = provider.name();
				assertEquals("{\"a\":1}", JSONTypeUtil.toJSONString("{\"a\":1}"), tag);
				assertNull(JSONTypeUtil.toJSONString(null), tag);
				Map<String, Object> map = new LinkedHashMap<>();
				map.put("a", 1);
				assertEquals("{\"a\":1}", JSONTypeUtil.toJSONString(map), tag);
				Staff staff = new Staff();
				staff.setStaffId("S001");
				assertTrue(JSONTypeUtil.toJSONString(staff).contains("S001"), tag);
			}
		}
	}

	/** JSON 列映射的目标 POJO:三个 provider 都要能绑定它 */
	public static class Staff {
		private String staffId;
		private String staffName;
		private int age;
		private boolean active;

		public Staff() {
		}

		public String getStaffId() {
			return staffId;
		}

		public void setStaffId(String staffId) {
			this.staffId = staffId;
		}

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

		public boolean isActive() {
			return active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

		// Set 去重语义依赖 equals/hashCode
		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Staff)) {
				return false;
			}
			Staff that = (Staff) other;
			return age == that.age && active == that.active && Objects.equals(staffId, that.staffId)
					&& Objects.equals(staffName, that.staffName);
		}

		@Override
		public int hashCode() {
			return Objects.hash(staffId, staffName, age, active);
		}
	}

	/**
	 * 模拟"库在 classpath 上但不可用"的形态:jackson3 的 databind 遇到被仲裁成过旧版本的
	 * jackson-annotations 时,正是这样在第一次解析就抛 NoSuchFieldError
	 */
	public static class BrokenProvider implements JsonProvider {

		@Override
		public String name() {
			return "broken";
		}

		@Override
		public String toJSONString(Object value) {
			throw new NoSuchFieldError("POJO");
		}

		@Override
		public Map<String, Object> parseObjectMap(String json) {
			throw new NoSuchFieldError("POJO");
		}

		@Override
		public Object parseObjectNative(String json) {
			throw new NoSuchFieldError("POJO");
		}

		@Override
		public Object parseArrayNative(String json) {
			throw new NoSuchFieldError("POJO");
		}

		@Override
		public List<Object> parseArray(String json) {
			throw new NoSuchFieldError("POJO");
		}

		@Override
		public <T> List<T> parseArray(String json, Class<T> elementType) {
			throw new NoSuchFieldError("POJO");
		}

		@Override
		public <T> T parseObject(String json, Class<T> type) {
			throw new NoSuchFieldError("POJO");
		}

		@Override
		public <T> T convert(Object node, Class<T> type) {
			throw new NoSuchFieldError("POJO");
		}
	}

	/**
	 * 模拟"实现类的静态初始化就失败"的形态:Jackson3JsonProvider 的静态 MAPPER 字段引用
	 * tools.jackson,在没装 jackson3 的项目里加载该类即抛 NoClassDefFoundError。
	 * probe 必须在 try 内部按类名反射加载,才能把这种失败降级掉
	 */
	public static class ExplodingOnInitProvider implements JsonProvider {

		static {
			// 类初始化即失败,任何实例都创建不出来。
			// 用运行时条件包裹:JLS 要求静态初始化块在语法上能够正常结束
			if (Boolean.parseBoolean("true")) {
				throw new NoClassDefFoundError("tools/jackson/databind/ObjectMapper");
			}
		}

		@Override
		public String name() {
			return "exploding";
		}

		@Override
		public String toJSONString(Object value) {
			return null;
		}

		@Override
		public Map<String, Object> parseObjectMap(String json) {
			return null;
		}

		@Override
		public Object parseObjectNative(String json) {
			return null;
		}

		@Override
		public Object parseArrayNative(String json) {
			return null;
		}

		@Override
		public List<Object> parseArray(String json) {
			return null;
		}

		@Override
		public <T> List<T> parseArray(String json, Class<T> elementType) {
			return null;
		}

		@Override
		public <T> T parseObject(String json, Class<T> type) {
			return null;
		}

		@Override
		public <T> T convert(Object node, Class<T> type) {
			return null;
		}
	}
}
