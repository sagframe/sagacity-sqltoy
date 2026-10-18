package org.sagacity.sqltoy.lambda.join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.LambdaFn;

/**
 * @description selectAll的字段排除定义:<code>selectAll(ExcludeCols.of(User::getStatus, User::getSalary))</code>
 *              表示主表全部字段中排除指定属性,字段引用须属于主表(from)实体
 * @param <T> 主表实体类型
 * @author zhongxuchen
 */
public final class ExcludeCols<T> {

	private final List<LambdaFn<T, ?>> columns;

	private ExcludeCols(List<LambdaFn<T, ?>> columns) {
		this.columns = columns;
	}

	/**
	 * @param columns 需要排除的字段引用
	 * @return 排除定义
	 */
	@SafeVarargs
	public static <E> ExcludeCols<E> of(LambdaFn<E, ?>... columns) {
		if (columns == null || columns.length == 0) {
			throw new DataAccessException("ExcludeCols.of requires at least one column");
		}
		return new ExcludeCols<>(new ArrayList<>(Arrays.asList(columns)));
	}

	public List<LambdaFn<T, ?>> getColumns() {
		return columns;
	}
}
