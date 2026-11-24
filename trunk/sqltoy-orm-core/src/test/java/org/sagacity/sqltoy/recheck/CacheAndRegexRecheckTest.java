package org.sagacity.sqltoy.recheck;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.KeyAndIndex;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.MapperUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 2026-11-24 缓存类与正则类改动的独立复核：@fast 标记正则预编译、splitRegex 复用 Pattern 缓存、
 *              枚举转换记忆化、属性取值缓存键、setter 元数据缓存。参考逻辑按 git 历史中改动之前的源码重新转写，
 *              不依赖被测的缓存结构本身。
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked", "deprecation" })
public class CacheAndRegexRecheckTest {

	// ======================= @fast 标记 =======================

	private static final String[] FAST_START_REGEXS = { "(?i)\\-{2}\\s+\\@fast\\_start",
			"(?i)\\/\\*\\s*\\@fast\\_start\\s*\\*\\/" };
	private static final String[] FAST_END_REGEXS = { "(?i)\\-{2}\\s+\\@fast\\_end",
			"(?i)\\/\\*\\s*\\@fast\\_end\\s*\\*\\/" };

	/** 改动之前的 uniformFastMarks：正则以字符串形式给出 */
	private static String refUniformFastMarks(String sql) {
		int startRegexIndex = 0;
		int endRegexIndex = 0;
		int fastStart = -1;
		int fastEnd = -1;
		while (startRegexIndex < FAST_START_REGEXS.length) {
			fastStart = StringUtil.matchIndex(sql, FAST_START_REGEXS[startRegexIndex]);
			if (fastStart >= 0) {
				break;
			}
			startRegexIndex++;
		}
		if (fastStart == -1) {
			return sql;
		}
		while (endRegexIndex < FAST_END_REGEXS.length) {
			fastEnd = StringUtil.matchIndex(sql, FAST_END_REGEXS[endRegexIndex]);
			if (fastEnd > 0) {
				break;
			}
			endRegexIndex++;
		}
		if (fastEnd == -1) {
			return sql.replaceFirst(FAST_START_REGEXS[startRegexIndex], "@fast");
		}
		if (fastEnd > fastStart) {
			return sql.replaceFirst(FAST_START_REGEXS[startRegexIndex], "@fast")
					.replaceFirst(FAST_END_REGEXS[endRegexIndex], " ");
		}
		return sql;
	}

	@Test
	public void uniformFastMarksMatchesPreChangeLogic() {
		String[] pieces = { "select a", "-- @fast_start", "/* @fast_start */", "/*@FAST_START*/", "-- @fast_end",
				"/* @fast_end*/", "--@fast_start", "--  @fast_end", "from t", "@fast", "x -- @fast_start y",
				"-- @fast_endx", "\n-- @Fast_End ", "", "where b", "-- @fast_start", "-- @fast_end" };
		String[] joins = { "", " ", "\n", "\t" };
		int replaced = 0;
		int startOnly = 0;
		for (int seed = 0; seed < 20000; seed++) {
			Random rnd = new Random(seed);
			StringBuilder sql = new StringBuilder();
			int count = rnd.nextInt(7);
			for (int i = 0; i < count; i++) {
				sql.append(pieces[rnd.nextInt(pieces.length)]).append(joins[rnd.nextInt(joins.length)]);
			}
			String source = sql.toString();
			String expected = refUniformFastMarks(source);
			String actual = SqlUtil.uniformFastMarks(source);
			assertEquals(expected, actual, "seed=" + seed + ", sql=" + source);
			if (!expected.equals(source)) {
				replaced++;
				if (expected.contains("@fast_end") || expected.contains("@Fast_End")) {
					startOnly++;
				}
			}
		}
		assertTrue(replaced > 3000, "发生替换的用例数=" + replaced);
		assertTrue(startOnly > 100, "只有起始标记的用例数=" + startOnly);
	}

	// ======================= splitRegex =======================

