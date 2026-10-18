package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.toolkit.ValueFilter;

/**
 * @description 单表lambda条件sql组装单元测试,无需数据库,采用恒等解析(属性名即字段名)
 * @author zhongxuchen
 */
@DisplayName("LambdaQuery/LambdaUpdate/LambdaDelete sql组装测试")
public class LambdaQuerySqlTest {

	/**
	 * 恒等解析:属性名即数据库字段名
	 */
	private static final Function<Class<?>, EntityMeta> IDENTITY = entityClass -> {
		EntityMeta entityMeta = new EntityMeta();
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

	public static class User {
		private Integer id;
		private String name;
		private Integer status;
		private Integer type;
		private Integer age;

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

		public Integer getType() {
			return type;
		}

		public void setType(Integer type) {
			this.type = type;
		}

		public Integer getAge() {
			return age;
		}

		public void setAge(Integer age) {
			this.age = age;
		}
	}

	private static String normalized(AbstractWhere<?, ?> where) {
		String sql = where.getSqlSegment();
		return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	@DisplayName("单条件eq")
	public void testEq() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1);
		query.assemble(IDENTITY);
		assertEquals("status = :p1", normalized(query));
		Map<String, Object> params = query.getParams();
		assertEquals(1, params.get("p1"));
	}

	@Test
	@DisplayName("链式条件与排序")
	public void testChained() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.like(User::getName, "张").orderByDesc(User::getId);
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND name LIKE CONCAT('%',CONCAT(:p2,'%')) ORDER BY id DESC", normalized(query));
	}

	@Test
	@DisplayName("or嵌套条件与参数序号连续")
	public void testNestedOr() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.or(nested -> nested.eq(User::getType, 2).eq(User::getName, "张")).eq(User::getId, 4);
		query.assemble(IDENTITY);
		assertEquals("status = :p1 OR (type = :p2 AND name = :p3) AND id = :p4", normalized(query));
	}

	@Test
	@DisplayName("and嵌套条件")
	public void testNestedAnd() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.and(nested -> nested.eq(User::getType, 2).or().eq(User::getType, 3));
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND (type = :p2 OR type = :p3)", normalized(query));
	}

	@Test
	@DisplayName("not嵌套条件")
	public void testNestedNot() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.not(nested -> nested.eq(User::getType, 2));
		query.assemble(IDENTITY);
		assertEquals("status = :p1 NOT (type = :p2)", normalized(query));
	}

	@Test
	@DisplayName("相邻连接符合并保留最后一个")
	public void testConnectorDeduplicate() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1).and().or()
				.eq(User::getId, 2);
		query.assemble(IDENTITY);
		assertEquals("status = :p1 OR id = :p2", normalized(query));
	}

	@Test
	@DisplayName("仅分组排序时以1=1补齐where")
	public void testGroupOrderOnly() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).groupBy(User::getStatus)
				.orderByDesc(User::getId);
		query.assemble(IDENTITY);
		assertEquals("1 = 1 GROUP BY status ORDER BY id DESC", normalized(query));
	}

	@Test
	@DisplayName("多次orderBy与groupBy合并")
	public void testOrderGroupMerge() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).orderByAsc(User::getId).orderByDesc(User::getName)
				.groupBy(User::getStatus).groupBy(User::getType);
		query.assemble(IDENTITY);
		assertEquals("1 = 1 GROUP BY status,type ORDER BY id ASC,name DESC", normalized(query));
	}

	@Test
	@DisplayName("having条件合并")
	public void testHaving() {
		Map<String, Object> havingParams = new HashMap<>();
		havingParams.put("sumAge", 10);
		LambdaQuery<User> query = LambdaQuery.create(User.class).groupBy(User::getStatus)
				.having("count(*) > 1").having("sum(age) > :sumAge", havingParams);
		query.assemble(IDENTITY);
		assertEquals("1 = 1 GROUP BY status HAVING count(*) > 1 AND sum(age) > :sumAge", normalized(query));
		assertEquals(10, query.getParams().get("sumAge"));
	}

	@Test
	@DisplayName("in集合与数组条件")
	public void testIn() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).in(User::getId, Arrays.asList(1, 2, 3));
		query.assemble(IDENTITY);
		assertEquals("id IN (:p1)", normalized(query));
		assertEquals(Arrays.asList(1, 2, 3), query.getParams().get("p1"));

		LambdaQuery<User> query2 = LambdaQuery.create(User.class).in(User::getId, 1, 2);
		query2.assemble(IDENTITY);
		assertEquals("id IN (:p1)", normalized(query2));
	}

	@Test
	@DisplayName("空集合与空数组不参与sql组装")
	public void testEmptyIn() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.in(User::getId, Arrays.asList()).in(User::getId, new Object[0]);
		query.assemble(IDENTITY);
		assertEquals("status = :p1", normalized(query));
	}

	@Test
	@DisplayName("inSql子查询")
	public void testInSql() {
		Map<String, Object> subParams = new HashMap<>();
		subParams.put("typeId", 3);
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.inSql(User::getId, "select user_id from t_role where type=:typeId", subParams);
		query.assemble(IDENTITY);
		assertEquals("id IN (select user_id from t_role where type=:typeId)", normalized(query));
		assertEquals(3, query.getParams().get("typeId"));
	}

	@Test
	@DisplayName("between与notBetween")
	public void testBetween() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).between(User::getAge, 18, 30);
		query.assemble(IDENTITY);
		assertEquals("age BETWEEN :p1 AND :p2", normalized(query));
		assertEquals(18, query.getParams().get("p1"));
		assertEquals(30, query.getParams().get("p2"));

		LambdaQuery<User> notBetween = LambdaQuery.create(User.class).notBetween(User::getAge, 18, 30);
		notBetween.assemble(IDENTITY);
		assertEquals("age NOT BETWEEN :p1 AND :p2", normalized(notBetween));
	}

	@Test
	@DisplayName("多字段元组in")
	public void testBatchIn() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.inb(User::getId, User::getName, Arrays.asList(new Object[] { 1, "张" },
						new Object[] { 2, "李" }));
		query.assemble(IDENTITY);
		assertEquals("(id,name) IN ((:p1,:p2),(:p3,:p4))", normalized(query));
		assertEquals(1, query.getParams().get("p1"));
		assertEquals("张", query.getParams().get("p2"));
		assertEquals(2, query.getParams().get("p3"));
		assertEquals("李", query.getParams().get("p4"));
	}

	@Test
	@DisplayName("null值默认过滤与自定义值过滤")
	public void testValueFilter() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, null).like(User::getName, "张");
		query.assemble(IDENTITY);
		assertEquals("name LIKE CONCAT('%',CONCAT(:p1,'%'))", normalized(query));

		LambdaQuery<User> blankFiltered = LambdaQuery.create(User.class)
				.valueFilter(value -> value != null && !"".equals(value)).eq(User::getStatus, 1)
				.like(User::getName, "");
		blankFiltered.assemble(IDENTITY);
		assertEquals("status = :p1", normalized(blankFiltered));
	}

	@Test
	@DisplayName("isNull与isNotNull")
	public void testNullCondition() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).isNull(User::getStatus).or()
				.isNotNull(User::getName);
		query.assemble(IDENTITY);
		assertEquals("status IS NULL OR name IS NOT NULL", normalized(query));
		assertTrue(query.getParams().isEmpty());
	}

	@Test
	@DisplayName("condition为false时条件跳过")
	public void testConditionFlag() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(false, User::getStatus, 1)
				.eq(User::getId, 2);
		query.assemble(IDENTITY);
		assertEquals("id = :p1", normalized(query));
	}

	@Test
	@DisplayName("IfPresent语法糖")
	public void testIfPresent() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eqIfPresent(User::getStatus, null)
				.likeIfPresent(User::getName, "张").betweenIfPresent(User::getAge, new Object[] { 18 });
		query.assemble(IDENTITY);
		assertEquals("name LIKE CONCAT('%',CONCAT(:p1,'%')) AND age >= :p2", normalized(query));
	}

	@Test
	@DisplayName("accept消费函数与last追加")
	public void testAcceptAndLast() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.accept(q -> q.eq(User::getType, 3)).last("limit 10");
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND type = :p2 limit 10", normalized(query));
	}

	@Test
	@DisplayName("select指定查询字段")
	public void testSelect() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).select(User::getId, User::getName)
				.eq(User::getStatus, 1);
		query.assemble(IDENTITY);
		assertEquals(Arrays.asList("id", "name"), query.getSelectFields());
		assertEquals("status = :p1", normalized(query));
	}

	@Test
	@DisplayName("重复assemble幂等")
	public void testAssembleIdempotent() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1);
		query.assemble(IDENTITY);
		String firstSql = query.getSqlSegment();
		query.assemble(IDENTITY);
		assertEquals(firstSql, query.getSqlSegment());
	}

	@Test
	@DisplayName("无条件删除与更新须显式matchAll")
	public void testMatchAllFlag() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).matchAll();
		assertFalse(query.hasCondition());
		assertTrue(query.isMatchAll());

		LambdaDelete<User> delete = LambdaDelete.create(User.class).eq(User::getStatus, 1);
		delete.assemble(IDENTITY);
		assertTrue(delete.hasCondition());
	}

	@Test
	@DisplayName("未绑定dao时终端方法抛出异常")
	public void testUnboundDao() {
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).find());
		assertThrows(DataAccessException.class, () -> LambdaUpdate.create(User.class).set(User::getStatus, 1)
				.update());
		assertThrows(DataAccessException.class, () -> LambdaDelete.create(User.class).delete());
	}

	@Test
	@DisplayName("update构建器的set与条件")
	public void testLambdaUpdate() {
		LambdaUpdate<User> update = LambdaUpdate.create(User.class).set(User::getStatus, 2)
				.set(false, User::getName, "跳过").eq(User::getId, 1);
		update.assemble(IDENTITY);
		assertEquals("id = :p1", normalized(update));
		assertEquals(2, update.getSetFields().get("status"));
		assertNull(update.getSetFields().get("name"));
	}

	@Test
	@DisplayName("setExpr表达式以属性名=表达式形态记录")
	public void testSetExpr() {
		LambdaUpdate<User> update = LambdaUpdate.create(User.class).setExpr(User::getAge, "age + 1")
				.setExpr(User::getName, "concat(name, ?)", "x").setExpr(false, User::getStatus, "status - 1")
				.eq(User::getId, 1);
		update.assemble(IDENTITY);
		assertEquals("id = :p1", normalized(update));
		assertNull(update.getSetFields().get("age=age + 1"));
		assertEquals("x", update.getSetFields().get("name=concat(name, ?)"));
		assertNull(update.getSetFields().get("status=status - 1"));
	}

	@Test
	@DisplayName("setExpr表达式为空或含=字符时快速失败")
	public void testSetExprValidation() {
		assertThrows(DataAccessException.class,
				() -> LambdaUpdate.create(User.class).setExpr(User::getAge, " "));
		assertThrows(DataAccessException.class,
				() -> LambdaUpdate.create(User.class).setExpr(User::getAge, "age = age + 1"));
	}

	@Test
	@DisplayName("likeLeft与likeRight")
	public void testLikeLeftRight() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).likeLeft(User::getName, "张")
				.likeRight(User::getName, "三");
		query.assemble(IDENTITY);
		assertEquals("name LIKE CONCAT('%',:p1) AND name LIKE CONCAT(:p2,'%')", normalized(query));
	}

	@Test
	@DisplayName("嵌套条件继承值过滤策略")
	public void testNestedValueFilter() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.valueFilter(value -> value != null && !"".equals(value))
				.eq(User::getStatus, 1)
				.or(nested -> nested.like(User::getName, "").eq(User::getId, 2));
		query.assemble(IDENTITY);
		assertEquals("status = :p1 OR (id = :p2)", normalized(query));
	}
}
