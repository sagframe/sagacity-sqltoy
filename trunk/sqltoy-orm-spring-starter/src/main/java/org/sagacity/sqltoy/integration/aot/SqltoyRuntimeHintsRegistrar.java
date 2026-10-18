package org.sagacity.sqltoy.integration.aot;

import java.lang.invoke.SerializedLambda;
import java.util.stream.Stream;

import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.ReverseModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.config.model.TreeSortModel;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.configure.SqlToyContextProperties;
import org.sagacity.sqltoy.configure.SqlToyContextTaskPoolProperties;
import org.sagacity.sqltoy.plugins.id.impl.DefaultIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.NanoTimeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.RedisIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.SnowflakeIdGenerator;
import org.sagacity.sqltoy.plugins.id.impl.UUIDGenerator;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Spring AOT 运行时 hints 注册：为配置属性类、主键生成器及缓存翻译相关资源注册反射与资源访问 hints
 * <p>
 * update 2026-10-18 (6.0.8)：补充 sql/翻译配置 XML 解析经 XMLUtil.setAttributes 反射赋值的模型类，
 * 否则 native 运行期解析 <unpivot>/<tree-sort>/<cols-chain-relative> 等标签时属性静默丢失
 *
 * @author limliu
 * @since 5.6
 */
class SqltoyRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		Stream.of("sqltoy-translate.xml", "translates/**", "sqltoy/**")
				.forEach(x -> hints.resources().registerPattern(x));
		Stream.of(SqlToyContextProperties.class, SqlToyContextTaskPoolProperties.class, DefaultIdGenerator.class,
				NanoTimeIdGenerator.class, RedisIdGenerator.class, SnowflakeIdGenerator.class, UUIDGenerator.class)
				.forEach(x -> hints.reflection().registerType(x, MemberCategory.values()));
		// XMLUtil.setAttributes 反射赋值的 xml 解析模型类(sql 结果处理器 + 缓存翻译配置)
		Stream.of(UnpivotModel.class, ColsChainRelativeModel.class, RowsChainRelativeModel.class, ReverseModel.class,
				TreeSortModel.class, DefaultConfig.class, TranslateConfigModel.class, CheckerConfigModel.class)
				.forEach(x -> hints.reflection().registerType(x, MemberCategory.values()));
		// lambda 条件体系(6.0.8)经 writeReplace/SerializedLambda 序列化镜像提取属性元数据,
		// native 下 SerializedLambda 需要 java 序列化注册,否则 lambdaQuery/lambdaUpdate 全部不可用;
		// 镜像类 org.sagacity.sqltoy.lambda.toolkit.SerializedLambda 是反序列化(resolveClass
		// 重定向)的目标,同样需注册
		hints.serialization().registerType(SerializedLambda.class);
		hints.serialization().registerType(org.sagacity.sqltoy.lambda.toolkit.SerializedLambda.class);
	}
}