package org.sagacity.sqltoy.integration.aot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;

/**
 * SqltoyRuntimeHintsRegistrar 单元测试:验证固定资源 pattern、XML 解析模型类反射
 * 与 lambda 条件体系所需的序列化注册(6.0.8 GraalVM 增强)
 */
public class SqltoyRuntimeHintsRegistrarTest {

	@Test
	void registersFixedPatternsModelClassesAndSerialization() {
		RuntimeHints hints = new RuntimeHints();
		new SqltoyRuntimeHintsRegistrar().registerHints(hints, getClass().getClassLoader());

		// 固定资源 pattern
		Set<String> patterns = hints.resources().resourcePatternHints().flatMap(rp -> rp.getIncludes().stream())
				.map(include -> include.getPattern()).collect(Collectors.toSet());
		assertTrue(patterns.contains("sqltoy-translate.xml"), "translate config pattern missing: " + patterns);
		assertTrue(patterns.contains("translates/**"), "translates dir pattern missing: " + patterns);
		assertTrue(patterns.contains("sqltoy/**"), "sqltoy dir pattern missing: " + patterns);

		// 固定反射:配置属性类 + XMLUtil.setAttributes 反射赋值的解析模型类
		Set<String> reflection = hints.reflection().typeHints().map(TypeHint::getType)
				.map(ref -> ref.getName()).collect(Collectors.toSet());
		assertTrue(reflection.contains(SqlToyContextProperties.class.getName()), "properties reflection missing");
		assertTrue(reflection.contains(UnpivotModel.class.getName()), "UnpivotModel reflection missing");
		assertTrue(reflection.contains(DefaultConfig.class.getName()), "translate DefaultConfig reflection missing");

		// lambda 条件体系:JDK SerializedLambda 与 sqltoy 镜像类的 java 序列化注册
		Set<String> serialization = hints.serialization().javaSerializationHints()
				.map(hint -> hint.getType().getName()).collect(Collectors.toSet());
		assertTrue(serialization.contains("java.lang.invoke.SerializedLambda"),
				"SerializedLambda serialization missing: " + serialization);
		assertTrue(serialization.contains("org.sagacity.sqltoy.lambda.toolkit.SerializedLambda"),
				"mirror SerializedLambda serialization missing: " + serialization);
	}
}
