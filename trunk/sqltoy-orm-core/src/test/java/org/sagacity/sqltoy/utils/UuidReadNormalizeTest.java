package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.model.JdbcTypes;

/**
 * update 2026-11-22 uuid读回统一String回归锁死:pg内核系/h2原生uuid列驱动getObject返回
 * java.util.UUID,Map/裸List无目标类型此前携带该对象(与承载库varchar(36)回读String不一致)。
 * 修复:ResultUtils.normalizeExtTypeValue将UUID归一为规范文本(Map/VO/裸List三路径同口径),
 * BeanUtil.convertType补String→UUID目标分支还原声明java.util.UUID的实体字段。
 *
 * @author zsy 2026-11-22
 */
public class UuidReadNormalizeTest {

	private static final String UUID_STR = "123e4567-e89b-12d3-a456-426614174000";

	/** normalize:驱动UUID归一为规范文本(Map/裸List得String) */
	@Test
	public void normalizeUuidToText() {
		Object out = ResultUtils.normalizeExtTypeValue(UUID.fromString(UUID_STR));
		assertTrue(out instanceof String, "UUID应归一为String,实为" + out.getClass());
		assertEquals(UUID_STR, out);
	}

	/** normalize:常规String快速通道不受影响 */
	@Test
	public void normalizeLeavesPlainString() {
		assertSame("abc", ResultUtils.normalizeExtTypeValue("abc"));
	}

	/** convertType:String→java.util.UUID目标字段(读侧归一后还原UUID字段) */
	@Test
	public void convertStringToUuidField() throws Exception {
		Object out = BeanUtil.convertType(UUID_STR, JdbcTypes.UUID, DataType.getType(UUID.class), "java.util.UUID");
		assertTrue(out instanceof UUID, "应转java.util.UUID,实为" + out.getClass());
		assertEquals(UUID.fromString(UUID_STR), out);
	}

	/** convertType:值本为UUID且目标UUID,typeName一致直接返回(恒等) */
	@Test
	public void convertUuidIdentity() throws Exception {
		UUID src = UUID.fromString(UUID_STR);
		Object out = BeanUtil.convertType(src, JdbcTypes.UUID, DataType.getType(UUID.class), "java.util.UUID");
		assertSame(src, out);
	}

	/** convertType:String目标字段维持文本(既有行为不变) */
	@Test
	public void convertStringFieldUnchanged() throws Exception {
		Object out = BeanUtil.convertType(UUID_STR, JdbcTypes.UUID, DataType.stringType, "java.lang.String");
		assertEquals(UUID_STR, out);
	}

	/** 全链路:UUID字段读回=normalize(UUID→String)再convertType(String→UUID)还原原值 */
	@Test
	public void uuidFieldReadRoundTrip() throws Exception {
		UUID src = UUID.fromString(UUID_STR);
		Object normalized = ResultUtils.normalizeExtTypeValue(src);
		Object restored = BeanUtil.convertType(normalized, JdbcTypes.UUID, DataType.getType(UUID.class),
				"java.util.UUID");
		assertEquals(src, restored, "UUID字段读回应还原原值");
	}

	/** toUuidValue:32位无连字符hex补连字符后解析(框架生成形态进原生uuid列) */
	@Test
	public void toUuidValueCompact32() {
		String compact = UUID_STR.replace("-", "");
		assertEquals(UUID.fromString(UUID_STR), SqlUtil.toUuidValue(compact));
	}

	/** toUuidValue:36位规范形直接解析 */
	@Test
	public void toUuidValueCanonical36() {
		assertEquals(UUID.fromString(UUID_STR), SqlUtil.toUuidValue(UUID_STR));
	}

	/** toUuidValue:值本为UUID恒等返回 */
	@Test
	public void toUuidValueIdentity() {
		UUID src = UUID.fromString(UUID_STR);
		assertSame(src, SqlUtil.toUuidValue(src));
	}

	/** toUuidValue:非法格式仍响亮报错 */
	@Test
	public void toUuidValueInvalidThrows() {
		assertThrows(IllegalArgumentException.class, () -> SqlUtil.toUuidValue("not-a-uuid"));
	}

	/** convertType:32位无连字符String→java.util.UUID字段(承载库varchar(32)读回还原) */
	@Test
	public void convertCompactStringToUuidField() throws Exception {
		String compact = UUID_STR.replace("-", "");
		Object out = BeanUtil.convertType(compact, JdbcTypes.UUID, DataType.getType(UUID.class), "java.util.UUID");
		assertEquals(UUID.fromString(UUID_STR), out);
	}
}