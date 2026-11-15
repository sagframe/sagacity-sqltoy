package org.sagacity.sqltoy.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.plugins.id.IdGenerator;

/**
 * 回归:主键生成器实例的缓存作用域必须与EntityManager一致(EntityManager为每SqlToyContext一份)。
 * 修复前IdGeneratorsInstance为static的JVM级共享:首个解析实体的上下文完成initialize后(上下文绑定型
 * 生成器如RedisIdGenerator在initialize时持有该上下文的redisTemplate),后续上下文的processIdGenerator
 * 因containsKey短路而直接复用首个上下文的实例,串用了别的上下文的连接资源/@bean实例;
 * 同时静态表还会让生成器及其持有的连接工厂在context销毁后仍被强引用
 */
public class EntityManagerIdGeneratorScopeTest {

	/** 记录initialize绑定的上下文,用于断言生成器归属 */
	public static class CtxAwareIdGenerator implements IdGenerator {
		private SqlToyContext boundContext;

		@Override
		public void initialize(SqlToyContext sqlToyContext) {
			this.boundContext = sqlToyContext;
		}

		@Override
		public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
				Date bizDate, String idJavaType, int length, int sequenceSize) {
			return "1";
		}
	}

	@Entity(tableName = "t_gen_scope_a")
	public static class ScopeEntityA {
		@Id(generator = "org.sagacity.sqltoy.config.EntityManagerIdGeneratorScopeTest$CtxAwareIdGenerator")
		@Column(name = "ID")
		private String id;

		@Column(name = "NAME")
		private String name;
	}

	/** 与ScopeEntityA使用同一个生成器类名,用于验证"上下文内按名称复用" */
	@Entity(tableName = "t_gen_scope_b")
	public static class ScopeEntityB {
		@Id(generator = "org.sagacity.sqltoy.config.EntityManagerIdGeneratorScopeTest$CtxAwareIdGenerator")
		@Column(name = "ID")
		private String id;
	}

	/** @bean模式的主键生成器:由所属context的AppContext解析 */
	@Entity(tableName = "t_bean_gen_scope")
	public static class BeanScopeEntity {
		@Id(generator = "@bean(scopeIdGenerator)")
		@Column(name = "ID")
		private String id;
	}

	private static SqlToyContext contextWithBean(String beanName, Object bean) {
		final Map<String, Object> beans = new HashMap<String, Object>();
		beans.put(beanName, bean);
		SqlToyContext sqlToyContext = new SqlToyContext();
		sqlToyContext.setAppContext(new AppContext() {
			@Override
			public boolean containsBean(String name) {
				return beans.containsKey(name);
			}

			@Override
			public Object getBean(String name) {
				return beans.get(name);
			}

			@Override
			public <T> T getBean(Class<T> requiredType) {
				return null;
			}

			@Override
			public <T> Map<String, T> getBeansOfType(Class<T> type) {
				return new HashMap<String, T>();
			}
		});
		return sqlToyContext;
	}

	@Test
	public void distinctContextsMustNotShareGeneratorInstance() {
		SqlToyContext context1 = new SqlToyContext();
		SqlToyContext context2 = new SqlToyContext();
		// 同一个实体类分别由两个上下文懒解析(多Spring上下文/上下文刷新/HotSpot重部署的真实形态)
		IdGenerator generator1 = new EntityManager().getEntityMeta(context1, ScopeEntityA.class).getIdGenerator();
		IdGenerator generator2 = new EntityManager().getEntityMeta(context2, ScopeEntityA.class).getIdGenerator();
		assertNotNull(generator1);
		assertNotNull(generator2);
		// 修复前两者是同一个实例(第二个上下文被containsKey短路,连initialize都没执行)
		assertNotSame(generator1, generator2, "不同上下文不得串用同一个主键生成器实例");
		// 修复前第二个上下文拿到的实例绑定的是第一个上下文
		assertSame(context1, ((CtxAwareIdGenerator) generator1).boundContext);
		assertSame(context2, ((CtxAwareIdGenerator) generator2).boundContext);
	}

	@Test
	public void sameContextMustReuseGeneratorByName() {
		SqlToyContext context = new SqlToyContext();
		EntityManager entityManager = new EntityManager();
		IdGenerator generator1 = entityManager.getEntityMeta(context, ScopeEntityA.class).getIdGenerator();
		IdGenerator generator2 = entityManager.getEntityMeta(context, ScopeEntityB.class).getIdGenerator();
		assertNotNull(generator1);
		// 上下文内的同名生成器缓存契约不变:只实例化一次
		assertSame(generator1, generator2, "同上下文内同名生成器仍应复用同一实例");
		assertSame(context, ((CtxAwareIdGenerator) generator1).boundContext);
	}

	@Test
	public void beanGeneratorMustResolveFromOwnContext() {
		CtxAwareIdGenerator bean1 = new CtxAwareIdGenerator();
		CtxAwareIdGenerator bean2 = new CtxAwareIdGenerator();
		SqlToyContext context1 = contextWithBean("scopeIdGenerator", bean1);
		SqlToyContext context2 = contextWithBean("scopeIdGenerator", bean2);
		IdGenerator generator1 = new EntityManager().getEntityMeta(context1, BeanScopeEntity.class).getIdGenerator();
		IdGenerator generator2 = new EntityManager().getEntityMeta(context2, BeanScopeEntity.class).getIdGenerator();
		// 修复前第二个上下文会直接复用第一个上下文解析出的bean
		assertSame(bean1, generator1, "@bean生成器必须来自解析它的上下文");
		assertSame(bean2, generator2, "@bean生成器必须来自解析它的上下文");
	}
}