	/** 改动之前的 splitRegex：全部走 String.split */
	private static String[] refSplitRegex(String source, String regex, boolean doTrim) {
		if (source == null) {
			return null;
		}
		String[] result;
		if ("?".equals(regex)) {
			result = source.split("\\?");
		} else if (",".equals(regex)) {
			result = source.split("\\,");
		} else if (";".equals(regex)) {
			result = source.split("\\;");
		} else if (":".equals(regex)) {
			result = source.split("\\:");
		} else if (".".equals(regex)) {
			result = source.split("\\.");
		} else if ("|".equals(regex)) {
			result = source.split("\\|");
		} else if (regex.length() > 0 && "".equals(regex.trim())) {
			result = source.split("\\s+");
		} else if ("||".equals(regex)) {
			result = source.split("\\|{2}");
		} else if ("&&".equals(regex)) {
			result = source.split("\\&{2}");
		} else {
			result = source.split(regex);
		}
		if (doTrim) {
			for (int i = 0; i < result.length; i++) {
				result[i] = result[i].trim();
			}
		}
		return result;
	}

	@Test
	public void splitRegexMatchesStringSplit() {
		String[] regexes = { ",", ";", ":", "?", ".", "|", " ", "  ", "\t", " \t ", "||", "&&", "[;,]", "\\,{2,}", ",,",
				"a", "1", "\\s*,\\s*" };
		char[] alphabet = { 'a', 'b', ',', ';', '|', '&', '.', ' ', '\t', '\n', '1' };
		int viaCache = 0;
		for (int seed = 0; seed < 30000; seed++) {
			Random rnd = new Random(seed);
			int length = rnd.nextInt(13);
			StringBuilder source = new StringBuilder();
			for (int i = 0; i < length; i++) {
				source.append(alphabet[rnd.nextInt(alphabet.length)]);
			}
			String regex = regexes[rnd.nextInt(regexes.length)];
			boolean doTrim = rnd.nextBoolean();
			String text = source.toString();
			assertArrayEquals(refSplitRegex(text, regex, doTrim), StringUtil.splitRegex(text, regex, doTrim),
					"seed=" + seed + ", regex=" + regex + ", source=" + text);
			// 同一个正则反复出现(命中 Pattern 缓存)时结果不变
			assertArrayEquals(refSplitRegex(text, regex, doTrim), StringUtil.splitRegex(text, regex, doTrim),
					"seed=" + seed + " 重复调用");
			if (regex.length() > 1) {
				viaCache++;
			}
		}
		assertTrue(viaCache > 5000, "走正则分支的用例数=" + viaCache);
		assertNull(StringUtil.splitRegex(null, ",", true));
	}

	// ======================= 枚举转换 =======================

	public enum Plain {
		Red, Green, Blue
	}

	/** D 与 A 的 key 相同：按声明顺序首个命中 */
	public enum WithKey {
		A(101), B(102), C(103), D(101);
		private final int value;

