package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * @project sagacity-sqltoy
 * @description 枚举转换的记忆化校验：缓存值必须与"按原逻辑扫描"的结果完全一致——声明顺序首个命中、
 *              getKey值或name(忽略大小写)、getKey抛异常时整轮中断返回null、非法文本不缓存。
 * @author zhongxuchen
 */
public class BeanUtilEnumInstanceCacheTest {
	public enum WithKey {
		A(101), B(102), C(103);
		private final int value;
		WithKey(int value) { this.value = value; }
		public int getValue() { return value; }
	}

	public enum Plain {
		Red, Green, Blue
	}

	public enum SameKey {
		FIRST(7), SECOND(7);
		private final int value;
		SameKey(int value) { this.value = value; }
		public int getValue() { return value; }
	}

	/** 第一个常量的getKey抛异常：原实现整轮中断，即使后续常量的name能匹配也返回null */
	public enum ThrowingKey {
		BAD, GOOD;
		public int getValue() {
			if (this == BAD) {
				throw new IllegalStateException("intentional");
			}
			return 2;
		}
	}

	/** 特殊字符大小写：equalsIgnoreCase 与"转小写比较"不等价，缓存键必须用原始文本 */
	public enum CaseEdge {
		İ, i
	}

	@Test
	public void matchesByKeyAndNameRepeatedly() {
		assertSame(WithKey.A, BeanUtil.newEnumInstance(101, WithKey.class));
		assertSame(WithKey.B, BeanUtil.newEnumInstance("102", WithKey.class));
		assertSame(WithKey.C, BeanUtil.newEnumInstance("103", WithKey.class));
		// name匹配(忽略大小写)
		assertSame(WithKey.A, BeanUtil.newEnumInstance("a", WithKey.class));
		assertSame(WithKey.B, BeanUtil.newEnumInstance("B", WithKey.class));
		// 非法文本与null
		assertNull(BeanUtil.newEnumInstance("999", WithKey.class));
		assertNull(BeanUtil.newEnumInstance(null, WithKey.class));
		// 重复调用命中同一常量(记忆化后的返回必须一致)
		for (int i = 0; i < 5; i++) {
			assertSame(WithKey.A, BeanUtil.newEnumInstance(101, WithKey.class));
			assertSame(WithKey.B, BeanUtil.newEnumInstance("b", WithKey.class));
			assertNull(BeanUtil.newEnumInstance("999", WithKey.class));
		}
	}

	@Test
	public void plainEnumMatchesByNameOnly() {
		assertSame(Plain.Green, BeanUtil.newEnumInstance("green", Plain.class));
		assertSame(Plain.Red, BeanUtil.newEnumInstance("RED", Plain.class));
		assertNull(BeanUtil.newEnumInstance("yellow", Plain.class));
		for (int i = 0; i < 3; i++) {
			assertSame(Plain.Blue, BeanUtil.newEnumInstance("Blue", Plain.class));
		}
	}

	@Test
	public void duplicateKeyReturnsFirstDeclared() {
		assertSame(SameKey.FIRST, BeanUtil.newEnumInstance(7, SameKey.class));
		for (int i = 0; i < 3; i++) {
			assertSame(SameKey.FIRST, BeanUtil.newEnumInstance("7", SameKey.class));
		}
	}

	@Test
	public void throwingKeyAbortsScanAndIsNotCachedAsMiss() {
		// 第一个常量getKey抛异常 -> 整轮中断，name可匹配的GOOD也返回null
		assertNull(BeanUtil.newEnumInstance("GOOD", ThrowingKey.class));
		assertNull(BeanUtil.newEnumInstance("good", ThrowingKey.class));
		// 重复调用保持同样结果
		assertNull(BeanUtil.newEnumInstance("GOOD", ThrowingKey.class));
	}

	@Test
	public void exoticCaseFoldingIsNotNormalized() {
		// "i" 与 "İ" 忽略大小写相等，按声明顺序应命中第一个常量 İ；两种原始文本各自记忆、结果一致
		assertSame(CaseEdge.İ, BeanUtil.newEnumInstance("İ", CaseEdge.class));
		assertSame(CaseEdge.İ, BeanUtil.newEnumInstance("i", CaseEdge.class));
		for (int i = 0; i < 3; i++) {
			assertSame(CaseEdge.İ, BeanUtil.newEnumInstance("i", CaseEdge.class));
			assertSame(CaseEdge.İ, BeanUtil.newEnumInstance("İ", CaseEdge.class));
		}
	}
}