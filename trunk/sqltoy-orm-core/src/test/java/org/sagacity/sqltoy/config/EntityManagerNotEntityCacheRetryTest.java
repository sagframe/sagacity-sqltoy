package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.plugins.id.IdGenerator;

/**
 * F10回归:notEntityMap负缓存只能记录"确定没有@Entity注解"的类。修复前isEntity对parseEntityMeta
 * 返回null一律落负缓存,而解析抛异常时(isWarn=false被吞)同样返回null,导致启动早期主键生成器bean
 * 未就绪这类瞬时故障会把实体永久判为非实体,直到重启都无法纠正
 */
public class EntityManagerNotEntityCacheRetryTest {

	/** 首次实例化抛错、其后成功:模拟启动早期主键生成器bean未就绪 */
	public static class FlakyIdGenerator implements IdGenerator {
		public FlakyIdGenerator() {
			if (CALLS.incrementAndGet() == 1) {
				throw new IllegalStateException("simulated: the id generator bean is not ready at startup");
			}
		}

		@Override
		public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
				Date bizDate, String idJavaType, int length, int sequenceSize) {
			return null;
		}
	}

	@Entity(tableName = "t_flaky_entity")
	public static class FlakyEntity {
		// 注意:id字段必须同时带@Column才会被parseAllFields纳入解析(只认@Column/@OneToMany/@OneToOne)
		@Id(generator = "org.sagacity.sqltoy.config.EntityManagerNotEntityCacheRetryTest$FlakyIdGenerator")
		@Column(name = "ID")
		private String id;
		@Column(name = "NAME")
		private String name;
	}

	/** 没有任何注解的普通VO */
	public static class PlainVo {
		private String name;
	}

	private static final AtomicInteger CALLS = new AtomicInteger();

	@SuppressWarnings("unchecked")
	private static Map<String, String> notEntityMap(EntityManager entityManager) throws Exception {
		Field field = EntityManager.class.getDeclaredField("notEntityMap");
		field.setAccessible(true);
		return (Map<String, String>) field.get(entityManager);
	}

	@Test
	public void parseFailureDoesNotPoisonNotEntityCache() {
		EntityManager entityManager = new EntityManager();
		SqlToyContext context = new SqlToyContext();
		// 第一次:主键生成器实例化失败(bean未就绪) → 本次返回非实体
		assertFalse(entityManager.isEntity(context, FlakyEntity.class));
		// 第二次:故障已消失,必须重新尝试解析并识别为实体
		// 修复前:第一次已把类名写入notEntityMap,此处直接返回false且不再解析(永久误判)
		assertTrue(entityManager.isEntity(context, FlakyEntity.class));
	}

	@Test
	public void definitiveNonEntityIsStillNegativelyCached() throws Exception {
		EntityManager entityManager = new EntityManager();
		SqlToyContext context = new SqlToyContext();
		assertFalse(entityManager.isEntity(context, PlainVo.class));
		// 无@Entity注解是确定性结论,必须落负缓存,否则每次调用都要重新解析
		assertTrue(notEntityMap(entityManager).containsKey(PlainVo.class.getName()));
		// 解析失败(而非确定性非实体)的类不得进入负缓存
		assertFalse(notEntityMap(entityManager).containsKey(FlakyEntity.class.getName()));
	}
}
