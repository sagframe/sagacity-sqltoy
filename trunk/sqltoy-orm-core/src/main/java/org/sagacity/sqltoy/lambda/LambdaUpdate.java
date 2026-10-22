package org.sagacity.sqltoy.lambda;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.dao.LambdaDao;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.engine.LambdaTypeChecker;
import org.sagacity.sqltoy.lambda.engine.SqlKeyword;
import org.sagacity.sqltoy.lambda.join.JoinContext;
import org.sagacity.sqltoy.lambda.join.JoinContext.Operate;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @description lambda更新条件构建器,核心职责是带条件的更新:set/setExpr指定更新字段,条件限定更新范围;
 *              单表与多表join统一入口:不调用join步骤即单表(条件转EntityUpdate复用updateByQuery链路),
 *              调用leftJoin/rightJoin/innerJoin即切换为join更新(生成可移植的
 *              <code>UPDATE 目标表 SET 列 = :pN WHERE EXISTS (SELECT 1 FROM (join派生表) sqd WHERE ...)</code>,
 *              经executeSql执行,不依赖MySQL多表UPDATE语法;主表隐式为更新实体,无需from);
 *              整对象更新(按主键)请直接使用lightDao.update(entity);
 *              通过<code>lightDao.lambdaUpdate(entityClass)</code>创建时已绑定执行dao,可直接调用update终端方法
 * @param <T> 实体类型
 * @author zhongxuchen
 */
public final class LambdaUpdate<T> extends AbstractWhere<T, LambdaUpdate<T>> {

	/**
	 * 绑定的执行dao,为null时终端方法不可用
	 */
	private LambdaDao dao;

	/**
	 * 延迟set操作列表,装配时按通道物化:单表物化为"属性名=值",join物化为"别名.列 = :pN"
	 */
	private final List<SetOp> setOps = new ArrayList<>();

	/**
	 * 单表通道物化后的set字段与值,保持设置顺序
	 */
	private final Map<String, Object> setFields = new LinkedHashMap<>();

	/**
	 * set操作定义:普通赋值或setExpr表达式
	 */
	private static final class SetOp {

		final LambdaFn<?, ?> column;

		final Object value;

		/**
		 * setExpr表达式,null表示普通赋值
		 */
		final String expression;

		SetOp(LambdaFn<?, ?> column, Object value, String expression) {
			this.column = column;
			this.value = value;
			this.expression = expression;
		}
	}

	private LambdaUpdate(Class<T> entityClass, JoinContext joinContext) {
		super(entityClass, joinContext);
	}

	public static <T> LambdaUpdate<T> create(Class<T> entityClass) {
		if (entityClass == null) {
			throw new DataAccessException("LambdaUpdate entityClass must not be null");
		}
		return new LambdaUpdate<>(entityClass, new JoinContext(Operate.UPDATE, null));
	}

	/**
	 * @description 绑定执行dao,由LightDao的lambdaUpdate工厂调用
	 */
	public LambdaUpdate<T> bind(LambdaDao dao) {
		this.dao = dao;
		return this;
	}

	/**
	 * @description 兼容旧版按LightDao类型编译的调用方,保持bind(LightDao)二进制签名
	 */
	public LambdaUpdate<T> bind(org.sagacity.sqltoy.dao.LightDao dao) {
		return bind((LambdaDao) dao);
	}

	@Override
	protected LambdaUpdate<T> instance() {
		// 嵌套条件与当前对象共享参数序号与join上下文(set操作仅根构建器持有)
		return new LambdaUpdate<>(entityClass, joinContext);
	}

	@Override
	protected ColumnResolver resolverFor(Function<Class<?>, EntityMeta> metaLoader) {
		if (isJoinMode()) {
			// join模式:别名限定列名,字段所属实体须参与join
			return (entity, fieldName) -> joinContext.columnName(entity, fieldName);
		}
		return super.resolverFor(metaLoader);
	}

