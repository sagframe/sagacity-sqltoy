package org.sagacity.sqltoy.utils;

public class EnumUtilTest {
	// 测试示例
	public static void main(String[] args) {
		// 测试1：无自定义属性的枚举 → true
		System.out.println(EnumUtil.isEnumWithoutCustomField(NoFieldEnum.class));
		System.out.println(EnumUtil.isEnumWithoutCustomField(NoFieldEnum.class));
		// 测试2：有自定义属性的枚举 → false
		System.out.println(EnumUtil.isEnumWithoutCustomField(HasFieldEnum.class));
		// 测试3：非枚举类 → false
		System.out.println(EnumUtil.isEnumWithoutCustomField(String.class));
		// 测试4：空枚举 → true
		System.out.println(EnumUtil.isEnumWithoutCustomField(EmptyEnum.class));
	}

	// 示例1：无自定义属性的枚举
	enum NoFieldEnum {
		A, B, C
	}

	// 示例2：有自定义属性的枚举
	enum HasFieldEnum {
		X(1), Y(2);

		private final int code;

		HasFieldEnum(int c) {
			code = c;
		}
	}

	// 示例3：空枚举
	enum EmptyEnum {
	}
}
