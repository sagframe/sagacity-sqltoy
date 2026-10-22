package org.sagacity.sqltoy.lambda.join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.LambdaFn;
import org.sagacity.sqltoy.lambda.LambdaUtils;
import org.sagacity.sqltoy.lambda.engine.SqlKeyword;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description 多表join的sql总装上下文:注册期收集实体别名、join步骤、select/set/删除表定义,
 *              assemble注入实体元信息后按SELECT/UPDATE/DELETE三种形态组装完整sql,内部使用
 * @author zhongxuchen
 */
public class JoinContext {

	/**
	 * setExpr中的命名参数(规避PostgreSQL :: 类型转换)
	 */
	private static final Pattern SET_EXPR_NAMED_PARAM = Pattern.compile("(?<!:):([A-Za-z_][A-Za-z0-9_]*)");

	public enum Operate {
		SELECT, UPDATE, DELETE
	}

	/**
	 * 表步骤:from主表、join连接、on关联条件
	 */
	private abstract static class TableStep {
	}

	private static final class FromStep extends TableStep {
		final Class<?> entity;

		FromStep(Class<?> entity) {
			this.entity = entity;
		}
	}

	private static final class JoinStep extends TableStep {
		final SqlKeyword joinKeyword;
		final Class<?> entity;

		JoinStep(SqlKeyword joinKeyword, Class<?> entity) {
			this.joinKeyword = joinKeyword;
			this.entity = entity;
		}
	}

	private static final class OnStep extends TableStep {
		final FieldNature columnA;
		final FieldNature columnB;

		OnStep(FieldNature columnA, FieldNature columnB) {
			this.columnA = columnA;
			this.columnB = columnB;
		}
	}

	/**
	 * update的set字段定义:常规形态为"列 = :pN",表达式形态为"列 = 表达式原文"(可含?pN绑定)
	 */
	private static final class SetItem {
		final FieldNature column;
		final String paramName;
		final Object value;
		final String rawExpression;

		SetItem(FieldNature column, String paramName, Object value, String rawExpression) {
			this.column = column;
			this.paramName = paramName;
			this.value = value;
			this.rawExpression = rawExpression;
		}
	}

	private final Operate operate;

	/**
	 * 查询结果类型,update/delete为null
	 */
	private final Class<?> resultClass;

	/**
	 * 实体类与别名的映射,保持join声明顺序
	 */
	private final LinkedHashMap<Class<?>, String> aliasMap = new LinkedHashMap<>();

	/**
	 * 表步骤序列:FROM、JOIN、ON按声明顺序排列
	 */
	private final List<TableStep> tableSteps = new ArrayList<>();

	/**
	 * select列定义
	 */
	private final List<JoinCol> selectColumns = new ArrayList<>();

	/**
	 * 以属性名形式指定的select列(单表查询的select在join模式下渲染为 别名.列 AS 属性)
	 */
	private final List<String> plainSelectColumns = new ArrayList<>();

	/**
	 * selectAll标记:true时主表全部字段以"别名.列 AS 属性"输出,与显式select列并存(主表字段在前)
	 */
	private boolean selectAll;

	/**
	 * selectAll(Class)指定的被join实体,其全部字段以"别名.列 AS 属性"并入select
	 */
	private final List<Class<?>> selectAllClasses = new ArrayList<>();

	/**
	 * distinct去重标记:渲染SELECT DISTINCT
	 */
	private boolean distinct;

	/**
	 * selectAll的字段排除定义,可为null
	 */
	private ExcludeCols<?> selectAllExclude;

	/**
	 * update的set字段定义
	 */
	private final List<SetItem> setItems = new ArrayList<>();

	/**
	 * delete目标实体,缺省为主表
	 */
	private final List<Class<?>> deleteClasses = new ArrayList<>();

	/**
	 * 参数序号,条件与set共享,保证参数名全局唯一;exists子查询与外层共享同一句柄
	 */
	final AtomicInteger paramSeq;

	/**
	 * exists子查询别名序号:按语句独立计数(嵌套子查询共享),保证同一语句内唯一,
	 * 同时让结构相同的查询生成完全一致的SQL文本以复用缓存
	 */
	private final AtomicInteger subQuerySeq;

	/**
	 * 别名序号
	 */
	private final AtomicInteger aliasSeq = new AtomicInteger(0);

	/**
	 * 别名前缀,exists子查询用sq前缀避免与外层单字母别名碰撞,null为类名首字母方案
	 */
	private final String aliasPrefix;

	/**
	 * 主表实体
	 */
	private Class<?> fromClass;

	/**
	 * 等待on关联的join实体,null表示无待关联的join
	 */
	private SqlKeyword pendingJoinKeyword;

