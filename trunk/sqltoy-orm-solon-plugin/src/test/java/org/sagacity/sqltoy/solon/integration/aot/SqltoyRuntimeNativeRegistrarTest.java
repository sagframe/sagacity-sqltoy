package org.sagacity.sqltoy.solon.integration.aot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.noear.solon.aot.RuntimeNativeMetadata;
import org.noear.solon.aot.hint.ResourceHint;
import org.noear.solon.core.AppContext;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.solon.configure.SqlToyContextProperties;

/**
 * SqltoyRuntimeNativeRegistrar 单元测试:验证固定资源 pattern、按配置动态生成的资源 pattern(solon 为正则形式)、
 * 扩展类与集成类反射、实体反射的注册行为(6.0.8 GraalVM 增强,与 spring 侧 SqltoyAotHintsTest 对称)
 */
public class SqltoyRuntimeNativeRegistrarTest {

	@Entity(tableName = "sqltoy_solon_aot_test")
	public static class SolonAotTestEntity {
		@Id(strategy = "ulid")
		private String id;

		@Column(name = "USER_NAME")
		private String userName;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getUserName() {
			return userName;
		}

		public void setUserName(String userName) {
			this.userName = userName;
		}
	}

	/** 扩展类夹具:按类名配置的扩展点只需可被 Class.forName 加载即可注册反射元数据 */
	public static class SolonAotTestHandler {
	}

	/** 非 @Entity 的普通 resultType DTO,验证 nativeReflectionClasses 人工登记 */
	public static class SolonAotTestDto {
		private String userName;

		public String getUserName() {
			return userName;
		}

		public void setUserName(String userName) {
			this.userName = userName;
		}
	}

	/** 自定义主键生成器夹具 */
	public static class SolonAotCustomGenerator implements IdGenerator {
		@Override
		public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
				Date bizDate, String idJavaType, int length, int sequenceSize) {
			return null;
		}
	}

	@Entity(tableName = "sqltoy_solon_aot_custom_id")
	public static class SolonAotCustomIdEntity {
		@Id(generator = "org.sagacity.sqltoy.solon.integration.aot.SqltoyRuntimeNativeRegistrarTest$SolonAotCustomGenerator")
		@Column(name = "ID")
		private String id;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	@Test
	void registersFixedAndConfigDrivenMetadata() {
		AppContext context = new AppContext();

		SqlToyContextProperties properties = new SqlToyContextProperties();
		// 多目录:中英文逗号、分号分隔
		properties.setSqlResourcesDir("classpath:com/sqltoy/solon-aot，classpath:sqltoy-ext");
		properties.setTranslateConfig("classpath:translate-test/my-translate.xml");
		properties.setSecurePrivateKey("classpath:mock/solon_private.key");
		properties.setUnifyFieldsHandler(SolonAotTestHandler.class.getName());
		// bean 名称(不含".")不按类名注册,由 solon aot 自行保障
		properties.setTypeHandler("solonAotTypeHandlerBean");
		// 简化别名展开为内置实现类
		properties.setSqlFormater("default");
		properties.setCacheType("caffeine");
		properties.setNativeReflectionClasses(new String[] { SolonAotTestDto.class.getName() });
		properties.setNativeReflectionPackages(
				new String[] { "org.sagacity.sqltoy.solon.integration.aot.dto" });
		properties.setNativeEntityProxies(new String[] { SolonAotTestEntity.class.getName() });
		context.wrapAndPut(SqlToyContextProperties.class, properties);

		SqlToyContext sqlToyContext = new SqlToyContext();
		// 走即用即加载路径动态注册实体(与运行期行为一致)
		sqlToyContext.getEntityManager().getEntityMeta(sqlToyContext, SolonAotTestEntity.class);
		sqlToyContext.getEntityManager().getEntityMeta(sqlToyContext, SolonAotCustomIdEntity.class);
		context.wrapAndPut(SqlToyContext.class, sqlToyContext);

		RuntimeNativeMetadata metadata = new RuntimeNativeMetadata();
		new SqltoyRuntimeNativeRegistrar().register(context, metadata);

		// 资源 pattern:固定项 + 按配置动态项(solon 为正则形式,目录补 /.*)
		Set<String> includes = metadata.getIncludes().stream().map(ResourceHint::getPattern)
				.collect(Collectors.toSet());
		assertTrue(includes.contains("sqltoy-translate.xml"), "fixed translate config pattern missing: " + includes);
		assertTrue(includes.contains("sqltoy/.*"), "fixed sqltoy dir pattern missing: " + includes);
		assertTrue(includes.contains("com/sqltoy/solon-aot/.*"),
				"dynamic sqlResourcesDir pattern missing: " + includes);
		assertTrue(includes.contains("sqltoy-ext/.*"), "multi-dir split pattern missing: " + includes);
		assertTrue(includes.contains("translate-test/my-translate.xml"),
				"translateConfig file pattern missing: " + includes);
		assertTrue(includes.contains("mock/solon_private.key"), "securePrivateKey pattern missing: " + includes);

		// 反射:实体 + 按类名扩展类 + sqlFormater 别名展开 + cacheType=caffeine 集成类 + 固定配置属性类
		Set<String> reflection = metadata.getReflection().keySet();
		assertTrue(reflection.contains(SolonAotTestEntity.class.getName()),
				"entity reflection missing: " + reflection);
		// update 2026-10-20 @Id(strategy="ulid")/@Id(generator=自定义类) 的生成器类注册
		assertTrue(reflection.contains("org.sagacity.sqltoy.plugins.id.impl.ULIDGenerator"),
				"ULIDGenerator reflection missing: " + reflection);
		assertTrue(reflection.contains(SolonAotCustomGenerator.class.getName()),
				"custom @Id generator reflection missing: " + reflection);
		assertTrue(reflection.contains(SolonAotTestDto.class.getName()),
				"nativeReflectionClasses DTO reflection missing: " + reflection);
		assertTrue(reflection.contains("org.sagacity.sqltoy.solon.integration.aot.dto.SolonAotOrderVO"),
				"nativeReflectionPackages VO reflection missing: " + reflection);
		assertTrue(reflection.contains(SolonAotTestEntity.class.getName() + "$$SqltoyEntityProxy"),
				"nativeEntityProxies generated proxy reflection missing: " + reflection);
		assertTrue(reflection.contains(SolonAotTestHandler.class.getName()),
				"class-name configured extension missing");
		assertTrue(reflection.contains("org.sagacity.sqltoy.plugins.formater.impl.DefaultSqlFormater"),
				"sqlFormater default alias expansion missing");
		assertTrue(
				reflection.contains("org.sagacity.sqltoy.solon.translate.cache.impl.SolonTranslateCacheManager"),
				"caffeine translate cache manager missing");
		assertTrue(reflection.contains(SqlToyContextProperties.class.getName()),
				"fixed properties reflection missing");
	}
}
