package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.annotation.SqlToyFieldAlias;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

/**
 * 回归:字段别名映射缓存必须以Class为key(ClassValue),不得按类名字符串缓存。
 * 修复前静态Map<"class=类名;direct=方向",...>:热部署/多classloader下同名类重新加载后仍命中
 * 旧注解推导出的别名映射(热改动@SqlToyFieldAlias不生效),且类名键常驻
 */
public class MapperUtilsClassValueTest {

	private static Class<?> defineAliasVo(String aliasValue) {
		return new ByteBuddy().subclass(Object.class).name("org.sagacity.sqltoy.utils.reload.AliasVo")
				.defineField("userName", String.class, Visibility.PUBLIC)
				.annotateField(AnnotationDescription.Builder.ofType(SqlToyFieldAlias.class)
						.define("value", aliasValue).build())
				.make().load(MapperUtilsClassValueTest.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
				.getLoaded();
	}

	@SuppressWarnings("unchecked")
	private static HashMap<String, String> aliasMap(Class<?> targetClass, boolean doFrom) throws Exception {
		Method method = MapperUtils.class.getDeclaredMethod("getClassAliasMap", Class.class, boolean.class);
		method.setAccessible(true);
		return (HashMap<String, String>) method.invoke(null, targetClass, doFrom);
	}

	@Test
	public void sameClassNameInDifferentClassLoaderMustNotShareAliasConfig() throws Exception {
		Class<?> first = defineAliasVo("aliasA");
		Class<?> second = defineAliasVo("aliasB");
		assertNotSame(first, second);
		HashMap<String, String> firstFrom = aliasMap(first, true);
		HashMap<String, String> secondFrom = aliasMap(second, true);
		assertEquals("aliasA", firstFrom.get("userName"));
		// 修复前按类名缓存,第二次直接返回第一次的结果(aliasA)
		assertEquals("aliasB", secondFrom.get("userName"), "同名类重新加载后必须按新Class重新解析字段别名");
		assertNotSame(firstFrom, secondFrom);
	}

	@Test
	public void sameClassReusesAliasMapAndSupportsBothDirections() throws Exception {
		Class<?> aliasVo = defineAliasVo("mapAlias");
		HashMap<String, String> from = aliasMap(aliasVo, true);
		HashMap<String, String> to = aliasMap(aliasVo, false);
		assertEquals("mapAlias", from.get("userName"));
		assertEquals("userName", to.get("mapAlias"));
		// 同一Class重复获取命中ClassValue缓存
		assertSame(from, aliasMap(aliasVo, true));
		assertSame(to, aliasMap(aliasVo, false));
	}
}