	@Override
	public void assemble(Function<Class<?>, EntityMeta> metaLoader) {
		joinContext.markAssembled();
		if (expression.hasAggregatePartitions()) {
			throw new DataAccessException("groupBy/having/orderBy are only supported by LambdaQuery, not by update");
		}
		if (isJoinMode()) {
			// 先物化set再装配上下文,使set字段所属实体的join校验生效
			for (SetOp setOp : setOps) {
				if (setOp.expression == null) {
					if (typeCheck) {
						LambdaTypeChecker.check(setOp.column, setOp.value, metaLoader, "set");
					}
					joinContext.addSetColumn(setOp.column, setOp.value);
				} else {
					joinContext.addSetExpression(setOp.column, setOp.expression, setOp.value);
				}
			}
			joinContext.assemble(metaLoader);
		} else {
			for (SetOp setOp : setOps) {
				if (setOp.expression == null) {
					if (typeCheck) {
						LambdaTypeChecker.check(setOp.column, setOp.value, metaLoader, "set");
					}
					setFields.put(LambdaUtils.extractProperty(setOp.column), setOp.value);
				} else {
					setFields.put(LambdaUtils.extractProperty(setOp.column) + "=" + setOp.expression, setOp.value);
				}
			}
		}
		setOps.clear();
		super.assemble(metaLoader);
	}

	/**
	 * @return 是否走join通道:注册了join关联表
	 */
	public boolean isJoinMode() {
		return joinContext.hasJoin();
	}

	/**
	 * @description 左连接,须紧跟on()完成关联;首次调用即切换为join更新通道,主表隐式为查询实体
	 */
	public JoinOn leftJoin(Class<?> entityClass) {
		return new JoinOn(SqlKeyword.LEFT_JOIN, entityClass);
	}

	/**
	 * @description 右连接,须紧跟on()完成关联
	 */
	public JoinOn rightJoin(Class<?> entityClass) {
		return new JoinOn(SqlKeyword.RIGHT_JOIN, entityClass);
	}

	/**
	 * @description 内连接,须紧跟on()完成关联
	 */
	public JoinOn innerJoin(Class<?> entityClass) {
		return new JoinOn(SqlKeyword.INNER_JOIN, entityClass);
	}

	/**
	 * ignore
	 */
	public LambdaUpdate<T> set(LambdaFn<T, ?> column, Object val) {
		return set(true, column, val);
	}

	/**
	 * @description 设置更新字段和值,condition为false时该字段不参与更新
	 * @param condition 执行条件
	 * @param column    字段引用
	 * @param val       更新值
	 * @return 自身
	 */
	public LambdaUpdate<T> set(boolean condition, LambdaFn<T, ?> column, Object val) {
		if (column == null) {
			throw new DataAccessException("Lambda update set column must not be null");
		}
		if (condition && valueFilter.test(val)) {
			setOps.add(new SetOp(column, val, null));
		}
		return this;
	}

	/**
	 * @description 将字段显式更新为null(默认值过滤会跳过set(col,null),本方法绕过该过滤)
	 * @param column 字段引用
	 * @return 自身
	 */
	public LambdaUpdate<T> setNull(LambdaFn<T, ?> column) {
		return setNull(true, column);
	}

	/**
	 * ignore
	 */
	public LambdaUpdate<T> setNull(boolean condition, LambdaFn<T, ?> column) {
		if (column == null) {
			throw new DataAccessException("Lambda update setNull column must not be null");
		}
		if (condition) {
			setOps.add(new SetOp(column, null, null));
		}
		return this;
	}

	/**
	 * @description 表达式赋值:基于字段的当前值计算更新,如
	 *              <code>.setExpr(User::getLoginCount, "loginCount + 1")</code>、
	 *              <code>.setExpr(User::getSalary, "salary * ?", rate)</code>;
	 *              单表通道表达式中的字段以属性名书写(执行时自动转为数据库字段),可引用本表其他字段,
	 *              表达式含单个<code>?</code>或单个<code>:name</code>时绑定value;
	 *              join通道表达式原样输出(字段请写实际列名),单个<code>?</code>绑定value;
	 *              注意表达式内不支持=字符(字段赋值等号由框架组装)
	 * @param column     更新字段引用
	 * @param expression 计算表达式
	 * @return 自身
	 */
	public LambdaUpdate<T> setExpr(LambdaFn<T, ?> column, String expression) {
		return setExpr(true, column, expression, null);
	}

