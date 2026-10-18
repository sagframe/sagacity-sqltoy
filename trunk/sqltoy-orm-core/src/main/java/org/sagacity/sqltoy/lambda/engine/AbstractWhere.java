package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.ColumnResolver;
import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.lambda.LambdaUtils;
import org.sagacity.sqltoy.lambda.condition.Aggregate;
import org.sagacity.sqltoy.lambda.condition.Compare;
import org.sagacity.sqltoy.lambda.condition.Logic;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;
import org.sagacity.sqltoy.lambda.toolkit.ValueFilter;
import org.sagacity.sqltoy.lambda.join.JoinContext;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description lambda条件构建基础类,条件骨架源自MyBatis-Plus的AbstractWrapper(Apache-2.0)经sqltoy-plus移植,本次重构为单表与join共用引擎:实现全部比较、
 *              聚合与逻辑条件方法(字段引用按方法泛型独立推断所属实体),条件在构建时先做值过滤,
 *              再以延迟组装器形式记录,执行前通过{@link #assemble(Function)}按当前模式解析列名——
 *              无join为单表(属性名直转字段名,字段实体须为查询实体),有join为别名限定列名(字段实体须参与join);
 *              单表只是join的退化形态,是否join由是否调用join步骤自动判定
 * @param <T>    查询实体类型(join更新/删除场景可为null)
 * @param <Self> 具体构建类型
 * @author zhongxuchen
 */
public abstract class AbstractWhere<T, Self extends AbstractWhere<T, Self>>
		implements Compare<Self>, Aggregate<Self>, Logic<Self>, SqlSegment {

	/**
	 * 泛型自身引用
	 */
	@SuppressWarnings("unchecked")
	protected final Self typedThis = (Self) this;

	/**
	 * 条件片段总装容器
	 */
	protected final MergeSegments expression;

	/**
	 * 参数序号,嵌套条件与join上下文共享,保证参数名全局唯一
	 */
	protected final AtomicInteger paramSeq;

	/**
	 * 查询实体类型,join更新/删除场景为null
	 */
	protected final Class<T> entityClass;

	/**
	 * join上下文,单表模式不使用
	 */
	protected final JoinContext joinContext;

	/**
	 * 条件值过滤策略,实例级配置,默认过滤null值
	 */
	protected ValueFilter valueFilter = ValueFilter.NON_NULL;

	/**
	 * 延迟组装器列表,assemble时按序执行
	 */
	private final List<Consumer<ColumnResolver>> assemblers = new ArrayList<>();

	/**
	 * 无条件匹配全部的显式放行标记,仅对按条件删除/更新操作生效
	 */
	private boolean matchAll;

	protected AbstractWhere(Class<T> entityClass, JoinContext joinContext) {
		this(entityClass, new AtomicInteger(0), joinContext);
	}

	protected AbstractWhere(Class<T> entityClass, AtomicInteger paramSeq, JoinContext joinContext) {
		this.entityClass = entityClass;
		this.paramSeq = paramSeq;
		this.joinContext = joinContext;
		this.expression = new MergeSegments();
	}

	/**
	 * @description 创建一个与当前对象共享参数序号与join上下文的全新实例,用于嵌套条件
	 */
	protected abstract Self instance();

	/**
	 * @description 记录一个延迟组装器,执行前通过assemble统一解析列名
	 * @param sqlAssembler 组装逻辑
	 * @return 自身
	 */
	protected Self addAssembler(Consumer<ColumnResolver> sqlAssembler) {
		assemblers.add(sqlAssembler);
		return typedThis;
	}

	/**
	 * @description 解析并生成最终的条件sql,执行侧在执行前调用;重复调用幂等
	 * @param metaLoader 实体元信息加载器
	 */
	public void assemble(Function<Class<?>, EntityMeta> metaLoader) {
		if (assemblers.isEmpty()) {
			return;
		}
		ColumnResolver resolver = resolverFor(metaLoader);
		// accept消费函数可能在遍历期间追加新的组装器(如consumer内调用eq),按下标遍历确保全部执行
		for (int i = 0; i < assemblers.size(); i++) {
			assemblers.get(i).accept(resolver);
		}
		assemblers.clear();
	}

	/**
	 * @description 按当前模式提供列名解析器:子类可覆盖以切换join/单表解析策略
	 * @param metaLoader 实体元信息加载器
	 * @return (字段所属实体, 属性名)到数据库列名的解析器
	 */
	protected ColumnResolver resolverFor(Function<Class<?>, EntityMeta> metaLoader) {
		return (entity, fieldName) -> {
			if (entityClass == null || !entityClass.equals(entity)) {
				throw new DataAccessException("Entity [" + entity.getSimpleName()
						+ "] does not participate in the current single-table query; condition fields must belong to the query entity. Use leftJoin for multi-table queries");
			}
			String columnName = metaLoader.apply(entityClass).getColumnName(fieldName);
			if (columnName == null) {
				throw new DataAccessException("Property [" + fieldName + "] of entity [" + entityClass.getSimpleName()
						+ "] is not a mapped database column (@Column); lambda conditions only accept mapped properties");
			}
			return columnName;
		};
	}

	@Override
	public <E, R> Self eq(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.EQ, val);
	}

	@Override
	public <E, R> Self ne(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.NE, val);
	}

	@Override
	public <E, R> Self gt(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.GT, val);
	}

	@Override
	public <E, R> Self ge(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.GE, val);
	}

	@Override
	public <E, R> Self lt(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.LT, val);
	}

	@Override
	public <E, R> Self le(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.LE, val);
	}

	@Override
	public <E, R> Self between(boolean condition, LambdaFn<E, R> column, Object val1, Object val2) {
		if (!valueFilter.test(val1) || !valueFilter.test(val2)) {
			return typedThis;
		}
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			String paramName1 = nextParamName();
			String paramName2 = nextParamName();
			Map<String, Object> params = new HashMap<>();
			params.put(paramName1, val1);
			params.put(paramName2, val2);
			appendSqlSegments(rawSegment(SqlCompare.BETWEEN.getMetaSql(columnName, paramName1, paramName2), params));
		}));
	}

	@Override
	public <E, R> Self notBetween(boolean condition, LambdaFn<E, R> column, Object val1, Object val2) {
		if (!valueFilter.test(val1) || !valueFilter.test(val2)) {
			return typedThis;
		}
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			String paramName1 = nextParamName();
			String paramName2 = nextParamName();
			Map<String, Object> params = new HashMap<>();
			params.put(paramName1, val1);
			params.put(paramName2, val2);
			appendSqlSegments(
					rawSegment(SqlCompare.NOT_BETWEEN.getMetaSql(columnName, paramName1, paramName2), params));
		}));
	}

	@Override
	public <E, R> Self like(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.LIKE, val);
	}

	@Override
	public <E, R> Self notLike(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.NOT_LIKE, val);
	}

	@Override
	public <E, R> Self likeLeft(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.LIKE_LEFT, val);
	}

	@Override
	public <E, R> Self likeRight(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.LIKE_RIGHT, val);
	}

	@Override
	public <E, R> Self isNull(boolean condition, LambdaFn<E, R> column) {
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(() -> SqlCompare.IS_NULL.getMetaSql(columnName));
		}));
	}

	@Override
	public <E, R> Self isNotNull(boolean condition, LambdaFn<E, R> column) {
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(() -> SqlCompare.IS_NOT_NULL.getMetaSql(columnName));
		}));
	}

	@Override
	public <E, R> Self in(boolean condition, LambdaFn<E, R> column, Collection<?> coll) {
		if (!valueFilter.test(coll) || coll.isEmpty()) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.IN, coll);
	}

	@Override
	public <E, R> Self in(boolean condition, LambdaFn<E, R> column, Object... values) {
		if (!valueFilter.test(values) || values.length == 0) {
			return typedThis;
		}
		// 归一为list,便于数据库侧按集合展开in参数
		List<Object> valueList = new ArrayList<>(Arrays.asList(values));
		return addCompareSegment(condition, column, SqlCompare.IN, valueList);
	}

	@Override
	public <E, R> Self inSql(boolean condition, LambdaFn<E, R> column, String subSql, Map<String, Object> paramMap) {
		if (StringUtil.isBlank(subSql)) {
			return typedThis;
		}
		checkExternalParamNames(paramMap);
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(rawSegment(SqlCompare.IN_SUBSQL.getMetaSql(columnName, subSql), paramMap));
		}));
	}

	@Override
	public <E> Self inb(boolean condition, Collection<LambdaFn<E, ?>> columns, Collection<Object[]> values) {
		if (columns == null || columns.isEmpty() || values == null || values.isEmpty()) {
			return typedThis;
		}
		return addAssembler(resolver -> maybeDo(condition, () -> addBatchInCondition(resolver, columns, values)));
	}

	@Override
	public <E, R1, R2> Self inb(boolean condition, LambdaFn<E, R1> columnA, LambdaFn<E, R2> columnB,
			Collection<Object[]> values) {
		return inb(condition, Arrays.asList(columnA, columnB), values);
	}

	@Override
	public <E, R> Self notIn(boolean condition, LambdaFn<E, R> column, Collection<?> coll) {
		if (!valueFilter.test(coll) || coll.isEmpty()) {
			return typedThis;
		}
		return addCompareSegment(condition, column, SqlCompare.NOT_IN, coll);
	}

	@Override
	public <E, R> Self notIn(boolean condition, LambdaFn<E, R> column, Object... values) {
		if (!valueFilter.test(values) || values.length == 0) {
			return typedThis;
		}
		List<Object> valueList = new ArrayList<>(Arrays.asList(values));
		return addCompareSegment(condition, column, SqlCompare.NOT_IN, valueList);
	}

	@Override
	public <E, R> Self groupBy(boolean condition, LambdaFn<E, R> column) {
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(SqlKeyword.GROUP_BY, () -> columnName);
		}));
	}

	@Override
	public <E> Self groupBy(boolean condition, Collection<LambdaFn<E, ?>> columns) {
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnNames = columns.stream().map(column -> resolveColumn(resolver, column))
					.collect(Collectors.joining(","));
			appendSqlSegments(SqlKeyword.GROUP_BY, () -> columnNames);
		}));
	}

	@Override
	public <E, R> Self orderBy(boolean condition, boolean isAsc, LambdaFn<E, R> column) {
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(SqlKeyword.ORDER_BY, () -> columnName, isAsc ? SqlKeyword.ASC : SqlKeyword.DESC);
		}));
	}

	@Override
	public <E> Self orderBy(boolean condition, boolean isAsc, Collection<LambdaFn<E, ?>> columns) {
		return addAssembler(resolver -> maybeDo(condition, () -> columns.forEach(column -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(SqlKeyword.ORDER_BY, () -> columnName, isAsc ? SqlKeyword.ASC : SqlKeyword.DESC);
		})));
	}

	@Override
	public Self having(boolean condition, String sqlHaving, Map<String, Object> paramMap) {
		checkExternalParamNames(paramMap);
		return addAssembler(resolver -> maybeDo(condition,
				() -> appendSqlSegments(SqlKeyword.HAVING, rawSegment(sqlHaving, paramMap))));
	}

	/**
	 * @description 外部参数名不得占用内部条件参数命名空间(p加纯数字),否则合并时静默错绑
	 */
	private void checkExternalParamNames(Map<String, Object> paramMap) {
		if (paramMap == null) {
			return;
		}
		for (String key : paramMap.keySet()) {
			if (key != null && key.matches("p\\d+")) {
				throw new DataAccessException("External parameter name [" + key
						+ "] collides with internal condition parameters (p followed by digits); rename it");
			}
		}
	}

	@Override
	public Self and(boolean condition) {
		return addAssembler(resolver -> maybeDo(condition, () -> appendSqlSegments(SqlKeyword.AND)));
	}

	@Override
	public Self or(boolean condition) {
		return addAssembler(resolver -> maybeDo(condition, () -> appendSqlSegments(SqlKeyword.OR)));
	}

	@Override
	public Self and(boolean condition, Function<Self, Self> function) {
		return addAssembler(resolver -> {
			maybeDo(condition, () -> appendSqlSegments(SqlKeyword.AND));
			addNestedCondition(resolver, condition, function);
		});
	}

	@Override
	public Self or(boolean condition, Function<Self, Self> function) {
		return addAssembler(resolver -> {
			maybeDo(condition, () -> appendSqlSegments(SqlKeyword.OR));
			addNestedCondition(resolver, condition, function);
		});
	}

	@Override
	public Self nested(boolean condition, Function<Self, Self> function) {
		return addAssembler(resolver -> addNestedCondition(resolver, condition, function));
	}

	@Override
	public Self not(boolean condition, Function<Self, Self> function) {
		return addAssembler(resolver -> {
			maybeDo(condition, () -> appendSqlSegments(SqlKeyword.NOT));
			addNestedCondition(resolver, condition, function);
		});
	}

	@Override
	public Self last(boolean condition, String lastSql) {
		return addAssembler(resolver -> maybeDo(condition, () -> expression.setLastSql(lastSql)));
	}

	@Override
	public Self accept(boolean condition, Consumer<Self> consumer) {
		return addAssembler(resolver -> maybeDo(condition, () -> consumer.accept(typedThis)));
	}

	@Override
	public String getSqlSegment() {
		return expression.getSqlSegment();
	}

	@Override
	public Map<String, Object> getParams() {
		Map<String, Object> params = expression.getParams();
		if (joinContext != null) {
			params.putAll(joinContext.getSetParams());
		}
		return params;
	}

	/**
	 * @return 组装后的完整sql(单表模式即条件sql,join模式为完整语句);assemble之后调用
	 */
	public String getSql() {
		if (joinContext != null && joinContext.isJoinChannel()) {
			return joinContext.getSql(expression.getSqlSegment());
		}
		return getSqlSegment();
	}

	/**
	 * @return 是否设置了last原始sql片段
	 */
	public boolean hasLastSql() {
		return expression.hasLastSql();
	}

	/**
	 * @return 是否存在where条件
	 */
	public boolean hasCondition() {
		return expression.hasCondition();
	}

	public Class<T> getEntityClass() {
		return entityClass;
	}

	/**
	 * @description 显式声明无条件时匹配全部记录,仅对按条件删除/更新操作生效
	 * @return 自身
	 */
	public Self matchAll() {
		this.matchAll = true;
		return typedThis;
	}

	public boolean isMatchAll() {
		return matchAll;
	}

	/**
	 * @description 设置条件值过滤策略(实例级),需在添加条件之前调用
	 * @return 自身
	 */
	public Self valueFilter(ValueFilter valueFilter) {
		this.valueFilter = valueFilter;
		return typedThis;
	}

	/**
	 * @description join更新/删除执行前的条件完整性校验,由执行侧在assemble之后调用
	 * @param operation 操作名称,用于异常提示
	 */
	public void requireCondition(String operation) {
		if (!hasCondition() && !matchAll) {
			throw new DataAccessException("Lambda conditional " + operation
					+ " requires where conditions; call matchAll() explicitly to match all records");
		}
	}

	/**
	 * @description 解析字段引用对应的数据库列名
	 */
	protected String resolveColumn(ColumnResolver resolver, LambdaFn<?, ?> column) {
		FieldNature nature = LambdaUtils.extractNature(column);
		return resolver.columnName(nature.entityClass(), nature.fieldName());
	}

	/**
	 * @description 生成下一个命名参数名:p1、p2...;having/inSql中的外部参数请避免使用该形式命名;
	 *              join模式下由join上下文统一取号,保证set与where参数名全局唯一
	 */
	protected String nextParamName() {
		if (joinContext != null) {
			return joinContext.nextParamName();
		}
		return "p" + paramSeq.incrementAndGet();
	}

	/**
	 * @description 添加where片段
	 */
	protected void appendSqlSegments(SqlSegment... sqlSegments) {
		expression.add(sqlSegments);
	}

	/**
	 * @description 构造一个已生成sql文本和参数的裸片段
	 */
	protected static SqlSegment rawSegment(String sql, Map<String, Object> params) {
		return new SqlSegment() {
			@Override
			public String getSqlSegment() {
				return sql;
			}

			@Override
			public Map<String, Object> getParams() {
				return params;
			}
		};
	}

	/**
	 * @description 普通比较条件组装
	 */
	private <E, R> Self addCompareSegment(boolean condition, LambdaFn<E, R> column, SqlCompare compare, Object val) {
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(new SegmentMeta(compare, columnName, nextParamName(), val));
		}));
	}

	/**
	 * @description 多字段元组in条件,按行展开占位符:(col1,col2...) in ((:p1,:p2),(:p3,:p4)...),
	 *              每个元组值独立绑定命名参数(内核参数绑定不支持把List绑到元组位置的单个命名参数)
	 */
	private <E> void addBatchInCondition(ColumnResolver resolver, Collection<LambdaFn<E, ?>> columns,
			Collection<Object[]> values) {
		List<String> columnNames = new ArrayList<>();
		for (LambdaFn<E, ?> column : columns) {
			columnNames.add(resolveColumn(resolver, column));
		}
		int columnCount = columnNames.size();
		List<String> rowTuples = new ArrayList<>();
		Map<String, Object> params = new HashMap<>();
		for (Object[] row : values) {
			if (row == null || row.length != columnCount) {
				throw new DataAccessException("Lambda inb tuple value length must equal the column count "
						+ columnCount + " but got " + (row == null ? 0 : row.length));
			}
			List<String> tupleParams = new ArrayList<>();
			for (Object rowValue : row) {
				String paramName = nextParamName();
				params.put(paramName, rowValue);
				tupleParams.add(":" + paramName);
			}
			rowTuples.add("(" + String.join(",", tupleParams) + ")");
		}
		String sql = "(" + String.join(",", columnNames) + ") IN (" + String.join(",", rowTuples) + ")";
		appendSqlSegments(rawSegment(sql, params));
	}

	/**
	 * @description 多重嵌套查询条件:构建独立子条件并整体以括号包裹追加
	 */
	protected Self addNestedCondition(ColumnResolver resolver, boolean condition, Function<Self, Self> function) {
		return maybeDo(condition, () -> {
			Self nested = instance();
			nested.valueFilter = this.valueFilter;
			Self result = function.apply(nested);
			result.assembleInternal(resolver);
			String sql = result.getSqlSegment();
			if (StringUtil.isNotBlank(sql)) {
				Map<String, Object> params = result.getParams();
				appendSqlSegments(rawSegment("(" + sql.trim() + ")", params));
			}
		});
	}

	/**
	 * @description 以已确定的解析器执行嵌套条件的组装,避免重复走模式判定
	 */
	protected void assembleInternal(ColumnResolver resolver) {
		for (int i = 0; i < assemblers.size(); i++) {
			assemblers.get(i).accept(resolver);
		}
		assemblers.clear();
	}

	/**
	 * @description 条件成立时执行组装动作
	 */
	protected final Self maybeDo(boolean condition, Runnable action) {
		if (condition) {
			action.run();
		}
		return typedThis;
	}
}
