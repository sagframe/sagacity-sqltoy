package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.model.JdbcTypes;

/**
 * 验证BeanUtil.convertType在float[]与Float[]互转场景的兼容性:
 * 1.convertType入口(aryOtherType分支)对"源值已是java数组"的处理
 * 2.convertArray底层反射转换能力
 * 3.setProperty真实赋值链路(setter参数类型与源数组类型不一致时是否argument type mismatch)
 */
public class BeanUtilFloatAryConvertTypeTest {

	public static class FloatAryBean {
		private Float[] boxedVector;

		private float[] primitiveVector;

		public Float[] getBoxedVector() {
			return boxedVector;
		}

		public void setBoxedVector(Float[] boxedVector) {
			this.boxedVector = boxedVector;
		}

		public float[] getPrimitiveVector() {
			return primitiveVector;
		}

		public void setPrimitiveVector(float[] primitiveVector) {
			this.primitiveVector = primitiveVector;
		}
	}

	/** convertType: float[] 源 -> Float[] 目标 */
	@Test
	public void convertTypePrimitiveToBoxed() throws Exception {
		float[] src = new float[] { 1.5f, 2.5f };
		Object result = BeanUtil.convertType(src, JdbcTypes.OTHER, DataType.getType("java.lang.Float[]"),
				"java.lang.Float[]");
		System.out.println("[convertType float[]->Float[]] 返回类型: "
				+ ((result == null) ? "null" : result.getClass().getTypeName()));
		assertTrue(result instanceof Float[],
				"convertType应将float[]转为Float[],实际返回: " + result.getClass().getTypeName());
		assertArrayEquals(new Float[] { 1.5f, 2.5f }, (Float[]) result);
	}

	/** convertType: Float[] 源 -> float[] 目标 */
	@Test
	public void convertTypeBoxedToPrimitive() throws Exception {
		Float[] src = new Float[] { 1.5f, 2.5f };
		Object result = BeanUtil.convertType(src, JdbcTypes.OTHER, DataType.getType("float[]"), "float[]");
		System.out.println("[convertType Float[]->float[]] 返回类型: "
				+ ((result == null) ? "null" : result.getClass().getTypeName()));
		assertTrue(result instanceof float[],
				"convertType应将Float[]转为float[],实际返回: " + result.getClass().getTypeName());
		assertArrayEquals(new float[] { 1.5f, 2.5f }, (float[]) result);
	}

	/** convertArray底层: 两个方向均应支持(getArrayComponentType含float[]/java.lang.Float[]) */
	@Test
	public void convertArrayBothDirections() {
		assertArrayEquals(new Float[] { 1.5f, 2.5f },
				(Float[]) BeanUtil.convertArray(new float[] { 1.5f, 2.5f }, "java.lang.Float[]"));
		assertArrayEquals(new float[] { 1.5f, 2.5f },
				(float[]) BeanUtil.convertArray(new Float[] { 1.5f, 2.5f }, "float[]"));
	}

	/** setProperty真实链路: float[]值赋给Float[]属性 */
	@Test
	public void setPropertyPrimitiveToBoxed() {
		FloatAryBean bean = new FloatAryBean();
		BeanUtil.setProperty(bean, "boxedVector", new float[] { 1.5f, 2.5f });
		assertArrayEquals(new Float[] { 1.5f, 2.5f }, bean.getBoxedVector());
	}

	/** setProperty真实链路: Float[]值赋给float[]属性 */
	@Test
	public void setPropertyBoxedToPrimitive() {
		FloatAryBean bean = new FloatAryBean();
		BeanUtil.setProperty(bean, "primitiveVector", new Float[] { 3.5f, 4.5f });
		assertArrayEquals(new float[] { 3.5f, 4.5f }, bean.getPrimitiveVector());
	}
}
