package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.LambdaQuerySqlTest.User;

/**
 * lambda 多层分组 + 判空的深度矩阵:覆盖空 and/or/not/nested、层级嵌套、
 * 分组条件 false、IfPresent 全空/部分空、优先级与参数序号
 */
@DisplayName("lambda多层级分组判空深度测试")
public class LambdaGroupingNullDepthTest {

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

	private static LambdaQuery<User> query() {
		return LambdaQuery.create(User.class);
	}

	@Test
	@DisplayName("空OR分组在中间:后续条件仍按AND,不扩大结果")
	public void emptyOrMiddle() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).or(w -> w.eq(false, User::getType, 2))
				.eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
		assertEquals(2, q.getParams().size());
	}

	@Test
	@DisplayName("空OR分组在首位:不留下前导OR")
	public void emptyOrFirst() {
		LambdaQuery<User> q = query().or(w -> w.eq(false, User::getType, 2)).eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("空OR分组在末尾:不留下尾随OR")
	public void emptyOrLast() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).or(w -> w.eq(false, User::getType, 2));
		q.assemble(IDENTITY);
		assertEquals("status = :p1", norm(q));
	}

	@Test
	@DisplayName("空NOT分组在中间:NOT不得套到后续条件")
	public void emptyNotMiddle() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).not(w -> w.eq(false, User::getType, 2))
				.eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("空AND分组在中间:不留下悬挂AND")
	public void emptyAndMiddle() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).and(w -> w.eq(false, User::getType, 2))
				.eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("空nested分组在中间:不渲染空括号")
	public void emptyNestedMiddle() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).nested(w -> w.eq(false, User::getType, 2))
				.eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("外层分组只含空内层:整组丢弃")
	public void nestedOnlyEmptyInnerGroup() {
		LambdaQuery<User> q = query()
				.nested(outer -> outer.and(inner -> inner.eq(false, User::getType, 2)))
				.eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
		assertTrue(q.hasCondition());
	}

	@Test
	@DisplayName("只有空分组:无有效条件")
	public void onlyEmptyGroupHasNoCondition() {
		LambdaQuery<User> q = query().or(w -> w.eq(false, User::getType, 2));
		q.assemble(IDENTITY);
		assertEquals("", norm(q));
		assertFalse(q.hasCondition(), "空分组不应被当作有效条件");
	}

	@Test
	@DisplayName("OR分组有一个有效条件:保留括号")
	public void orGroupWithOneCondition() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.eq(User::getStatus, 1));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 OR (status = :p2)", norm(q));
	}

	@Test
	@DisplayName("AND分组有一个有效条件:保留括号")
	public void andGroupWithOneCondition() {
		LambdaQuery<User> q = query().eq(User::getId, 1).and(w -> w.eq(User::getStatus, 1));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 AND (status = :p2)", norm(q));
	}

	@Test
	@DisplayName("NOT分组有一个有效条件:NOT只作用于括号内")
	public void notGroupWithOneCondition() {
		LambdaQuery<User> q = query().eq(User::getId, 1).not(w -> w.eq(User::getStatus, 1));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 AND NOT (status = :p2)", norm(q));
	}

	@Test
	@DisplayName("nested分组有一个有效条件:自动补AND并保留括号")
	public void nestedGroupWithOneCondition() {
		LambdaQuery<User> q = query().eq(User::getId, 1).nested(w -> w.eq(User::getStatus, 1));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 AND (status = :p2)", norm(q));
	}

	@Test
	@DisplayName("分组condition=false:连接符和括号都不生成")
	public void groupConditionFalseDropsConnector() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(false, w -> w.eq(User::getStatus, 1))
				.eq(User::getType, 2);
		q.assemble(IDENTITY);
		assertEquals("id = :p1 AND type = :p2", norm(q));
	}

	@Test
	@DisplayName("OR分组全部IfPresent为空:整组丢弃")
	public void orGroupAllIfPresentSkipped() {
		LambdaQuery<User> q = query().eq(User::getId, 1)
				.or(w -> w.eqIfPresent(User::getName, null).likeIfPresent(User::getName, " "));
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("OR分组部分IfPresent为空:只保留有效条件")
	public void orGroupPartiallyPresent() {
		LambdaQuery<User> q = query().eq(User::getId, 1)
				.or(w -> w.eqIfPresent(User::getName, null).eq(User::getStatus, 2));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 OR (status = :p2)", norm(q));
	}

	@Test
	@DisplayName("多层分组内层为空:外层保留有效条件")
	public void multiLevelEmptyInner() {
		LambdaQuery<User> q = query()
				.nested(w -> w.eq(User::getStatus, 1).or(inner -> inner.eq(false, User::getType, 2)))
				.eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("(status = :p1) AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("多层分组优先级:(A OR B) AND C")
	public void multiLevelPrecedence() {
		LambdaQuery<User> q = query()
				.nested(w -> w.eq(User::getStatus, 1).or().eq(User::getType, 2))
				.eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("(status = :p1 OR type = :p2) AND id = :p3", norm(q));
	}

	@Test
	@DisplayName("NOT分组内含OR:NOT作用于整个括号")
	public void notGroupWithNestedOr() {
		LambdaQuery<User> q = query().not(w -> w.eq(User::getStatus, 1).or().eq(User::getType, 2));
		q.assemble(IDENTITY);
		assertEquals("NOT (status = :p1 OR type = :p2)", norm(q));
	}

	@Test
	@DisplayName("空分组后接OR分组:参数序号和连接符稳定")
	public void emptyGroupThenOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).and(w -> w.eq(false, User::getType, 2))
				.or(w -> w.eq(User::getStatus, 3));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 OR (status = :p2)", norm(q));
		Map<String, Object> params = q.getParams();
		assertEquals(1, params.get("p1"));
		assertEquals(3, params.get("p2"));
	}

	@Test
	@DisplayName("非空分组不被误删:AND/OR都保留")
	public void nonEmptyGroupsRemain() {
		LambdaQuery<User> q = query().eq(User::getId, 1).and(w -> w.eq(User::getStatus, 1))
				.or(w -> w.eq(User::getType, 2));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 AND (status = :p2) OR (type = :p3)", norm(q));
	}

	@Test
	@DisplayName("显式or()后接空分组:不得留下悬挂OR")
	public void explicitOrFollowedByEmptyGroup() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).or()
				.and(w -> w.eq(false, User::getType, 2)).eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("显式or()后接空nested:不得留下悬挂OR")
	public void explicitOrFollowedByEmptyNested() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).or()
				.nested(w -> w.eq(false, User::getType, 2)).eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("首位显式or()后接空分组:不留下前导OR")
	public void leadingExplicitOrFollowedByEmptyGroup() {
		LambdaQuery<User> q = query().or().and(w -> w.eq(false, User::getType, 2)).eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("连续多个空OR分组:不累积连接符")
	public void multipleEmptyOrGroups() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).or(w -> w.eq(false, User::getType, 2))
				.or(w -> w.eq(false, User::getAge, 3)).eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("空OR分组后显式or():按用户显式意图保留OR")
	public void emptyOrThenExplicitOr() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).or(w -> w.eq(false, User::getType, 2))
				.or().eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 OR id = :p2", norm(q));
	}

	@Test
	@DisplayName("空OR分组后显式and():后续条件仍为AND")
	public void emptyOrThenExplicitAnd() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).or(w -> w.eq(false, User::getType, 2))
				.and().eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", norm(q));
	}

	@Test
	@DisplayName("空NOT分组在首位:不留下前导NOT")
	public void emptyNotFirst() {
		LambdaQuery<User> q = query().not(w -> w.eq(false, User::getType, 2)).eq(User::getId, 4);
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("空NOT分组在末尾:不留下尾随NOT")
	public void emptyNotLast() {
		LambdaQuery<User> q = query().eq(User::getStatus, 1).not(w -> w.eq(false, User::getType, 2));
		q.assemble(IDENTITY);
		assertEquals("status = :p1", norm(q));
	}

	@Test
	@DisplayName("分组condition=false:and/or/not/nested全部不生成")
	public void allGroupConditionsFalse() {
		LambdaQuery<User> q = query().eq(User::getId, 1).and(false, w -> w.eq(User::getStatus, 1))
				.or(false, w -> w.eq(User::getStatus, 2)).not(false, w -> w.eq(User::getStatus, 3))
				.nested(false, w -> w.eq(User::getStatus, 4));
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("OR分组内betweenIfPresent仅下限:生成 >= 保留分组")
	public void betweenIfPresentLowerOnlyInOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.betweenIfPresent(User::getAge, 18, null));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 OR (age >= :p2)", norm(q));
	}

	@Test
	@DisplayName("OR分组内betweenIfPresent仅上限:生成 <= 保留分组")
	public void betweenIfPresentUpperOnlyInOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.betweenIfPresent(User::getAge, null, 60));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 OR (age <= :p2)", norm(q));
	}

	@Test
	@DisplayName("OR分组内betweenIfPresent双边界:BETWEEN")
	public void betweenIfPresentBothBoundsInOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.betweenIfPresent(User::getAge, 18, 60));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 OR (age BETWEEN :p2 AND :p3)", norm(q));
	}

	@Test
	@DisplayName("OR分组内inIfPresent varargs为空:整组丢弃")
	public void emptyVarargsInIfPresentInOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.inIfPresent(User::getType, new Object[0]));
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("OR分组内isNull:IS NULL属于有效条件")
	public void isNullInOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.isNull(User::getName));
		q.assemble(IDENTITY);
		assertEquals("id = :p1 OR (name IS NULL)", norm(q));
	}

	@Test
	@DisplayName("NOT分组内isNotNull:保留NOT括号")
	public void isNotNullInNotGroup() {
		LambdaQuery<User> q = query().not(w -> w.isNotNull(User::getName));
		q.assemble(IDENTITY);
		assertEquals("NOT (name IS NOT NULL)", norm(q));
	}

	@Test
	@DisplayName("空分组+groupBy:以1=1补齐where")
	public void emptyGroupWithGroupBy() {
		LambdaQuery<User> q = query().or(w -> w.eq(false, User::getType, 2)).groupBy(User::getStatus);
		q.assemble(IDENTITY);
		assertEquals("1 = 1 GROUP BY status", norm(q));
	}

	@Test
	@DisplayName("空OR分组+groupBy+orderBy:两个分区都保留")
	public void emptyGroupWithGroupByAndOrderBy() {
		LambdaQuery<User> q = query().or(w -> w.eq(false, User::getType, 2)).groupBy(User::getStatus)
				.orderByDesc(User::getId);
		q.assemble(IDENTITY);
		assertEquals("1 = 1 GROUP BY status ORDER BY id DESC", norm(q));
	}

	@Test
	@DisplayName("仅空OR分组的删除:无有效条件必须拒绝")
	public void emptyOrOnlyDeleteRejected() {
		LambdaDelete<User> d = LambdaDelete.create(User.class).or(w -> w.eq(false, User::getType, 2));
		d.assemble(IDENTITY);
		assertFalse(d.hasCondition());
		org.junit.jupiter.api.Assertions.assertThrows(org.sagacity.sqltoy.exception.DataAccessException.class,
				() -> LambdaConvert.toDeleteQuery(d));
	}

	@Test
	@DisplayName("OR分组内betweenIfPresent上下限都空:整组丢弃")
	public void betweenIfPresentBothNullInOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.betweenIfPresent(User::getAge, null, null));
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("OR分组内inIfPresent空集合:整组丢弃")
	public void emptyInIfPresentInOrGroup() {
		LambdaQuery<User> q = query().eq(User::getId, 1).or(w -> w.inIfPresent(User::getType, new java.util.ArrayList<>()));
		q.assemble(IDENTITY);
		assertEquals("id = :p1", norm(q));
	}

	@Test
	@DisplayName("0和false不是空值:IfPresent仍生成条件")
	public void zeroAndFalseArePresent() {
		LambdaQuery<User> q = query().eqIfPresent(User::getStatus, 0).eqIfPresent(User::getAge, 0);
		q.assemble(IDENTITY);
		assertEquals("status = :p1 AND age = :p2", norm(q));
	}

	@Test
	@DisplayName("空分组+orderBy:以1=1补齐where")
	public void emptyGroupWithOrderBy() {
		LambdaQuery<User> q = query().or(w -> w.eq(false, User::getType, 2)).orderByAsc(User::getId);
		q.assemble(IDENTITY);
		assertEquals("1 = 1 ORDER BY id ASC", norm(q));
	}

	@Test
	@DisplayName("空分组+last:last不丢且以1=1承载")
	public void emptyGroupWithLast() {
		LambdaQuery<User> q = query().or(w -> w.eq(false, User::getType, 2)).last("limit 1");
		q.assemble(IDENTITY);
		assertEquals("1 = 1 limit 1", norm(q));
	}

	@Test
	@DisplayName("仅空OR分组的更新:无有效条件必须拒绝")
	public void emptyOrOnlyUpdateRejected() {
		LambdaUpdate<User> u = LambdaUpdate.create(User.class).set(User::getStatus, 9)
				.or(w -> w.eq(false, User::getType, 2));
		u.assemble(IDENTITY);
		assertFalse(u.hasCondition());
		org.junit.jupiter.api.Assertions.assertThrows(org.sagacity.sqltoy.exception.DataAccessException.class,
				() -> LambdaConvert.toEntityUpdate(u));
	}
}
