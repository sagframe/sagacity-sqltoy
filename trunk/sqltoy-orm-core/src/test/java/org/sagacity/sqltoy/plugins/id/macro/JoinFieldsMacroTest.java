package org.sagacity.sqltoy.plugins.id.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * @project sagacity-sqltoy
 * @description &#64;joinFields 宏的验证:归一规则、default 回退、字段名白名单校验(含注入用例)、
 *              保留字按方言引用、命名参数与位置参数两种写法、以及消费参数后的占位符对齐。
 *              全部走真实入口 SqlConfigParseUtils.processSql。
 *              <p>
 *              注意:字段名刻意避开 staff_name / name / status / sex_type —— 其它测试类会把这些词
 *              put 进全局保留字集合(put 是累加合并语义),用它们做"非保留字"断言会随执行顺序摆动。
 * @author zhongxuchen
 * @version v1.0, Date:2026-10-15
 */
public class JoinFieldsMacroTest {

	@BeforeEach
	void setUp() {
		// put为累加合并语义且保留字集合是全局静态,显式清空建立前置状态(与 ReservedWordsUtilBoundaryTest 同约定)
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("order,desc,level,group");
	}

	@AfterEach
	void tearDown() {
		// 避免全局静态状态影响其他测试类
		ReservedWordsUtil.clear();
	}

	/** 命名参数形态:select 列表由 :fields 决定,为空回退 t.* */
	private static SqlToyResult named(Object fieldsValue, String dialect) {
		return SqlConfigParseUtils.processSql("select @joinFields(:fields,{t.*}) from sys_staff t",
				new String[] { "fields" }, new Object[] { fieldsValue }, dialect);
	}

	private static String sql(Object fieldsValue, String dialect) {
		return named(fieldsValue, dialect).getSql();
	}

	private static String sqlWithDefault(Object fieldsValue, String defaultExpr, String dialect) {
		return SqlConfigParseUtils
				.processSql("select @joinFields(:fields," + defaultExpr + ") from sys_staff t",
						new String[] { "fields" }, new Object[] { fieldsValue }, dialect)
				.getSql();
	}

	/** #[...] 动态段展开后会留下空白,断言前归一 */
	private static String norm(String sql) {
		return sql.replaceAll("\\s+", " ").trim();
	}

	// ==================================================================================
	@Nested
	@DisplayName("归一规则:单串与长度为1的数组按逗号切割,长度>1时元素即字段")
	class Normalization {

		@Test
		@DisplayName("多元素数组:元素即字段,逗号拼接")
		void multiElementArray() {
			assertEquals("select staff_id,staff_code from sys_staff t",
					sql(new String[] { "staff_id", "staff_code" }, "mysql"));
		}

		@Test
		@DisplayName("多元素 List 与数组行为一致")
		void multiElementList() {
			List<String> fields = new ArrayList<>(Arrays.asList("staff_id", "staff_code"));
			assertEquals("select staff_id,staff_code from sys_staff t", sql(fields, "mysql"));
		}

		@Test
		@DisplayName("单个字符串:按逗号切割")
		void singleStringIsSplit() {
			assertEquals("select a,b,c from sys_staff t", sql("a,b,c", "mysql"));
		}

		@Test
		@DisplayName("长度为1的数组:统一按单个字符串切割(页面多选常序列化成 [\"a,b,c\"])")
		void singleElementArrayIsSplit() {
			assertEquals("select a,b,c from sys_staff t", sql(new String[] { "a,b,c" }, "mysql"));
			assertEquals("select a,b,c from sys_staff t", sql(Arrays.asList("a,b,c"), "mysql"));
		}

		@Test
		@DisplayName("长度为1且不含逗号:就是一个字段")
		void singleElementNoComma() {
			assertEquals("select staff_id from sys_staff t", sql(new String[] { "staff_id" }, "mysql"));
		}