	/**
	 * 实体元信息加载器,assemble时由执行侧注入
	 */
	private Function<Class<?>, EntityMeta> metaLoader;

	/**
	 * 是否已完成装配
	 */
	private boolean assembled;

	public JoinContext(Operate operate, Class<?> resultClass) {
		this(operate, resultClass, null, null);
	}

	/**
	 * @param aliasPrefix    别名前缀(exists子查询用sq前缀避免与外层单字母别名碰撞),null为类名首字母方案
	 * @param sharedParamSeq 共享参数序号(exists子查询与外层共用,保证参数名全局唯一),null为自建
	 */
	public JoinContext(Operate operate, Class<?> resultClass, String aliasPrefix, AtomicInteger sharedParamSeq) {
		this(operate, resultClass, aliasPrefix, sharedParamSeq, null);
	}

	/**
	 * @param aliasPrefix       别名前缀(exists子查询用sq前缀避免与外层单字母别名碰撞),null为类名首字母方案
	 * @param sharedParamSeq    共享参数序号(exists子查询与外层共用),null为自建
	 * @param sharedSubQuerySeq 共享exists别名序号(嵌套子查询与外层共用),null为自建
	 */
	public JoinContext(Operate operate, Class<?> resultClass, String aliasPrefix, AtomicInteger sharedParamSeq,
			AtomicInteger sharedSubQuerySeq) {
		this.operate = operate;
		this.resultClass = resultClass;
		this.aliasPrefix = aliasPrefix;
		this.paramSeq = (sharedParamSeq != null) ? sharedParamSeq : new AtomicInteger(0);
		this.subQuerySeq = (sharedSubQuerySeq != null) ? sharedSubQuerySeq : new AtomicInteger(0);
	}

	/**
	 * @description 注入实体元信息加载器并做完整性校验
	 */
	public void assemble(Function<Class<?>, EntityMeta> metaLoader) {
		// 查询场景未显式from时,缺省以结果类型作为主表(内部注册,不受装配后守卫约束)
		if (fromClass == null && resultClass != null) {
			doRegisterFrom(resultClass);
		}
		if (fromClass == null) {
			throw new DataAccessException("Lambda join requires a from entity; call from(entityClass) first");
		}
		if (pendingJoinKeyword != null) {
			throw new DataAccessException("Lambda join has joined tables without on() association");
		}
		// 校验set/删除目标/select列所属实体均已参与join
		for (SetItem setItem : setItems) {
			requireJoined(setItem.column.entityClass());
			// 单目标表UPDATE只能更新主表列;若set的是join表同名列,会静默改错目标表列
			if (!fromClass.equals(setItem.column.entityClass())) {
				throw new DataAccessException("Join update can only set columns of the target entity ["
						+ fromClass.getSimpleName() + "] but got [" + setItem.column.entityClass().getSimpleName()
						+ "]; update the joined table through its own lambdaUpdate");
			}
		}
		for (Class<?> deleteClass : deleteClasses) {
			requireJoined(deleteClass);
		}
		for (JoinCol selectColumn : selectColumns) {
			// 原生片段列(JoinCol.raw)无所属实体,跳过join参与校验
			if (selectColumn.getColumn() != null) {
				requireJoined(LambdaUtils.extractNature(selectColumn.getColumn()).entityClass());
			}
		}
		this.metaLoader = metaLoader;
	}

	/**
	 * @return 下一个命名参数名
	 */
	public String nextParamName() {
		return "p" + paramSeq.incrementAndGet();
	}

	/**
	 * @description 注册主表,必须第一个调用
	 */
	public void registerFrom(Class<?> entityClass) {
		if (entityClass == null) {
			throw new DataAccessException("Lambda from entityClass must not be null");
		}
		if (isAssembled()) {
			throw new DataAccessException(
					"Join structure cannot be modified after assemble; build joins before execution");
		}
		doRegisterFrom(entityClass);
	}

	private void doRegisterFrom(Class<?> entityClass) {
		if (fromClass != null) {
			throw new DataAccessException("Lambda join from entity can only be set once");
		}
		registerAlias(entityClass);
		tableSteps.add(new FromStep(entityClass));
		this.fromClass = entityClass;
	}

