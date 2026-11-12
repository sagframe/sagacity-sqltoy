package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.LambdaQuerySqlTest.User;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.model.inner.EntityQueryExtend;

/**
 * @description 嵌套分组判空与"只有连接符"条件判空的回归测试:last()属于分组边界之外的裸sql,
 *              不能参与判空;连接符渲染为空白时不能当作有效where
 * @author zhongxuchen
 */
@DisplayName("lambda嵌套分组判空与空白条件回归测试")
public class LambdaNestedConditionGuardTest {

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

	private static String normSql(String sql) {
		return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	@DisplayName("嵌套分组内的last()必须快速失败,不得生成(1 = 1 limit 1)")
	public void nestedLastMustFailFast() {
		LambdaQuery<User> onlyLast = LambdaQuery.create(User.class)
				.nested(w -> w.last("limit 1"));
		assertThrows(DataAccessException.class, () -> onlyLast.assemble(IDENTITY),
				"嵌套分组只有last时应明确拒绝,而不是生成(1 = 1 limit 1)");

		LambdaQuery<User> withCondition = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.nested(w -> w.eq(User::getType, 2).last("limit 1"));
		assertThrows(DataAccessException.class, () -> withCondition.assemble(IDENTITY),
				"嵌套分组内条件与last并存时同样应拒绝,而不是生成(status = :p2 limit 1)");

		LambdaDelete<User> delete = LambdaDelete.create(User.class).nested(w -> w.last("limit 1"));
		assertThrows(DataAccessException.class, () -> delete.assemble(IDENTITY),
				"更新/删除的嵌套分组内last也须同样拒绝");
	}

	@Test
	@DisplayName("嵌套分组内的空白last不得绕过无条件守卫")
	public void blankNestedLastMustNotBypassGuard() {
		LambdaDelete<User> delete = LambdaDelete.create(User.class).nested(w -> w.last("   "));
		delete.assemble(IDENTITY);
		assertFalse(delete.hasCondition(), "空白last不构成条件,不得被当作有效where");
		assertThrows(DataAccessException.class, () -> LambdaConvert.toDeleteQuery(delete),
				"空白last不得绕过删除的无条件守卫而生成全表删除");
	}

	@Test
	@DisplayName("根条件上的空白last按未设置处理")
	public void blankRootLastIgnored() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).last("   ");
		query.assemble(IDENTITY);
		assertFalse(query.hasLastSql(), "空白last不应被标记为已设置");
		assertNull(query.getSqlSegment(), "空白last不应产生任何条件片段");
	}

	@Test
	@DisplayName("只有连接符的条件不得让join查询生成裸WHERE")
	public void connectorOnlyJoinQueryOmitsWhere() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.select(JoinCol.count(User::getId).as("total")).or();
		query.assemble(IDENTITY);
		assertEquals("SELECT COUNT(u1.id) AS total FROM user u1", normSql(query.getSql()),
				"连接符被剔除后应按无where处理,不生成'WHERE ',也不补1 = 1");
	}

	@Test
	@DisplayName("只有连接符+groupBy时按独立子句输出,不补1=1")
	public void connectorOnlyWithGroupBySplitsClause() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).or().groupBy(User::getStatus);
		query.assemble(IDENTITY);
		assertEquals("GROUP BY status", norm(query),
				"连接符不是有效条件,groupBy按独立子句输出,不补1 = 1");
	}

	@Test
	@DisplayName("连接符-only条件配合last时不补1=1,交给运行时暴露")
	public void connectorOnlyWithLastNoCarrier() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.select(JoinCol.count(User::getId).as("total")).or().last("limit 1");
		query.assemble(IDENTITY);
		assertEquals("SELECT COUNT(u1.id) AS total FROM user u1 limit 1", normSql(query.getSql()),
				"last不再用1 = 1承载");
	}

	@Test
	@DisplayName("正常嵌套分组不受last守卫影响")
	public void normalNestedGroupStillWorks() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.nested(w -> w.eq(User::getType, 2));
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND (type = :p2)", norm(query));
		assertTrue(query.hasCondition());
	}

	@Test
	@DisplayName("单表通道把groupBy/orderBy/having拆到EntityQuery原生子句,不补1=1")
	public void singleChannelSplitsClausesWithoutCarrier() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).or().groupBy(User::getStatus)
				.orderByAsc(User::getId).having("count(1) > 1", null);
		query.assemble(IDENTITY);
		EntityQueryExtend inner = LambdaConvert.toEntityQuery(query).getInnerModel();
		assertNull(inner.where, "groupBy/orderBy/having-only 不应再补 1 = 1 where，实际: " + inner.where);
		assertEquals("status", inner.groupBy);
		assertEquals(1, inner.orderBy.size());
		assertTrue(inner.having.contains("count(1) > 1"));

		LambdaQuery<User> havingOnly = LambdaQuery.create(User.class).having("count(1) > 1", null);
		havingOnly.assemble(IDENTITY);
		assertThrows(DataAccessException.class, () -> LambdaConvert.toEntityQuery(havingOnly),
				"单表having-only必须快速失败,不能生成 WHERE HAVING");
	}

	@Test
	@DisplayName("join通道把groupBy/having/orderBy按原生子句输出,不生成WHERE 1=1")
	public void joinChannelSplitsClausesWithoutCarrier() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.select(JoinCol.count(User::getId).as("total")).or().groupBy(User::getStatus)
				.having("count(1) > 1", null).orderByAsc(User::getId);
		query.assemble(IDENTITY);
		assertEquals(
				"SELECT COUNT(u1.id) AS total FROM user u1 GROUP BY u1.status HAVING count(1) > 1 ORDER BY u1.id ASC",
				normSql(query.getSql()));
	}

	@Test
	@DisplayName("前后拼接不得留下悬挂AND/OR")
	public void redundantConnectorsAtEdges() {
		LambdaQuery<User> middle = LambdaQuery.create(User.class).eq(User::getStatus, 1).or().and()
				.eq(User::getId, 2);
		middle.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(middle),
				"相邻连接符应合并为最后一个 AND");

		LambdaQuery<User> trailing = LambdaQuery.create(User.class).eq(User::getStatus, 1).or().and();
		trailing.assemble(IDENTITY);
		assertEquals("status = :p1", norm(trailing), "尾部悬挂连接符必须剔除");

		LambdaQuery<User> leading = LambdaQuery.create(User.class).or().and().eq(User::getId, 2);
		leading.assemble(IDENTITY);
		assertEquals("id = :p1", norm(leading), "首部悬挂连接符必须剔除");
	}

	@Test
	@DisplayName("exists子查询内禁止groupBy/orderBy/last,避免WHERE GROUP BY")
	public void existsSubQueryMustRejectTrailingClauses() {
		assertThrows(DataAccessException.class, () -> {
			LambdaQuery<User> q = LambdaQuery.create(User.class)
					.exists(User.class, sub -> sub.groupBy(User::getStatus));
			q.assemble(IDENTITY);
		}, "exists子查询内不能使用groupBy");
		assertThrows(DataAccessException.class, () -> {
			LambdaQuery<User> q = LambdaQuery.create(User.class)
					.exists(User.class, sub -> sub.orderByAsc(User::getId));
			q.assemble(IDENTITY);
		}, "exists子查询内不能使用orderBy");
		assertThrows(DataAccessException.class, () -> {
			LambdaQuery<User> q = LambdaQuery.create(User.class)
					.exists(User.class, sub -> sub.last("limit 1"));
			q.assemble(IDENTITY);
		}, "exists子查询内不能使用last");
	}
}
