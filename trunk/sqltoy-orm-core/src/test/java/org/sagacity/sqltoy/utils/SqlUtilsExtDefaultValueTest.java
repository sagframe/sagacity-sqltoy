package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.DBProfile;

/**
 * SqlUtilsExt 缺陷修复回归测试(2026-9-14):
 * 1.默认值为小数形态时INTEGER/BIGINT/BIT分支经BigDecimal截断,不再抛NumberFormatException
 * 2.带时区类型解析失败容错返回null,不再抛NPE
 * 3.signSql对null dbType安全
 * 4.resultUpdate空集合按null值语义处理(强制更新置null,否则跳过);Boolean/NVARCHAR列正确走updateString
 */
public class SqlUtilsExtDefaultValueTest {

	// update 2026-9-15 适配resultUpdate签名重构(dbType前插DBProfile参数):本类聚焦值语义分支,
	// 按UNDEFINE构造最小档案(族判定全false,与旧dbType=0入参行为等价)
	private static DBProfile undefProfile() {
		return new DBProfile(null, null, DataSourceUtils.DBType.UNDEFINE, null, 0, null, null, false);
	}

	@Test
	public void decimalDefaultForIntegerFamily() {
		// 此前Integer.valueOf("12.5")抛NumberFormatException,现截断小数位(与convertType拆分小数点语义一致)
		assertEquals(12, SqlUtilsExt.getDefaultValue(null, "12.5", Types.INTEGER, false));
		assertEquals(-12, SqlUtilsExt.getDefaultValue(null, "-12.5", Types.INTEGER, false));
		assertEquals(java.math.BigInteger.valueOf(12), SqlUtilsExt.getDefaultValue(null, "12.5", Types.BIGINT, false));
		assertEquals(12, SqlUtilsExt.getDefaultValue(null, "12.5", Types.BIT, false));
		// DECIMAL分支原有行为保持
		assertEquals(new java.math.BigDecimal("12.5"), SqlUtilsExt.getDefaultValue(null, "12.5", Types.DECIMAL, false));
		// 整数、负数原有行为保持
		assertEquals(Integer.valueOf(-3), SqlUtilsExt.getDefaultValue(null, "-3", Types.INTEGER, false));
		// 非数字容错返回null保持
		assertNull(SqlUtilsExt.getDefaultValue(null, "abc", Types.INTEGER, false));
		assertNull(SqlUtilsExt.getDefaultValue(null, "abc", Types.BIGINT, false));
	}

	@Test
	public void garbageDefaultForTimezoneTypes() {
		// 此前TIMESTAMP_WITH_TIMEZONE解析为null后atZone抛NPE,现容错返回null
		assertNull(SqlUtilsExt.getDefaultValue(null, "garbage", Types.TIMESTAMP_WITH_TIMEZONE, false));
		// 此前TIME_WITH_TIMEZONE解析为null后LocalDateTime.of抛NPE,现容错返回null
		assertNull(SqlUtilsExt.getDefaultValue(null, "garbage", Types.TIME_WITH_TIMEZONE, false));
		// 合法值仍正常解析
		Object offsetDateTime = SqlUtilsExt.getDefaultValue(null, "2024-05-01 10:30:00",
				Types.TIMESTAMP_WITH_TIMEZONE, false);
		assertNotNull(offsetDateTime);
		assertTrue(offsetDateTime instanceof java.time.OffsetDateTime);
	}

	@Test
	public void clockKeywordAndBasicDefaults() {
		// 系统时间关键字语义保持
		assertNotNull(SqlUtilsExt.getDefaultValue(null, "now()", Types.TIMESTAMP, false));
		assertNotNull(SqlUtilsExt.getDefaultValue(null, "sysdate", Types.DATE, false));
		assertNotNull(SqlUtilsExt.getDefaultValue(null, "", Types.TIMESTAMP, false));
		// 字符类型原样返回
		assertEquals("abc", SqlUtilsExt.getDefaultValue(null, "abc", Types.VARCHAR, false));
		// 非字符类型空白:不允许null给默认值,允许null返回null
		assertEquals(Integer.valueOf(0), SqlUtilsExt.getDefaultValue(null, "", Types.INTEGER, false));
		assertNull(SqlUtilsExt.getDefaultValue(null, "  ", Types.INTEGER, true));
		// 当前值非null时直通,不使用默认值
		assertEquals("有值", SqlUtilsExt.getDefaultValue("有值", "默认值", Types.VARCHAR, false));
		assertNull(SqlUtilsExt.getDefaultValue(null, null, Types.INTEGER, false));
	}