	/**
	 * @description 注册主表并指定别名(exists子查询使用sq前缀别名)
	 */
	public void registerFrom(Class<?> entityClass, String alias) {
		if (entityClass == null) {
			throw new DataAccessException("Lambda from entityClass must not be null");
		}
		if (isAssembled()) {
			throw new DataAccessException(
					"Join structure cannot be modified after assemble; build joins before execution");
		}
		if (alias == null || alias.trim().isEmpty()) {
			throw new DataAccessException("Lambda from alias must not be blank");
		}
		if (fromClass != null) {
			throw new DataAccessException("Lambda join from entity can only be set once");
		}
		for (Map.Entry<Class<?>, String> entry : aliasMap.entrySet()) {
			if (!entry.getKey().equals(entityClass) && alias.equals(entry.getValue())) {
				throw new DataAccessException(
						"Lambda from alias [" + alias + "] conflicts with an existing table alias");
			}
		}
		if (!aliasMap.containsKey(entityClass)) {
			aliasMap.put(entityClass, alias);
		}
		tableSteps.add(new FromStep(entityClass));
		this.fromClass = entityClass;
	}

	/**
	 * @description 注册join表,等待on关联;查询场景未显式from时缺省以结果类型作为主表
	 */
	public void registerJoin(SqlKeyword joinKeyword, Class<?> entityClass) {
		if (entityClass == null) {
			throw new DataAccessException("Lambda join entityClass must not be null");
		}
		if (isAssembled()) {
			throw new DataAccessException(
					"Join structure cannot be modified after assemble; build joins before execution");
		}
		if (fromClass == null && resultClass != null) {
			registerFrom(resultClass);
		}
		if (fromClass == null) {
			throw new DataAccessException("Lambda join requires the from entity to be registered before joining");
		}
		if (pendingJoinKeyword != null) {
			throw new DataAccessException("Previous join is missing on(); call on() before adding another join");
		}
		if (aliasMap.containsKey(entityClass)) {
			throw new DataAccessException("Self-join is not supported: entity [" + entityClass.getSimpleName()
					+ "] is already registered; lambda join cannot alias the same entity twice");
		}
		registerAlias(entityClass);
		tableSteps.add(new JoinStep(joinKeyword, entityClass));
		this.pendingJoinKeyword = joinKeyword;
	}

	/**
	 * @description 登记on等值关联:别名a.字段a = 别名b.字段b,字段对依次成对传入, 同一join的多个关联条件自动以AND连接
	 */
	public void registerOn(LambdaFn<?, ?>... columnPairs) {
		if (pendingJoinKeyword == null) {
			throw new DataAccessException("on() must be called right after leftJoin/rightJoin/innerJoin");
		}
		if (columnPairs == null || columnPairs.length == 0 || columnPairs.length % 2 != 0) {
			throw new DataAccessException("on() arguments must be paired join columns (even count)");
		}
		for (int i = 0; i < columnPairs.length; i += 2) {
			if (columnPairs[i] == null || columnPairs[i + 1] == null) {
				throw new DataAccessException("on() join columns must not contain null");
			}
			FieldNature natureA = LambdaUtils.extractNature(columnPairs[i]);
			FieldNature natureB = LambdaUtils.extractNature(columnPairs[i + 1]);
			if (!aliasMap.containsKey(natureA.entityClass()) || !aliasMap.containsKey(natureB.entityClass())) {
				throw new DataAccessException("on() columns belong to entities not registered by from/join");
			}
			tableSteps.add(new OnStep(natureA, natureB));
		}
		pendingJoinKeyword = null;
	}

	/**
	 * @description 注册select列
	 */
	public void addSelectColumn(JoinCol joinColumn) {
		checkOperate(Operate.SELECT, "select");
		selectColumns.add(joinColumn);
	}

	/**
	 * @description 注册属性名形式的select列(来自单表select方法)
	 */
	public void addPlainSelectColumn(String fieldName) {
		checkOperate(Operate.SELECT, "select");
		if (!plainSelectColumns.contains(fieldName)) {
			plainSelectColumns.add(fieldName);
		}
	}

	/**
	 * @description 标记查询结果去重
	 */
	public void markDistinct() {
		checkOperate(Operate.SELECT, "distinct");
		this.distinct = true;
	}

	/**
	 * @description 标记输出某个被join实体的全部字段(别名.列 AS 属性),补sqltoy-plus
	 *              select(mappingClass,...)能力
	 */
	public void markSelectAll(Class<?> entityClass) {
		checkOperate(Operate.SELECT, "selectAll");
		selectAllClasses.add(entityClass);
	}

	/**
	 * @description 标记输出主表全部字段(可与显式select列并存)
	 */
	public void markSelectAll() {
		checkOperate(Operate.SELECT, "selectAll");
		this.selectAll = true;
	}

	/**
	 * @description 标记输出主表全部字段并排除指定字段(可与显式select列并存)
	 */
	public void markSelectAll(ExcludeCols<?> excludeColumns) {
		checkOperate(Operate.SELECT, "selectAll");
		this.selectAll = true;
		this.selectAllExclude = excludeColumns;
	}

