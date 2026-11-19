package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 原生byte/short空串与null赋值的端到端回归:convertType空串/null分支曾返回Integer装箱的0,
 * Method.invoke不允许Integer窄化为byte/short形参,导致setProperty抛IllegalArgumentException。
 * 修复后空串/null返回(byte)0/(short)0,经真实反射setter赋值验证。
 */
public class BeanUtilPrimitiveBlankAssignTest {

	public static class PrimitiveVo {
		private byte byteVal;
		private short shortVal;
		private int intVal;
		private long longVal;
		private float floatVal;
		private double doubleVal;
		private Byte wrapByteVal;
		private Short wrapShortVal;

		public byte getByteVal() {
			return byteVal;
		}

		public void setByteVal(byte byteVal) {
			this.byteVal = byteVal;
		}

		public short getShortVal() {
			return shortVal;
		}

		public void setShortVal(short shortVal) {
			this.shortVal = shortVal;
		}

		public int getIntVal() {
			return intVal;
		}

		public void setIntVal(int intVal) {
			this.intVal = intVal;
		}

		public long getLongVal() {
			return longVal;
		}

		public void setLongVal(long longVal) {
			this.longVal = longVal;
		}

		public float getFloatVal() {
			return floatVal;
		}

		public void setFloatVal(float floatVal) {
			this.floatVal = floatVal;
		}

		public double getDoubleVal() {
			return doubleVal;
		}

		public void setDoubleVal(double doubleVal) {
			this.doubleVal = doubleVal;
		}

		public Byte getWrapByteVal() {
			return wrapByteVal;
		}

		public void setWrapByteVal(Byte wrapByteVal) {
			this.wrapByteVal = wrapByteVal;
		}

		public Short getWrapShortVal() {
			return wrapShortVal;
		}

		public void setWrapShortVal(Short wrapShortVal) {
			this.wrapShortVal = wrapShortVal;
		}
	}

	@Test
	public void blankStringAssignsZeroToByteAndShort() {
		PrimitiveVo vo = new PrimitiveVo();
		vo.setByteVal((byte) 9);
		vo.setShortVal((short) 9);
		BeanUtil.setProperty(vo, "byteVal", "");
		BeanUtil.setProperty(vo, "shortVal", "");
		assertEquals((byte) 0, vo.getByteVal());
		assertEquals((short) 0, vo.getShortVal());
		// 纯空白与空串同口径
		BeanUtil.setProperty(vo, "byteVal", "   ");
		BeanUtil.setProperty(vo, "shortVal", "   ");
		assertEquals((byte) 0, vo.getByteVal());
		assertEquals((short) 0, vo.getShortVal());
	}

	@Test
	public void nullAssignsZeroToByteAndShort() {
		PrimitiveVo vo = new PrimitiveVo();
		vo.setByteVal((byte) 9);
		vo.setShortVal((short) 9);
		BeanUtil.setProperty(vo, "byteVal", null);
		BeanUtil.setProperty(vo, "shortVal", null);
		assertEquals((byte) 0, vo.getByteVal());
		assertEquals((short) 0, vo.getShortVal());
	}

	@Test
	public void normalValuesStillConvert() {
		PrimitiveVo vo = new PrimitiveVo();
		BeanUtil.setProperty(vo, "byteVal", "127");
		BeanUtil.setProperty(vo, "shortVal", "1,234");
		assertEquals((byte) 127, vo.getByteVal());
		assertEquals((short) 1234, vo.getShortVal());
	}

	@Test
	public void outOfRangeStillThrows() {
		PrimitiveVo vo = new PrimitiveVo();
		assertThrows(RuntimeException.class, () -> BeanUtil.setProperty(vo, "byteVal", "128"));
		assertThrows(RuntimeException.class, () -> BeanUtil.setProperty(vo, "shortVal", "32768"));
	}

	@Test
	public void blankStringAssignsNullToWrappers() {
		PrimitiveVo vo = new PrimitiveVo();
		vo.setWrapByteVal((byte) 9);
		vo.setWrapShortVal((short) 9);
		BeanUtil.setProperty(vo, "wrapByteVal", "");
		BeanUtil.setProperty(vo, "wrapShortVal", "");
		assertNull(vo.getWrapByteVal());
		assertNull(vo.getWrapShortVal());
	}

	@Test
	public void widenedPrimitivesBlankStillZero() {
		PrimitiveVo vo = new PrimitiveVo();
		BeanUtil.setProperty(vo, "intVal", "");
		BeanUtil.setProperty(vo, "longVal", "");
		BeanUtil.setProperty(vo, "floatVal", "");
		BeanUtil.setProperty(vo, "doubleVal", "");
		assertEquals(0, vo.getIntVal());
		assertEquals(0L, vo.getLongVal());
		assertEquals(0f, vo.getFloatVal());
		assertEquals(0d, vo.getDoubleVal());
	}
}