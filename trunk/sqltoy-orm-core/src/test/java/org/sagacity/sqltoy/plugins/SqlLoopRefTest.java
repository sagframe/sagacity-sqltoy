package org.sagacity.sqltoy.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.impl.SqlLoop;

/**
 * @loop(拼接模式)的引用解析与字面量回归:
 * 1) 属性名互为前缀时各自独立取值;
 * 2) 同一循环变量的裸引用(:x[i])与属性引用(:x[i].prop)同时出现时两者都要被解析,不能把内部标记漏进sql;
 * 3) 框架补引号拼接的字符串值需转义,值内单引号/末尾反斜杠不得破坏字面量边界
 */
public class SqlLoopRefTest {

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

		@Override
		public String toString() {
			return "ITEM(" + id + "," + idCard + ")";
		}
	}

	private static IgnoreKeyCaseMap<String, Object> loopItems() {
		List<LoopItem> items = new ArrayList<LoopItem>();
		items.add(new LoopItem("1", "A"));
		items.add(new LoopItem("2", "B"));
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", items);
		return keyValues;
	}

	private static String execute(String[] params, IgnoreKeyCaseMap<String, Object> keyValues) {
		return new SqlLoop().execute(params, keyValues, null, null, null);
	}

	@Test
	public void propertyNamesSharingPrefixAreResolvedIndependently() {
		String sql = execute(new String[] { "ids", "id=:ids[i].id and idCard=:ids[i].idCard", " or " }, loopItems());
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "不应残留内部标记:" + sql);
		assertTrue(sql.contains("id='1' and idCard='A'"), "实际:" + sql);
		assertTrue(sql.contains("id='2' and idCard='B'"), "实际:" + sql);
	}

	@Test
	public void moreThanTenLoopKeysResolveIndependently() {
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
		String sql = execute(new String[] { loopParams.get(0), content.toString(), " and " }, keyValues);
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "不应残留内部标记:" + sql);
		for (int k = 0; k < 11; k++) {
			assertTrue(sql.contains("c" + k + "='p" + k + "-value'"), "第" + k + "个循环键未解析,sql=" + sql);
		}
	}

	/**
	 * 裸引用在前:修复前parseParams的裸形态记录被属性形态覆盖,裸引用以:sqlToyLoopAsKey_0A原样漏进sql
	 */
	@Test
	public void mixedBareAndDottedRefsBothResolve() {
		String sql = execute(new String[] { "ids", "(code=:ids[i] and idCard=:ids[i].idCard)", " or " }, loopItems());
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "不应残留内部标记:" + sql);
		// 裸形态对集合元素整体取值(toSqlString对未知对象类型按toString内联,故裸引用只适合标量数组),
		// 属性形态取属性值
		assertTrue(sql.contains("code=ITEM(1,A)"), "实际:" + sql);
		assertTrue(sql.contains("idCard='A'"), "实际:" + sql);
		assertTrue(sql.contains("code=ITEM(2,B)"), "实际:" + sql);
		assertTrue(sql.contains("idCard='B'"), "实际:" + sql);
	}

	/**
	 * 属性引用在前、裸引用在后:修复前两者都会漏(属性列表被裸形态覆盖为空数组)
	 */
	@Test
	public void mixedRefsInReverseOrderBothResolve() {
		String sql = execute(new String[] { "ids", "(idCard=:ids[i].idCard and code=:ids[i])", " or " }, loopItems());
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "不应残留内部标记:" + sql);
		assertTrue(sql.contains("idCard='A'") && sql.contains("code=ITEM(1,A)"), "实际:" + sql);
		assertTrue(sql.contains("idCard='B'") && sql.contains("code=ITEM(2,B)"), "实际:" + sql);
	}

	/**
	 * @loop为拼接模式,框架对值**不做转义**(调用方可能已按目标方自行转义,框架再转一次会造成
	 * 二次转义改变值语义),值按原样内联:含单引号/反斜杠的值请走参数化(@secure-loop)或自行保证安全
	 */
	@Test
	public void inlinedStringValuesAreNotEscaped() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("names", new String[] { "it's", "C:\\tmp\\" });
		String sql = execute(new String[] { "names", "name=:names[i]", " or " }, keyValues);
		// 值按原样内联:不做单引号双写、不做反斜杠加倍
		assertTrue(sql.contains("name='it's'"), "值应原样内联,实际:" + sql);
		assertTrue(sql.contains("name='C:\\tmp\\'"), "值应原样内联,实际:" + sql);
		assertFalse(sql.contains("it''s"), "不应做单引号双写,实际:" + sql);
		assertFalse(sql.contains("C:\\\\tmp"), "不应做反斜杠加倍,实际:" + sql);
	}

	/**
	 * 引用后紧跟名字字符(如 like ':ids[i]_%'):该形态无法与参数名区分(参数名可含下划线),
	 * 原来会NPE或把内部标记漏进sql,现统一给出明确报错并保持sql不变
	 */
	@Test
	public void referenceFollowedByNameCharFailsWithClearError() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", new String[] { "S1" });
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> execute(new String[] { "ids", "code like ':ids[i]_%'" }, keyValues));
		assertTrue(ex.getMessage().contains("non-name character"), "报错应说明需用分隔字符,实际:" + ex.getMessage());
		assertTrue(ex.getMessage().contains(":ids[i]"), "报错应指出用户书写的引用,实际:" + ex.getMessage());
		// 引用后带分隔字符(引号/空格/括号)均正常
		assertTrue(execute(new String[] { "ids", "code like ':ids[i]%'" }, keyValues).contains("like 'S1%'"));
		assertTrue(execute(new String[] { "ids", "code=:ids[i] " }, keyValues).contains("code='S1'"));
	}

	/**
	 * 起始下标为负:按0处理(原来直接用作下标会取loopValues[-1]抛数组越界)
	 */
	@Test
	public void negativeStartTreatedAsZero() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("ids", new String[] { "A", "B" });
		String sql = execute(new String[] { "ids", "x=:ids[i]", " or ", "-1" }, keyValues);
		assertTrue(sql.contains("x='A'") && sql.contains("x='B'"), "负start应按0处理,实际:" + sql);
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "实际:" + sql);
	}

	@Test
	public void plainScalarLoopUnchanged() {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("codes", new String[] { "A", "B" });
		String sql = execute(new String[] { "codes", "code=:codes[i]", " or " }, keyValues);
		assertTrue(sql.contains("code='A'") && sql.contains("code='B'"), "实际:" + sql);
		assertFalse(sql.contains("sqlToyLoopAsKey_"), "实际:" + sql);
	}

	/**
	 * 调用方自行转义过的值(如'it\'s'、已经双写的''等)不会被框架二次加工,原样内联
	 */
	@Test
	public void callerEscapedValuesAreInlinedVerbatim() {
		assertInlinedVerbatim("it\\'s");
		assertInlinedVerbatim("it''s");
		assertInlinedVerbatim("C:\\tmp\\");
	}

	private static void assertInlinedVerbatim(String value) {
		IgnoreKeyCaseMap<String, Object> keyValues = new IgnoreKeyCaseMap<String, Object>();
		keyValues.put("names", new String[] { value });
		String sql = execute(new String[] { "names", "name=:names[i]" }, keyValues);
		assertTrue(sql.contains("name='" + value + "'"), "值应原样内联,sql=" + sql);
	}
}
