package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
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
import org.sagacity.sqltoy.lambda.join.ExcludeCols;
import org.sagacity.sqltoy.lambda.join.JoinCol;

/**
 * lambda公开API空入参/非法入参的深度防御:entityClass、column、JoinCol、orderBy集合
 */
@DisplayName("lambda API入参防御深度测试")
public class LambdaApiGuardDeepTest {

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
	@DisplayName("create(entityClass=null)三种构建器均快速失败")
	public void nullEntityClassRejected() {
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(null));
		assertThrows(DataAccessException.class, () -> LambdaUpdate.create(null));
		assertThrows(DataAccessException.class, () -> LambdaDelete.create(null));
	}

	@Test
	@DisplayName("JoinCol列引用为null快速失败")
	public void nullJoinColumnRejected() {
		assertThrows(DataAccessException.class, () -> JoinCol.of(null));
		assertThrows(DataAccessException.class, () -> JoinCol.count(null));
		assertThrows(DataAccessException.class, () -> JoinCol.sum(null));
	}

	@Test
	@DisplayName("ExcludeCols空/含null字段快速失败")
	@SuppressWarnings("unchecked")
	public void nullExcludeColumnRejected() {
		assertThrows(DataAccessException.class, () -> ExcludeCols.of());
		assertThrows(DataAccessException.class,
				() -> ExcludeCols.of((LambdaFn<User, ?>) null));
	}

	@Test
	@DisplayName("条件/分组/排序的字段引用为null快速失败")
	public void nullLambdaColumnRejected() {
		LambdaQuery<User> eq = LambdaQuery.create(User.class).eq((LambdaFn<User, ?>) null, 1);
		assertThrows(DataAccessException.class, () -> eq.assemble(IDENTITY));

		LambdaQuery<User> group = LambdaQuery.create(User.class)
				.groupBy(Collections.<LambdaFn<User, ?>>singletonList(null));
		assertThrows(DataAccessException.class, () -> group.assemble(IDENTITY));

		LambdaQuery<User> order = LambdaQuery.create(User.class)
				.orderByAsc(Collections.<LambdaFn<User, ?>>singletonList(null));
		assertThrows(DataAccessException.class, () -> order.assemble(IDENTITY));
	}

	@Test
	@DisplayName("orderBy空集合/null集合不生成ORDER BY片段")
	public void nullOrderByCollectionIgnored() {
		LambdaQuery<User> nullList = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.orderByAsc((java.util.Collection<LambdaFn<User, ?>>) null);
		nullList.assemble(IDENTITY);
		assertEquals("status = :p1", norm(nullList));

		LambdaQuery<User> emptyList = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.orderByAsc(Collections.<LambdaFn<User, ?>>emptyList());
		emptyList.assemble(IDENTITY);
		assertEquals("status = :p1", norm(emptyList));
	}

	@Test
	@DisplayName("set/setNull/setExpr字段引用为null快速失败")
	public void nullUpdateColumnRejected() {
		assertThrows(DataAccessException.class,
				() -> LambdaUpdate.create(User.class).set((LambdaFn<User, ?>) null, 1));
		assertThrows(DataAccessException.class,
				() -> LambdaUpdate.create(User.class).setNull((LambdaFn<User, ?>) null));
		assertThrows(DataAccessException.class,
				() -> LambdaUpdate.create(User.class).setExpr((LambdaFn<User, ?>) null, "age + 1"));
	}

	@Test
	@DisplayName("from/leftJoin/on/deleteTables空实体或空join字段快速失败")
	public void nullJoinStructureRejected() {
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).from(null));
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).leftJoin(null));
		assertThrows(DataAccessException.class,
				() -> LambdaQuery.create(User.class).leftJoin(String.class)
						.on((LambdaFn<User, ?>) null, (LambdaFn<User, ?>) null));
		assertThrows(DataAccessException.class,
				() -> LambdaDelete.create(User.class).deleteTables(new Class<?>[] { null }));
	}

	@Test
	@DisplayName("分页/取前N/随机取数的非法参数快速失败")
	public void invalidTerminalArgumentsRejected() {
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).findPage(null));
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).findTop(0));
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).findTop(-1));
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).findRandom(0));
	}

	@Test
	@DisplayName("select/selectAll/orderBy别名非法入参快速失败")
	@SuppressWarnings("unchecked")
	public void invalidSelectAndOrderArgumentsRejected() {
		assertThrows(DataAccessException.class,
				() -> LambdaQuery.create(User.class).select((LambdaFn<User, ?>) null));
		assertThrows(DataAccessException.class,
				() -> LambdaQuery.create(User.class).selectAll((ExcludeCols<?>) null));
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).orderByAsc((String) null));
		assertThrows(DataAccessException.class,
				() -> LambdaQuery.create(User.class).orderByAsc("userCount; drop table ff_user"));
	}
}
