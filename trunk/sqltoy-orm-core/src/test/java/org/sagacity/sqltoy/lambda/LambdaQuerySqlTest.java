package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.lambda.toolkit.ValueFilter;
import org.sagacity.sqltoy.model.EntityQuery;

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
		return normalized(where.getSqlSegment());
	}

	private static String normalized(String sql) {
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
		assertEquals("status = :p1 AND name LIKE :p2 ORDER BY id DESC", normalized(query));
		// like为包含匹配:通配符由lambda层补入参数值(纯 LIKE :param 形态,数据库无关)
		assertEquals("%张%", query.getParams().get("p2"));
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
		assertEquals("status = :p1 AND NOT (type = :p2)", normalized(query));
	}

	@Test
	@DisplayName("二层嵌套分组(逐层包括号,参数序号跨层连续)")
	public void testNestedTwoLevel() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.nested(outer -> outer.nested(inner -> inner.eq(User::getType, 2).or().eq(User::getType, 3))
						.eq(User::getStatus, 1))
				.or().eq(User::getId, 9);
		query.assemble(IDENTITY);
		assertEquals("((type = :p1 OR type = :p2) AND status = :p3) OR id = :p4", normalized(query));
	}

	@Test
	@DisplayName("空分组自动丢弃(不渲染()、不留悬挂AND)")
	public void testNestedEmptyGroupDropped() {
		// 组内条件condition=false被跳过→整组空白→丢弃,and()的连接符恰作前后条件的AND
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.and(n -> n.eq(false, User::getType, 2)).eq(User::getId, 4);
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", normalized(query));
	}

	@Test
	@DisplayName("空OR分组自动丢弃,不得把后续条件并成OR")
	public void testNestedEmptyOrGroupDropped() {
		// 组内条件全部被跳过时,OR(...)整体不存在语义,后续条件仍应走AND
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.or(n -> n.eq(false, User::getType, 2)).eq(User::getId, 4);
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", normalized(query));
	}

	@Test
	@DisplayName("空NOT分组自动丢弃,不得把NOT套到后续条件上")
	public void testNestedEmptyNotGroupDropped() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.not(n -> n.eq(false, User::getType, 2)).eq(User::getId, 4);
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND id = :p2", normalized(query));
	}

	@Test
	@DisplayName("accept(inline无括号)与nested(独立分组有括号)对比")
	public void testAcceptVsNested() {
		LambdaQuery<User> acceptQuery = LambdaQuery.create(User.class).eq(User::getType, 9)
				.accept(q -> q.eq(User::getStatus, 1).eq(User::getAge, 5));
		acceptQuery.assemble(IDENTITY);
		assertEquals("type = :p1 AND status = :p2 AND age = :p3", normalized(acceptQuery));

		LambdaQuery<User> nestedQuery = LambdaQuery.create(User.class).eq(User::getType, 9)
				.nested(q -> q.eq(User::getStatus, 1).eq(User::getAge, 5));
		nestedQuery.assemble(IDENTITY);
		assertEquals("type = :p1 AND (status = :p2 AND age = :p3)", normalized(nestedQuery));
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
	@DisplayName("仅分组排序时按独立子句输出,不补1=1")
	public void testGroupOrderOnly() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).groupBy(User::getStatus)
				.orderByDesc(User::getId);
		query.assemble(IDENTITY);
		assertEquals("GROUP BY status ORDER BY id DESC", normalized(query));
	}

	@Test
	@DisplayName("多次orderBy与groupBy合并")
	public void testOrderGroupMerge() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).orderByAsc(User::getId).orderByDesc(User::getName)
				.groupBy(User::getStatus).groupBy(User::getType);
		query.assemble(IDENTITY);
		assertEquals("GROUP BY status,type ORDER BY id ASC,name DESC", normalized(query));
	}

	@Test
	@DisplayName("having条件合并")
	public void testHaving() {
		Map<String, Object> havingParams = new HashMap<>();
		havingParams.put("sumAge", 10);
		LambdaQuery<User> query = LambdaQuery.create(User.class).groupBy(User::getStatus)
				.having("count(*) > 1").having("sum(age) > :sumAge", havingParams);
		query.assemble(IDENTITY);
		assertEquals("GROUP BY status HAVING count(*) > 1 AND sum(age) > :sumAge", normalized(query));
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
		assertEquals("((id = :p1 AND name = :p2) OR (id = :p3 AND name = :p4))", normalized(query));
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
		assertEquals("name LIKE :p1", normalized(query));
		assertEquals("%张%", query.getParams().get("p1"));

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
		assertEquals("name LIKE :p1 AND age >= :p2", normalized(query));
		assertEquals("%张%", query.getParams().get("p1"));
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
	@DisplayName("orderBy多列重复读取getSqlSegment不崩溃且幂等")
	public void testOrderByRepeatRead() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.orderByAsc(User::getId).orderByDesc(User::getName);
		query.assemble(IDENTITY);
		String first = normalized(query);
		assertEquals(first, normalized(query));
		assertEquals("status = :p1 ORDER BY id ASC,name DESC", first);
	}

	@Test
	@DisplayName("空not()不留悬挂NOT")
	public void testEmptyNotNoDangling() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1)
				.not(n -> n.eq(false, User::getId, 1));
		query.assemble(IDENTITY);
		assertEquals("status = :p1", normalized(query));
	}

	@Test
	@DisplayName("两个inSql共用参数名且值不同快速失败,值相同允许共享")
	public void testDuplicateExternalParam() {
		Map<String, Object> paramsA = new HashMap<>();
		paramsA.put("v", 1);
		Map<String, Object> paramsB = new HashMap<>();
		paramsB.put("v", 2);
		LambdaQuery<User> conflict = LambdaQuery.create(User.class)
				.inSql(User::getId, "select id from a where x = :v", paramsA)
				.inSql(User::getName, "select name from b where y = :v", paramsB);
		query_assemble:
		conflict.assemble(IDENTITY);
		assertThrows(DataAccessException.class, () -> conflict.getParams());

		Map<String, Object> shared1 = new HashMap<>();
		shared1.put("v", 7);
		Map<String, Object> shared2 = new HashMap<>();
		shared2.put("v", 7);
		LambdaQuery<User> shared = LambdaQuery.create(User.class)
				.inSql(User::getId, "select id from a where x = :v", shared1)
				.inSql(User::getName, "select name from b where y = :v", shared2);
		shared.assemble(IDENTITY);
		assertEquals(7, shared.getParams().get("v"));
	}

	@Test
	@DisplayName("having参数值为null不抛NPE")
	public void testHavingNullParamValue() {
		Map<String, Object> params = new HashMap<>();
		params.put("v", null);
		LambdaQuery<User> query = LambdaQuery.create(User.class).groupBy(User::getStatus)
				.having("count(*) > :v", params);
		query.assemble(IDENTITY);
		assertTrue(query.getParams().containsKey("v"));
		assertNull(query.getParams().get("v"));
	}

	@Test
	@DisplayName("仅having时不补1=1,缺groupBy交给运行时暴露")
	public void testHavingOnly() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).having("count(*) > 1");
		query.assemble(IDENTITY);
		assertEquals("HAVING count(*) > 1", normalized(query));
	}

	@Test
	@DisplayName("别名排序与lambda排序保持调用顺序(join通道)")
	public void testAliasOrderKeepsCallOrder() {
		LambdaQuery<User> query = LambdaQuery.create(User.class)
				.select(JoinCol.count(User::getId).as("total"))
				.orderByAsc(User::getName).orderByDesc("total");
		query.assemble(IDENTITY);
		assertEquals("SELECT COUNT(u1.id) AS total FROM user u1 "
				+ "ORDER BY u1.name ASC,total DESC", normalized(query.getSql()));
	}

	@Test
	@DisplayName("单表通道getSql返回条件sql")
	public void testSingleChannelGetSql() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getId, 1);
		query.assemble(IDENTITY);
		assertEquals("id = :p1", normalized(query.getSql()));
	}

	@Test
	@DisplayName("likeLeft与likeRight")
	public void testLikeLeftRight() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).likeLeft(User::getName, "张")
				.likeRight(User::getName, "三");
		query.assemble(IDENTITY);
		assertEquals("name LIKE :p1 AND name LIKE :p2", normalized(query));
		// likeLeft补左侧%(后缀匹配),likeRight补右侧%(前缀匹配),等价sqltoy原生ParamsFilter.llike/rlike
		assertEquals("%张", query.getParams().get("p1"));
		assertEquals("三%", query.getParams().get("p2"));
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

	@Test
	@DisplayName("likeExact把值中的%按字面量处理并保留方向")
	public void testLikeExact() {
		LambdaQuery<User> exact = LambdaQuery.create(User.class).likeExact(User::getName, "100%");
		exact.assemble(IDENTITY);
		assertEquals("name LIKE :p1", normalized(exact));
		assertEquals("%100\\%%", exact.getParams().get("p1"));

		LambdaQuery<User> left = LambdaQuery.create(User.class).likeExactLeft(User::getName, "100%");
		left.assemble(IDENTITY);
		assertEquals("%100\\%", left.getParams().get("p1"));

		LambdaQuery<User> right = LambdaQuery.create(User.class).likeExactRight(User::getName, "100%");
		right.assemble(IDENTITY);
		assertEquals("100\\%%", right.getParams().get("p1"));
	}

	@Test
	@DisplayName("setExpr单表通道占位符与value校验")
	public void testSetExprPlaceholderGuard() {
		assertThrows(DataAccessException.class, () -> LambdaUpdate.create(User.class)
				.setExpr(User::getName, "coalesce(name, ?) || ?", "x"));
		assertThrows(DataAccessException.class, () -> LambdaUpdate.create(User.class)
				.setExpr(User::getName, "upper(name)", "x"));
		assertThrows(DataAccessException.class, () -> LambdaUpdate.create(User.class)
				.setExpr(User::getName, "coalesce(name, :p1)", "x"));
	}

	@Test
	@DisplayName("pageOptimize透传到EntityQuery(单表分页优化:并行count+keep-alive缓存)")
	public void testPageOptimizeThreading() {
		PageOptimize po = new PageOptimize().parallel(true).aliveSeconds(120);
		LambdaQuery<User> query = LambdaQuery.create(User.class).eq(User::getStatus, 1).pageOptimize(po);
		query.assemble(IDENTITY);
		// 单表通道:pageOptimize经LambdaConvert落到EntityQuery,再由findEntityBase透传到内部QueryExecutor
		assertSame(po, LambdaConvert.toEntityQuery(query).getInnerModel().pageOptimize);
		// 未设置时为null(执行侧回退全局SqlToyConfig.pageOptimize)
		LambdaQuery<User> plain = LambdaQuery.create(User.class).eq(User::getStatus, 1);
		plain.assemble(IDENTITY);
		assertNull(LambdaConvert.toEntityQuery(plain).getInnerModel().pageOptimize);
	}

	@Test
	@DisplayName("strict模式:显式null条件快速失败,eqIfPresent仍可跳过")
	public void testStrictNullCondition() {
		assertThrows(DataAccessException.class, () -> LambdaDelete.create(User.class).strict()
				.eq(User::getStatus, 1).eq(User::getName, null));
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class)
				.eq(User::getStatus, Arrays.asList(1, 2)).assemble(IDENTITY));
		assertThrows(DataAccessException.class, () -> LambdaUpdate.create(User.class).strict()
				.set(User::getStatus, 2).eq(User::getName, null));
		assertThrows(DataAccessException.class, () -> LambdaDelete.create(User.class).strict()
				.nested(n -> n.eq(User::getName, null)).assemble(IDENTITY));
		// exists子查询也必须继承strict
		assertThrows(DataAccessException.class, () -> LambdaQuery.create(User.class).strict()
				.exists(User.class, sub -> sub.eq(User::getName, null)).assemble(IDENTITY));

		LambdaDelete<User> optional = LambdaDelete.create(User.class).strict()
				.eq(User::getStatus, 1).eqIfPresent(User::getName, null);
		optional.assemble(IDENTITY);
		assertEquals("status = :p1", normalized(optional));
	}

	@Test
	@DisplayName("eqStrict:null渲染恒假条件,非null等价eq")
	public void testEqStrict() {
		LambdaQuery<User> nullValue = LambdaQuery.create(User.class)
				.eq(User::getStatus, 1).eqStrict(User::getName, null);
		nullValue.assemble(IDENTITY);
		assertEquals("status = :p1 AND 1 = 0", normalized(nullValue));
		assertEquals(1, nullValue.getParams().size());

		LambdaQuery<User> value = LambdaQuery.create(User.class).eqStrict(User::getName, "张三");
		value.assemble(IDENTITY);
		assertEquals("name = :p1", normalized(value));

		LambdaQuery<User> disabled = LambdaQuery.create(User.class)
				.eqStrict(false, User::getName, null).eq(User::getId, 1);
		disabled.assemble(IDENTITY);
		assertEquals("id = :p1", normalized(disabled));
	}

	@Test
	@DisplayName("ValueFilter.ignore:哨兵值与null均不参与条件")
	public void testValueFilterIgnore() {
		LambdaQuery<User> query = LambdaQuery.create(User.class).valueFilter(ValueFilter.ignore(-1))
				.eq(User::getStatus, 1).eq(User::getAge, -1).eq(User::getType, 0);
		query.assemble(IDENTITY);
		assertEquals("status = :p1 AND type = :p2", normalized(query));
		assertEquals(1, query.getParams().get("p1"));
		assertEquals(0, query.getParams().get("p2"));
	}

	@Test
	@DisplayName("自引用CTE/fromFast快速失败,避免递归StackOverflow")
	public void testSelfReferenceGuards() {
		LambdaQuery<User> query = LambdaQuery.create(User.class);
		assertThrows(DataAccessException.class, () -> query.with("x", query));
		assertThrows(DataAccessException.class, () -> query.withRecursive("x", query, "id"));
		assertThrows(DataAccessException.class, () -> query.fromFast(query, "t"));
	}
}
