package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.impl.SecureSqlLoop;

/**
 * @secure-loop的行内参数替换回归:
 * 1) 属性名互为前缀(:p[i].id 与 :p[i].idCard)时不得相互截断;
 * 2) 循环键达到两位数后标记(:sqlToyLoopAsKey_1A)不得截断更长标记(:sqlToyLoopAsKey_10A)
 */
public class SecureSqlLoopTest {

	@BeforeEach
	public void setUp() {
		// 与SqlConfigParseUtils的宏处理一致:计数器需显式初始化(incrementCounterAndGet取前置值)
		SqlToyThreadDataHolder.setCounter(0);
	}

	@AfterEach
	public void tearDown() {
		SqlToyThreadDataHolder.clearCounter();
	}

	/**
	 * 循环元素:属性名id是idCard的前缀,两者在同一sql片段中同时被引用
	 */
	public static class LoopItem {
		private final String id;
		private final String idCard;

		public LoopItem(String id, String idCard) {
			this.id = id;
			this.idCard = idCard;
		}

		public String getId() {
			return id;
		}

		public String getIdCard() {
			return idCard;
		}
	}

	/**
	 * 属性名之间不存在前缀关系(常规形态)
	 */
	public static class PlainItem {
		private final String staffId;
		private final String status;

		public PlainItem(String staffId, String status) {
			this.staffId = staffId;
			this.status = status;
		}

		public String getStaffId() {
			return staffId;
		}

		public String getStatus() {
			return status;
		}
	}

	private static final Pattern REF_PATTERN = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");

	private static List<String> extractRefs(String sql) {
		List<String> refs = new ArrayList<String>();
		Matcher matcher = REF_PATTERN.matcher(sql);
		while (matcher.find()) {
			refs.add(matcher.group(1));
		}
		return refs;
	}

	private static void assertAllRefsBound(String sql, IgnoreKeyCaseMap<String, Object> keyValues) {
		for (String ref : extractRefs(sql)) {
			assertTrue(keyValues.containsKey(ref),
					"sql引用的参数未被绑定:" + ref + ", 已绑定:" + keyValues.keySet() + ", sql=" + sql);
		}
	}

	@Test
	public void dottedPropertyNamesSharingPrefixAreBoundIndependently() {
		List<LoopItem> items = new ArrayList<LoopItem>();
		items.add(new LoopItem("1", "A"));
		items.add(new LoopItem("2", "B"));
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", items);
		String[] params = { "ids", "id=:ids[i].id and idCard=:ids[i].idCard", " or " };
		String sql = new SecureSqlLoop().execute(params, keyValues, null, null, null);
		// 修复前:属性"id"的正则替换把".idCard"截断成"...S1BCard",sql引用到未绑定的参数
		assertAllRefsBound(sql, keyValues);
		List<String> refs = extractRefs(sql);
		assertEquals(4, new LinkedHashSet<String>(refs).size(), "两行×两属性应各自独立绑定,sql=" + sql);
		// 值按文档顺序对应:第1行 id=1,idCard=A;第2行 id=2,idCard=B
		assertEquals("1", keyValues.get(refs.get(0)));
		assertEquals("A", keyValues.get(refs.get(1)));
		assertEquals("2", keyValues.get(refs.get(2)));
		assertEquals("B", keyValues.get(refs.get(3)));
	}

	/**
	 * 常规形态(整参数引用 + 属性名互不为前缀)的值绑定:行顺序与属性顺序都按文档顺序
	 */
	@Test
	public void plainAndDottedRefsBindValuesInOrder() {
		List<PlainItem> items = new ArrayList<PlainItem>();
		items.add(new PlainItem("S1", "启用"));
		items.add(new PlainItem("S2", "停用"));
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", items);
		String[] params = { "ids", "staffId=:ids[i].staffId and status=:ids[i].status", " or " };
		String sql = new SecureSqlLoop().execute(params, keyValues, null, null, null);
		assertAllRefsBound(sql, keyValues);
		List<String> refs = extractRefs(sql);
		assertEquals(4, new LinkedHashSet<String>(refs).size(), "sql=" + sql);
		assertEquals("S1", keyValues.get(refs.get(0)));
		assertEquals("启用", keyValues.get(refs.get(1)));
		assertEquals("S2", keyValues.get(refs.get(2)));
		assertEquals("停用", keyValues.get(refs.get(3)));

		// 整参数引用(无属性)路径
		IgnoreKeyCaseMap<String, Object> plainValues = new IgnoreKeyCaseMap<String, Object>();
		plainValues.put("codes", new String[] { "A", "B" });
		String[] plainParams = { "codes", "code in (:codes[i])", " or " };
		String plainSql = new SecureSqlLoop().execute(plainParams, plainValues, null, null, null);
		assertAllRefsBound(plainSql, plainValues);
		List<String> plainRefs = extractRefs(plainSql);
		assertEquals(2, new LinkedHashSet<String>(plainRefs).size(), "sql=" + plainSql);
		assertEquals("A", plainValues.get(plainRefs.get(0)));
		assertEquals("B", plainValues.get(plainRefs.get(1)));
	}

