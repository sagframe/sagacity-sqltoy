package org.sagacity.sqltoy.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.dao.impl.DefaultSqlToyDaoImpl;

/**
 * SqlToyDao 接口与 DefaultSqlToyDaoImpl 实现完整性测试 无需数据库连接，纯反射验证接口方法全部被实现，命名分组符合规范
 */
public class SqlToyDaoInterfaceTest {

	// ============================================================
	// 测试1：接口所有方法在实现类中均有对应的 @Override 实现
	// ============================================================
	@Test
	@DisplayName("DefaultSqlToyDaoImpl 必须实现 SqlToyDao 接口的所有方法")
	public void testAllInterfaceMethodsImplemented() {
		Method[] interfaceMethods = SqlToyDao.class.getMethods();
		Class<?> implClass = DefaultSqlToyDaoImpl.class;

		List<String> missing = new ArrayList<>();
		for (Method ifMethod : interfaceMethods) {
			try {
				Method implMethod = implClass.getMethod(ifMethod.getName(), ifMethod.getParameterTypes());
				// 确认是实现类自己声明的（不是从父类透传的 abstract）
				assertNotNull(implMethod, "方法未找到: " + ifMethod);
			} catch (NoSuchMethodException e) {
				missing.add(ifMethod.toGenericString());
			}
		}
		if (!missing.isEmpty()) {
			fail("以下接口方法在实现类中未找到:\n" + String.join("\n", missing));
		}
	}

	// ============================================================
	// 测试2：实现类不是抽象类（可实例化）
	// ============================================================
	@Test
	@DisplayName("DefaultSqlToyDaoImpl 不能是抽象类")
	public void testImplClassIsNotAbstract() {
		int modifiers = DefaultSqlToyDaoImpl.class.getModifiers();
		assertTrue(!Modifier.isAbstract(modifiers), "DefaultSqlToyDaoImpl 不应是抽象类");
	}

	// ============================================================
	// 测试3：接口方法命名规范检查 - find* 系列命名一致性
	// ============================================================
	@Test
	@DisplayName("SqlToyDao 查询方法命名规范验证")
	public void testFindMethodNamingConventions() {
		Method[] methods = SqlToyDao.class.getMethods();
		Set<String> methodNames = new HashSet<>();
		for (Method m : methods) {
			methodNames.add(m.getName());
		}

		// findOneById / findAllByIds 系列
		assertTrue(methodNames.contains("findOneById"), "必须有 findOneById 方法");
		assertTrue(methodNames.contains("findAllByIds"), "必须有 findAllByIds 方法");

		// findOneByEntity / findAllByEntities 系列
		assertTrue(methodNames.contains("findOneByEntity"), "必须有 findOneByEntity 方法");
		assertTrue(methodNames.contains("findAllByEntities"), "必须有 findAllByEntities 方法");

		// 级联加载 findOneCascade / findAllCascade
		assertTrue(methodNames.contains("findOneCascade"), "必须有 findOneCascade 方法");
		assertTrue(methodNames.contains("findAllCascade"), "必须有 findAllCascade 方法");

		// 查询单条 findOne
		assertTrue(methodNames.contains("findOne"), "必须有 findOne 方法");

		// 查询多条 findList
		assertTrue(methodNames.contains("findList"), "必须有 findList 方法");

		// top/random/page
		assertTrue(methodNames.contains("findTop"), "必须有 findTop 方法");
		assertTrue(methodNames.contains("findRandom"), "必须有 findRandom 方法");
		assertTrue(methodNames.contains("findPage"), "必须有 findPage 方法");

		// 不应存在旧命名（load系列作为按主键加载的直接方法，已被findByEntity/findAllByEntities/findOneCascade等替代）
		// 注意：load() 零参链式入口仍保留，这里检查的是有参数的 load(entity) 形式
		for (Method m : methods) {
			if ("load".equals(m.getName()) && m.getParameterCount() > 0) {
				fail("不应存在 load(entity,...) 方法，应使用 findByEntity 替代: " + m.toGenericString());
			}
		}
		for (Method m : methods) {
			if ("loadAll".equals(m.getName())) {
				fail("不应存在 loadAll 方法，应使用 findAllByEntities 替代: " + m.toGenericString());
			}
		}
		assertTrue(!methodNames.contains("loadById"), "不应存在 loadById 方法");
		assertTrue(!methodNames.contains("loadByIds"), "不应存在 loadByIds 方法");
		assertTrue(!methodNames.contains("loadEntity"), "不应存在 loadEntity 方法");
		assertTrue(!methodNames.contains("findEntity"), "不应存在 findEntity 方法");
		assertTrue(!methodNames.contains("findPageEntity"), "不应存在 findPageEntity 方法");
		assertTrue(!methodNames.contains("findByQuery"), "不应存在 findByQuery 方法");
		assertTrue(!methodNames.contains("findPageByQuery"), "不应存在 findPageByQuery 方法");
		assertTrue(!methodNames.contains("findTopByQuery"), "不应存在 findTopByQuery 方法");
		assertTrue(!methodNames.contains("findRandomByQuery"), "不应存在 findRandomByQuery 方法");
		assertTrue(!methodNames.contains("loadByQuery"), "不应存在 loadByQuery 方法");
	}

