package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-21 D2/D3回归锁死:
 * D2 SqlUtilsExt.resultUpdate的Object[]分支须与SqlUtil.processParam同口径定型归一
 * (此前裸Object[]直传updateObject,kingbase/gauss系真库报malformed array literal/Cannot cast);
 * D3 createArrayOf类型名统一小写pg规范名(华为驱动按typname精确匹配,大写VARCHAR/NUMERIC报
 * type not found,NUMBER为oracle别名og/kingbase拒绝——四引擎真库矩阵实证)
 * 复查扩展:Double[]/Boolean[]/Short[]补入float8/bool/int2分支(四引擎24红→24绿),
 * resultUpdate与绑定路径同口径接纳原始类型数组(char[]此前kingbase回写报malformed)
 *
 * @author zsy 2026-11-21
 */
public class SqlUtilsExtArrayResultUpdateTest {

	private static final class Recorder {
		final List<String> calls = new ArrayList<String>();

		Connection connection() {
			return (Connection) Proxy.newProxyInstance(Recorder.class.getClassLoader(), new Class[] { Connection.class },
					(proxy, method, args) -> {
						if ("createArrayOf".equals(method.getName())) {
							calls.add("createArrayOf:" + args[0] + ":" + args[1].getClass().getSimpleName());
							return Proxy.newProxyInstance(Recorder.class.getClassLoader(), new Class[] { Array.class },
									(p, m, a) -> null);
						}
						return null;
					});
		}

		ResultSet resultSet() {
			return (ResultSet) Proxy.newProxyInstance(Recorder.class.getClassLoader(), new Class[] { ResultSet.class },
					(proxy, method, args) -> {
						String name = method.getName();
						if ("updateArray".equals(name) || "updateNull".equals(name) || "updateObject".equals(name)) {
							calls.add(name);
						}
						return null;
					});
		}

		PreparedStatement statement() {
			return (PreparedStatement) Proxy.newProxyInstance(Recorder.class.getClassLoader(),
					new Class[] { PreparedStatement.class }, (proxy, method, args) -> {
						if ("setArray".equals(method.getName()) || "setObject".equals(method.getName())
								|| "setNull".equals(method.getName())) {
							calls.add(method.getName());
						}
						return null;
					});
		}
	}

	private static FieldMeta arrayColumn() {
		FieldMeta f = new FieldMeta();
		f.setColumnName("arr");
		f.setType(Types.ARRAY);
		f.setNullable(true);
		return f;
	}

	private static void resultUpdate(Recorder rec, Object value, boolean forced) throws Exception {
		SqlUtilsExt.resultUpdate(null, rec.connection(), rec.resultSet(), arrayColumn(), value, null, DBType.KINGBASE,
				false, forced);
	}

	@Test
	public void objectArrayNormalizedBeforeSetArray() throws Exception {
		Recorder rec = new Recorder();
		resultUpdate(rec, new Object[] { "a", "b" }, true);
		// 修复前:裸Object[]落updateObject(真库报Cannot cast/malformed array literal)
		assertTrue(rec.calls.contains("createArrayOf:varchar:String[]"), "Object[]应定型转String[]经createArrayOf,实际:" + rec.calls);
		assertTrue(rec.calls.contains("updateArray"), "应走updateArray,实际:" + rec.calls);
		assertFalse(rec.calls.contains("updateObject"), "不得裸传updateObject,实际:" + rec.calls);
	}

	@Test
	public void emptyOrAllNullObjectArrayForcedUpdateSetsNull() throws Exception {
		Recorder rec = new Recorder();
		resultUpdate(rec, new Object[0], true);
		// jre8同步适配:List.of(JDK9)→Arrays.asList
		assertEquals(Arrays.asList("updateNull"), rec.calls, "空Object[]无法定型,强制更新置null");
		rec = new Recorder();
		resultUpdate(rec, new Object[] { null }, true);
		assertEquals(Arrays.asList("updateNull"), rec.calls, "全null Object[]无法定型,强制更新置null");
	}

	@Test
	public void emptyObjectArrayNonForcedSkips() throws Exception {
		Recorder rec = new Recorder();
		resultUpdate(rec, new Object[0], false);
		assertTrue(rec.calls.isEmpty(), "非强制更新无法定型时跳过,实际:" + rec.calls);
	}

