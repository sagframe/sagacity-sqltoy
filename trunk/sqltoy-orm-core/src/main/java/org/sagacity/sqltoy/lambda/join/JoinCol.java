package org.sagacity.sqltoy.lambda.join;

import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.lambda.engine.SqlFunction;

/**
 * @description 多表join查询的select列定义,支持聚合函数(count/sum/avg/min/max)、别名与原生sql片段,别名用于结果对象属性映射
 *              <p>
 *              例:
 *              JoinCol.of(User::getStaffName).as("staffName")、JoinCol.count(Role::getId).as("roleCount")、
 *              JoinCol.raw("ROW_NUMBER() OVER(ORDER BY id)").as("rn")
 *              </p>
 * @author zhongxuchen
 */
public final class JoinCol {

	private final LambdaFn<?, ?> column;

	/**
	 * 聚合函数,可为null表示原始列
	 */
	private final SqlFunction function;

	/**
	 * 原生sql片段(非null时为raw列,column/function均为null),原样拼入SELECT
	 */
	private final String rawSql;

	/**
	 * 结果别名
	 */
	private String alias;

	private JoinCol(LambdaFn<?, ?> column, SqlFunction function) {
		this(column, function, null);
	}

	private JoinCol(LambdaFn<?, ?> column, SqlFunction function, String rawSql) {
		this.column = column;
		this.function = function;
		this.rawSql = rawSql;
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
	 * @description 原生sql片段列:用于类型化select表达不了的形态(窗口函数ROW_NUMBER() OVER(..)、数据库特有函数、
	 *              CASE WHEN、算术表达式等)。别名可经as()设置,或直接在片段内写"... AS rn";片段原样拼入SELECT,
	 *              会触发join/SQL通道(结果须映射到含该别名字段的VO)。
	 *              <p>
	 *              安全:与last(sql)同为原始SQL逃逸口,不做注入筛查,禁止拼入终端用户输入
	 *              </p>
	 * @param sqlFragment 原生sql片段,如 "ROW_NUMBER() OVER(ORDER BY id)" 或 "ROW_NUMBER()
	 *                    OVER(ORDER BY id) AS rn"
	 * @return raw列
	 */
	public static JoinCol raw(String sqlFragment) {
		if (sqlFragment == null || sqlFragment.trim().isEmpty()) {
			throw new DataAccessException("JoinCol.raw requires a non-blank sql fragment");
		}
		return new JoinCol(null, null, sqlFragment.trim());
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

	public String getRawSql() {
		return rawSql;
	}

	public String getAlias() {
		return alias;
	}
}