	/**
	 * @return 是否已注册join关联表
	 */
	public boolean hasJoin() {
		for (TableStep step : tableSteps) {
			if (step instanceof JoinStep) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return 是否已注册select列定义
	 */
	public boolean hasSelectColumns() {
		return !selectColumns.isEmpty() || !plainSelectColumns.isEmpty() || !selectAllClasses.isEmpty();
	}

	/**
	 * @return 是否设置了无参selectAll();该标记不进入hasSelectColumns,需单独用于聚合状态守卫
	 */
	public boolean isSelectAll() {
		return selectAll;
	}

	/**
	 * @return 是否处于join通道(注册了join或select列定义)
	 */
	public boolean isJoinChannel() {
		return forcedJoinChannel || hasJoin() || hasSelectColumns();
	}

	/**
	 * fromCte/fromFast等组合场景强制走join/SQL通道(FROM为CTE名或@fast派生表,非实体表,必须渲染完整SELECT..FROM)
	 */
	private boolean forcedJoinChannel;

	public void forceJoinChannel() {
		this.forcedJoinChannel = true;
	}

	/**
	 * @description 标记已完成装配(单表与join通道均标记),装配后不允许再变更join结构
	 */
	public void markAssembled() {
		this.assembled = true;
	}

	/**
	 * @return 是否已完成装配(装配后不允许再变更join结构)
	 */
	public boolean isAssembled() {
		return assembled;
	}

	/**
	 * @description 注册update的set字段,字段所属实体须参与join(assemble时校验)
	 */
	public <E, R> void addSetColumn(LambdaFn<E, R> column, Object value) {
		checkOperate(Operate.UPDATE, "set");
		FieldNature nature = LambdaUtils.extractNature(column);
		removeExistingSet(nature);
		setItems.add(new SetItem(nature, nextParamName(), value, null));
	}

	/**
	 * @description 同一列重复set时保留最后一次(与单表通道setFields Map覆盖语义一致)
	 */
	private void removeExistingSet(FieldNature nature) {
		setItems.removeIf(item -> item.column.entityClass() == nature.entityClass()
				&& item.column.fieldName().equals(nature.fieldName()));
	}

	/**
	 * @description 注册update的set表达式(基于当前值计算,如<code>status = status + 1</code>),
	 *              表达式原文输出,其中单个<code>?</code>或单个<code>:name</code>替换为内部命名参数并绑定value
	 */
	public <E, R> void addSetExpression(LambdaFn<E, R> column, String expression, Object value) {
		checkOperate(Operate.UPDATE, "setExpr");
		FieldNature nature = LambdaUtils.extractNature(column);
		if (value != null) {
			int placeholderCount = countChar(expression, '?');
			if (placeholderCount > 1) {
				throw new DataAccessException(
						"Lambda setExpr supports only one ? placeholder when binding a value: " + expression);
			}
			if (placeholderCount == 1) {
				removeExistingSet(nature);
				String paramName = nextParamName();
				setItems.add(new SetItem(nature, paramName, value, expression.replace("?", ":" + paramName)));
				return;
			}
			Matcher matcher = SET_EXPR_NAMED_PARAM.matcher(expression);
			if (matcher.find()) {
				int paramStart = matcher.start();
				int paramEnd = matcher.end();
				if (matcher.find()) {
					throw new DataAccessException(
							"Lambda setExpr supports only one named parameter when binding a value: " + expression);
				}
				removeExistingSet(nature);
				String paramName = nextParamName();
				String rewritten = expression.substring(0, paramStart) + ":" + paramName
						+ expression.substring(paramEnd);
				setItems.add(new SetItem(nature, paramName, value, rewritten));
				return;
			}
			throw new DataAccessException(
					"Lambda setExpr received a value but the expression contains no ? or :param placeholder: "
							+ expression);
		}
		removeExistingSet(nature);
		setItems.add(new SetItem(nature, null, null, expression));
	}

	private static int countChar(String text, char target) {
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == target) {
				count++;
			}
		}
		return count;
	}

	/**
	 * @description 注册delete目标表,缺省为主表,所属实体须参与join(assemble时校验)
	 */
	public void addDeleteClass(Class<?> entityClass) {
		checkOperate(Operate.DELETE, "deleteTables");
		if (entityClass == null) {
			throw new DataAccessException("Lambda delete target entityClass must not be null");
		}
		deleteClasses.add(entityClass);
	}

	public List<Class<?>> getDeleteClasses() {
		return deleteClasses;
	}