		WithKey(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	public enum ByCode {
		X("x1"), Y("x2"), Z("x1");
		private final String code;

		ByCode(String code) {
			this.code = code;
		}

		public String getCode() {
			return code;
		}
	}

	public enum Typed {
		LOW(1), HIGH(2);
		private final int type;

		Typed(int type) {
			this.type = type;
		}

		public int getType() {
			return type;
		}
	}

	/** 改动之前的匹配逻辑：按声明顺序首个命中，getKey 值或 name(忽略大小写)均可，getKey 抛异常时整轮中断返回 null */
	private static Object refEnumInstance(Object key, Class enumClass, Method keyMethod) {
		if (key == null) {
			return null;
		}
		String keyStr = key.toString();
		for (Object constant : enumClass.getEnumConstants()) {
			if (keyMethod == null) {
				if (keyStr.equalsIgnoreCase(((Enum) constant).name())) {
					return constant;
				}
			} else {
				try {
					Object keyVal = keyMethod.invoke(constant);
					if (keyStr.equalsIgnoreCase(((Enum) constant).name())
							|| (keyVal != null && keyStr.equalsIgnoreCase(keyVal.toString()))) {
						return constant;
					}
				} catch (Exception e) {
					return null;
				}
			}
		}
		return null;
	}

	@Test
	public void newEnumInstanceMatchesPreChangeLogic() throws Exception {
		Class[] enumClasses = { Plain.class, WithKey.class, ByCode.class, Typed.class };
		Method[] keyMethods = { null, WithKey.class.getMethod("getValue"), ByCode.class.getMethod("getCode"),
				Typed.class.getMethod("getType") };
		String[][] spellings = { { "red", "green", "blue" }, { "a", "b", "c", "d", "101", "102", "103" },
				{ "x", "y", "z", "x1", "x2" }, { "low", "high", "1", "2" } };
		int hits = 0;
		int misses = 0;
		for (int seed = 0; seed < 40000; seed++) {
			Random rnd = new Random(seed);
			int index = rnd.nextInt(enumClasses.length);
			Class enumClass = enumClasses[index];
			Object key;
			int pick = rnd.nextInt(10);
			if (pick == 0) {
				key = rnd.nextBoolean() ? null : "";
			} else if (pick == 1) {
				key = "no-such-" + rnd.nextInt(50);
			} else {
				String spelling = spellings[index][rnd.nextInt(spellings[index].length)];
				StringBuilder text = new StringBuilder();
				for (int i = 0; i < spelling.length(); i++) {
					char ch = spelling.charAt(i);
					text.append(rnd.nextBoolean() ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
				}
				// 同一个值用 String、Integer、Long 等不同类型给出(只按文本比较)
				if (spelling.matches("\\d+") && rnd.nextInt(3) == 0) {
					key = rnd.nextBoolean() ? (Object) Integer.valueOf(spelling) : Long.valueOf(spelling);
				} else {
					key = text.toString();
				}
			}
			Object expected = refEnumInstance(key, enumClass, keyMethods[index]);
			Object actual = BeanUtil.newEnumInstance(key, enumClass);
			// 枚举常量是单例，命中时必须是同一个对象
			assertSame(expected, actual, "seed=" + seed + ", enum=" + enumClass.getSimpleName() + ", key=" + key);
			// 紧接着再取一次(命中记忆结果)：结果一致
			assertSame(expected, BeanUtil.newEnumInstance(key, enumClass), "seed=" + seed + " 重复调用");
			if (expected != null) {
				hits++;
			} else {
				misses++;
			}
		}
		assertTrue(hits > 20000, "命中的用例数=" + hits);
		assertTrue(misses > 3000, "未命中的用例数=" + misses);
	}

	// ======================= 属性取值 =======================

	public static class Holder implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name = "n";
		private List<String> tags = new ArrayList<String>(Arrays.asList("a", "b", "c"));

		public String getName() {
			return name;
		}

		public List<String> getTags() {
			return tags;
		}
	}

	@Test
	public void getPropertyAndComplexPropertyShareOneEntryPerProperty() {
		Holder holder = new Holder();
		for (int round = 0; round < 6; round++) {
			// 第 1 轮填充缓存，之后各轮命中缓存；两种取值方式交替进行
			assertEquals("n", BeanUtil.getProperty(holder, "name"), "round=" + round);
			assertEquals("n", BeanUtil.getComplexProperty(holder, "name"), "round=" + round);
			assertEquals("b", BeanUtil.getComplexProperty(holder, "tags[1]"), "round=" + round);
			assertEquals(Arrays.asList("a", "b", "c"), BeanUtil.getProperty(holder, "tags"), "round=" + round);
			assertEquals("a", BeanUtil.getComplexProperty(holder, "tags[0]"), "round=" + round);
			assertEquals("c", BeanUtil.getComplexProperty(holder, "tags[2]"), "round=" + round);
			// 不存在的属性、越界的下标
			assertNull(BeanUtil.getProperty(holder, "missing"));
			assertNull(BeanUtil.getComplexProperty(holder, "missing[0]"));
			assertNull(BeanUtil.getComplexProperty(holder, "tags[9]"));
		}
	}

	@Test
	public void getKeyAndIndexMatchesRegexDetection() {
		// 改动之前：统一用 ARRAY_PATTERN(\[\d+\]$) 判断，不含 '[' 时也要创建一次 Matcher
		Pattern arrayPattern = Pattern.compile("\\[\\d+\\]$");
		String[] pieces = { "name", "tags", "[", "]", "0", "12", "x", "[1]", "[]", "[a]", " ", "", "[3", "1]" };
		int indexed = 0;
		for (int seed = 0; seed < 30000; seed++) {
			Random rnd = new Random(seed);
			int count = rnd.nextInt(4);
			StringBuilder property = new StringBuilder();
			for (int i = 0; i < count; i++) {
				property.append(pieces[rnd.nextInt(pieces.length)]);
			}
			String text = property.toString();
			KeyAndIndex expected = null;
			// jre8同步适配:String.isBlank()(JDK11)→trim().isEmpty()
			if (!text.trim().isEmpty() && arrayPattern.matcher(text).find()) {
				expected = new KeyAndIndex();
				int lastIndex = text.lastIndexOf("[");
				expected.setKey(text.substring(0, lastIndex));
				expected.setIndex(Integer.parseInt(text.substring(lastIndex + 1, text.length() - 1)));
			}
			KeyAndIndex actual = BeanUtil.getKeyAndIndex(text);
			if (expected == null) {
				assertNull(actual, "property=" + text);
			} else {
				assertEquals(expected.getKey(), actual.getKey(), "property=" + text);
				assertEquals(expected.getIndex(), actual.getIndex(), "property=" + text);
				indexed++;
			}
		}
		assertNull(BeanUtil.getKeyAndIndex(null));
		assertTrue(indexed > 1000, "识别出数组下标的用例数=" + indexed);
	}

	// ======================= DTO 映射的 setter 元数据缓存 =======================

	public static class ItemSource implements Serializable {
		private static final long serialVersionUID = 1L;
		private String title;
		private Integer qty;

		public ItemSource() {
		}

		public ItemSource(String title, Integer qty) {
			this.title = title;
			this.qty = qty;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public Integer getQty() {
			return qty;
		}

		public void setQty(Integer qty) {
			this.qty = qty;
		}
	}

	public static class ItemTarget implements Serializable {
		private static final long serialVersionUID = 1L;
		private String title;
		private Integer qty;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public Integer getQty() {
			return qty;
		}

		public void setQty(Integer qty) {
			this.qty = qty;
		}
	}

	public static class ParentSourceA implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name;
		private List<ItemSource> items;
		private Integer count;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<ItemSource> getItems() {
			return items;
		}

		public void setItems(List<ItemSource> items) {
			this.items = items;
		}

		public Integer getCount() {
			return count;
		}

		public void setCount(Integer count) {
			this.count = count;
		}
	}

	/** 与 ParentSourceA 映射到同一个目标类，但属性集合不同(没有 count、多出 memo) */
	public static class ParentSourceB implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name;
		private List<ItemSource> items;
		private String memo;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<ItemSource> getItems() {
			return items;
		}

		public void setItems(List<ItemSource> items) {
			this.items = items;
		}

		public String getMemo() {
			return memo;
		}

		public void setMemo(String memo) {
			this.memo = memo;
		}
	}

