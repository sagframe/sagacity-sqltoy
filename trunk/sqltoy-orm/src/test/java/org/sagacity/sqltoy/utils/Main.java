package org.sagacity.sqltoy.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.sagacity.sqltoy.demo.vo.StaffInfoVO;

public class Main {
	public static void main(String[] args) {
		Foo<StaffInfoVO> foo = new Foo<StaffInfoVO>() {
		};
		// 在类的外部这样获取
		Type type = ((ParameterizedType) foo.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
		System.out.println(type);
		// 在类的内部这样获取
		System.out.println(foo.getTClass());
	}
}

abstract class Foo<T> {
	public Class<T> getTClass() {
		Class<T> tClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass())
				.getActualTypeArguments()[0];
		return tClass;
	}
}