package org.sagacity.sqltoy.lambda.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.ColumnResolver;
import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.lambda.LambdaSubQuery;
import org.sagacity.sqltoy.lambda.LambdaUtils;
import org.sagacity.sqltoy.lambda.OuterRef;
import org.sagacity.sqltoy.lambda.condition.Aggregate;
import org.sagacity.sqltoy.lambda.condition.Compare;
import org.sagacity.sqltoy.lambda.condition.Logic;
import org.sagacity.sqltoy.lambda.join.JoinContext;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;
import org.sagacity.sqltoy.lambda.toolkit.ValueFilter;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description lambda条件构建基础类,条件骨架源自MyBatis-Plus的AbstractWrapper(Apache-2.0)经sqltoy-plus移植,本次重构为单表与join共用引擎:实现全部比较、
 *              聚合与逻辑条件方法(字段引用按方法泛型独立推断所属实体),条件在构建时先做值过滤,
 *              再以延迟组装器形式记录,执行前通过{@link #assemble(Function)}按当前模式解析列名——
 *              无join为单表(属性名直转字段名,字段实体须为查询实体),有join为别名限定列名(字段实体须参与join);
 *              单表只是join的退化形态,是否join由是否调用join步骤自动判定
 * @param <T>    查询/更新/删除的实体类型(join更新/删除场景兼作隐式主表)
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
	 * 显式指定的数据源,为null时使用dao默认数据源;单表通道经EntityQuery/EntityUpdate透传,join通道经QueryExecutor/executeSql透传
	 */
	protected DataSource dataSource;

	/**
	 * 延迟组装器列表,assemble时按序执行
	 */
	private final List<Consumer<ColumnResolver>> assemblers = new ArrayList<>();

	/**
	 * 当前装配的元信息加载器,供exists子查询等嵌套装配复用
	 */
	protected Function<Class<?>, EntityMeta> currentMetaLoader;

	/**
	 * 无条件匹配全部的显式放行标记,仅对按条件删除/更新操作生效
	 */
	private boolean matchAll;

	protected AbstractWhere(Class<T> entityClass, JoinContext joinContext) {
		this.entityClass = entityClass;
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
		this.currentMetaLoader = metaLoader;
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
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.EQ, val);
	}

	@Override
	public <E, R> Self ne(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.NE, val);
	}

	@Override
	public <E, R> Self gt(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.GT, val);
	}

	@Override
	public <E, R> Self ge(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.GE, val);
	}

	@Override
	public <E, R> Self lt(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.LT, val);
	}

	@Override
	public <E, R> Self le(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.LE, val);
	}

	@Override
	public <E, R> Self between(boolean condition, LambdaFn<E, R> column, Object val1, Object val2) {
		if (val1 instanceof OuterRef || val2 instanceof OuterRef) {
			throw new DataAccessException("Outer.ref is only supported by eq/ne/gt/ge/lt/le comparisons");
		}
		if (!valueFilter.test(val1) || !valueFilter.test(val2)) {
			return skipCondition();
		}
		if (!condition) {
			return skipCondition();
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
		if (val1 instanceof OuterRef || val2 instanceof OuterRef) {
			throw new DataAccessException("Outer.ref is only supported by eq/ne/gt/ge/lt/le comparisons");
		}
		if (!valueFilter.test(val1) || !valueFilter.test(val2)) {
			return skipCondition();
		}
		if (!condition) {
			return skipCondition();
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
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.LIKE, wrapLike(val, true, true));
	}

	@Override
	public <E, R> Self notLike(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.NOT_LIKE, wrapLike(val, true, true));
	}

	@Override
	public <E, R> Self likeLeft(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.LIKE_LEFT, wrapLike(val, true, false));
	}

	@Override
	public <E, R> Self likeRight(boolean condition, LambdaFn<E, R> column, Object val) {
		if (!valueFilter.test(val)) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.LIKE_RIGHT, wrapLike(val, false, true));
	}

	/**
	 * @description like值按方向补通配符%,与sqltoy原生ParamsFilter.llike()/rlike()及中央解析链processLike
	 *              "值已含%则不再二次包装、仅转义_和\等特殊字符"的约定对齐:采用纯 LIKE :param 形态(数据库无关,
	 *              不依赖CONCAT,规避sqlite等库CONCAT改写为 value||'%' 使?紧跟LIKE而触发对裸值二次补%的缺陷)。
	 *              OuterRef为外层列引用(内联渲染非字面量),不补通配符。
	 * @param val   原始值
	 * @param left  是否左侧补%
	 * @param right 是否右侧补%
	 * @return 补通配符后的值(OuterRef原样返回)
	 */
	private static Object wrapLike(Object val, boolean left, boolean right) {
		if (val instanceof OuterRef) {
			return val;
		}
		String str = String.valueOf(val);
		// 与processLike约定一致:值已含%说明调用方自带通配符,不再二次包装
		if (str.indexOf('%') >= 0) {
			return str;
		}
		StringBuilder sb = new StringBuilder(str.length() + 2);
		if (left) {
			sb.append('%');
		}
		sb.append(str);
		if (right) {
			sb.append('%');
		}
		return sb.toString();
	}

	@Override
	public <E, R> Self isNull(boolean condition, LambdaFn<E, R> column) {
		if (!condition) {
			return skipCondition();
		}
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(() -> SqlCompare.IS_NULL.getMetaSql(columnName));
		}));
	}

	@Override
	public <E, R> Self isNotNull(boolean condition, LambdaFn<E, R> column) {
		if (!condition) {
			return skipCondition();
		}
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(() -> SqlCompare.IS_NOT_NULL.getMetaSql(columnName));
		}));
	}

	@Override
	public <E, R> Self in(boolean condition, LambdaFn<E, R> column, Collection<?> coll) {
		if (!valueFilter.test(coll) || coll.isEmpty()) {
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.IN, coll);
	}

	@Override
	public <E, R> Self in(boolean condition, LambdaFn<E, R> column, Object... values) {
		if (!valueFilter.test(values) || values.length == 0) {
			return skipCondition();
		}
		// 归一为list,便于数据库侧按集合展开in参数
		List<Object> valueList = new ArrayList<>(Arrays.asList(values));
		return addCompareSegment(condition, column, SqlCompare.IN, valueList);
	}

	@Override
	public <E, R> Self inSql(boolean condition, LambdaFn<E, R> column, String subSql, Map<String, Object> paramMap) {
		if (!condition) {
			return skipCondition();
		}
		if (StringUtil.isBlank(subSql)) {
			return skipCondition();
		}
		checkExternalParamNames(paramMap);
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = resolveColumn(resolver, column);
			appendSqlSegments(rawSegment(SqlCompare.IN_SUBSQL.getMetaSql(columnName, subSql), paramMap));
		}));
	}

	@Override
	public <E> Self inb(boolean condition, Collection<LambdaFn<E, ?>> columns, Collection<Object[]> values) {
		if (!condition) {
			return skipCondition();
		}
		if (columns == null || columns.isEmpty() || values == null || values.isEmpty()) {
			return skipCondition();
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
			return skipCondition();
		}
		return addCompareSegment(condition, column, SqlCompare.NOT_IN, coll);
	}

	@Override
	public <E, R> Self notIn(boolean condition, LambdaFn<E, R> column, Object... values) {
		if (!valueFilter.test(values) || values.length == 0) {
			return skipCondition();
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
		// update 2026-10-20 空集合原会生成"GROUP BY "非法片段;无列可分组时直接跳过
		if (columns == null || columns.isEmpty()) {
			return typedThis;
		}
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnNames = columns.stream().map(column -> resolveColumn(resolver, column))
					.collect(Collectors.joining(","));
			appendSqlSegments(SqlKeyword.GROUP_BY, () -> columnNames);
		}));
	}

	@Override
	public <E, R> Self orderBy(boolean condition, boolean isAsc, LambdaFn<E, R> column) {
		return addAssembler(resolver -> maybeDo(condition, () -> {
			String columnName = orderByColumn(resolver, column);
			appendSqlSegments(SqlKeyword.ORDER_BY, () -> columnName, isAsc ? SqlKeyword.ASC : SqlKeyword.DESC);
		}));
	}

	@Override
	public <E> Self orderBy(boolean condition, boolean isAsc, Collection<LambdaFn<E, ?>> columns) {
		if (columns == null || columns.isEmpty()) {
			return typedThis;
		}
		return addAssembler(resolver -> maybeDo(condition, () -> columns.forEach(column -> {
			String columnName = orderByColumn(resolver, column);
			appendSqlSegments(SqlKeyword.ORDER_BY, () -> columnName, isAsc ? SqlKeyword.ASC : SqlKeyword.DESC);
		})));
	}

	/**
	 * @description 排序字段的sql渲染;子类可覆盖以适配特殊场景(如union根排序只能用结果输出列)
	 */
	protected String orderByColumn(ColumnResolver resolver, LambdaFn<?, ?> column) {
		return resolveColumn(resolver, column);
	}

	@Override
	public Self having(boolean condition, String sqlHaving, Map<String, Object> paramMap) {
		// update 2026-10-20 空having原会生成"HAVING "非法片段;条件为真且sql为空时快速失败
		if (condition && StringUtil.isBlank(sqlHaving)) {
			throw new DataAccessException("Lambda having sql must not be blank");
		}
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
		return addAssembler(resolver -> addNestedCondition(resolver, condition, function, SqlKeyword.AND));
	}

	@Override
	public Self or(boolean condition, Function<Self, Self> function) {
		return addAssembler(resolver -> addNestedCondition(resolver, condition, function, SqlKeyword.OR));
	}

	@Override
	public Self nested(boolean condition, Function<Self, Self> function) {
		return addAssembler(resolver -> addNestedCondition(resolver, condition, function, null));
	}

	@Override
	public Self not(boolean condition, Function<Self, Self> function) {
		return addAssembler(resolver -> addNestedCondition(resolver, condition, function, SqlKeyword.NOT));
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

	/**
	 * @return 仅where条件(不含groupBy/having/orderBy/last);无真实条件返回null
	 */
	public String getWhereSql() {
		return expression.getWhereSql();
	}

	/**
	 * @return GROUP BY后的列清单(不含关键字);无分组返回null
	 */
	public String getGroupByColumns() {
		return expression.getGroupByColumns();
	}

	/**
	 * @return HAVING后的条件(不含关键字);无having返回null
	 */
	public String getHavingCondition() {
		return expression.getHavingCondition();
	}

	/**
	 * @return ORDER BY后的列清单(不含关键字);无排序返回null
	 */
	public String getOrderByColumns() {
		return expression.getOrderByColumns();
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
			return joinContext.getSql(getWhereSql(), getGroupByColumns(), getHavingCondition(), getOrderByColumns(),
					getLastSql());
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

	/**
	 * @return 是否存在orderBy排序分区
	 */
	public boolean hasOrderBy() {
		return expression.hasOrderBy();
	}

	/**
	 * @return 是否存在orderBy或last尾部片段(union根排序/限制需要移动到union之后)
	 */
	public boolean hasOrderByOrLastSql() {
		return expression.hasOrderBy() || expression.hasLastSql();
	}

	/**
	 * @return last()原始sql片段(未设置返回null)
	 */
	public String getLastSql() {
		return expression.getLastSql();
	}

	/**
	 * @return 不含orderBy/last的where/groupBy/having片段,供union根排序拆分使用
	 */
	protected String getSqlSegmentWithoutOrderBy() {
		return expression.getSqlSegmentWithoutOrderBy();
	}

	/**
	 * @return orderBy+last尾部片段,供union根排序/限制在union之后追加
	 */
	protected String getOrderByTailSql() {
		return expression.getOrderByTailSql();
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
	 * @description 显式指定本次操作使用的数据源(多数据源场景),为null时用dao默认数据源;
	 *              与sqltoy既有惯例一致(EntityQuery/QueryExecutor/lightDao.update()均有dataSource(...)),
	 *              单表通道经EntityQuery/EntityUpdate透传、join通道经QueryExecutor/executeSql透传,查询/更新/删除/count全终端生效
	 * @param dataSource 目标数据源
	 * @return 自身
	 */
	public Self dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return typedThis;
	}

	/**
	 * @return 显式指定的数据源,未指定返回null(执行侧回退dao默认数据源)
	 */
	public DataSource getDataSource() {
		return dataSource;
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
		if (column == null) {
			throw new DataAccessException("Lambda column reference must not be null");
		}
		FieldNature nature = LambdaUtils.extractNature(column);
		return resolver.columnName(nature.entityClass(), nature.fieldName());
	}

	/**
	 * @description 生成下一个命名参数名:p1、p2...;having/inSql中的外部参数请避免使用该形式命名;
	 *              join模式下由join上下文统一取号,保证set与where参数名全局唯一
	 */
	protected String nextParamName() {
		return joinContext.nextParamName();
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
		if (!condition) {
			// 显式or()后接被跳过条件时,不能把OR留给后续条件
			return skipCondition();
		}
		return addAssembler(resolver -> {
			String columnName = resolveColumn(resolver, column);
			if (val instanceof OuterRef) {
				// 外层列引用内联渲染,不做参数绑定
				appendSqlSegments(
						rawSegment(compare.getInlineMetaSql(columnName, outerRefSql((OuterRef<?>) val)), null));
				return;
			}
			appendSqlSegments(new SegmentMeta(compare, columnName, nextParamName(), val));
		});
	}

	/**
	 * @description 外层列引用的sql渲染,仅exists子查询构建器支持
	 */
	protected String outerRefSql(OuterRef<?> ref) {
		throw new DataAccessException("Outer.ref(...) is only supported inside exists/notExists sub-queries");
	}

	/**
	 * @description 外层查询的限定列解析器:join/SQL通道为别名限定,纯单表实体通道为表名限定
	 *              (子查询内关联外层列须限定,避免与子查询同名列歧义)
	 */
	protected ColumnResolver qualifiedResolverFor(Function<Class<?>, EntityMeta> metaLoader) {
		// 走join/SQL通道时FROM已带别名(即使没有实际join,如select(JoinCol)/fromCte/fromFast),
		// 此时必须用别名限定;只有纯单表实体通道才使用原表名
		if (joinContext != null && joinContext.isJoinChannel()) {
			return (entity, fieldName) -> joinContext.columnName(entity, fieldName);
		}
		return (entity, fieldName) -> {
			if (entityClass == null || !entityClass.equals(entity)) {
				throw new DataAccessException("Entity [" + entity.getSimpleName()
						+ "] does not participate in the current single-table query; Outer.ref must reference the query entity");
			}
			EntityMeta meta = metaLoader.apply(entityClass);
			String columnName = meta.getColumnName(fieldName);
			if (columnName == null) {
				throw new DataAccessException("Property [" + fieldName + "] of entity [" + entityClass.getSimpleName()
						+ "] is not a mapped database column (@Column); lambda conditions only accept mapped properties");
			}
			return meta.getSchemaTable(null, null) + "." + columnName;
		};
	}

	/**
	 * ignore
	 */
	public <E> Self exists(Class<E> subEntity, Function<LambdaSubQuery<E>, LambdaSubQuery<E>> subFn) {
		return exists(true, subEntity, subFn);
	}

	/**
	 * @description EXISTS子查询条件,子查询内可通过OuterRef引用外层查询的列
	 * @param condition 执行条件
	 * @param subEntity 子查询实体
	 * @param subFn     子查询条件组装函数
	 * @return 自身
	 */
	public <E> Self exists(boolean condition, Class<E> subEntity,
			Function<LambdaSubQuery<E>, LambdaSubQuery<E>> subFn) {
		return addExistsSegment(condition, subEntity, subFn, false);
	}

	/**
	 * ignore
	 */
	public <E> Self notExists(Class<E> subEntity, Function<LambdaSubQuery<E>, LambdaSubQuery<E>> subFn) {
		return notExists(true, subEntity, subFn);
	}

	/**
	 * @description NOT EXISTS子查询条件,见{@link #exists(boolean, Class, Function)}
	 */
	public <E> Self notExists(boolean condition, Class<E> subEntity,
			Function<LambdaSubQuery<E>, LambdaSubQuery<E>> subFn) {
		return addExistsSegment(condition, subEntity, subFn, true);
	}

	private <E> Self addExistsSegment(boolean condition, Class<E> subEntity,
			Function<LambdaSubQuery<E>, LambdaSubQuery<E>> subFn, boolean negate) {
		if (!condition) {
			return skipCondition();
		}
		return addAssembler(resolver -> maybeDo(condition, () -> {
			LambdaSubQuery<E> subQuery = new LambdaSubQuery<>(subEntity, joinContext.nextSubQueryAlias(), joinContext,
					qualifiedResolverFor(currentMetaLoader));
			subFn.apply(subQuery);
			subQuery.assemble(currentMetaLoader);
			// EXISTS子查询的where后面不能再挂groupBy/having/orderBy/last,否则会生成
			// "WHERE GROUP BY ..." 这类非法SQL;不补1 = 1,直接快速失败
			if (subQuery.getGroupByColumns() != null || subQuery.getHavingCondition() != null
					|| subQuery.getOrderByColumns() != null || subQuery.hasLastSql()) {
				throw new DataAccessException(
						"groupBy/having/orderBy/last are not supported inside exists sub-queries; move them to the outer query");
			}
			String keyword = negate ? SqlKeyword.NOT_EXISTS.getSqlSegment() : SqlKeyword.EXISTS.getSqlSegment();
			appendSqlSegments(rawSegment(keyword + " (" + subQuery.existsSql() + ")", subQuery.getParams()));
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
		List<String> rowConditions = new ArrayList<>();
		List<String> singleColumnParams = new ArrayList<>();
		Map<String, Object> params = new HashMap<>();
		for (Object[] row : values) {
			if (row == null || row.length != columnCount) {
				throw new DataAccessException("Lambda inb tuple value length must equal the column count " + columnCount
						+ " but got " + (row == null ? 0 : row.length));
			}
			List<String> columnConditions = new ArrayList<>();
			for (int i = 0; i < columnCount; i++) {
				String paramName = nextParamName();
				params.put(paramName, row[i]);
				columnConditions.add(columnNames.get(i) + " = :" + paramName);
				if (columnCount == 1) {
					singleColumnParams.add(":" + paramName);
				}
			}
			rowConditions.add("(" + String.join(" AND ", columnConditions) + ")");
		}
		// 单列退化为普通IN;多列用OR-of-ANDs展开,规避行值IN在SQLServer/DB2等库的方言差异
		String sql = (columnCount == 1) ? columnNames.get(0) + " IN (" + String.join(",", singleColumnParams) + ")"
				: "(" + String.join(" OR ", rowConditions) + ")";
		appendSqlSegments(rawSegment(sql, params));
	}

	/**
	 * @description 多重嵌套查询条件:构建独立子条件并整体以括号包裹追加
	 */
	protected Self addNestedCondition(ColumnResolver resolver, boolean condition, Function<Self, Self> function) {
		return addNestedCondition(resolver, condition, function, null);
	}

	/**
	 * @description 多重嵌套查询条件:构建独立子条件并整体以括号包裹追加;prefix(AND/OR/NOT)
	 *              仅当子条件最终非空时才追加,避免空分组留下悬挂的OR/NOT把后续条件错误串联
	 */
	protected Self addNestedCondition(ColumnResolver resolver, boolean condition, Function<Self, Self> function,
			SqlKeyword prefix) {
		return maybeDo(condition, () -> {
			Self nested = instance();
			nested.valueFilter = this.valueFilter;
			// exists/notExists等嵌套装配需要元信息加载器,必须随嵌套实例一起传递
			nested.currentMetaLoader = this.currentMetaLoader;
			Self result = function.apply(nested);
			result.assembleInternal(resolver);
			String sql = result.getSqlSegment();
			if (StringUtil.isNotBlank(sql)) {
				Map<String, Object> params = result.getParams();
				if (prefix != null) {
					appendSqlSegments(prefix);
				}
				appendSqlSegments(rawSegment("(" + sql.trim() + ")", params));
			} else {
				// prefix已延后到非空分支才拼接,空分组自身不会写连接符;此处补偿"显式or()后接空分组":
				// 取消该OR分支,避免其作用到后续条件上扩大结果范围;condition=false的禁用分组不进本分支,挂起OR保持原样
				expression.removeTrailingOr();
			}
		});
	}

	/**
	 * @description 条件被跳过时的清理:按装配顺序移除紧邻的显式OR,避免延迟装配下OR残留到后续条件
	 */
	private Self skipCondition() {
		return addAssembler(resolver -> expression.removeTrailingOr());
	}

	/**
	 * @description 以已确定的解析器执行嵌套条件的组装,避免重复走模式判定
	 */
	protected void assembleInternal(ColumnResolver resolver) {
		for (int i = 0; i < assemblers.size(); i++) {
			assemblers.get(i).accept(resolver);
		}
		assemblers.clear();
		// 括号内是where条件,分组/聚合/排序在此处无SQL语义(会生成非法的"(.. GROUP BY ..)")
		if (expression.hasAggregatePartitions()) {
			throw new DataAccessException(
					"groupBy/having/orderBy are not supported inside nested conditions; add them on the query root instead");
		}
		// last是真假条件识别之外的裸sql:放进括号会生成"(1 = 1 limit 1)"这类非法条件,
		// 空白last还会让空分组被当成有效条件、绕过删除/更新的无条件守卫,故与分组/排序同等对待
		if (expression.hasLastSql()) {
			throw new DataAccessException(
					"last() raw sql append is not supported inside nested conditions; add it on the query root instead");
		}
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