	public static class ParentTarget implements Serializable {
		private static final long serialVersionUID = 1L;
		private String name;
		private List<ItemTarget> items;
		private Integer count;
		private String memo;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<ItemTarget> getItems() {
			return items;
		}

		public void setItems(List<ItemTarget> items) {
			this.items = items;
		}

		public Integer getCount() {
			return count;
		}

		public void setCount(Integer count) {
			this.count = count;
		}

		public String getMemo() {
			return memo;
		}

		public void setMemo(String memo) {
			this.memo = memo;
		}
	}

	private static void assertMappedA(ParentTarget target) {
		assertEquals("a", target.getName());
		assertEquals(Integer.valueOf(3), target.getCount());
		assertNull(target.getMemo(), "memo");
		assertEquals(2, target.getItems().size());
		assertEquals("t1", target.getItems().get(0).getTitle());
		assertEquals(Integer.valueOf(1), target.getItems().get(0).getQty());
		assertEquals("t2", target.getItems().get(1).getTitle());
		assertNull(target.getItems().get(1).getQty());
	}

	private static void assertMappedB(ParentTarget target) {
		assertEquals("b", target.getName());
		assertNull(target.getCount(), "count");
		assertEquals("m", target.getMemo());
		assertEquals(1, target.getItems().size());
		assertEquals("t3", target.getItems().get(0).getTitle());
		assertEquals(Integer.valueOf(3), target.getItems().get(0).getQty());
	}

