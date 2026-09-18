package org.sagacity.sqltoy.dao;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SqlToyDao 接口签名完整性测试 确保所有声明的方法都在实现类中正确实现
 */
@DisplayName("SqlToyDao 接口签名完整性测试")
public class SqlToyDaoSignatureTest {

	/**
	 * 测试 SqlToyDao 接口的所有方法都必须在 DefaultSqlToyDaoImpl 中实现
	 */
	@Test
	@DisplayName("DefaultSqlToyDaoImpl 必须实现 SqlToyDao 的所有方法")
	public void testDefaultSqlToyDaoImplImplementsAllMethods() throws ClassNotFoundException {
		// 获取 SqlToyDao 接口的所有方法
		Class<?> sqlToyDaoInterface = SqlToyDao.class;
		Method[] interfaceMethods = sqlToyDaoInterface.getDeclaredMethods();

		// 获取 DefaultSqlToyDaoImpl 实现类的所有方法
		Class<?> implClass = Class.forName("org.sagacity.sqltoy.dao.impl.DefaultSqlToyDaoImpl");
		Method[] implMethods = implClass.getDeclaredMethods();

		// 创建实现类方法的签名集合
		Set<String> implMethodSignatures = new HashSet<>();
		for (Method method : implMethods) {
			implMethodSignatures.add(getMethodSignature(method));
		}

		// 检查每个接口方法是否都有实现
		List<String> missingMethods = new ArrayList<>();
		for (Method interfaceMethod : interfaceMethods) {
			String signature = getMethodSignature(interfaceMethod);
			if (!implMethodSignatures.contains(signature)) {
				missingMethods.add(signature);
			}
		}

		// 断言：所有接口方法都必须有实现
		Assertions.assertTrue(missingMethods.isEmpty(),
				"DefaultSqlToyDaoImpl 缺少以下方法的实现:\n" + String.join("\n", missingMethods));
	}

	/**
	 * 测试 SqlToyDao 接口的所有方法都必须在 SqlToyDaoImpl(Spring 模块) 中实现
	 */
	@Test
	@DisplayName("SqlToyDaoImpl(Spring) 必须实现 SqlToyDao 的所有方法")
	public void testSpringSqlToyDaoImplImplementsAllMethods() throws ClassNotFoundException {
		testImplImplementsAllMethods("org.sagacity.sqltoy.dao.impl.SqlToyDaoImpl");
	}

	/**
	 * 测试 SqlToyDao 接口的所有方法都必须在 SqlToyDaoImpl(Spring Starter 模块) 中实现
	 */
	@Test
	@DisplayName("SqlToyDaoImpl(Spring Starter) 必须实现 SqlToyDao 的所有方法")
	public void testSpringStarterSqlToyDaoImplImplementsAllMethods() throws ClassNotFoundException {
		testImplImplementsAllMethods("org.sagacity.sqltoy.dao.impl.SqlToyDaoImpl");
	}

	/**
	 * 测试 SqlToyDao 接口的所有方法都必须在 SqlToyDaoImpl(Solon 模块) 中实现
	 */
	@Test
	@DisplayName("SqlToyDaoImpl(Solon) 必须实现 SqlToyDao 的所有方法")
	public void testSolonSqlToyDaoImplImplementsAllMethods() throws ClassNotFoundException {
		testImplImplementsAllMethods("org.sagacity.sqltoy.solon.dao.impl.SqlToyDaoImpl");
	}

	/**
	 * 通用的实现类方法检查
	 */
	private void testImplImplementsAllMethods(String implClassName) throws ClassNotFoundException {
		// 获取 SqlToyDao 接口的所有方法
		Class<?> sqlToyDaoInterface = SqlToyDao.class;
		Method[] interfaceMethods = sqlToyDaoInterface.getDeclaredMethods();

		// 获取实现类的所有方法
		Class<?> implClass = Class.forName(implClassName);
		Method[] implMethods = implClass.getDeclaredMethods();

		// 创建实现类方法的签名集合
		Set<String> implMethodSignatures = new HashSet<>();
		for (Method method : implMethods) {
			implMethodSignatures.add(getMethodSignature(method));
		}

		// 检查每个接口方法是否都有实现
		List<String> missingMethods = new ArrayList<>();
		for (Method interfaceMethod : interfaceMethods) {
			String signature = getMethodSignature(interfaceMethod);
			if (!implMethodSignatures.contains(signature)) {
				missingMethods.add(signature);
			}
		}

		// 断言：所有接口方法都必须有实现
		Assertions.assertTrue(missingMethods.isEmpty(),
				implClassName + " 缺少以下方法的实现:\n" + String.join("\n", missingMethods));
	}

