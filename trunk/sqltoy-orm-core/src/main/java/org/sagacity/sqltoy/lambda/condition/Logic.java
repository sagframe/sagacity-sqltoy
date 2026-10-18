package org.sagacity.sqltoy.lambda.condition;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @description 逻辑连接与流式控制接口:and/or连接符、嵌套条件、not取反、末尾sql追加、条件消费函数;
 *              形状源自MyBatis-Plus的Join与Nested接口(Apache-2.0)经sqltoy-plus移植,本次重构合并改写
 * @param <Children> 具体的条件构建类型
 * @author zhongxuchen
 */
public interface Logic<Children> {

	/**
	 * ignore
	 */
	default Children and() {
		return and(true);
	}

	/**
	 * 拼接 AND 连接符
	 */
	Children and(boolean condition);

	/**
	 * ignore
	 */
	default Children or() {
		return or(true);
	}

	/**
	 * 拼接 OR 连接符
	 */
	Children or(boolean condition);

	/**
	 * ignore
	 */
	default Children and(Function<Children, Children> function) {
		return and(true, function);
	}

	/**
	 * AND 嵌套
	 * <p>例: and(n -&gt; n.eq(User::getStatus, 1).eq(User::getType, 2))</p>
	 */
	Children and(boolean condition, Function<Children, Children> function);

	/**
	 * ignore
	 */
	default Children or(Function<Children, Children> function) {
		return or(true, function);
	}

	/**
	 * OR 嵌套
	 * <p>例: or(n -&gt; n.eq(User::getStatus, 1).eq(User::getType, 2))</p>
	 */
	Children or(boolean condition, Function<Children, Children> function);

	/**
	 * ignore
	 */
	default Children nested(Function<Children, Children> function) {
		return nested(true, function);
	}

	/**
	 * 正常嵌套,不带AND或OR连接符
	 * <p>例: nested(n -&gt; n.eq(User::getStatus, 1).or().eq(User::getType, 2))</p>
	 */
	Children nested(boolean condition, Function<Children, Children> function);

	/**
	 * ignore
	 */
	default Children not(Function<Children, Children> function) {
		return not(true, function);
	}

	/**
	 * NOT 嵌套取反
	 */
	Children not(boolean condition, Function<Children, Children> function);

	/**
	 * ignore
	 */
	default Children last(String lastSql) {
		return last(true, lastSql);
	}

	/**
	 * 无视优化规则直接拼接到sql的最后(有sql注入风险,请谨慎使用)
	 * <p>例: last("limit 10")</p>
	 * <p>注意只能调用一次,多次调用以最后一次为准</p>
	 */
	Children last(boolean condition, String lastSql);

	/**
	 * ignore
	 */
	default Children accept(Consumer<Children> consumer) {
		return accept(true, consumer);
	}

	/**
	 * 条件消费函数,用于复用一段条件组装逻辑
	 * <p>例: .accept(n -&gt; { if (...) n.eq(User::getStatus, 1); })</p>
	 */
	Children accept(boolean condition, Consumer<Children> consumer);
}
