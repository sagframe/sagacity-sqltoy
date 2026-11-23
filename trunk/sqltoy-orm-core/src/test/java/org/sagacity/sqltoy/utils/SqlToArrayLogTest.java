package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-21 标量比较符位置数组/集合参数的日志分派输出验证:
 * PG系/H2输出ARRAY[..]、clickhouse输出[..](原生数组方言),无原生数组语义方言按各库
 * 二进制字面量呈现Java序列化流头ACED0005(mysql系X''、oracle/dm用HEXTORAW、
 * sqlserver用0x、db2用BX'')+array注释附原始值,未实测方言null兜底——保证日志SQL
 * copy执行不产生"日志查出结果而程序无结果/报错"的假阳性
 *
 * @author zsy 2026-11-21
 */
public class SqlToArrayLogTest {

	private static final String SERIALIZE_HEAD = "X'ACED0005'";

	@Test
	public void legacySignatureKeepsCommaFormForLoopMacro() {
		// 4参签名是@loop宏拼接真实sql的调用路径,行为与分派前完全一致(逗号串形态)
		assertEquals("'a','b'",
				SqlUtil.toSqlLogStr(new String[] { "a", "b" }, " where name =", true, DBType.POSTGRESQL));
		assertEquals("'a'", SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.MYSQL));
	}

	@Test
	public void pgEqualsArrayOutputsArrayLiteral() {
		assertEquals("ARRAY['a','b']",
				SqlUtil.toSqlLogStr(new String[] { "a", "b" }, " where name =", true, DBType.POSTGRESQL, true));
	}

	@Test
	public void pgFamilyDialectsAllOutputArrayLiteral() {
		String[] names = { "a" };
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.POSTGRESQL14, true));
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.GAUSSDB, true));
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.OPENGAUSS, true));
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.MOGDB, true));
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.STARDB, true));
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.VASTBASE, true));
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.HIGHGO, true));
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.KINGBASE, true));
		// h2的setObject(Object[])原生映射数组类型,字面量同为ARRAY[..]形态
		assertEquals("ARRAY['a']", SqlUtil.toSqlLogStr(names, " t.f =", true, DBType.H2, true));
	}

	@Test
	public void clickHouseOutputsNativeBracketLiteral() {
		// clickhouse原生数组字面量为['a','b'],驱动经createArrayOf/setArray绑定
		assertEquals("['a','b']",
				SqlUtil.toSqlLogStr(new String[] { "a", "b" }, " where arr =", true, DBType.CLICKHOUSE, true));
		assertEquals("[1,2,3]",
				SqlUtil.toSqlLogStr(new Integer[] { 1, 2, 3 }, " where ids =", true, DBType.CLICKHOUSE, true));
	}

	@Test
	public void pgNumberAndDateElements() {
		assertEquals("ARRAY[1,2,3]",
				SqlUtil.toSqlLogStr(new Integer[] { 1, 2, 3 }, " t.id =", true, DBType.POSTGRESQL, true));
		// 数组内日期为字符串形态,pg按目标数组列隐式转换,date[]列copy可执行
		assertEquals("ARRAY['2024-01-01','2024-06-30']",
				SqlUtil.toSqlLogStr(new LocalDate[] { LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30) },
						" t.d =", true, DBType.POSTGRESQL, true));
	}

	@Test
	public void mysqlEqualsArrayOutputsSerializeRevealForm() {
		// 矛盾场景锚点:单元素数组此前输出'a',copy执行能查出结果而程序(序列化bytes绑定)恒报错;
		// X''仅接受十六进制位,8位hex即序列化流头ACED0005(带省略号的X'ACED0005...'实为语法错误)
		assertEquals(SERIALIZE_HEAD + " /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.MYSQL, true));
		assertEquals(SERIALIZE_HEAD + " /*array:'a','b'*/",
				SqlUtil.toSqlLogStr(new String[] { "a", "b" }, " where name =", true, DBType.MYSQL, true));
		// mysql系方言同走X''形态(ob主流mysql模式、tidb/doris/starrocks为mysql协议)
		assertEquals(SERIALIZE_HEAD + " /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.TIDB, true));
		assertEquals(SERIALIZE_HEAD + " /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.OCEANBASE, true));
	}

	@Test
	public void binaryLiteralDialectsUseNativeRevealForm() {
		// oracle/dm:HEXTORAW('ACED0005')真库实测合法且恒0行(oracle绑定报ORA-17004/17059、
		// dm报不支持该数据类型,旧版='a'能查出结果属假阳性)
		assertEquals("HEXTORAW('ACED0005') /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.ORACLE, true));
		assertEquals("HEXTORAW('ACED0005') /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.DM, true));
		// sqlserver:0x十六进制字面量真库实测合法且恒0行(绑定报不支持从UNKNOWN到OTHER/ARRAY的转换)
		assertEquals("0xACED0005 /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.SQLSERVER, true));
		// db2二进制串常量BX'..'
		assertEquals("BX'ACED0005' /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.DB2, true));
	}

	@Test
	public void hanaUsesToVarbinaryRevealForm() {
		// hana真库实测:驱动绑定数组不报错、比较静默0行(唯一"静默0行"方言),TO_VARBINARY
		// copy执行同为0行,双向一致
		assertEquals("TO_VARBINARY('ACED0005') /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.HANA, true));
	}

	@Test
	public void unverifiedDialectsFallToNullRevealForm() {
		// oscar等数组能力未实测的方言:null形态copy恒0行且不赌语法
		assertEquals("null /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name =", true, DBType.OSCAR, true));
	}

	@Test
	public void otherCompareOperatorsDispatch() {
		assertEquals(SERIALIZE_HEAD + " /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name !=", true, DBType.MYSQL, true));
		assertEquals(SERIALIZE_HEAD + " /*array:'a'*/",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " where name <>", true, DBType.MYSQL, true));
		assertEquals("ARRAY['a']",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " t.f >=", true, DBType.POSTGRESQL, true));
		assertEquals("ARRAY['a']",
				SqlUtil.toSqlLogStr(new String[] { "a" }, " t.f <=", true, DBType.POSTGRESQL, true));
	}

	@Test
	public void inAndBetweenPositionsKeepLegacyForm() {
		// in(位置正常流程参数已被processIn摊平,此处为防御分支,维持逗号串形态
		assertEquals("'a','b'",
				SqlUtil.toSqlLogStr(new String[] { "a", "b" }, " where id in (", true, DBType.MYSQL, true));
		assertEquals("'a','b'",
				SqlUtil.toSqlLogStr(new String[] { "a", "b" }, " and d between", true, DBType.MYSQL, true));
	}

	@Test
	public void noQuotationModeKeepsLegacyForm() {
		// addSingleQuotation=false(非日志拟合场景)不做分派,且不包裹日期函数
		assertEquals("'a','b'", SqlUtil.toSqlLogStr(new String[] { "a", "b" }, null, false, DBType.MYSQL, true));
	}

	@Test
	public void revealFormCommentIsSafeWhenValueContainsCommentEnd() {
		// 元素值含*/时插入空格消解,注释不会被提前截断
		assertEquals(SERIALIZE_HEAD + " /*array:'a* /b'*/",
				SqlUtil.toSqlLogStr(new String[] { "a*/b" }, " where name =", true, DBType.MYSQL, true));
	}

	@Test
	public void collectionParamDispatchesSameAsArray() {
		List<String> values = Arrays.asList("a", "b");
		assertEquals("ARRAY['a','b']",
				SqlUtil.toSqlLogStr(values, " where name =", true, DBType.POSTGRESQL, true));
		assertEquals(SERIALIZE_HEAD + " /*array:'a','b'*/",
				SqlUtil.toSqlLogStr(values, " where name =", true, DBType.MYSQL, true));
	}

	@Test
	public void emptyTypedArrayMatchesEmptyBinding() {
		// update 2026-11-21 空定型数组绑定为{}/[](SqlUtil定型组件路径),日志不得输出
		// ARRAY[null](1元素null数组)——H2/kingbase真库实证ARRAY[null]与空数组绑定0命中
		assertEquals("'{}'", SqlUtil.toSqlLogStr(new String[0], " where arr =", true, DBType.POSTGRESQL, true));
		assertEquals("ARRAY[]", SqlUtil.toSqlLogStr(new String[0], " where arr =", true, DBType.H2, true));
		assertEquals("[]", SqlUtil.toSqlLogStr(new String[0], " where arr =", true, DBType.CLICKHOUSE, true));
		assertEquals(SERIALIZE_HEAD + " /*array:[]*/",
				SqlUtil.toSqlLogStr(new String[0], " where arr =", true, DBType.MYSQL, true));
	}

	@Test
	public void untypedEmptyOrAllNullMatchesNullBinding() {
		// update 2026-11-21 无法定型(Object[]组件/Collection泛型擦除)且无非null元素:
		// 绑定侧置SQL NULL,日志同口径输出null揭示形态,不得输出ARRAY[null]
		assertEquals("null /*array:[]*/",
				SqlUtil.toSqlLogStr(new Object[0], " where arr =", true, DBType.POSTGRESQL, true));
		assertEquals("null /*array:null*/",
				SqlUtil.toSqlLogStr(new Object[] { null }, " where arr =", true, DBType.POSTGRESQL, true));
		assertEquals("null /*array:[]*/",
				SqlUtil.toSqlLogStr(java.util.Collections.emptyList(), " where arr =", true, DBType.MYSQL, true));
		// 定型全null数组绑定{NULL}(有效值),ARRAY[null]形态保持不变
		assertEquals("ARRAY[null]",
				SqlUtil.toSqlLogStr(new String[] { null }, " where arr =", true, DBType.POSTGRESQL, true));
	}

	@Test
	public void primitiveArrayDispatchesLikeBoxed() {
		// update 2026-11-21 原始类型数组装箱后与装箱数组同形态(此前不匹配任何分支,
		// ""+paramValue输出"[I@.."乱码)
		assertEquals("ARRAY[1,2]",
				SqlUtil.toSqlLogStr(new int[] { 1, 2 }, " where ids =", true, DBType.POSTGRESQL, true));
		assertEquals("[1,2]", SqlUtil.toSqlLogStr(new int[] { 1, 2 }, " where ids =", true, DBType.CLICKHOUSE, true));
		assertEquals(SERIALIZE_HEAD + " /*array:1,2*/",
				SqlUtil.toSqlLogStr(new int[] { 1, 2 }, " where ids =", true, DBType.MYSQL, true));
		// 4参旧签名(@loop真实sql路径)同样装箱为逗号串
		assertEquals("1,2", SqlUtil.toSqlLogStr(new int[] { 1, 2 }, " where ids =", true, DBType.POSTGRESQL));
		// 空原始数组按组件类型定型(int→Integer),非"无法定型null"路径
		assertEquals("'{}'", SqlUtil.toSqlLogStr(new int[0], " where ids =", true, DBType.POSTGRESQL, true));
	}

	@Test
	public void endToEndFitSqlParams() {
		// 端到端:mysql下=传数组,日志输出copy执行同为0行,与真实绑定一致
		SqlToyResult result = SqlConfigParseUtils.processSql("select * from staff_info where staff_name = :names",
				new String[] { "names" }, new Object[] { new String[] { "张三" } }, "mysql");
		String log = SqlExecuteStat.fitSqlParams(result.getSql(), result.getParamsValue(), DBType.MYSQL);
		assertTrue(log.contains("staff_name = " + SERIALIZE_HEAD + " /*array:'张三'*/"), log);
	}

	@Test
	public void inScenarioRegression() {
		// 回归:in(:ids)数组参数经processIn摊平后日志仍为标量列表,不受本次改动影响
		SqlToyResult result = SqlConfigParseUtils.processSql(
				"select * from staff_info where staff_id in (:staffIds) and status=:status",
				new String[] { "staffIds", "status" }, new Object[] { new String[] { "S1001", "S1002" }, 1 },
				"mysql");
		String log = SqlExecuteStat.fitSqlParams(result.getSql(), result.getParamsValue(), DBType.MYSQL);
		assertTrue(log.contains("staff_id in ('S1001','S1002')"), log);
		assertTrue(log.contains("status=1"), log);
	}
}