	/**
	 * @description 解析实体属性对应的别名限定列名,实体未参与join时抛出异常
	 */
	public String columnName(Class<?> entityClass, String fieldName) {
		requireJoined(entityClass);
		String columnName = metaLoader.apply(entityClass).getColumnName(fieldName);
		if (columnName == null) {
			throw new DataAccessException("Property [" + fieldName + "] of entity [" + entityClass.getSimpleName()
					+ "] is not a mapped database column (@Column); lambda conditions only accept mapped properties");
		}
		return aliasMap.get(entityClass) + "." + columnName;
	}

	private void requireJoined(Class<?> entityClass) {
		if (!aliasMap.containsKey(entityClass)) {
			throw new DataAccessException("Entity [" + entityClass.getSimpleName()
					+ "] does not participate in the join; join fields must come from the from or joined entities");
		}
	}

	/**
	 * @description 解析selectAll的排除属性名集合,排除字段须属于主表实体
	 */
	private Set<String> resolveExcludeFields(Class<?> fromClass) {
		if (selectAllExclude == null) {
			return Collections.emptySet();
		}
		Set<String> fields = new HashSet<>();
		for (LambdaFn<?, ?> column : selectAllExclude.getColumns()) {
			FieldNature nature = LambdaUtils.extractNature(column);
			if (!fromClass.equals(nature.entityClass())) {
				throw new DataAccessException(
						"ExcludeCols columns must belong to the from entity [" + fromClass.getSimpleName() + "]");
			}
			fields.add(nature.fieldName());
		}
		return fields;
	}

	private void checkOperate(Operate expected, String operation) {
		if (operate != expected) {
			throw new DataAccessException("Join " + operation + "() is only supported in " + expected + " mode");
		}
	}

	private void registerAlias(Class<?> entityClass) {
		if (!aliasMap.containsKey(entityClass)) {
			String alias;
			do {
				alias = (aliasPrefix != null) ? aliasPrefix + aliasSeq.incrementAndGet()
						: Character.toLowerCase(entityClass.getSimpleName().charAt(0))
								+ String.valueOf(aliasSeq.incrementAndGet());
			} while (aliasMap.containsValue(alias));
			aliasMap.put(entityClass, alias);
		}
	}

	/**
	 * @description 组装完整sql,conditionSql为where条件文本;兼容旧调用,不做1 = 1补齐
	 */
	public String getSql(String conditionSql) {
		return getSql(conditionSql, null, null, null, null);
	}

	/**
	 * @description 组装完整sql:where与groupBy/having/orderBy/last各归其位;无where时不再用1 = 1占位
	 * @param whereSql          where条件文本(可为null)
	 * @param groupByColumns    GROUP BY后的列清单(不含关键字)
	 * @param havingCondition   HAVING后的条件(不含关键字)
	 * @param orderByColumns    ORDER BY后的列清单(不含关键字)
	 * @param lastSql           last原始尾部SQL
	 */
	public String getSql(String whereSql, String groupByColumns, String havingCondition, String orderByColumns,
			String lastSql) {
		if (metaLoader == null) {
			throw new DataAccessException("Lambda join has not been assembled; getSql() is not available yet");
		}
		StringBuilder sql = new StringBuilder();
		switch (operate) {
		case SELECT:
			sql.append(SqlKeyword.SELECT.getSqlSegment()).append(" ").append(distinct ? "DISTINCT " : "")
					.append(buildSelectSql()).append(" ").append(SqlKeyword.FROM.getSqlSegment()).append(" ")
					.append(buildTableSql());
			if (StringUtil.isNotBlank(whereSql)) {
				sql.append(" ").append(SqlKeyword.WHERE.getSqlSegment()).append(" ").append(whereSql);
			}
			if (StringUtil.isNotBlank(groupByColumns)) {
				sql.append(" ").append(SqlKeyword.GROUP_BY.getSqlSegment()).append(" ").append(groupByColumns);
			}
			if (StringUtil.isNotBlank(havingCondition)) {
				sql.append(" ").append(SqlKeyword.HAVING.getSqlSegment()).append(" ").append(havingCondition);
			}
			if (StringUtil.isNotBlank(orderByColumns)) {
				sql.append(" ").append(SqlKeyword.ORDER_BY.getSqlSegment()).append(" ").append(orderByColumns);
			}
			if (StringUtil.isNotBlank(lastSql)) {
				sql.append(" ").append(lastSql);
			}
			return sql.toString();
		case UPDATE:
			requireDmlCondition(whereSql);
			rejectDmlGrouping(groupByColumns, havingCondition, orderByColumns);
			if (setItems.isEmpty()) {
				throw new DataAccessException("Lambda join update requires at least one field set via set()");
			}
			sql.append(SqlKeyword.UPDATE.getSqlSegment()).append(" ")
					.append(metaLoader.apply(fromClass).getSchemaTable(null, null)).append(" ")
					.append(SqlKeyword.SET.getSqlSegment()).append(" ").append(buildSetSql()).append(" ")
					.append(SqlKeyword.WHERE.getSqlSegment()).append(" ")
					.append(buildExistsRewrite(fromClass, whereSql));
			if (StringUtil.isNotBlank(lastSql)) {
				sql.append(" ").append(lastSql);
			}
			return sql.toString();
		case DELETE:
			requireDmlCondition(whereSql);
			rejectDmlGrouping(groupByColumns, havingCondition, orderByColumns);
			List<String> deleteSqls = getDeleteSqls(whereSql, lastSql);
			if (deleteSqls.size() > 1) {
				throw new DataAccessException(
						"Lambda delete has multiple target tables; getDeleteSqls() must be used instead of getSql()");
			}
			return deleteSqls.get(0);
		default:
			throw new DataAccessException("Unsupported lambda join operate type");
		}
	}

