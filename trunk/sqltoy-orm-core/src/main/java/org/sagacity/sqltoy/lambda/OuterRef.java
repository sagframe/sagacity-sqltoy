package org.sagacity.sqltoy.lambda;

/**
 * @description 外层查询列引用标记:在exists/notExists子查询条件中引用外层查询的列,
 *              如<code>sub.eq(Dept::getDeptId, Outer.ref(User::getDeptId))</code>;
 *              装配期按外层通道渲染为<code>表名.列</code>(单表)或<code>别名.列</code>(join)
 * @param <T> 外层实体类型
 * @author zhongxuchen
 */
public final class OuterRef<T> {

	private final LambdaFn<T, ?> column;

	OuterRef(LambdaFn<T, ?> column) {
		this.column = column;
	}

	public LambdaFn<T, ?> getColumn() {
		return column;
	}

	/**
	 * @description 引用外层查询的列,仅支持eq/ne/gt/ge/lt/le比较条件
	 * @param column 外层实体字段引用
	 * @return 外层列引用标记
	 */
	public static <T, R> OuterRef<T> of(LambdaFn<T, R> column) {
		if (column == null) {
			throw new org.sagacity.sqltoy.exception.DataAccessException("Outer.of column must not be null");
		}
		return new OuterRef<>(column);
	}
}