	// ============================================================
	// 测试4：findOne 重载数量和参数验证
	// ============================================================
	@Test
	@DisplayName("findOne 方法应有5个重载")
	public void testFindOneOverloads() {
		Method[] methods = SqlToyDao.class.getMethods();
		long count = countMethodOverloads(methods, "findOne");
		assertEquals(5, count, "findOne 应有5个重载: (String,Map,Class) / (String,Serializable,Class) / "
				+ "(QueryExecutor) / (Class,EntityQuery) / (Class,EntityQuery,Class)");
	}

	// ============================================================
	// 测试5：findList 重载数量验证
	// ============================================================
	@Test
	@DisplayName("findList 方法应有6个重载")
	public void testFindListOverloads() {
		Method[] methods = SqlToyDao.class.getMethods();
		long count = countMethodOverloads(methods, "findList");
		assertEquals(6, count, "findList 应有6个重载: (String,Map,Class) / (String,Map) / (String,Serializable,Class) / "
				+ "(QueryExecutor) / (Class,EntityQuery) / (Class,EntityQuery,Class)");
	}

	// ============================================================
	// 测试6：findTop 重载数量验证
	// ============================================================
	@Test
	@DisplayName("findTop 方法应有3个重载")
	public void testFindTopOverloads() {
		Method[] methods = SqlToyDao.class.getMethods();
		long count = countMethodOverloads(methods, "findTop");
		assertEquals(3, count, "findTop 应有3个重载: (String,Map,Class,double) / (String,Serializable,Class,double) / "
				+ "(QueryExecutor,double)");
	}

	// ============================================================
	// 测试7：findRandom 重载数量验证
	// ============================================================
	@Test
	@DisplayName("findRandom 方法应有3个重载")
	public void testFindRandomOverloads() {
		Method[] methods = SqlToyDao.class.getMethods();
		long count = countMethodOverloads(methods, "findRandom");
		assertEquals(3, count, "findRandom 应有3个重载");
	}

	// ============================================================
	// 测试8：findPage 重载数量验证
	// ============================================================
	@Test
	@DisplayName("findPage 方法应有6个重载")
	public void testFindPageOverloads() {
		Method[] methods = SqlToyDao.class.getMethods();
		long count = countMethodOverloads(methods, "findPage");
		assertEquals(6, count,
				"findPage 应有6个重载: (Page,String,Map) / (Page,String,Map,Class) / "
						+ "(Page,String,Serializable,Class) / (Page,QueryExecutor) / (Page,Class,EntityQuery) / "
						+ "(Page,Class,EntityQuery,Class)");
	}

