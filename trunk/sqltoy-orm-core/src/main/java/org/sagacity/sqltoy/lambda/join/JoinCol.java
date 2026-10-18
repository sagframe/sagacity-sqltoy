package org.sagacity.sqltoy.lambda.join;

import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.lambda.engine.SqlFunction;

/**
 * @description 多表join查询的select列定义,支持聚合函数(count/sum/avg/min/max)与别名,别名用于结果对象属性映射
 * <p>例: JoinCol.of(User::getStaffName).as("staffName")、JoinCol.count(Role::getId).as("roleCount")</p>
 * @author zhongxuchen
 */
public final class JoinCol {

	private final LambdaFn<?, ?> column;

	/**
	 * 聚合函数,可为null表示原始列
	 */
	private final SqlFunction function;

	/**
	 * 结果别名
	 */
	private String alias;

	private JoinCol(LambdaFn<?, ?> column, SqlFunction function) {
		this.column = column;
		this.function = function;
	}

	public static <E, R> JoinCol of(LambdaFn<E, R> column) {
		return new JoinCol(column, null);
	}

	public static <E, R> JoinCol avg(LambdaFn<E, R> column) {
		return new JoinCol(column, SqlFunction.AVG);
	}

	public static <E, R> JoinCol count(LambdaFn<E, R> column) {
		return new JoinCol(column, SqlFunction.COUNT);
	}

	public static <E, R> JoinCol sum(LambdaFn<E, R> column) {
		return new JoinCol(column, SqlFunction.SUM);
	}

	public static <E, R> JoinCol min(LambdaFn<E, R> column) {
		return new JoinCol(column, SqlFunction.MIN);
	}

	public static <E, R> JoinCol max(LambdaFn<E, R> column) {
		return new JoinCol(column, SqlFunction.MAX);
	}

	/**
	 * @description 设置结果别名,用于查询结果对象按属性名映射
	 * @return 自身
	 */
	public JoinCol as(String alias) {
		this.alias = alias;
		return this;
	}

	public LambdaFn<?, ?> getColumn() {
		return column;
	}

	public SqlFunction getFunction() {
		return function;
	}

	public String getAlias() {
		return alias;
	}
}