	private static void requireDmlCondition(String whereSql) {
		if (StringUtil.isBlank(whereSql)) {
			throw new DataAccessException("Conditional join update/delete requires where conditions");
		}
	}

	private static void rejectDmlGrouping(String groupByColumns, String havingCondition, String orderByColumns) {
		if (StringUtil.isNotBlank(groupByColumns) || StringUtil.isNotBlank(havingCondition)
				|| StringUtil.isNotBlank(orderByColumns)) {
			throw new DataAccessException(
					"groupBy/having/orderBy are not supported in join update/delete");
		}
	}

	/**
	 * @return 组装的全部参数集合(set参数+条件参数由wrapper合并)
	 */
	public Map<String, Object> getSetParams() {
		Map<String, Object> params = new LinkedHashMap<>();
		for (SetItem setItem : setItems) {
			if (setItem.paramName != null) {
				params.put(setItem.paramName, setItem.value);
			}
		}
		return params;
	}

	/**
	 * @return 是否已登记set字段(装配后用于重复执行时的set完整性判断)
	 */
	public boolean hasSetItems() {
		return !setItems.isEmpty();
	}

	/**
	 * @return 参数序号句柄,供exists子查询共享保证参数名全局唯一
	 */
	public AtomicInteger getParamSeq() {
		return paramSeq;
	}

	/**
	 * @return exists子查询别名序号句柄,供嵌套子查询共享保证同一语句内别名唯一
	 */
	public AtomicInteger getSubQuerySeq() {
		return subQuerySeq;
	}

	/**
	 * @return 下一个exists子查询别名(sq1、sq2...):按语句独立计数,结构相同的查询SQL文本稳定
	 */
	public String nextSubQueryAlias() {
		return "sq" + subQuerySeq.incrementAndGet();
	}

	/**
	 * @return 主表别名(未注册时返回null),供union根排序的派生表包裹复用同一别名
	 */
	public String getFromAlias() {
		return (fromClass == null) ? null : aliasMap.get(fromClass);
	}

	/**
	 * @return 表关联sql片段(含别名),供exists子查询拼装 SELECT 1 FROM ...
	 */
	public String getFromSql() {
		if (metaLoader == null) {
			throw new DataAccessException("Lambda join has not been assembled; getFromSql() is not available yet");
		}
		return buildTableSql();
	}

	/**
	 * @description 组装表关联sql:主表 别名 [JOIN 表 别名 ON 条件[ AND 条件]...]...
	 */
	private String buildTableSql() {
		StringBuilder tableSql = new StringBuilder();
		boolean pendingOn = false;
		for (TableStep step : tableSteps) {
			if (step instanceof FromStep) {
				tableSql.append(tableName(((FromStep) step).entity)).append(" ")
						.append(aliasMap.get(((FromStep) step).entity));
			} else if (step instanceof JoinStep) {
				JoinStep joinStep = (JoinStep) step;
				tableSql.append(" ").append(joinStep.joinKeyword.getSqlSegment()).append(" ")
						.append(tableName(joinStep.entity)).append(" ").append(aliasMap.get(joinStep.entity));
				pendingOn = true;
			} else if (step instanceof OnStep) {
				OnStep onStep = (OnStep) step;
				tableSql.append(" ").append(pendingOn ? SqlKeyword.ON : SqlKeyword.AND).append(" ")
						.append(columnName(onStep.columnA.entityClass(), onStep.columnA.fieldName())).append(" = ")
						.append(columnName(onStep.columnB.entityClass(), onStep.columnB.fieldName()));
				pendingOn = false;
			}
		}
		return tableSql.toString();
	}

