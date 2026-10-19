package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.model.Page;

/**
 * @description lambda缺陷回归测试:覆盖无条件DML守卫、组合查询通道与参数续号、嵌套exists、
 *              OuterRef别名限定、组合参数冲突、排序重复合并、like通配符、union根/分支排序包装、
 *              exists别名稳定性、单表countSql守卫以及更新/删除的输入校验。
 *              用例编号P0/P1/P2/P3与缺陷清单一一对应,后续改动不得回退这些行为。
 * @author zhongxuchen
 */
@DisplayName("lambda缺陷回归测试")
public class LambdaDefectRegressionTest {

	/**
	 * 恒等元信息:属性名转大写列名;helper字段模拟无@Column的非映射属性
	 */
	private static final Function<Class<?>, EntityMeta> META = entityClass -> {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName(entityClass.getSimpleName().toLowerCase(Locale.ROOT));
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<>();
		List<String> fields = new ArrayList<>();
		for (Field field : entityClass.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			if ("helper".equals(field.getName())) {
				continue;
			}
			FieldMeta fieldMeta = new FieldMeta();
			fieldMeta.setFieldName(field.getName());
			fieldMeta.setColumnName(field.getName().toUpperCase(Locale.ROOT));
			fieldsMeta.put(field.getName().toLowerCase(Locale.ROOT), fieldMeta);
			fields.add(field.getName());
		}
		entityMeta.setFieldsMeta(fieldsMeta);
		entityMeta.setFieldsArray(fields.toArray(new String[0]));
		entityMeta.setIdArray(new String[] { "id" });
		return entityMeta;
	};

	private static String norm(String sql) {
		return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
	}