		@Test
		@DisplayName("长度>1 时元素含逗号必须报错,不能静默拆成三个字段")
		void multiElementWithEmbeddedCommaFails() {
			// ["a,b","c"] 若被静默拆成 a、b、c,调用方的错误就被掩盖了
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { "a,b", "c" }, "mysql"));
		}

		@Test
		@DisplayName("空白段跳过:页面多选序列化常产生尾逗号")
		void blankFragmentsAreSkipped() {
			assertEquals("select a,b from sys_staff t", sql("a,,b", "mysql"));
			assertEquals("select a,b from sys_staff t", sql("a,b,", "mysql"));
			assertEquals("select a,b from sys_staff t", sql(" a , b ", "mysql"));
		}

		@Test
		@DisplayName("字面量形态:用 {} 包裹规避宏参数切割")
		void literalFieldList() {
			assertEquals("select a,b,c from sys_staff t where t.status=?",
					norm(SqlConfigParseUtils
							.processSql("select @joinFields({a,b,c}) from sys_staff t where t.status=?", null,
									new Object[] { 1 }, "mysql")
							.getSql()));
		}

		@Test
		@DisplayName("约束:sql 完全无参数时提前返回,宏不展开(既有框架行为)")
		void noParamAtAllIsNotExpanded() {
			String result = SqlConfigParseUtils
					.processSql("select @joinFields({a,b,c}) from sys_staff t", null, null, "mysql").getSql();
			assertTrue(result.contains("@joinFields"), "无任何参数时不会进入处理链:" + result);
		}
	}

	// ==================================================================================
	@Nested
	@DisplayName("default 回退")
	class DefaultFallback {

		@Test
		@DisplayName("fields 为 null 时输出 default")
		void nullFieldsUsesDefault() {
			assertEquals("select t.* from sys_staff t", sql(null, "mysql"));
		}

		@Test
		@DisplayName("空数组/空集合/空串都走 default")
		void emptyFieldsUseDefault() {
			assertEquals("select t.* from sys_staff t", sql(new String[0], "mysql"));
			assertEquals("select t.* from sys_staff t", sql(new ArrayList<String>(), "mysql"));
			assertEquals("select t.* from sys_staff t", sql("", "mysql"));
			assertEquals("select t.* from sys_staff t", sql("   ", "mysql"));
		}

		@Test
		@DisplayName("default 本身含逗号:引号或 {} 包裹后正确剥离")
		void defaultContainingComma() {
			assertEquals("select a.id,a.code from sys_staff t", sqlWithDefault(null, "'a.id,a.code'", "mysql"));
			assertEquals("select a.id,a.code from sys_staff t", sqlWithDefault(null, "{a.id,a.code}", "mysql"));
			assertEquals("select a.id,a.code from sys_staff t", sqlWithDefault(null, "\"a.id,a.code\"", "mysql"));
		}

		@Test
		@DisplayName("default 含括号也能正确配对(正则截断会在这里出错)")
		void defaultContainingParenthesis() {
			assertEquals("select sum(t.amount) from sys_staff t", sqlWithDefault(null, "{sum(t.amount)}", "mysql"));
		}

		@Test
		@DisplayName("default 是可信的 sql 文本:原样输出,不做标识符校验")
		void defaultIsNotValidated() {
			assertEquals("select t.* from sys_staff t", sqlWithDefault(null, "{t.*}", "mysql"));
		}

		@Test
		@DisplayName("未提供 default 且 fields 为空:输出空串")
		void noDefaultYieldsEmpty() {
			String result = SqlConfigParseUtils
					.processSql("select @joinFields(:fields) from sys_staff t", new String[] { "fields" },
							new Object[] { null }, "mysql")
					.getSql();
			assertEquals("select  from sys_staff t", result);
		}

		@Test
		@DisplayName("fields 有值时 default 不生效")
		void defaultIgnoredWhenFieldsPresent() {
			assertEquals("select a,b from sys_staff t", sqlWithDefault(new String[] { "a", "b" }, "{t.*}", "mysql"));
		}

		@Test
		@DisplayName("显式写 null 字面量也走 default")
		void explicitNullLiteral() {
			assertEquals("select t.* from sys_staff t where t.status=?",
					norm(SqlConfigParseUtils
							.processSql("select @joinFields(null,{t.*}) from sys_staff t where t.status=?", null,
									new Object[] { 1 }, "mysql")
							.getSql()));
		}
	}

	// ==================================================================================
	@Nested
	@DisplayName("字段名白名单校验(安全边界)")
	class Validation {

		@Test
		@DisplayName("合法:字母、数字、下划线、中文")
		void legalIdentifiers() {
			assertEquals("select staff_id,staffCode2 from sys_staff t",
					sql(new String[] { "staff_id", "staffCode2" }, "mysql"));
			assertEquals("select 员工编号,员工姓名 from sys_staff t",
					sql(new String[] { "员工编号", "员工姓名" }, "mysql"));
		}

		@Test
		@DisplayName("合法:alias.column 与 schema.table.column(最多三段)")
		void qualifiedIdentifiers() {
			assertEquals("select t.staff_id,hr.sys_staff.staff_code from sys_staff t",
					sql(new String[] { "t.staff_id", "hr.sys_staff.staff_code" }, "mysql"));
		}

		@Test
		@DisplayName("非法:四段点号限定")
		void tooManySegments() {
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { "a.b.c.d" }, "mysql"));
		}

		@Test
		@DisplayName("非法:数字或点号开头")
		void illegalStartCharacter() {
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { "2col" }, "mysql"));
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { ".col" }, "mysql"));
		}

		@Test
		@DisplayName("注入:闭合括号后接新语句")
		void injectionWithStatementBreak() {
			assertThrows(IllegalArgumentException.class,
					() -> sql(new String[] { "a) from users; drop table sys_staff--" }, "mysql"));
			assertThrows(IllegalArgumentException.class,
					() -> sql("a,b) union select password from admin--", "mysql"));
		}

		@Test
		@DisplayName("注入:引号、反引号、方括号、分号、注释符、反斜杠、空白")
		void injectionCharacters() {
			for (String bad : new String[] { "a'", "a\"", "a`", "a[", "a]", "a;", "a--", "a/*", "a\\", "a b",
					"a\tb" }) {
				assertThrows(IllegalArgumentException.class, () -> sql(new String[] { bad }, "mysql"),
						"应当拒绝:[" + bad + "]");
			}
		}

		@Test
		@DisplayName("排除 $ 与 #:与 sqltoy 自身的 #[] 动态段和 ${}/#{} 参数占位冲突")
		void dollarAndHashAreRejected() {
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { "a$b" }, "mysql"));
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { "a#b" }, "mysql"));
		}

		@Test
		@DisplayName("PG 的 field::type 强转不在受理范围:属表达式而非标识符")
		void pgCastIsRejected() {
			// :: 是 postgresql 专有强转语法,sqltoy 内部做类型转换一律用标准 cast(? as TYPE);
			// 且 : 是命名参数前缀,放开它会与 :paramName 语法产生歧义
			assertThrows(IllegalArgumentException.class,
					() -> sql(new String[] { "amount::numeric" }, "postgresql"));
			assertThrows(IllegalArgumentException.class,
					() -> sql("create_time::date,organ_id", "postgresql"));
			// 标准 cast() 同样不受理(含括号),表达式一律走 @value
			assertThrows(IllegalArgumentException.class,
					() -> sql(new String[] { "cast(amount as numeric)" }, "postgresql"));
		}

		@Test
		@DisplayName("表达式与别名不在受理范围:异常信息要指明改用 @value")
		void expressionsAreRejected() {
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
					() -> sql(new String[] { "sum(amount)" }, "mysql"));
			assertTrue(e.getMessage().contains("@value"), e.getMessage());
			assertTrue(e.getMessage().contains("sum(amount)"), "异常应带上具体非法值:" + e.getMessage());
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { "col as alias" }, "mysql"));
			assertThrows(IllegalArgumentException.class, () -> sql(new String[] { "*" }, "mysql"));
		}

		@Test
		@DisplayName("数组元素为 null 时跳过,不影响其它字段")
		void nullElementsSkippedInMultiElementArray() {
			assertEquals("select a,b from sys_staff t", sql(new String[] { "a", null, "b" }, "mysql"));
		}
	}

	// ==================================================================================
	@Nested
	@DisplayName("保留字按方言加引用符,非保留字保持裸标识符")
	class ReservedWordQuoting {

		@Test
		@DisplayName("mysql 系用反引号")
		void mysqlBackQuote() {
			assertEquals("select `order`,`desc`,staff_id from sys_staff t",
					sql(new String[] { "order", "desc", "staff_id" }, "mysql"));
		}

		@Test
		@DisplayName("oracle / postgresql 用双引号")
		void oracleAndPgDoubleQuote() {
			assertEquals("select \"order\",\"desc\",staff_id from sys_staff t",
					sql(new String[] { "order", "desc", "staff_id" }, "oracle"));
			assertEquals("select \"order\",\"desc\",staff_id from sys_staff t",
					sql(new String[] { "order", "desc", "staff_id" }, "postgresql"));
		}

		@Test
		@DisplayName("sqlserver 用方括号")
		void sqlServerBrackets() {
			assertEquals("select [order],[desc],staff_id from sys_staff t",
					sql(new String[] { "order", "desc", "staff_id" }, "sqlserver"));
		}

		@Test
		@DisplayName("非保留字不加引号:避免 oracle/pg 上引号引入的大小写敏感问题")
		void nonReservedStaysBare() {
			assertEquals("select staff_id,staff_code from sys_staff t",
					sql(new String[] { "staff_id", "staff_code" }, "oracle"));
		}

		@Test
		@DisplayName("保留字大小写不敏感")
		void reservedWordCaseInsensitive() {
			assertEquals("select `ORDER` from sys_staff t", sql(new String[] { "ORDER" }, "mysql"));
		}

		@Test
		@DisplayName("限定名逐段处理:t.order 的列段也要加引用符")
		void qualifiedReservedWord() {
			assertEquals("select t.`order` from sys_staff t", sql(new String[] { "t.order" }, "mysql"));
			assertEquals("select `group`.staff_id from sys_staff t",
					sql(new String[] { "group.staff_id" }, "mysql"));
		}
	}

	// ==================================================================================
	@Nested
	@DisplayName("两种写法与参数对齐")
	class ParameterAlignment {

		@Test
		@DisplayName("命名参数::fields 转成 ? 后被消费,不再产生占位符")
		void namedParamIsConsumed() {
			SqlToyResult result = SqlConfigParseUtils.processSql(
					"select @joinFields(:fields,{t.*}) from sys_staff t #[where t.status=:status]",
					new String[] { "fields", "status" }, new Object[] { new String[] { "staff_id", "staff_code" }, 1 },
					"mysql");
			assertEquals("select staff_id,staff_code from sys_staff t where t.status=?", norm(result.getSql()));
			assertEquals(1, result.getParamsValue().length, "参数个数:" + Arrays.toString(result.getParamsValue()));
			assertEquals(1, result.getParamsValue()[0]);
		}

		@Test
		@DisplayName("位置参数:sql 里直写 @joinFields(?,',') 与命名参数形态等价")
		void positionalParamForm() {
			SqlToyResult result = SqlConfigParseUtils.processSql(
					"select @joinFields(?,{t.*}) from sys_staff t where t.status=?", null,
					new Object[] { new String[] { "staff_id", "staff_code" }, 1 }, "mysql");
			assertEquals("select staff_id,staff_code from sys_staff t where t.status=?", norm(result.getSql()));
			assertEquals(1, result.getParamsValue().length, "参数个数:" + Arrays.toString(result.getParamsValue()));
			assertEquals(1, result.getParamsValue()[0]);
		}

		@Test
		@DisplayName("位置参数在中间:前后其它 ? 的对位不受影响")
		void positionalParamInMiddle() {
			SqlToyResult result = SqlConfigParseUtils.processSql(
					"select @joinFields(?,{t.*}) from sys_staff t where t.status=? and t.organ_id=?", null,
					new Object[] { "staff_id,staff_code", 1, "100005" }, "mysql");
			assertEquals("select staff_id,staff_code from sys_staff t where t.status=? and t.organ_id=?",
					norm(result.getSql()));
			assertEquals(2, result.getParamsValue().length, Arrays.toString(result.getParamsValue()));
			assertEquals(1, result.getParamsValue()[0]);
			assertEquals("100005", result.getParamsValue()[1]);
		}

		@Test
		@DisplayName("多个 @joinFields:逐个消费参数且对位正确")
		void multipleMacros() {
			SqlToyResult result = SqlConfigParseUtils.processSql(
					"select @joinFields(?,{t.*}),count(1) total from sys_staff t"
							+ " group by @joinFields(?,{t.organ_id}) where t.status=?",
					null, new Object[] { "organ_id,dept_id", "organ_id,dept_id", 1 }, "postgresql");
			assertEquals("select organ_id,dept_id,count(1) total from sys_staff t"
					+ " group by organ_id,dept_id where t.status=?", norm(result.getSql()));
			assertEquals(1, result.getParamsValue().length, Arrays.toString(result.getParamsValue()));
			assertEquals(1, result.getParamsValue()[0]);
		}

		@Test
		@DisplayName("fields 为空走 default 时同样不产生占位符")
		void consumedParamWithDefaultIsNotBound() {
			SqlToyResult result = SqlConfigParseUtils.processSql(
					"select @joinFields(:fields,{t.*}) from sys_staff t #[where t.status=:status]",
					new String[] { "fields", "status" }, new Object[] { null, 1 }, "mysql");
			assertEquals("select t.* from sys_staff t where t.status=?", norm(result.getSql()));
			assertEquals(1, result.getParamsValue().length);
		}

		@Test
		@DisplayName("group by / order by 场景")
		void groupByAndOrderBy() {
			SqlToyResult result = SqlConfigParseUtils.processSql(
					"select @joinFields(:cols,{t.*}),count(1) total from sys_staff t"
							+ " group by @joinFields(:cols,{t.organ_id}) order by @joinFields(:cols,{t.organ_id})",
					new String[] { "cols" }, new Object[] { "organ_id,dept_id" }, "postgresql");
			assertEquals("select organ_id,dept_id,count(1) total from sys_staff t"
					+ " group by organ_id,dept_id order by organ_id,dept_id", norm(result.getSql()));
		}

		@Test
		@DisplayName("宏名大小写不敏感")
		void macroNameCaseInsensitive() {
			assertEquals("select a,b from sys_staff t", SqlConfigParseUtils
					.processSql("select @JOINFIELDS(:fields) from sys_staff t", new String[] { "fields" },
							new Object[] { "a,b" }, "mysql")
					.getSql());
		}

		@Test
		@DisplayName("非法字段名在端到端路径上同样抛出")
		void illegalFieldPropagates() {
			assertThrows(IllegalArgumentException.class, () -> SqlConfigParseUtils.processSql(
					"select @joinFields(:fields,{t.*}) from sys_staff t", new String[] { "fields" },
					new Object[] { "a; drop table sys_staff" }, "mysql"));
		}

		@Test
		@DisplayName("参数个数不足时给出明确指引,而不是数组越界")
		void missingParamGivesClearError() {
			// @joinFields 占的是第 2 个 ? 的位置,但只传了 1 个参数
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
					() -> SqlConfigParseUtils.processSql(
							"select * from sys_staff t where t.status=? and @joinFields(?,{t.*}) is not null", null,
							new Object[] { 1 }, "mysql"));
			assertTrue(e.getMessage().contains("@joinFields"), e.getMessage());
			assertTrue(e.getMessage().contains("index"), e.getMessage());
		}
	}
}