	private String tableName(Class<?> entityClass) {
		// fromCte/fromFast:主表FROM用原始源(CTE名或@fast派生表)替代实体表名,列解析仍走外层结果类型EntityMeta
		if (fromRawSource != null && fromClass != null && fromClass.equals(entityClass)) {
			return fromRawSource;
		}
		// getSchemaTable携带schema配置,与实体通道口径一致
		return metaLoader.apply(entityClass).getSchemaTable(null, null);
	}

	/**
	 * 主表FROM的原始源覆盖(CTE名或@fast(...)派生表),为null时用实体表名
	 */
	private String fromRawSource;

	/**
	 * @description 设置主表FROM原始源(fromCte/fromFast),旁路实体表名解析
	 */
	public void setFromRawSource(String fromRawSource) {
		this.fromRawSource = fromRawSource;
	}

	/**
	 * @description 组装select列sql,未指定时:结果类型为主表则查询主表全部字段(带属性别名),否则*
	 */
	private String buildSelectSql() {
		List<String> columns = new ArrayList<>();
		Set<String> aliasNames = new HashSet<>();
		// selectAll:主表全部字段在前(可排除指定字段),与显式select列并存
		Set<Class<?>> dumped = new HashSet<>();
		if (selectAll && fromClass != null) {
			dumped.add(fromClass);
			EntityMeta fromMeta = metaLoader.apply(fromClass);
			String fromAlias = aliasMap.get(fromClass);
			for (String fieldName : fromMeta.getFieldsArray(false)) {
				if (!resolveExcludeFields(fromClass).contains(fieldName) && checkAlias(aliasNames, fieldName, false)) {
					columns.add(fromAlias + "." + fromMeta.getColumnName(fieldName) + " AS " + fieldName);
				}
			}
		}
		// selectAll(Class):被join实体全部字段并入(与主表重复的实体跳过,避免重复列)
		for (Class<?> entityClass : selectAllClasses) {
			if (!dumped.add(entityClass)) {
				continue;
			}
			requireJoined(entityClass);
			EntityMeta entityMeta = metaLoader.apply(entityClass);
			String alias = aliasMap.get(entityClass);
			for (String fieldName : entityMeta.getFieldsArray(false)) {
				// 与主表dump同名的列跳过(主表优先),避免结果映射歧义
				if (checkAlias(aliasNames, fieldName, false)) {
					columns.add(alias + "." + entityMeta.getColumnName(fieldName) + " AS " + fieldName);
				}
			}
		}
		if (!plainSelectColumns.isEmpty()) {
			// 属性名形式的select列归属结果类型,结果类型未参与join时无法限定别名,快速失败
			requireJoined(resultClass);
			EntityMeta entityMeta = metaLoader.apply(resultClass);
			String alias = aliasMap.get(resultClass);
			for (String fieldName : plainSelectColumns) {
				checkAlias(aliasNames, fieldName, true);
				columns.add(alias + "." + entityMeta.getColumnName(fieldName) + " AS " + fieldName);
			}
		}
		for (JoinCol joinColumn : selectColumns) {
			columns.add(buildSelectColumn(joinColumn, aliasNames));
		}
		if (!columns.isEmpty()) {
			return String.join(", ", columns);
		}
		if (selectAll) {
			throw new DataAccessException(
					"Lambda query selectAll excludes all fields of the from entity; keep at least one field");
		}
		if (aliasMap.containsKey(resultClass)) {
			EntityMeta entityMeta = metaLoader.apply(resultClass);
			String alias = aliasMap.get(resultClass);
			return Arrays.stream(entityMeta.getFieldsArray(false))
					.map(field -> alias + "." + entityMeta.getColumnName(field) + " AS " + field)
					.collect(Collectors.joining(", "));
		}
		return "*";
	}

	/**
	 * @description 别名查重:loud=true时重复即抛异常(用户显式命名的别名重复属使用错误);
	 *              loud=false时重复返回false由调用方跳过(框架dump的同名列冲突按声明顺序主表优先)
	 */
	private boolean checkAlias(Set<String> aliasNames, String alias, boolean loud) {
		if (!aliasNames.add(alias)) {
			if (loud) {
				throw new DataAccessException("Duplicate select alias [" + alias
						+ "]; duplicated aliases make result mapping ambiguous, rename via JoinCol.as()");
			}
			return false;
		}
		return true;
	}

	private String buildSelectColumn(JoinCol joinColumn, Set<String> aliasNames) {
		String columnSql;
		if (joinColumn.getRawSql() != null) {
			// 原生片段列:原样输出(别名可在片段内联,或经as()追加)
			columnSql = joinColumn.getRawSql();
		} else {
			FieldNature nature = LambdaUtils.extractNature(joinColumn.getColumn());
			String columnName = columnName(nature.entityClass(), nature.fieldName());
			columnSql = joinColumn.getFunction() != null ? joinColumn.getFunction().getSqlSegment(columnName)
					: columnName;
		}
		if (joinColumn.getAlias() != null) {
			checkAlias(aliasNames, joinColumn.getAlias(), true);
			return columnSql + " AS " + joinColumn.getAlias();
		}
		return columnSql;
	}

