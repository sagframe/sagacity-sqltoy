package org.sagacity.sqltoy.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.annotation.Translate;
import org.sagacity.sqltoy.config.model.FieldTranslate;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

/**
 * 回归:类上的@Translate注解配置缓存必须以Class为key(ClassValue),不得按类名全局缓存。
 * 修复前静态Map<String(类名),...>:热部署/多classloader场景下同名类的新注解配置不会重新解析,
 * 会串用旧类的缓存翻译定义(且静态Map强引用应用侧解析结果)
 */
public class TranslateClassValueTest {

	public static class ParentVo {
		@Translate(cacheName = "parentCache", keyField = "type")
		private String statusName;
	}

	public static class ChildVo extends ParentVo {
		@Translate(cacheName = "childCache", keyField = "type")
		private String typeName;
	}

	private static Class<?> defineReloadedVo(String cacheName) {
		// 模拟热部署:同一个类名在两个classloader中分别是不同版本的注解配置
		return new ByteBuddy().subclass(Object.class).name("org.sagacity.sqltoy.translate.reload.ReloadedVo")
				.defineField("typeName", String.class, Visibility.PUBLIC)
				.annotateField(AnnotationDescription.Builder.ofType(Translate.class).define("cacheName", cacheName)
						.define("keyField", "type").build())
				.make().load(TranslateClassValueTest.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
				.getLoaded();
	}

	@Test
	public void sameClassNameInDifferentClassLoaderMustNotShareConfig() {
		Class<?> first = defineReloadedVo("cacheA");
		Class<?> second = defineReloadedVo("cacheB");
		assertNotSame(first, second);
		HashMap<String, FieldTranslate> firstConfig = TranslateConfigParse.getClassTranslates(first);
		HashMap<String, FieldTranslate> secondConfig = TranslateConfigParse.getClassTranslates(second);
		assertNotNull(firstConfig);
		assertNotNull(secondConfig);
		// 修复前按类名缓存,第二次直接返回第一次的结果(cacheA)
		assertEquals("cacheA", firstConfig.get("typename").translates[0].getExtend().cache);
		assertEquals("cacheB", secondConfig.get("typename").translates[0].getExtend().cache,
				"同名类重新加载后必须按新Class重新解析注解配置");
	}

	@Test
	public void sameClassReusesParsedConfigAndInheritsParentFields() {
		HashMap<String, FieldTranslate> config = TranslateConfigParse.getClassTranslates(ChildVo.class);
		assertNotNull(config);
		assertNotNull(config.get("statusname"), "父类字段的@Translate配置应被继承");
		assertNotNull(config.get("typename"));
		// 同一Class重复获取应命中ClassValue缓存(返回同一实例)
		assertSame(config, TranslateConfigParse.getClassTranslates(ChildVo.class));
	}

	@Test
	public void baseTypesAndNullReturnNull() {
		assertNull(TranslateConfigParse.getClassTranslates(null));
		assertNull(TranslateConfigParse.getClassTranslates(String.class));
		assertNull(TranslateConfigParse.getClassTranslates(Map.class));
		assertNull(TranslateConfigParse.getClassTranslates(HashMap.class));
	}
}
