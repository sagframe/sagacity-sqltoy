package org.sagacity.sqltoy.plugins;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyThreadDataHolder;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.BeanUtil;

/**
 * 疑点实证探针:C1(@if在secure-loop内)/C2(?模式+@loop)/C3(in(@loop)空数组)/
 * C4(逗号linkSign+段剔空)/A3(BeanUtil Byte分支)——按实测输出定性真伪
 */
public class SuspectVerificationTest {

	@BeforeEach
	public void setUp() {
		SqlToyThreadDataHolder.setCounter(0);
	}

	@AfterEach
	public void tearDown() {
		SqlToyThreadDataHolder.clearCounter();
	}

	// C1: @secure-loop循环体内@if——ok=true的段保留,ok=false的段剔除?
	@Test
	public void c1_ifInsideSecureLoop() {
		java.util.Map<String, Object> item1 = new java.util.HashMap<>();
		item1.put("ok", Boolean.TRUE);
		item1.put("v", "a");
		java.util.Map<String, Object> item2 = new java.util.HashMap<>();
		item2.put("ok", Boolean.FALSE);
		item2.put("v", "b");
		SqlToyResult r = SqlConfigParseUtils.processSql(
				"select * from t where 1=1 and (@secure-loop(:items, ' #[@if(:items[i].ok) t.x=:items[i].v] ', ' or '))",
				new String[] { "items" }, new Object[] { new Object[] { item1, item2 } });
		System.out.println("[C1-if-in-loop ] sql=" + r.getSql() + " params=" + java.util.Arrays.toString(r.getParamsValue()));
	}

	// C2: ?位置模式(不传paramsNamed)的sql里写@loop——宏是否被处理?
	@Test
	public void c2_loopWithPositionalArgs() {
		SqlToyResult r = SqlConfigParseUtils.processSql(
				"select * from t where id=? and exists (@loop(:ids, ' select 1 from d where d.x=':ids[i]'' , ' union all '))",
				null, new Object[] { 1, new String[] { "a", "b" } });
		System.out.println("[C2-positional ] sql=" + r.getSql() + " params=" + java.util.Arrays.toString(r.getParamsValue()));
	}

	// C3: id in (@loop(:ids,...)) 空数组——最终sql形态?
	@Test
	public void c3_inLoopEmptyArray() {
		SqlToyResult r = SqlConfigParseUtils.processSql(
				"select * from t where id in (@loop(:ids, ':ids[i]', ','))",
				new String[] { "ids" }, new Object[] { new String[] {} });
		System.out.println("[C3-in-empty   ] sql=[" + r.getSql() + "] params=" + java.util.Arrays.toString(r.getParamsValue()));
		// secure版对照
		SqlToyResult r2 = SqlConfigParseUtils.processSql(
				"select * from t where id in (@secure-loop(:ids, ':ids[i]', ','))",
				new String[] { "ids" }, new Object[] { new String[] {} });
		System.out.println("[C3-in-empty-s ] sql=[" + r2.getSql() + "]");
	}

	// C4: 逗号linkSign+段剔空——in列表场景跳过迭代后逗号是否连续?
	@Test
	public void c4_commaLinkWithBlankRef() {
		SqlToyResult r = SqlConfigParseUtils.processSql(
				"select * from t where x in (@secure-loop(:names, ' #[ :ids[i]] ', ','))",
				new String[] { "names", "ids" },
				new Object[] { new String[] { "a", "b", "c" }, new Object[] { "x", "", "z" } });
		System.out.println("[C4-comma-blank] sql=[" + r.getSql() + "] params=" + java.util.Arrays.toString(r.getParamsValue()));
	}

	// A3: BeanUtil Byte分支归一锁定(修复前:空串/带空白/小数/千分位全抛NumberFormatException)
	@Test
	public void a3_byteBranchNormalization() throws Exception {
		org.junit.jupiter.api.Assertions.assertEquals((byte) 5,
				BeanUtil.convertType(" 5", 0, org.sagacity.sqltoy.config.model.DataType.wrapByteType, null), "带空白文本应归一");
		org.junit.jupiter.api.Assertions.assertEquals((byte) 1,
				BeanUtil.convertType("1.0", 0, org.sagacity.sqltoy.config.model.DataType.wrapByteType, null), "小数应向零截断");
		org.junit.jupiter.api.Assertions.assertNull(
				BeanUtil.convertType("", 0, org.sagacity.sqltoy.config.model.DataType.wrapByteType, null), "空串转null");
		// primitive形态(空串兜底return 0装箱Integer,与Short族同款,按数值比较)
		org.junit.jupiter.api.Assertions.assertEquals(0, ((Number) BeanUtil.convertType("",
				0, org.sagacity.sqltoy.config.model.DataType.primitiveByteType, null)).byteValue(), "primitive空串转0");
		org.junit.jupiter.api.Assertions.assertEquals(7, ((Number) BeanUtil.convertType(" 7",
				0, org.sagacity.sqltoy.config.model.DataType.primitiveByteType, null)).byteValue(), "primitive带空白归一");
		// 越界显式报错(与Integer/Short同口径,不再静默回绕)
		org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class,
				() -> BeanUtil.convertType("999", 0, org.sagacity.sqltoy.config.model.DataType.wrapByteType, null),
				"越界应显式抛NumberFormatException");
		org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class,
				() -> BeanUtil.convertType("8,888", 0, org.sagacity.sqltoy.config.model.DataType.wrapByteType, null),
				"千分位剥离后8888越byte界应显式抛");
	}

	// C1补充: @if字符串比较形态在secure-loop内的表现(用户反馈"判断条件不成功"的疑似形态)
	@Test
	public void c1_ifCompareInsideSecureLoop() {
		java.util.Map<String, Object> item1 = new java.util.HashMap<>();
		item1.put("tp", "a");
		java.util.Map<String, Object> item2 = new java.util.HashMap<>();
		item2.put("tp", "b");
		SqlToyResult r = SqlConfigParseUtils.processSql(
				"select * from t where 1=1 and (@secure-loop(:items, ' #[@if(:items[i].tp==\"a\") t.x=:items[i].tp] ', ' or '))",
				new String[] { "items" }, new Object[] { new Object[] { item1, item2 } });
		System.out.println("[C1-if-cmp     ] sql=" + r.getSql() + " params=" + java.util.Arrays.toString(r.getParamsValue()));
	}
}