	/**
	 * 获取方法的唯一签名（包括方法名和参数类型）
	 */
	private String getMethodSignature(Method method) {
		StringBuilder signature = new StringBuilder();
		signature.append(method.getReturnType().getName()).append("#").append(method.getName()).append("(");

		Class<?>[] paramTypes = method.getParameterTypes();
		for (int i = 0; i < paramTypes.length; i++) {
			if (i > 0) {
				signature.append(",");
			}
			signature.append(paramTypes[i].getName());
		}
		signature.append(")");

		return signature.toString();
	}

	/**
	 * 测试 SqlToyDao 接口方法命名规范
	 */
	@Test
	@DisplayName("SqlToyDao 方法命名应符合规范")
	public void testMethodNameConventions() {
		Method[] methods = SqlToyDao.class.getDeclaredMethods();

		List<String> nonStandardMethods = new ArrayList<>();

		for (Method method : methods) {
			String methodName = method.getName();

			// 检查是否符合常见的命名模式
			boolean isStandard = methodName.startsWith("findOne") || methodName.startsWith("findAll")
					|| methodName.startsWith("findList") || methodName.startsWith("findTop")
					|| methodName.startsWith("findRandom") || methodName.startsWith("findPage")
					|| methodName.startsWith("findStream") || methodName.startsWith("get")
					|| methodName.startsWith("set") || methodName.startsWith("is") || methodName.startsWith("save")
					|| methodName.startsWith("update") || methodName.startsWith("delete")
					|| methodName.startsWith("load") || methodName.startsWith("execute")
					|| methodName.startsWith("batch") || methodName.startsWith("convert")
					|| methodName.startsWith("translate") || methodName.startsWith("cache")
					|| methodName.startsWith("generate") || methodName.startsWith("wrap")
					|| methodName.startsWith("truncate") || methodName.startsWith("flush")
					|| methodName.startsWith("exist") || methodName.equals("count") || methodName.equals("elastic")
					|| methodName.equals("mongo") || methodName.equals("delete") || methodName.equals("update")
					|| methodName.equals("store") || methodName.equals("save") || methodName.equals("query")
					|| methodName.equals("load") || methodName.equals("unique") || methodName.equals("treeTable")
					|| methodName.equals("execute") || methodName.equals("batch") || methodName.equals("tableApi");

			if (!isStandard) {
				nonStandardMethods.add(methodName);
			}
		}

		// 打印非标准方法名称（仅作为参考，不失败）
		if (!nonStandardMethods.isEmpty()) {
			System.out.println("注意：以下方法名称可能不符合命名规范:\n" + String.join("\n", nonStandardMethods));
		}
	}

	/**
	 * 统计 SqlToyDao 接口的方法数量
	 */
	@Test
	@DisplayName("统计 SqlToyDao 接口的方法数量")
	public void testMethodCount() {
		Method[] methods = SqlToyDao.class.getDeclaredMethods();
		System.out.println("SqlToyDao 接口总方法数：" + methods.length);

		// 按前缀分组统计
		Map<String, Long> methodGroups = new HashMap<>();
		for (Method method : methods) {
			String prefix = getMethodPrefix(method.getName());
			methodGroups.merge(prefix, 1L, Long::sum);
		}

		System.out.println("\n方法分组统计:");
		methodGroups.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByKey().reversed())
				.forEach(entry -> System.out.println(String.format("%-20s: %d", entry.getKey(), entry.getValue())));
	}

	/**
	 * 获取方法名称的前缀（用于分组统计）
	 */
	private String getMethodPrefix(String methodName) {
		if (methodName.startsWith("findOne"))
			return "findOne*";
		if (methodName.startsWith("findAll"))
			return "findAll*";
		if (methodName.startsWith("findList"))
			return "findList*";
		if (methodName.startsWith("findTop"))
			return "findTop*";
		if (methodName.startsWith("findRandom"))
			return "findRandom*";
		if (methodName.startsWith("findPage"))
			return "findPage*";
		if (methodName.startsWith("find"))
			return "find*";
		if (methodName.startsWith("save"))
			return "save*";
		if (methodName.startsWith("update"))
			return "update*";
		if (methodName.startsWith("delete"))
			return "delete*";
		if (methodName.startsWith("get"))
			return "get*";
		if (methodName.startsWith("set"))
			return "set*";
		if (methodName.startsWith("execute"))
			return "execute*";
		if (methodName.startsWith("batch"))
			return "batch*";
		if (methodName.startsWith("convert"))
			return "convert*";
		if (methodName.startsWith("translate"))
			return "translate*";
		if (methodName.startsWith("cache"))
			return "cache*";
		if (methodName.startsWith("generate"))
			return "generate*";
		return "other";
	}
}