	@Test
	public void typedArrayAndBigDecimalUseLowercasePgNames() throws Exception {
		Recorder rec = new Recorder();
		resultUpdate(rec, new String[] { "a" }, true);
		assertTrue(rec.calls.contains("createArrayOf:varchar:String[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new BigDecimal[] { new BigDecimal("1.5") }, true);
		// 修复前:NUMBER(kingbase/og报type not found,vastbase的number[]无法赋numeric[]列)
		assertTrue(rec.calls.contains("createArrayOf:numeric:BigDecimal[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new Integer[] { 1 }, true);
		assertTrue(rec.calls.contains("createArrayOf:integer:Integer[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new Long[] { 4000000000L }, true);
		assertTrue(rec.calls.contains("createArrayOf:bigint:Long[]"), "实际:" + rec.calls);
	}

	@Test
	public void bindPathSetArrayUsesLowercasePgNames() throws Exception {
		Method setArray = SqlUtil.class.getDeclaredMethod("setArray", Integer.class, Connection.class,
				PreparedStatement.class, int.class, Object.class);
		setArray.setAccessible(true);
		Recorder rec = new Recorder();
		setArray.invoke(null, DBType.KINGBASE, rec.connection(), rec.statement(), 1, new String[] { "a" });
		assertTrue(rec.calls.contains("createArrayOf:varchar:String[]"), "实际:" + rec.calls);
		assertTrue(rec.calls.contains("setArray"), "实际:" + rec.calls);
		rec = new Recorder();
		setArray.invoke(null, DBType.GAUSSDB, rec.connection(), rec.statement(), 1,
				new BigDecimal[] { new BigDecimal("1.5") });
		assertTrue(rec.calls.contains("createArrayOf:numeric:BigDecimal[]"), "实际:" + rec.calls);
	}

	@Test
	public void doubleBooleanShortUseLowercasePgNames() throws Exception {
		// 修复前:三者落else updateObject/setObject(x,ARRAY),华为驱动/kingbase绑定报Cannot cast,
		// 回写路径四引擎均失败;float8/bool/int2四引擎真库矩阵24红→24绿
		Recorder rec = new Recorder();
		resultUpdate(rec, new Double[] { 1.5d }, true);
		assertTrue(rec.calls.contains("createArrayOf:float8:Double[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new Boolean[] { true }, true);
		assertTrue(rec.calls.contains("createArrayOf:bool:Boolean[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new Short[] { (short) 1 }, true);
		assertTrue(rec.calls.contains("createArrayOf:int2:Short[]"), "实际:" + rec.calls);
	}

	@Test
	public void bindPathDoubleBooleanShortUseLowercasePgNames() throws Exception {
		Method setArray = SqlUtil.class.getDeclaredMethod("setArray", Integer.class, Connection.class,
				PreparedStatement.class, int.class, Object.class);
		setArray.setAccessible(true);
		Recorder rec = new Recorder();
		setArray.invoke(null, DBType.KINGBASE, rec.connection(), rec.statement(), 1, new Double[] { 1.5d });
		assertTrue(rec.calls.contains("createArrayOf:float8:Double[]"), "实际:" + rec.calls);
		assertTrue(rec.calls.contains("setArray"), "实际:" + rec.calls);
		rec = new Recorder();
		setArray.invoke(null, DBType.GAUSSDB, rec.connection(), rec.statement(), 1, new Boolean[] { true });
		assertTrue(rec.calls.contains("createArrayOf:bool:Boolean[]"), "实际:" + rec.calls);
		rec = new Recorder();
		setArray.invoke(null, DBType.KINGBASE, rec.connection(), rec.statement(), 1, new Short[] { (short) 1 });
		assertTrue(rec.calls.contains("createArrayOf:int2:Short[]"), "实际:" + rec.calls);
	}

	@Test
	public void primitiveArraysNormalizedOnWriteback() throws Exception {
		// 与绑定路径口径对齐:原始数组装箱后按包装类定型(char[]装箱产物String[]按varchar);
		// 修复前char[]落else updateObject,kingbase真库报malformed array literal "[C@.."
		Recorder rec = new Recorder();
		resultUpdate(rec, new int[] { 1, 2 }, true);
		assertTrue(rec.calls.contains("createArrayOf:integer:Integer[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new char[] { 'a', 'b' }, true);
		assertTrue(rec.calls.contains("createArrayOf:varchar:String[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new double[] { 1.5d }, true);
		assertTrue(rec.calls.contains("createArrayOf:float8:Double[]"), "实际:" + rec.calls);
		rec = new Recorder();
		resultUpdate(rec, new long[] { 4000000000L }, true);
		assertTrue(rec.calls.contains("createArrayOf:bigint:Long[]"), "实际:" + rec.calls);
	}

	@Test
	public void emptyPrimitiveArrayBindsEmptyNotNull() throws Exception {
		// 定型空数组与绑定侧{}口径一致:不得置null(int[0]装箱Integer[0]可定型)
		Recorder rec = new Recorder();
		resultUpdate(rec, new int[0], true);
		assertTrue(rec.calls.contains("createArrayOf:integer:Integer[]"), "实际:" + rec.calls);
		assertFalse(rec.calls.contains("updateNull"), "定型空数组不得置null,实际:" + rec.calls);
	}

	@Test
	public void primitiveBindPathBoxesAndTypes() throws Exception {
		// 绑定路径胶水锁死:原始数组经convertArray装箱+toBoxedComponentName定型后到达setArray
		Method m = SqlUtil.class.getDeclaredMethod("setBinaryAndOtherParam", Connection.class, DBProfile.class,
				Integer.class, PreparedStatement.class, int.class, int.class, Object.class);
		m.setAccessible(true);
		DBProfile profile = new DBProfile("jdbc:kingbase8://localhost/kingbase", "kingbase", DBType.KINGBASE,
				"KingbaseES", 8, null, null, false);
		Recorder rec = new Recorder();
		m.invoke(null, rec.connection(), profile, DBType.KINGBASE, rec.statement(), Types.NULL, 1, new int[] { 1, 2 });
		assertTrue(rec.calls.contains("createArrayOf:integer:Integer[]"), "实际:" + rec.calls);
		rec = new Recorder();
		m.invoke(null, rec.connection(), profile, DBType.KINGBASE, rec.statement(), Types.NULL, 1, new char[] { 'a' });
		assertTrue(rec.calls.contains("createArrayOf:varchar:String[]"), "char[]装箱String[]按varchar定型,实际:" + rec.calls);
		rec = new Recorder();
		m.invoke(null, rec.connection(), profile, DBType.KINGBASE, rec.statement(), Types.NULL, 1, new double[] { 1.5d });
		assertTrue(rec.calls.contains("createArrayOf:float8:Double[]"), "实际:" + rec.calls);
		rec = new Recorder();
		m.invoke(null, rec.connection(), profile, DBType.KINGBASE, rec.statement(), Types.NULL, 1, new Object[0]);
		assertEquals(Arrays.asList("setNull"), rec.calls, "无法定型的空Object[]置SQL NULL,实际:" + rec.calls);
	}
}