	private String buildSetSql() {
		return setItems.stream().map(setItem -> {
			// 可移植UPDATE的目标表不带别名,SET列名非限定(单目标表内无歧义)
			String left = metaLoader.apply(setItem.column.entityClass()).getColumnName(setItem.column.fieldName());
			return (setItem.rawExpression != null) ? left + " = " + setItem.rawExpression
					: left + " = :" + setItem.paramName;
		}).collect(Collectors.joining(", "));
	}

	/**
	 * @description 可移植的join更新/删除改写:UPDATE/DELETE 目标表不带别名,以 WHERE EXISTS (SELECT 1
	 *              FROM (join查询派生表) sqd WHERE sqd.LPKn = 目标表.主键列) 限定行, 保留join(含LEFT
	 *              JOIN)的完整语义,覆盖Oracle等无多表DML语法的全部关系型数据库
	 * @param targetClass  更新/删除目标实体
	 * @param conditionSql where条件文本
	 * @return EXISTS改写片段
	 */
	private String buildExistsRewrite(Class<?> targetClass, String conditionSql) {
		EntityMeta targetMeta = metaLoader.apply(targetClass);
		String[] idFields = targetMeta.getIdArray();
		if (idFields == null || idFields.length == 0) {
			throw new DataAccessException("Join update/delete requires @Id primary key on entity ["
					+ targetClass.getSimpleName() + "] for the portable EXISTS rewrite");
		}
		String targetAlias = aliasMap.get(targetClass);
		String schemaTable = targetMeta.getSchemaTable(null, null);
		List<String> pkSelects = new ArrayList<>();
		List<String> correlations = new ArrayList<>();
		for (int i = 0; i < idFields.length; i++) {
			String pkColumn = targetMeta.getColumnName(idFields[i]);
			String lpkAlias = "LPK" + (i + 1);
			pkSelects.add(targetAlias + "." + pkColumn + " AS " + lpkAlias);
			correlations.add("sqd." + lpkAlias + " = " + schemaTable + "." + pkColumn);
		}
		String whereText = (conditionSql == null) ? "" : conditionSql.trim();
		if (whereText.isEmpty()) {
			throw new DataAccessException("Conditional join update/delete requires where conditions");
		}
		String derived = "SELECT " + String.join(", ", pkSelects) + " FROM " + buildTableSql() + " WHERE "
				+ whereText;
		return SqlKeyword.EXISTS.getSqlSegment() + " (SELECT 1 FROM (" + derived + ") sqd WHERE "
				+ String.join(" AND ", correlations) + ")";
	}

	/**
	 * @description 可移植join删除语句列表(多删除目标时每表一条,由执行侧置于同一事务)
	 * @param conditionSql where条件文本
	 * @return 删除语句列表
	 */
	public List<String> getDeleteSqls(String conditionSql) {
		return getDeleteSqls(conditionSql, null);
	}

	/**
	 * @description 可移植join删除语句列表:where + last,无where时直接拒绝,不补1 = 1
	 * @param conditionSql where条件文本
	 * @param lastSql      last原始尾部SQL
	 * @return 删除语句列表
	 */
	public List<String> getDeleteSqls(String conditionSql, String lastSql) {
		if (metaLoader == null) {
			throw new DataAccessException("Lambda join has not been assembled; getDeleteSqls() is not available yet");
		}
		requireDmlCondition(conditionSql);
		List<Class<?>> targets = deleteClasses.isEmpty() ? java.util.Collections.singletonList(fromClass)
				: deleteClasses;
		if (targets.size() > 1) {
			throw new DataAccessException(
					"Lambda delete supports only one target table; multiple DELETE statements cannot share the same join snapshot. Call delete() once per table");
		}
		List<String> sqls = new ArrayList<>();
		for (Class<?> target : targets) {
			String deleteSql = SqlKeyword.DELETE.getSqlSegment() + " FROM "
					+ metaLoader.apply(target).getSchemaTable(null, null) + " WHERE "
					+ buildExistsRewrite(target, conditionSql);
			if (StringUtil.isNotBlank(lastSql)) {
				deleteSql = deleteSql + " " + lastSql;
			}
			sqls.add(deleteSql);
		}
		return sqls;
	}

	public Class<?> getFromClass() {
		return fromClass;
	}

	public Operate getOperate() {
		return operate;
	}

	public Class<?> getResultClass() {
		return resultClass;
	}
}