	private static int count(String text, String token) {
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(token, index)) >= 0) {
			count++;
			index += token.length();
		}
		return count;
	}

	// ==================== 测试实体 ====================

	public static class DefUser {
		private Integer id;
		private String name;
		private Integer status;
		private Integer deptId;

		public Integer getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public Integer getStatus() {
			return status;
		}

		public Integer getDeptId() {
			return deptId;
		}
	}

	public static class DefDept {
		private Integer deptId;
		private String deptName;
		private Integer status;

		public Integer getDeptId() {
			return deptId;
		}

		public String getDeptName() {
			return deptName;
		}

		public Integer getStatus() {
			return status;
		}
	}

	public static class DefUserVO {
		private String name;
		private String deptName;

		public String getName() {
			return name;
		}

		public String getDeptName() {
			return deptName;
		}
	}

	public static class DefHelperDept {
		private Integer deptId;
		private String helper;

		public Integer getDeptId() {
			return deptId;
		}

		public String getHelper() {
			return helper;
		}
	}

	// ==================== P0 空条件守卫(H2真实执行) ====================

	private static final String URL = "jdbc:h2:mem:lambdadefect;DB_CLOSE_DELAY=-1";
	private static LightDao defectDao;

	@Entity(tableName = "reg_guard_user")
	public static class RegUser implements Serializable {
		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "ID")
		private Integer id;

		@Column(name = "NAME")
		private String name;

		@Column(name = "STATUS")
		private Integer status;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getStatus() {
			return status;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}
	}

	@BeforeAll
	public static void initDefectDb() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists reg_guard_user");
			st.execute("create table reg_guard_user (id int primary key, name varchar(50), status int)");
		}
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL(URL);
		ds.setUser("sa");
		SqlToyContext context = new SqlToyContext();
		context.setDefaultDataSource(ds);
		context.initialize();
		defectDao = new DefaultLightDaoImpl(context);
	}

	private static void resetGuardRows() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("delete from reg_guard_user");
			st.execute("insert into reg_guard_user values (1,'a',1)");
			st.execute("insert into reg_guard_user values (2,'b',0)");
			st.execute("insert into reg_guard_user values (3,'c',1)");
		}
	}

	@Test
	@DisplayName("P0-1 删除:条件被值过滤后仅剩AND,必须按无条件拒绝")
	public void p0DeleteWithOnlyConnectorMustBeRejected() {
		LambdaDelete<DefUser> delete = LambdaDelete.create(DefUser.class)
				.and(true, w -> w.eq(DefUser::getStatus, null));
		delete.assemble(META);
		assertAll(
				() -> assertFalse(delete.hasCondition(),
						"组内条件全部被过滤后只剩连接符AND,hasCondition()应为false"),
				() -> assertThrows(DataAccessException.class, () -> LambdaConvert.toDeleteQuery(delete),
						"只剩连接符时必须拒绝按条件删除,否则会退化为全表删除"));
	}

	@Test
	@DisplayName("P0-2 更新:条件被值过滤后仅剩AND,必须按无条件拒绝")
	public void p0UpdateWithOnlyConnectorMustBeRejected() {
		LambdaUpdate<DefUser> update = LambdaUpdate.create(DefUser.class).set(DefUser::getStatus, 9)
				.and(true, w -> w.eq(DefUser::getName, null));
		update.assemble(META);
		assertAll(
				() -> assertFalse(update.hasCondition(),
						"组内条件全部被过滤后只剩连接符AND,hasCondition()应为false"),
				() -> assertThrows(DataAccessException.class, () -> LambdaConvert.toEntityUpdate(update),
						"只剩连接符时必须拒绝按条件更新,否则会退化为全表更新"));
	}

	@Test
	@DisplayName("P0-3 join删除:条件为空时不得用1=1的EXISTS改写")
	public void p0JoinDeleteWithOnlyConnectorMustBeRejected() {
		LambdaDelete<DefUser> delete = LambdaDelete.create(DefUser.class).leftJoin(DefDept.class)
				.on(DefUser::getDeptId, DefDept::getDeptId)
				.and(true, w -> w.eq(DefDept::getDeptName, null));
		delete.assemble(META);
		assertAll(
				() -> assertThrows(DataAccessException.class, () -> delete.requireCondition("delete"),
						"join删除空条件必须拒绝"),
				() -> assertThrows(DataAccessException.class, delete::getDeleteSqls,
						"直接生成join删除SQL时同样必须拒绝空条件,不得退化为1=1"));
	}

	@Test
	@DisplayName("P0-4 H2真实执行:只剩AND的删除不得删除任何行")
	public void p0DeleteAllMustNotHappen() throws Exception {
		resetGuardRows();
		long deleted = 0;
		Throwable error = null;
		try {
			deleted = defectDao.lambdaDelete(RegUser.class)
					.and(true, w -> w.eq(RegUser::getStatus, null)).delete();
		} catch (Throwable t) {
			error = t;
		}
		long remaining = defectDao.lambdaQuery(RegUser.class).count();
		long finalDeleted = deleted;
		assertTrue(error instanceof DataAccessException || finalDeleted == 0,
				"空条件删除必须抛异常或删除0行,实际 deleted=" + deleted + ", remaining=" + remaining);
	}

	@Test
	@DisplayName("P0-5 H2真实执行:只剩AND的更新不得更新任何行")
	public void p0UpdateAllMustNotHappen() throws Exception {
		resetGuardRows();
		long updated = 0;
		Throwable error = null;
		try {
			updated = defectDao.lambdaUpdate(RegUser.class).set(RegUser::getStatus, 99)
					.and(true, w -> w.eq(RegUser::getName, null)).update();
		} catch (Throwable t) {
			error = t;
		}
		long changed = defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 99).count();
		long finalUpdated = updated;
		assertTrue(error instanceof DataAccessException || finalUpdated == 0,
				"空条件更新必须抛异常或更新0行,实际 updated=" + updated + ", status99=" + changed);
	}

	// ==================== P1 组合查询与子查询 ====================

	@Test
	@DisplayName("P1-1 单表查询union:两个分支都必须渲染完整SELECT且可执行")
	public void p1UnionOfPlainQueriesMustRenderSelects() throws Exception {
		LambdaQuery<DefUser> q1 = LambdaQuery.create(DefUser.class).eq(DefUser::getStatus, 1);
		LambdaQuery<DefUser> q2 = LambdaQuery.create(DefUser.class).eq(DefUser::getStatus, 2);
		q1.unionAll(q2);
		q1.assemble(META);
		String sql = norm(q1.getSql());
		assertAll(
				() -> assertEquals(2, count(sql, "SELECT"),
						"union两个分支各应有一个SELECT,实际SQL=" + sql),
				() -> assertTrue(sql.contains("FROM"),
						"union分支必须是完整SELECT..FROM,实际SQL=" + sql));
		// H2真实执行:status=1两行 + status=0一行
		resetGuardRows();
		List<RegUser> rows = defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 1)
				.unionAll(defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 0)).find();
		assertEquals(3, rows.size(), "单表unionAll应可执行并返回两个分支的并集");
	}

	@Test
	@DisplayName("P1-2 with未fromCte:WITH之后仍必须是一条SELECT且可执行")
	public void p1WithMustRenderSelect() throws Exception {
		LambdaQuery<DefDept> inner = LambdaQuery.create(DefDept.class).from(DefDept.class)
				.select(JoinCol.of(DefDept::getDeptId).as("deptId")).eq(DefDept::getStatus, 1);
		LambdaQuery<DefUser> outer = LambdaQuery.create(DefUser.class).with("d", inner)
				.eq(DefUser::getStatus, 1);
		outer.assemble(META);
		String sql = norm(outer.getSql());
		assertTrue(sql.contains(") SELECT "), "WITH之后必须是完整SELECT而非裸条件,实际SQL=" + sql);
		// H2真实执行:CTE定义存在,外层按主表过滤(status=0一行)
		resetGuardRows();
		LambdaQuery<RegUser> cte = defectDao.lambdaQuery(RegUser.class)
				.select(JoinCol.of(RegUser::getId).as("id")).eq(RegUser::getStatus, 1);
		List<RegUser> rows = defectDao.lambdaQuery(RegUser.class).with("d", cte)
				.eq(RegUser::getStatus, 0).find();
		assertEquals(1, rows.size(), "with组合SQL应可执行");
	}

	@Test
	@DisplayName("P1-3 嵌套and/or/nested内的exists不得抛NPE")
	public void p1NestedExistsMustWork() {
		LambdaQuery<DefUser> query = LambdaQuery.create(DefUser.class)
				.and(w -> w.exists(DefDept.class, sub -> sub.eq(DefDept::getStatus, 1)));
		assertAll(
				() -> assertDoesNotThrow(() -> query.assemble(META),
						"嵌套分组中的exists装配不应抛异常"),
				() -> assertTrue(norm(query.getSqlSegment()).contains("EXISTS"),
						"嵌套分组中的exists应正常渲染,实际条件=" + norm(query.getSqlSegment())));
	}

	@Test
	@DisplayName("P1-4 递归CTE中的union分支序号必须回写,外层条件不得撞号")
	public void p1OuterConditionAfterUnionMustNotReuseParamName() {
		LambdaQuery<DefUser> base = LambdaQuery.create(DefUser.class).from(DefUser.class)
				.select(JoinCol.of(DefUser::getId).as("id")).eq(DefUser::getStatus, 1);
		LambdaQuery<DefUser> recursive = LambdaQuery.create(DefUser.class).from(DefUser.class)
				.select(JoinCol.of(DefUser::getId).as("id")).eq(DefUser::getStatus, 2);
		LambdaQuery<DefUser> inner = base.unionAll(recursive);
		LambdaQuery<DefUser> outer = LambdaQuery.create(DefUser.class)
				.withRecursive("tree", inner, "id").fromCte("tree").eq(DefUser::getId, 5);
		outer.assemble(META);
		Map<String, Object> params = outer.getParams();
		String sql = norm(outer.getSql());
		assertAll(
				() -> assertEquals(Integer.valueOf(2), params.get("p2"),
						"union递归分支的p2应保持为2,实际SQL=" + sql + ", params=" + params),
				() -> assertEquals(Integer.valueOf(5), params.get("p3"),
						"外层新增条件应续号为p3,实际SQL=" + sql + ", params=" + params));
	}

	@Test
	@DisplayName("P1-5 无join的SQL通道中OuterRef必须使用表别名")
	public void p1OuterRefMustUseAliasInSqlChannel() {
		LambdaQuery<DefUser> query = LambdaQuery.create(DefUser.class)
				.select(JoinCol.count(DefUser::getId).as("cnt")).from(DefUser.class)
				.exists(DefDept.class, sub -> sub.eq(DefDept::getDeptId, OuterRef.of(DefUser::getDeptId)));
		query.assemble(META);
		String sql = norm(query.getSql());
		assertAll(
				() -> assertTrue(sql.contains("FROM defuser d1"),
						"外层表应带别名渲染,实际SQL=" + sql),
				() -> assertFalse(sql.contains("defuser.DEPTID"),
						"外层表已取别名d1,OuterRef不得再引用原表名,实际SQL=" + sql),
				() -> assertTrue(sql.contains("d1.DEPTID"),
						"OuterRef应渲染为d1.DEPTID,实际SQL=" + sql));
	}

	// ==================== P2 参数、渲染与解析 ====================

	@Test
	@DisplayName("P2-1 exists子查询内的非映射属性必须快速失败")
	public void p2SubQueryUnmappedPropertyMustFailFast() {
		LambdaQuery<DefUser> query = LambdaQuery.create(DefUser.class)
				.exists(DefHelperDept.class, sub -> sub.eq(DefHelperDept::getHelper, "x"));
		assertThrows(DataAccessException.class, () -> {
			query.assemble(META);
			query.getSqlSegment();
		}, "非映射属性应抛出明确异常,而不是拼出sqN.null");
	}

	@Test
	@DisplayName("P2-2 CTE内外同名外部参数值不同必须冲突报错")
	public void p2ComposedParamCollisionMustFail() {
		Map<String, Object> innerParams = new LinkedHashMap<>();
		innerParams.put("v", 1);
		Map<String, Object> outerParams = new LinkedHashMap<>();
		outerParams.put("v", 2);
		LambdaQuery<DefDept> inner = LambdaQuery.create(DefDept.class).from(DefDept.class)
				.select(JoinCol.of(DefDept::getDeptId).as("deptId"))
				.inSql(DefDept::getDeptId, "select id from t_a where x = :v", innerParams);
		LambdaQuery<DefUserVO> outer = LambdaQuery.create(DefUserVO.class).with("d", inner).fromCte("d")
				.inSql(DefUserVO::getName, "select name from t_b where y = :v", outerParams);
		outer.assemble(META);
		assertThrows(DataAccessException.class, outer::getParams,
				"CTE内外同名外部参数值不同,应在合并参数时响亮失败而不是静默覆盖");
	}

	@Test
	@DisplayName("P2-3 首次渲染后追加orderBy仍只能有一个ORDER BY")
	public void p2OrderByAfterRenderMustStaySingleClause() {
		LambdaQuery<DefUser> query = LambdaQuery.create(DefUser.class).eq(DefUser::getStatus, 1)
				.orderByAsc(DefUser::getId);
		query.assemble(META);
		query.getSqlSegment();
		query.orderByDesc(DefUser::getName);
		query.assemble(META);
		String sql = norm(query.getSqlSegment());
		assertEquals(1, count(sql, "ORDER BY"), "多次追加orderBy应合并为一个ORDER BY,实际SQL=" + sql);
	}

	@Test
	@DisplayName("P2-4 已含%的like值不得再次补%")
	public void p2LikeValueWithWildcardMustNotBeWrappedAgain() {
		LambdaQuery<DefUser> query = LambdaQuery.create(DefUser.class).like(DefUser::getName, "50%");
		query.assemble(META);
		assertEquals("50%", query.getParams().get("p1"),
				"值已包含通配符%时按约定应原样使用、不再二次包装,实际绑定值=" + query.getParams().get("p1"));
	}

	@Test
	@DisplayName("P2-5 like下划线由框架在SQL解析期自动ESCAPE")
	public void p2LikeUnderscoreEscapedByFramework() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("delete from reg_guard_user");
			st.execute("insert into reg_guard_user values (1,'a_b',1)");
			st.execute("insert into reg_guard_user values (2,'axb',1)");
		}
		try {
			List<RegUser> rows = defectDao.lambdaQuery(RegUser.class).like(RegUser::getName, "a_b").find();
			assertEquals(1, rows.size(), "like值中的_应被框架转义为字面量,不能把axb也匹配出来");
			assertEquals("a_b", rows.get(0).getName());
		} finally {
			resetGuardRows();
		}
	}

	// ==================== P3 一致性与输入校验 ====================

	@Test
	@DisplayName("P3-1 单表更新/删除的last()必须被明确拒绝")
	public void p3LastMustBeRejectedBySingleTableUpdateAndDelete() {
		LambdaUpdate<DefUser> update = LambdaUpdate.create(DefUser.class).set(DefUser::getStatus, 1)
				.eq(DefUser::getId, 1).last("limit 1");
		update.assemble(META);
		LambdaDelete<DefUser> delete = LambdaDelete.create(DefUser.class).eq(DefUser::getId, 1)
				.last("limit 1");
		delete.assemble(META);
		assertAll(
				() -> assertThrows(DataAccessException.class, () -> LambdaConvert.toEntityUpdate(update),
						"单表更新通道不支持last(),应像查询通道一样明确拒绝"),
				() -> assertThrows(DataAccessException.class, () -> LambdaConvert.toDeleteQuery(delete),
						"单表删除通道不支持last(),应明确拒绝"));
	}

	@Test
	@DisplayName("P3-2 更新/删除嵌套分组内的groupBy/orderBy必须被拒绝")
	public void p3NestedAggregatePartitionMustBeRejected() {
		LambdaUpdate<DefUser> update = LambdaUpdate.create(DefUser.class).set(DefUser::getStatus, 1)
				.eq(DefUser::getId, 1).nested(w -> w.groupBy(DefUser::getId));
		assertThrows(DataAccessException.class, () -> update.assemble(META),
				"更新不支持分组,嵌套块内的groupBy也必须拒绝,而不是生成(1 = 1 GROUP BY ...)");
		LambdaDelete<DefUser> delete = LambdaDelete.create(DefUser.class).eq(DefUser::getId, 1)
				.nested(w -> w.orderByAsc(DefUser::getId));
		assertThrows(DataAccessException.class, () -> delete.assemble(META),
				"删除不支持排序,嵌套块内的orderBy也必须拒绝");
	}

	@Test
	@DisplayName("P3-3 select(JoinCol)空元素必须快速失败")
	public void p3SelectJoinColNullMustFailFast() {
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(DefUser.class).select((JoinCol) null),
				"select(JoinCol)不接受null元素,应与select(LambdaFn)一致快速失败");
	}

	@Test
	@DisplayName("P3-4 同一更新构建器重复执行不得丢失已物化的set")
	public void p3RepeatedUpdateMustKeepSetFields() throws Exception {
		resetGuardRows();
		LambdaUpdate<RegUser> update = defectDao.lambdaUpdate(RegUser.class).set(RegUser::getStatus, 9)
				.eq(RegUser::getId, 1);
		assertEquals(1L, update.update());
		// 首次执行后setOps已物化清空,第二次执行不得误报"requires at least one field set"
		assertEquals(1L, update.update());
		assertEquals(9, defectDao.lambdaQuery(RegUser.class).eq(RegUser::getId, 1).getOne().getStatus().intValue());
	}

	@Test
	@DisplayName("P3-5 CTE名称/派生表别名必须是普通标识符且不可重复")
	public void p3CompositionIdentifierGuards() {
		LambdaQuery<DefDept> inner = LambdaQuery.create(DefDept.class).from(DefDept.class)
				.select(JoinCol.of(DefDept::getDeptId).as("deptId")).eq(DefDept::getStatus, 1);
		LambdaQuery<DefUser> outer = LambdaQuery.create(DefUser.class).with("d", inner);
		assertAll(
				() -> assertThrows(DataAccessException.class, () -> outer.with("d", inner),
						"重复CTE名称必须拒绝"),
				() -> assertThrows(DataAccessException.class,
						() -> LambdaQuery.create(DefUser.class).with("d;drop table x", inner),
						"非法CTE名称必须拒绝"),
				() -> assertThrows(DataAccessException.class,
						() -> LambdaQuery.create(DefUser.class).union(null),
						"union分支不得为null"),
				() -> assertThrows(DataAccessException.class,
						() -> LambdaQuery.create(DefUser.class).fromFast(inner, "a b"),
						"派生表别名必须是普通标识符"));
	}

	@Test
	@DisplayName("P3-6 exists子查询别名应语句内唯一且SQL文本稳定")
	public void p3ExistsAliasMustBeStable() {
		LambdaQuery<DefUser> first = LambdaQuery.create(DefUser.class)
				.exists(DefDept.class, sub -> sub.eq(DefDept::getStatus, 1))
				.exists(DefDept.class, sub -> sub.eq(DefDept::getDeptId, 2))
				.eq(DefUser::getStatus, 1);
		first.assemble(META);
		String sql = norm(first.getSqlSegment());
		LambdaQuery<DefUser> second = LambdaQuery.create(DefUser.class)
				.exists(DefDept.class, sub -> sub.eq(DefDept::getStatus, 1))
				.exists(DefDept.class, sub -> sub.eq(DefDept::getDeptId, 2))
				.eq(DefUser::getStatus, 1);
		second.assemble(META);
		assertAll(
				() -> assertTrue(sql.contains("FROM defdept sq1"),
						"同一语句内第一个exists应使用sq1,实际SQL=" + sql),
				() -> assertTrue(sql.contains("FROM defdept sq2"),
						"同一语句内第二个exists应使用sq2,实际SQL=" + sql),
				() -> assertEquals(sql, norm(second.getSqlSegment()),
						"结构相同的查询应生成完全一致的SQL(exists别名按语句重新计数)"));
		first.assemble(META);
		assertEquals(sql, norm(first.getSqlSegment()), "重复assemble不得让exists别名漂移");
	}

	@Test
	@DisplayName("P3-7 union根排序/limit移到union之后,分支排序按方言包裹且可执行")
	public void p3UnionOrderingMustBeWrappedAndExecutable() throws Exception {
		LambdaQuery<DefUser> first = LambdaQuery.create(DefUser.class).from(DefUser.class)
				.select(JoinCol.of(DefUser::getId).as("id")).eq(DefUser::getStatus, 1)
				.orderByDesc(DefUser::getId);
		LambdaQuery<DefUser> second = LambdaQuery.create(DefUser.class).from(DefUser.class)
				.select(JoinCol.of(DefUser::getId).as("id")).eq(DefUser::getStatus, 0)
				.orderByAsc(DefUser::getId).last("limit 1");
		first.unionAll(second);
		first.assemble(META);
		String sql = norm(first.getSql());
		assertAll(
				() -> assertTrue(sql.contains(" UNION ALL (SELECT"),
						"自带orderBy/last的分支必须包裹成完整查询表达式,实际SQL=" + sql),
				() -> assertTrue(sql.endsWith("ORDER BY id DESC"),
						"union根排序必须位于union之后且使用输出列,实际SQL=" + sql),
				() -> assertFalse(sql.contains("ORDER BY d1.ID DESC"),
						"union根排序不得再用分支表别名限定,实际SQL=" + sql));
		// H2:非首分支自带limit(括号包裹);首分支的orderBy/last属于整个union,此处不加
		resetGuardRows();
		List<RegUser> rows = defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 1)
				.unionAll(defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 0)
						.orderByAsc(RegUser::getId).last("limit 1"))
				.find();
		assertEquals(3, rows.size(), "status=1两行 + 第二分支limit 1一行");
		// H2:union根排序 + sqltoy count/分页二次包装
		LambdaQuery<RegUser> union = defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 1)
				.unionAll(defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 0))
				.orderByDesc(RegUser::getId);
		List<RegUser> ordered = union.find();
		assertEquals(3, ordered.size());
		assertEquals(3, ordered.get(0).getId().intValue(), "union根排序应作用于整个union结果");
		Page<RegUser> page = union.findPage(new Page(2, 1));
		assertEquals(3L, page.getRecordCount(), "union根排序应被sqltoy count包装正确剔除");
		assertEquals(2, page.getRows().size());
		assertEquals(3, ((RegUser) page.getRows().get(0)).getId().intValue());
	}

	@Test
	@DisplayName("P3-8 单表通道countSql必须快速失败")
	public void p3SingleTableCountSqlMustFail() {
		LambdaQuery<RegUser> query = defectDao.lambdaQuery(RegUser.class).eq(RegUser::getStatus, 1)
				.countSql("select count(1) from reg_guard_user");
		assertThrows(DataAccessException.class, () -> query.findPage(new Page(2, 1)),
				"单表通道无法承载countSql,应快速失败而不是静默忽略");
	}

	@Test
	@DisplayName("P3-9 groupBy空集合不得生成GROUP BY空片段")
	public void p3EmptyGroupBy() {
		LambdaQuery<DefUser> query = LambdaQuery.create(DefUser.class)
				.groupBy(Collections.<LambdaFn<DefUser, ?>>emptyList());
		query.assemble(META);
		assertTrue(query.getSqlSegment() == null || query.getSqlSegment().trim().isEmpty(),
				"空集合groupBy不得生成非法GROUP BY片段: " + query.getSqlSegment());
	}

	@Test
	@DisplayName("P3-10 having空SQL必须快速失败")
	public void p3BlankHaving() {
		assertThrows(DataAccessException.class,
				() -> LambdaQuery.create(DefUser.class).having(true, "  ", null),
				"空having不得生成HAVING空片段");
		// condition=false时不参与组装,允许空sql
		assertDoesNotThrow(() -> LambdaQuery.create(DefUser.class).having(false, "  ", null));
	}
}