	/**
	 * ignore
	 */
	public LambdaUpdate<T> setExpr(LambdaFn<T, ?> column, String expression, Object value) {
		return setExpr(true, column, expression, value);
	}

	/**
	 * ignore
	 */
	public LambdaUpdate<T> setExpr(boolean condition, LambdaFn<T, ?> column, String expression) {
		return setExpr(condition, column, expression, null);
	}

	/**
	 * ignore
	 */
	public LambdaUpdate<T> setExpr(boolean condition, LambdaFn<T, ?> column, String expression, Object value) {
		if (column == null) {
			throw new DataAccessException("Lambda update setExpr column must not be null");
		}
		if (!condition) {
			return this;
		}
		if (StringUtil.isBlank(expression)) {
			throw new DataAccessException("Lambda update setExpr expression must not be blank");
		}
		String trimmed = expression.trim();
		if (trimmed.indexOf('=') >= 0) {
			throw new DataAccessException(
					"Lambda update setExpr expression must not contain '=' (the assignment equals is assembled by the framework): "
							+ expression);
		}
		// 表达式以延迟操作形式记录,单表由updateByQuery按"属性名=表达式"渲染,join由JoinContext渲染
		validateSetExpr(trimmed, value);
		setOps.add(new SetOp(column, value, trimmed));
		return this;
	}

	/**
	 * setExpr在两条通道共用同一套占位符约束,避免单表/join行为不一致或静默丢参
	 */
	private static void validateSetExpr(String expression, Object value) {
		// 引号内的 ? / :name 是字面量而非占位符(如 replace(name, ':x', '')),先掩码再统计
		String code = maskStringLiterals(expression);
		int placeholders = 0;
		int rawQuestionMarks = 0;
		for (int i = 0; i < code.length(); i++) {
			if (expression.charAt(i) == '?') {
				rawQuestionMarks++;
			}
			if (code.charAt(i) == '?') {
				placeholders++;
			}
		}
		// 字面量'?'必须拒绝:两条通道的渲染端都对原文做全量"?"改写(单表updateByQuery、join addSetExpression),
		// 引号内的'?'会被一并替换成参数名,SQL静默损坏且可能照常执行落库
		if (rawQuestionMarks != placeholders) {
			throw new DataAccessException(
					"Lambda setExpr cannot contain a literal '?' inside quotes: both channels rewrite every '?' in the raw expression into a parameter placeholder; bind the value via a real ? outside quotes or use raw sql: "
							+ expression);
		}
		if (placeholders > 1) {
			throw new DataAccessException(
					"Lambda setExpr supports at most one ? placeholder: " + expression);
		}
		if (java.util.regex.Pattern.compile(":p\\d+\\b").matcher(code).find()) {
			throw new DataAccessException(
					"Lambda setExpr must not use internal parameter names like :p1: " + expression);
		}
		boolean hasNamedParam = java.util.regex.Pattern.compile(":[A-Za-z_][A-Za-z0-9_]*").matcher(code).find();
		if (value == null && (placeholders > 0 || hasNamedParam)) {
			throw new DataAccessException(
					"Lambda setExpr has a placeholder but no value; pass a non-null value or remove the placeholder: "
							+ expression);
		}
		if (value != null && placeholders == 0 && !hasNamedParam) {
			throw new DataAccessException(
					"Lambda setExpr received a value but the expression contains no ? or :param placeholder: "
							+ expression);
		}
	}