	private static Map<?, ?> setterMetaCacheOf(Class<?> targetClass) throws Exception {
		Field field = MapperUtils.class.getDeclaredField("SETTER_META");
		field.setAccessible(true);
		Object holder = field.get(null);
		return (Map<?, ?>) ClassValue.class.getMethod("get", Class.class).invoke(holder, targetClass);
	}

	private static List<String> snapshot(Map<?, ?> cache) {
		List<String> snapshot = new ArrayList<String>();
		for (Map.Entry<?, ?> entry : cache.entrySet()) {
			snapshot.add(entry.getKey() + " => " + Arrays.deepToString((Object[]) entry.getValue()));
		}
		return snapshot;
	}

	@Test
	public void setterMetadataIsCachedPerSetterListAndNeverModified() throws Exception {
		ParentSourceA sourceA = new ParentSourceA();
		sourceA.setName("a");
		sourceA.setItems(new ArrayList<ItemSource>(
				Arrays.asList(new ItemSource("t1", 1), new ItemSource("t2", null))));
		sourceA.setCount(3);
		ParentSourceB sourceB = new ParentSourceB();
		sourceB.setName("b");
		sourceB.setItems(new ArrayList<ItemSource>(Arrays.asList(new ItemSource("t3", 3))));
		sourceB.setMemo("m");

		// 第 1 轮计算元数据并填入缓存，之后各轮命中缓存；两种来源类交替映射到同一个目标类
		for (int round = 0; round < 8; round++) {
			assertMappedA(MapperUtils.map(sourceA, ParentTarget.class));
			assertMappedB(MapperUtils.map(sourceB, ParentTarget.class));
		}
		// 忽略 count 属性：又是一组不同的 setter 方法
		ParentTarget ignored = MapperUtils.map(sourceA, ParentTarget.class, "count");
		assertEquals("a", ignored.getName());
		assertNull(ignored.getCount());
		assertEquals(2, ignored.getItems().size());
		// 集合映射、含 null 行
		List<ParentSourceA> rows = new ArrayList<ParentSourceA>();
		rows.add(null);
		rows.add(sourceA);
		List<ParentTarget> mapped = MapperUtils.mapList(rows, ParentTarget.class);
		assertEquals(2, mapped.size());
		assertNull(mapped.get(0));
		assertMappedA(mapped.get(1));

		Map<?, ?> cache = setterMetaCacheOf(ParentTarget.class);
		// 三组不同的 setter 方法列表各占一个缓存条目
		assertEquals(3, cache.size(), "缓存条目数");
		List<?> keys = new ArrayList<Object>(cache.keySet());
		for (int i = 0; i < keys.size(); i++) {
			for (int j = i + 1; j < keys.size(); j++) {
				assertNotEquals(keys.get(i), keys.get(j), "缓存键重复");
			}
		}
		// 元数据是只读的：继续映射不会改动已缓存的数组
		List<String> before = snapshot(cache);
		for (int round = 0; round < 5; round++) {
			assertMappedA(MapperUtils.map(sourceA, ParentTarget.class));
			assertMappedB(MapperUtils.map(sourceB, ParentTarget.class));
			MapperUtils.map(sourceA, ParentTarget.class, "count");
		}
		assertEquals(before, snapshot(cache), "缓存中的元数据被修改");
		assertEquals(3, cache.size(), "缓存条目数发生变化");
	}
}
