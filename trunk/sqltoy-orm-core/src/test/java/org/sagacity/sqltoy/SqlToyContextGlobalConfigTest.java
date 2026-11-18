package org.sagacity.sqltoy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.function.IFunction;

/**
 * 回归:JVM级静态配置(functionConverts/keywordSign)必须"initialize阶段首占生效"。
 * 修复前两个setter即时写FunctionUtils/SqlToyConstants的JVM全局值:多上下文时后配置者静默覆盖
 * 先初始化上下文的语义(last-wins且无告警),测试套件里也大量直接改全局转换器,极易随类序翻转
 */
public class SqlToyContextGlobalConfigTest {

	private List<IFunction> originConverts;

	private String originKeywordSign;

	@SuppressWarnings("unchecked")
	private static List<IFunction> functionConverts() throws Exception {
		Field field = FunctionUtils.class.getDeclaredField("functionConverts");
		field.setAccessible(true);
		return (List<IFunction>) field.get(null);
	}

	private static void setFunctionConverts(List<IFunction> converts) throws Exception {
		Field field = FunctionUtils.class.getDeclaredField("functionConverts");
		field.setAccessible(true);
		field.set(null, converts);
	}

	@SuppressWarnings("unchecked")
	private static Set<String> staticStateOwners() throws Exception {
		Field field = SqlToyContext.class.getDeclaredField("STATIC_STATE_OWNERS");
		field.setAccessible(true);
		return (Set<String>) field.get(null);
	}

	@BeforeEach
	public void setUp() throws Exception {
		originConverts = functionConverts();
		originKeywordSign = SqlToyConstants.keywordSign;
		// 隔离:清空全局转换器与首占登记,保证断言不依赖其它测试的执行顺序
		setFunctionConverts(new ArrayList<IFunction>());
		staticStateOwners().clear();
	}

	@AfterEach
	public void tearDown() throws Exception {
		setFunctionConverts(originConverts);
		SqlToyConstants.keywordSign = originKeywordSign;
		staticStateOwners().clear();
	}

	private static String convert(String sql, String dialect) {
		return FunctionUtils.getDialectSql(sql, dialect);
	}

	@Test
	public void functionConvertsSetterDefersUntilInitializeStage() {
		SqlToyContext context = new SqlToyContext();
		context.setFunctionConverts("default");
		// 修复前:setter即时写全局,此处置换已生效
		assertFalse(convert("select isnull(name,'-') from t", "mysql").contains("ifnull"),
				"setFunctionConverts不得在setter阶段直接改JVM全局");
		context.applyJvmStaticConfig();
		assertTrue(convert("select isnull(name,'-') from t", "mysql").contains("ifnull"),
				"initialize阶段应完成函数转换器注册");
	}

	@Test
	public void laterContextConfigMustNotOverrideFirst() {
		SqlToyContext first = new SqlToyContext();
		first.setFunctionConverts("default");
		first.applyJvmStaticConfig();
		SqlToyContext second = new SqlToyContext();
		// 第二个上下文只注册Decode:若覆盖全局,isnull->ifnull转换会消失
		second.setFunctionConverts(
				Arrays.asList("org.sagacity.sqltoy.plugins.function.impl.Decode"));
		second.applyJvmStaticConfig();
		assertTrue(convert("select isnull(name,'-') from t", "mysql").contains("ifnull"),
				"先初始化上下文的函数转换器配置不得被后初始化上下文覆盖");
	}

	@Test
	public void keywordSignFirstWins() {
		SqlToyContext first = new SqlToyContext();
		first.setKeywordSign("`");
		first.applyJvmStaticConfig();
		SqlToyContext second = new SqlToyContext();
		second.setKeywordSign("\"");
		second.applyJvmStaticConfig();
		assertEquals("`", SqlToyConstants.keywordSign, "先初始化上下文的关键字引用符号不得被覆盖");
	}

	@Test
	public void keywordSignSetterDefersUntilInitializeStage() {
		SqlToyContext context = new SqlToyContext();
		context.setKeywordSign("`");
		assertEquals(originKeywordSign, SqlToyConstants.keywordSign,
				"setKeywordSign不得在setter阶段直接改JVM全局");
		context.applyJvmStaticConfig();
		assertEquals("`", SqlToyConstants.keywordSign);
	}

	@Test
	public void unconfiguredContextMustNotClaim() {
		// 未携带配置的上下文不得认领key,否则会永久挡住后续真正配置的上下文
		new SqlToyContext().applyJvmStaticConfig();
		SqlToyContext configured = new SqlToyContext();
		configured.setFunctionConverts("default");
		configured.applyJvmStaticConfig();
		assertTrue(convert("select isnull(name,'-') from t", "mysql").contains("ifnull"),
				"未配置的上下文不得占用JVM级配置的首占权");
	}

	@Test
	public void closeMarkerDisablesAndClaims() {
		SqlToyContext close = new SqlToyContext();
		close.setFunctionConverts("close");
		close.applyJvmStaticConfig();
		// close表示关闭转换:不注册转换器,且已认领,后配置的上下文被忽略
		SqlToyContext later = new SqlToyContext();
		later.setFunctionConverts("default");
		later.applyJvmStaticConfig();
		assertFalse(convert("select isnull(name,'-') from t", "mysql").contains("ifnull"),
				"close应保持转换关闭,且后续上下文的配置被首占策略忽略");
	}
}