	/**
	 * @description 将sql单引号/双引号字面量内容替换为空格,保留长度与引号外文本,供占位符统计使用;
	 *              支持''与反斜杠转义,避免 replace(name, ':x', '') 这类合法表达式被判成占位符。
	 *              掩码只用于统计:字面量':name'可安全放行(内核: name识别是字面量感知的),
	 *              字面量'?'由validateSetExpr单独拒绝(渲染端对原文全量replace会损坏它)
	 */
	private static String maskStringLiterals(String expression) {
		StringBuilder masked = new StringBuilder(expression.length());
		char quote = 0;
		for (int i = 0; i < expression.length(); i++) {
			char c = expression.charAt(i);
			if (quote == 0) {
				if (c == '\'' || c == '"') {
					quote = c;
					masked.append(' ');
				} else {
					masked.append(c);
				}
				continue;
			}
			// 字面量内部:成对引号''或反斜杠转义的引号不结束字面量
			if (c == '\\' && i + 1 < expression.length()) {
				masked.append(' ');
				masked.append(' ');
				i++;
			} else if (c == quote) {
				if (i + 1 < expression.length() && expression.charAt(i + 1) == quote) {
					masked.append(' ');
					masked.append(' ');
					i++;
				} else {
					quote = 0;
					masked.append(' ');
				}
			} else {
				masked.append(' ');
			}
		}
		return masked.toString();
	}

	/**
	 * @return 单表通道set的字段与值(assemble之后有效)
	 */
	public Map<String, Object> getSetFields() {
		return setFields;
	}

	@Override
	public String getSql() {
		// 显式matchAll的join更新没有where片段,补1=1仅用于EXISTS派生表,保持“无条件更新全部”语义
		if (isJoinMode() && isMatchAll() && getWhereSql() == null) {
			return joinContext.getSql("1 = 1", getGroupByColumns(), getHavingCondition(), getOrderByColumns(),
					getLastSql());
		}
		return super.getSql();
	}

	/**
	 * @description 按条件更新,无任何条件时须先调用matchAll();至少需要一个set字段
	 * @return 更新的记录数
	 */
	public long update() {
		return updateInternal();
	}

	private long updateInternal() {
		if (dao == null) {
			throw new DataAccessException(
					"LambdaUpdate is not bound to a dao; create it via lightDao.lambdaUpdate(entityClass) or bind a dao with bind(dao) before executing");
		}
		// assemble会把setOps物化到setFields/joinContext,重复执行时以已物化的set字段判断
		if (setOps.isEmpty() && setFields.isEmpty() && !joinContext.hasSetItems()) {
			throw new DataAccessException(
					"Lambda update requires at least one field set via set(), setNull() or setExpr()");
		}
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			requireCondition("update");
			// join更新走sqltoy既有execute()链式构建器(原生sql+命名参数),经dataSource(ds)透传显式数据源,无需给LightDao新增方法
			return dao.execute().dataSource(getDataSource()).sql(getSql())
					.entity((Serializable) new IgnoreKeyCaseMap(getParams())).submit();
		}
		return dao.updateByQuery(getEntityClass(), LambdaConvert.toEntityUpdate(this));
	}

	/**
	 * @description join关联步骤,on()完成后返回更新构建器继续添加条件或叠加join
	 */
	public final class JoinOn {

		private JoinOn(SqlKeyword joinKeyword, Class<?> entityClass) {
			// join更新的主表隐式为查询实体
			if (joinContext.getFromClass() == null) {
				joinContext.registerFrom(getEntityClass());
			}
			joinContext.registerJoin(joinKeyword, entityClass);
		}

		/**
		 * @description 登记on等值关联字段对,完成后返回更新构建器
		 */
		public <A, B> LambdaUpdate<T> on(LambdaFn<A, ?> columnA, LambdaFn<B, ?> columnB) {
			joinContext.registerOn(columnA, columnB);
			return LambdaUpdate.this;
		}

		/**
		 * @description 登记同一join的两个on等值关联字段对(自动AND),完成后返回更新构建器
		 */
		public <A, B, C, D> LambdaUpdate<T> on(LambdaFn<A, ?> columnA1, LambdaFn<B, ?> columnB1,
				LambdaFn<C, ?> columnA2, LambdaFn<D, ?> columnB2) {
			joinContext.registerOn(columnA1, columnB1, columnA2, columnB2);
			return LambdaUpdate.this;
		}
	}
}