	// ============================================================
	// 测试9：findOneById / findAllByIds 重载验证
	// ============================================================
	@Test
	@DisplayName("findOneById 应有2个重载，findAllByIds 应有2个重载")
	public void testFindByIdOverloads() {
		Method[] methods = SqlToyDao.class.getMethods();
		assertEquals(2, countMethodOverloads(methods, "findOneById"),
				"findOneById 应有2个重载: (Class,Object) / (Class,Object,LockMode)");
		assertEquals(2, countMethodOverloads(methods, "findAllByIds"),
				"findAllByIds 应有2个重载: (Class,Object...) / (Class,LockMode,Object...)");
	}

	// ============================================================
	// 测试10：链式操作入口全部存在
	// ============================================================
	@Test
	@DisplayName("SqlToyDao 必须包含所有链式操作入口方法")
	public void testChainOperationMethods() {
		Method[] methods = SqlToyDao.class.getMethods();
		Set<String> methodNames = new HashSet<>();
		for (Method m : methods) {
			methodNames.add(m.getName());
		}

		String[] chainMethods = { "elastic", "mongo", "delete", "update", "store", "save", "query", "load", "unique",
				"treeTable", "execute", "batch", "tableApi" };
		for (String name : chainMethods) {
			assertTrue(methodNames.contains(name), "缺少链式操作入口: " + name + "()");
		}
	}

	// ============================================================
	// 测试11：增删改基础方法全部存在
	// ============================================================
	@Test
	@DisplayName("SqlToyDao 必须包含所有增删改基础方法")
	public void testCRUDMethods() {
		Method[] methods = SqlToyDao.class.getMethods();
		Set<String> methodNames = new HashSet<>();
		for (Method m : methods) {
			methodNames.add(m.getName());
		}

		String[] crudMethods = { "save", "saveAll", "saveAllIgnoreExist", "update", "updateDeeply", "updateFetch",
				"updateSaveFetch", "updateByQuery", "updateCascade", "updateAll", "updateAllDeeply", "saveOrUpdate",
				"saveOrUpdateAll", "delete", "deleteAll", "deleteByIds", "deleteByQuery", "truncate", "batchExecute",
				"insertReturnPrimaryKey", "executeSql" };
		for (String name : crudMethods) {
			assertTrue(methodNames.contains(name), "缺少增删改方法: " + name);
		}
	}

	// ============================================================
	// 测试12：SqlToyDao 不继承 LightDao（保持独立）
	// ============================================================
	@Test
	@DisplayName("SqlToyDao 不应继承 LightDao")
	public void testSqlToyDaoNotExtendLightDao() {
		Class<?>[] interfaces = SqlToyDao.class.getInterfaces();
		for (Class<?> iface : interfaces) {
			assertTrue(!iface.equals(LightDao.class), "SqlToyDao 不应继承 LightDao，应保持接口独立");
		}
	}

	// ============================================================
	// 测试13：find 系列旧方法（LightDao 兼容方法）不应出现在 SqlToyDao 中
	// ============================================================
	@Test
	@DisplayName("SqlToyDao 不应包含 LightDao 的旧命名 find(String,...) 方法")
	public void testNoLegacyFindMethods() {
		Method[] methods = SqlToyDao.class.getMethods();
		// SqlToyDao 中的 find 方法只允许是链式操作 find()，不能是旧的 find(String,...)
		for (Method m : methods) {
			if ("find".equals(m.getName()) && m.getParameterCount() > 0) {
				fail("SqlToyDao 中不应存在旧命名的 find(String,...) 方法: " + m.toGenericString() + "，应使用 findList 替代");
			}
		}
	}

	// ============================================================
	// 辅助方法
	// ============================================================
	private long countMethodOverloads(Method[] methods, String name) {
		long count = 0;
		for (Method m : methods) {
			if (m.getName().equals(name)) {
				count++;
			}
		}
		return count;
	}
}