	@Test
	public void isCurrentTimeKeywords() {
		assertTrue(SqlUtilsExt.isCurrentTime("sysdate"));
		assertTrue(SqlUtilsExt.isCurrentTime("now()"));
		assertTrue(SqlUtilsExt.isCurrentTime("CURRENT_TIMESTAMP"));
		assertTrue(SqlUtilsExt.isCurrentTime("getdate()"));
		assertTrue(SqlUtilsExt.isCurrentTime("systimestamp"));
		assertTrue(SqlUtilsExt.isCurrentTime("CURRENT DATE"));
		assertTrue(SqlUtilsExt.isCurrentTime("curdate"));
	}

	@Test
	public void signSqlNullDbTypeSafe() {
		// 此前dbType为null时equals抛NPE,现直接返回原sql
		assertEquals("select 1", SqlUtilsExt.signSql("select 1", null, null));
	}

	// ==================== resultUpdate 行为 ====================

	/** 记录ResultSet上被调用的方法名 */
	private ResultSet mockResultSet(List<String> invoked) {
		return (ResultSet) Proxy.newProxyInstance(SqlUtilsExtDefaultValueTest.class.getClassLoader(),
				new Class[] { ResultSet.class }, (proxy, method, args) -> {
					invoked.add(method.getName());
					if (method.getReturnType() == boolean.class) {
						return false;
					}
					if (method.getReturnType() == int.class) {
						return 0;
					}
					return null;
				});
	}

	private FieldMeta fieldMeta(String columnName, int jdbcType) {
		FieldMeta fieldMeta = new FieldMeta();
		fieldMeta.setFieldName(columnName);
		fieldMeta.setColumnName(columnName);
		fieldMeta.setType(jdbcType);
		return fieldMeta;
	}

	@Test
	public void resultUpdateEmptyCollectionSkipsWhenNotForced() throws Exception {
		List<String> invoked = new ArrayList<>();
		ResultSet rs = mockResultSet(invoked);
		Connection conn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> null);
		// 空集合:非强制更新跳过,不产生任何update调用
		SqlUtilsExt.resultUpdate(null, conn, rs, fieldMeta("tags", Types.ARRAY), new ArrayList<>(), undefProfile(), 0, false);
		assertTrue(invoked.stream().noneMatch(m -> m.startsWith("update")),
				"非强制更新时空集合不应产生update调用,实际调用:" + invoked);
	}

	@Test
	public void resultUpdateEmptyCollectionSetsNullWhenForced() throws Exception {
		List<String> invoked = new ArrayList<>();
		ResultSet rs = mockResultSet(invoked);
		Connection conn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> null);
		// 空集合:强制更新时置null(修复此前注释声称置null但静默跳过的偏差)
		SqlUtilsExt.resultUpdate(null, conn, rs, fieldMeta("tags", Types.ARRAY), new ArrayList<>(), undefProfile(), 0, false, true);
		assertEquals("updateNull", invoked.get(invoked.size() - 1));

		// 全null元素集合同样按置null处理
		List<String> invoked2 = new ArrayList<>();
		ResultSet rs2 = mockResultSet(invoked2);
		SqlUtilsExt.resultUpdate(null, conn, rs2, fieldMeta("tags", Types.ARRAY), Arrays.asList(null, null), undefProfile(), 0, false,
				true);
		assertEquals("updateNull", invoked2.get(invoked2.size() - 1));
	}

	@Test
	public void resultUpdateBooleanToNvarcharColumn() throws Exception {
		List<String> invoked = new ArrayList<>();
		ResultSet rs = mockResultSet(invoked);
		Connection conn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> null);
		// Boolean写NVARCHAR列:此前落到updateBoolean分支(驱动报错),现正确走updateString("1"/"0")
		SqlUtilsExt.resultUpdate(null, conn, rs, fieldMeta("enabled", Types.NVARCHAR), Boolean.TRUE, undefProfile(), 0, false);
		assertEquals("updateString", invoked.get(invoked.size() - 1));
	}

	@Test
	public void resultUpdateCollectionToArrayNoThrow() throws Exception {
		ResultSet rs = mockResultSet(new ArrayList<>());
		Connection conn = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class[] { Connection.class }, (proxy, method, args) -> null);
		// 正常有值集合不回归:非gaussdb系走updateObject通用路径,不应抛异常
		SqlUtilsExt.resultUpdate(null, conn, rs, fieldMeta("tags", Types.ARRAY),
				Arrays.asList("a", "b"), undefProfile(), 0, false);
	}
}