	/**
	 * 同一循环变量同时出现裸引用与属性引用:两种形态都要替换,不得残留内部标记
	 */
	@Test
	public void mixedBareAndDottedRefsAreBothReplaced() {
		List<LoopItem> items = new ArrayList<LoopItem>();
		items.add(new LoopItem("1", "A"));
		items.add(new LoopItem("2", "B"));
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", items);
		String[] params = { "ids", "(code=:ids[i] and idCard=:ids[i].idCard)", " or " };
		String sql = new SecureSqlLoop().execute(params, keyValues, null, null, null);
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "不应残留内部标记,sql=" + sql);
		assertAllRefsBound(sql, keyValues);
		// 裸形态绑定元素本身(参数化,不会像拼接模式那样退化成对象toString),属性形态绑定属性值
		List<String> refs = extractRefs(sql);
		assertEquals(4, new LinkedHashSet<String>(refs).size(), "sql=" + sql);
		assertEquals(items.get(0), keyValues.get(refs.get(0)));
		assertEquals("A", keyValues.get(refs.get(1)));
		assertEquals(items.get(1), keyValues.get(refs.get(2)));
		assertEquals("B", keyValues.get(refs.get(3)));
	}

	@Test
	public void moreThanTenLoopKeysDoNotCollideOnMarkerPrefix() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		StringBuilder content = new StringBuilder();
		List<String> loopParams = new ArrayList<String>();
		for (int k = 0; k < 11; k++) {
			String name = "p" + k;
			keyValues.put(name, new String[] { name + "-value" });
			loopParams.add(name);
			if (k > 0) {
				content.append(" and ");
			}
			content.append("c").append(k).append("=:").append(name).append("[i]");
		}
		String[] params = { loopParams.get(0), content.toString(), " and " };
		String sql = new SecureSqlLoop().execute(params, keyValues, null, null, null);
		// 标记_1A是_10A的前缀:替换必须带后边界判断,否则截断出未绑定参数
		assertAllRefsBound(sql, keyValues);
		assertEquals(11, new LinkedHashSet<String>(extractRefs(sql)).size(), "11个循环键应各自独立绑定,sql=" + sql);
	}

	/**
	 * 同一sql内多次@secure-loop:依赖线程计数器递增保证参数名前缀不同,两次调用的参数名不得相同
	 */
	@Test
	public void repeatedCallsInSameSqlGetDistinctParamNames() {
		IgnoreKeyCaseMap<String, Object> first = new IgnoreKeyCaseMap<String, Object>();
		first.put("ids", new String[] { "A" });
		IgnoreKeyCaseMap<String, Object> second = new IgnoreKeyCaseMap<String, Object>();
		second.put("ids", new String[] { "B" });
		SecureSqlLoop loop = new SecureSqlLoop();
		String sql1 = loop.execute(new String[] { "ids", "x=:ids[i]" }, first, null, null, null);
		String sql2 = loop.execute(new String[] { "ids", "x=:ids[i]" }, second, null, null, null);
		String ref1 = extractRefs(sql1).get(0);
		String ref2 = extractRefs(sql2).get(0);
		assertFalse(ref1.equalsIgnoreCase(ref2), "两次调用的参数名不得相同:" + sql1 + " / " + sql2);
		assertEquals("A", first.get(ref1));
		assertEquals("B", second.get(ref2));
	}

	/**
	 * 同一属性被引用多次:替换后都指向同一个参数(值一致),不产生未绑定引用
	 */
	@Test
	public void duplicatePropertyRefsShareSingleParam() {
		List<PlainItem> items = new ArrayList<PlainItem>();
		items.add(new PlainItem("S1", "启用"));
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", items);
		String sql = new SecureSqlLoop().execute(new String[] { "ids", "x=:ids[i].staffId and y=:ids[i].staffId" },
				keyValues, null, null, null);
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "sql=" + sql);
		List<String> refs = extractRefs(sql);
		assertEquals(2, refs.size(), "sql=" + sql);
		assertEquals(refs.get(0), refs.get(1), "同一属性应指向同一参数,sql=" + sql);
		assertEquals("S1", keyValues.get(refs.get(0)));
	}

	/**
	 * 引用后紧跟名字字符(如 like ':ids[i]_%'):无法与参数名区分,给出明确报错而不是残留内部标记
	 */
	@Test
	public void referenceFollowedByNameCharFailsWithClearError() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", new String[] { "S1" });
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new SecureSqlLoop()
				.execute(new String[] { "ids", "code like ':ids[i]_%'" }, keyValues, null, null, null));
		assertTrue(ex.getMessage().contains("non-name character"), "实际:" + ex.getMessage());
	}

	/**
	 * 起始下标为负:按0处理,不抛数组越界
	 */
	@Test
	public void negativeStartTreatedAsZero() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", new String[] { "A", "B" });
		String sql = new SecureSqlLoop().execute(new String[] { "ids", "x=:ids[i]", " or ", "-1" }, keyValues, null, null,
				null);
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "sql=" + sql);
		assertAllRefsBound(sql, keyValues);
		assertEquals(2, new LinkedHashSet<String>(extractRefs(sql)).size(), "两行都应产出,sql=" + sql);
	}
}