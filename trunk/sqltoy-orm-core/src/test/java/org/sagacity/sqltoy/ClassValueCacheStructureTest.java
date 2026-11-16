package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.TranslateConfigParse;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.EnumUtil;
import org.sagacity.sqltoy.utils.MapperUtils;

/**
 * 结构守卫(不依赖GC):本轮把按Class做key的静态缓存统一改为ClassValue后,用反射锁定该结构,
 * 防止后续改动又回退成 static Map&lt;Class,...&gt; / ClassKey Map。
 * <p>
 * 热部署类卸载的行为验证见BeanUtilClassValueUnloadTest(WeakReference+GC),
 * 本用例只做"缓存类型/键类型"的确定性断言,避免GC不确定性。
 * </p>
 */
public class ClassValueCacheStructureTest {

	@Test
	public void classKeyedCachesAreClassValue() throws Exception {
		// (jre8同步适配:剔除recordProperties——本侧BeanUtil无record分支,该缓存不存在)
		assertClassValueCaches(BeanUtil.class, "setMethods", "getMethods", "cascadeModels", "enumKeyAccessors",
				"matchSetMethodsCache", "matchGetMethodsCache", "noArgConstructors",
				"mappingPlanCache", "classFieldTypeCache");
		assertClassValueCaches(EnumUtil.class, "withoutCustomFieldEnum");
		assertClassValueCaches(MapperUtils.class, "dtoEntityMapperCache", "classFieldAliasFromMap",
				"classFieldAliasTargetMap");
		assertClassValueCaches(TranslateConfigParse.class, "classTranslateConfigs");
	}

	@Test
	public void noStaticMapKeyedByClassRemains() throws Exception {
		for (Class<?> owner : new Class<?>[] { BeanUtil.class, EnumUtil.class, MapperUtils.class,
				TranslateConfigParse.class }) {
			for (Field field : owner.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) {
					continue;
				}
				Type genericType = field.getGenericType();
				if (!(genericType instanceof ParameterizedType)) {
					continue;
				}
				Type firstArg = ((ParameterizedType) genericType).getActualTypeArguments()[0];
				if (firstArg == Class.class) {
					fail(owner.getSimpleName() + "." + field.getName() + " 仍是 static Map<Class,...>,"
							+ "应按ClassValue治理(热部署类卸载泄漏)");
				}
			}
		}
	}

	private static void assertClassValueCaches(Class<?> owner, String... fieldNames) throws Exception {
		for (String fieldName : fieldNames) {
			Field field = owner.getDeclaredField(fieldName);
			assertTrue(Modifier.isStatic(field.getModifiers()),
					owner.getSimpleName() + "." + fieldName + " 应为静态缓存");
			assertTrue(ClassValue.class.isAssignableFrom(field.getType()), owner.getSimpleName() + "." + fieldName
					+ " 应为ClassValue,实际:" + field.getType().getName());
		}
	}
}
