package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.LambdaQuerySqlTest.User;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.engine.SqlFunction;
import org.sagacity.sqltoy.lambda.join.ExcludeCols;
import org.sagacity.sqltoy.lambda.join.JoinCol;

/**
 * Lambda 模块公开 API 表面测试:查询构建、状态读取、选择列、分页配置、JoinCol/ExcludeCols/OuterRef
 */
@DisplayName("lambda公开API表面测试")
public class LambdaApiSurfaceTest {

	private static final Function<Class<?>, EntityMeta> IDENTITY = entityClass -> {
		EntityMeta entityMeta = new EntityMeta();
		entityMeta.setTableName(entityClass.getSimpleName().toLowerCase(Locale.ROOT));
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<>();
		for (Field field : entityClass.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			FieldMeta fieldMeta = new FieldMeta();
			fieldMeta.setFieldName(field.getName());
			fieldMeta.setColumnName(field.getName());
			fieldsMeta.put(field.getName().toLowerCase(Locale.ROOT), fieldMeta);
		}
		entityMeta.setFieldsMeta(fieldsMeta);
		return entityMeta;
	};

	private static String norm(AbstractWhere<?, ?> where) {
		String sql = where.getSqlSegment();
		return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	@DisplayName("select(LambdaFn...)记录属性名并按顺序转EntityQuery select")
	public void selectLambdaFields() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).select(User::getName, User::getId);
		assertEquals(Arrays.asList("name", "id"), query.getSelectFields());
		query.eq(User::getStatus, 1).assemble(IDENTITY);
		assertEquals(Arrays.asList("name", "id"), query.getSelectFields());
	}

	@Test
	@DisplayName("selectAll(ExcludeCols)记录排除字段")
	public void selectAllExcludeColumns() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.selectAll(ExcludeCols.of(User::getStatus, User::getAge));
		assertEquals(Arrays.asList("status", "age"), query.getExcludeFields());
		query.assemble(IDENTITY);
		assertEquals(Arrays.asList("status", "age"), query.getExcludeFields());
	}

	@Test
	@DisplayName("distinct/pageOptimize/dataSource状态可读取")
	public void queryConfigurationState() {
		PageOptimize pageOptimize = new PageOptimize().parallel(true).aliveSeconds(120);
		DataSource dataSource = new JdbcDataSource();
		LambdaQuery<User> query = LambdaQuery.create(User.class).distinct().pageOptimize(pageOptimize)
				.dataSource(dataSource);
		assertTrue(query.isDistinct());
		assertSame(pageOptimize, query.getPageOptimize());
		assertSame(dataSource, query.getDataSource());
		assertEquals(User.class, query.getEntityClass());
	}

	@Test
	@DisplayName("hasCondition/hasOrderBy/hasLastSql/getLastSql/getSqlSegment状态")
	public void queryStateFlags() {
		LambdaQuery<User> query = LambdaQuery.create(User.class);
		assertFalse(query.hasCondition());
		assertFalse(query.hasOrderBy());
		assertFalse(query.hasLastSql());
		query.eq(User::getStatus, 1).orderByAsc(User::getId).last("limit 1");
		query.assemble(IDENTITY);
		assertTrue(query.hasCondition());
		assertTrue(query.hasOrderBy());
		assertTrue(query.hasLastSql());
		assertTrue(query.hasOrderByOrLastSql());
		assertEquals("limit 1", query.getLastSql());
		assertEquals("status = :p1 ORDER BY id ASC limit 1", norm(query));
		assertTrue(query.getSql().contains("status = :p1"));
	}

	@Test
	@DisplayName("matchAll/requireCondition语义")
	public void matchAllAndRequireCondition() {
		LambdaQuery<User> empty = LambdaQuery.create(User.class);
		assertThrows(DataAccessException.class, () -> empty.requireCondition("query"));
		assertFalse(empty.isMatchAll());
		empty.matchAll();
		assertTrue(empty.isMatchAll());
		empty.requireCondition("query");

		LambdaQuery<User> conditioned = LambdaQuery.create(User.class).eq(User::getStatus, 1);
		conditioned.assemble(IDENTITY);
		conditioned.requireCondition("query");
		assertTrue(conditioned.hasCondition());
	}

	@Test
	@DisplayName("JoinCol工厂、函数、别名与getter")
	public void joinColFactoriesAndGetters() {
		JoinCol of = JoinCol.of(User::getName).as("nameAlias");
		assertSame(SqlFunction.COUNT, JoinCol.count(User::getId).getFunction());
		assertSame(SqlFunction.SUM, JoinCol.sum(User::getAge).getFunction());
		assertSame(SqlFunction.AVG, JoinCol.avg(User::getAge).getFunction());
		assertSame(SqlFunction.MIN, JoinCol.min(User::getAge).getFunction());
		assertSame(SqlFunction.MAX, JoinCol.max(User::getAge).getFunction());
		assertEquals("nameAlias", of.getAlias());
		assertEquals("name", LambdaUtils.extractProperty(of.getColumn()));
		JoinCol raw = JoinCol.raw("ROW_NUMBER() OVER(ORDER BY id)").as("rn");
		assertEquals("ROW_NUMBER() OVER(ORDER BY id)", raw.getRawSql());
		assertEquals("rn", raw.getAlias());
		assertThrows(DataAccessException.class, () -> JoinCol.raw(" "));
		assertThrows(DataAccessException.class, () -> JoinCol.raw("x").as("bad alias"));
	}

	@Test
	@DisplayName("ExcludeCols与OuterRef公开getter")
	public void excludeColsAndOuterRefGetters() {
		ExcludeCols<User> exclude = ExcludeCols.of(User::getStatus, User::getAge);
		assertEquals(2, exclude.getColumns().size());
		OuterRef<User> outer = OuterRef.of(User::getId);
		assertEquals("id", LambdaUtils.extractProperty(outer.getColumn()));
	}

	@Test
	@DisplayName("LambdaUtils extract/extractProperty/extractNature一致")
	public void lambdaUtilsConsistency() {
		assertEquals("name", LambdaUtils.extractProperty(User::getName));
		assertEquals("name", LambdaUtils.extractNature(User::getName).fieldName());
		assertEquals(User.class, LambdaUtils.extractNature(User::getName).entityClass());
		org.sagacity.sqltoy.lambda.toolkit.LambdaMeta meta = LambdaUtils.extract(User::getName);
		assertEquals("getName", meta.getImplMethodName());
		assertEquals(User.class, meta.getInstantiatedClass());
	}

	@Test
	@DisplayName("LambdaUpdate.set/getSetFields与条件组装")
	public void updateSetFieldsAndCondition() {
		LambdaUpdate<User> update = LambdaUpdate.create(User.class).set(User::getName, "n")
				.setNull(User::getAge).setExpr(User::getStatus, "status + 1").eq(User::getId, 1);
		update.assemble(IDENTITY);
		Map<String, Object> setFields = update.getSetFields();
		assertEquals("n", setFields.get("name"));
		assertTrue(setFields.containsKey("age"));
		assertTrue(setFields.containsKey("status=status + 1"));
		assertEquals("id = :p1", norm(update));
	}

	@Test
	@DisplayName("join模式select别名与别名排序进入SQL")
	public void joinSelectAndAliasOrder() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).from(User.class)
				.select(JoinCol.of(User::getName).as("userName"), JoinCol.count(User::getId).as("userCount"))
				.orderByDesc("userCount");
		query.assemble(IDENTITY);
		String sql = query.getSql().replaceAll("\\s+", " ").trim();
		assertTrue(sql.contains("AS userName"), sql);
		assertTrue(sql.contains("AS userCount"), sql);
		assertTrue(sql.contains("ORDER BY userCount DESC"), sql);
	}
}
