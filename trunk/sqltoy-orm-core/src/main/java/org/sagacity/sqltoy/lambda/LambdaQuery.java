package org.sagacity.sqltoy.lambda;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SqlWithAnalysis;
import org.sagacity.sqltoy.dao.LambdaDao;
import org.sagacity.sqltoy.dialect.utils.SqlServerDialectUtils;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.engine.SegmentList;
import org.sagacity.sqltoy.lambda.engine.SqlKeyword;
import org.sagacity.sqltoy.lambda.join.ExcludeCols;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.lambda.join.JoinContext;
import org.sagacity.sqltoy.lambda.join.JoinContext.Operate;
import org.sagacity.sqltoy.lambda.toolkit.FieldNature;
import org.sagacity.sqltoy.link.Query;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @description lambda查询构建器,单表与多表join统一入口:单表只是join的退化形态,是否join由是否
 *              调用leftJoin/rightJoin/innerJoin自动判定,无需预先选择构建器类型。
 *              无join(单表)走sqltoy实体查询通道(EntityQuery,继承分片/缓存翻译/加密脱敏等内核能力);
 *              有join拼装完整sql走原生sql通道(结果类型可与主表不同,select缺省查询主表全部字段);
 *              注意在单表查询上追加join会切换执行通道,实体级增强不再生效;
 *              条件动态化推荐IfPresent语法糖(eqIfPresent/likeIfPresent/inIfPresent等),
 *              复杂动态场景请使用sqltoy的SQL模式(sqlId加#[...]动态段与@if宏,见包说明)
 *              <p>
 *              单表示例: lightDao.lambdaQuery(User.class).eq(User::getStatus, 1)
 *              .like(User::getStaffName,
 *              "张").orderByDesc(User::getCreateTime).find();
 *              </p>
 *              <p>
 *              join示例:
 *              lightDao.lambdaQuery(StaffInfo.class).leftJoin(OrganInfo.class)
 *              .on(StaffInfo::getOrganId,
 *              OrganInfo::getOrganId).eq(StaffInfo::getStatus, 1).find();
 *              </p>
 * @param <T> 查询结果类型:单表即查询实体;join时缺省作为主表与结果类型,主表不同时通过from(entityClass)指定
 * @author zhongxuchen
 */
public final class LambdaQuery<T> extends AbstractWhere<T, LambdaQuery<T>> {

	/**
	 * 绑定的执行dao,为null时终端方法不可用
	 */
	private LambdaDao dao;

	/**
	 * select指定的查询属性名列表(单表通道直接使用,join通道渲染为别名.列 AS 属性)
	 */
	private final List<String> selectFields = new ArrayList<>();

	/**
	 * distinct去重标记
	 */
	private boolean distinct;

	/**
	 * 是否使用了select别名排序(仅join通道有效)
	 */
	private boolean aliasOrderUsed;

	/**
	 * 单表通道排除字段名(ExcludeCols在单表通道转EntityQuery.unselect)
	 */
	private final List<String> excludeFields = new ArrayList<>();

	/**
	 * 分页优化配置(并行count、count缓存keep-alive等),仅对findPage生效;为null时用全局SqlToyConfig.pageOptimize
	 */
	private PageOptimize pageOptimize;

	/**
	 * 自定义精简count
	 * SQL(仅join/SQL通道的findPage生效,经QueryExecutor.countSql);比@fast内层count更精简的极特殊场景用
	 */
	private String countSql;

	private LambdaQuery(Class<T> entityClass, JoinContext joinContext) {
		super(entityClass, joinContext);
	}

	public static <T> LambdaQuery<T> create(Class<T> entityClass) {
		if (entityClass == null) {
			throw new DataAccessException("LambdaQuery entityClass must not be null");
		}
		return new LambdaQuery<>(entityClass, new JoinContext(Operate.SELECT, entityClass));
	}

	/**
	 * @description 绑定执行dao,由LightDao的lambdaQuery工厂调用
	 */
	public LambdaQuery<T> bind(LambdaDao dao) {
		this.dao = dao;
		return this;
	}

	/**
	 * @description 兼容旧版按LightDao类型编译的调用方,保持bind(LightDao)二进制签名
	 */
	public LambdaQuery<T> bind(org.sagacity.sqltoy.dao.LightDao dao) {
		return bind((LambdaDao) dao);
	}

	@Override
	protected LambdaQuery<T> instance() {
		// 嵌套条件与当前对象共享参数序号与join上下文
		return new LambdaQuery<>(entityClass, joinContext);
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
	protected String orderByColumn(ColumnResolver resolver, LambdaFn<?, ?> column) {
		if (!unions.isEmpty()) {
			// union结果只暴露输出列(select别名=属性名),不能再用分支内的表别名限定,否则union根排序非法
			return LambdaUtils.extractProperty(column);
		}
		return super.orderByColumn(resolver, column);
	}

	/**
	 * @return 是否使用了CTE(分支若含WITH,在SQLServer的union派生表包裹中不合法)
	 */
	public boolean hasCte() {
		return !ctes.isEmpty();
	}

	public boolean isComposed() {
		return composed;
	}

	@Override
	public void assemble(Function<Class<?>, EntityMeta> metaLoader) {
		// 组合模式:先装配CTE/@fast内层(各自独立paramSeq),按序续号,保证组合后参数名全局唯一不撞号;
		// 已装配过的内层不把序号回退,避免重复执行/继续追加条件时重新从p1起号
		int seq = 0;
		for (CteDef cte : ctes) {
			cte.query.joinContext.getParamSeq()
					.set(Math.max(cte.query.joinContext.getParamSeq().get(), seq));
			cte.query.assemble(metaLoader);
			seq = cte.query.joinContext.getParamSeq().get();
		}
		if (fastInner != null) {
			fastInner.joinContext.getParamSeq()
					.set(Math.max(fastInner.joinContext.getParamSeq().get(), seq));
			fastInner.assemble(metaLoader);
			seq = fastInner.joinContext.getParamSeq().get();
			// @fast派生表:FROM @fast(内层sql),别名由registerFrom(entity,alias)提供
			joinContext.setFromRawSource("@fast(" + fastInner.getSql() + ")");
		}
		if (seq > 0) {
			joinContext.getParamSeq().set(Math.max(joinContext.getParamSeq().get(), seq));
		}
		joinContext.markAssembled();
		if (!isJoinMode() && aliasOrderUsed) {
			throw new DataAccessException(
					"Ordering by select alias requires the join channel; single-table queries have no column aliases");
		}
		if (isJoinMode()) {
			// 缺省以结果类型作为主表并做join完整性校验
			if (distinct) {
				joinContext.markDistinct();
			}
			joinContext.assemble(metaLoader);
			// 单表select指定的属性列在join通道注册渲染
			for (String fieldName : selectFields) {
				joinContext.addPlainSelectColumn(fieldName);
			}
		}
		super.assemble(metaLoader);
		// union各分支在外层之后续号装配
		int unionSeq = joinContext.getParamSeq().get();
		for (Object[] u : unions) {
			LambdaQuery<?> unionQuery = (LambdaQuery<?>) u[1];
			unionQuery.joinContext.getParamSeq()
					.set(Math.max(unionQuery.joinContext.getParamSeq().get(), unionSeq));
			unionQuery.assemble(metaLoader);
			unionSeq = unionQuery.joinContext.getParamSeq().get();
		}
		// 分支(含分支自身的union嵌套)占用到的最新序号必须回写外层,否则后续追加的条件会与分支撞号
		joinContext.getParamSeq().set(Math.max(joinContext.getParamSeq().get(), unionSeq));
	}

	/**
	 * @return 是否走join通道:注册了join关联表,或使用了聚合/别名列定义
	 */
	public boolean isJoinMode() {
		return composed || joinContext.isJoinChannel();
	}

	/**
	 * @description 指定join主表,可省略;缺省以结果类型作为主表,结果类型与主表不同时必须指定
	 * @param entityClass 主表实体
	 * @return 自身
	 */
	public LambdaQuery<T> from(Class<?> entityClass) {
		joinContext.registerFrom(entityClass);
		return this;
	}

	/**
	 * CTE定义:name+内层查询+recursive+PG物化hint
	 */
	private static final class CteDef {
		final String name;
		final LambdaQuery<?> query;
		final boolean recursive;
		final Boolean materialized;
		final String[] columns;

		CteDef(String name, LambdaQuery<?> query, boolean recursive, Boolean materialized, String[] columns) {
			this.name = name;
			this.query = query;
			this.recursive = recursive;
			this.materialized = materialized;
			this.columns = columns;
		}
	}

	private final List<CteDef> ctes = new ArrayList<>();

	/**
	 * union组合:[连接符UNION/UNION ALL, 另一查询]
	 */
	private final List<Object[]> unions = new ArrayList<>();

	private LambdaQuery<?> fastInner;

	private boolean composed;

	/**
	 * @description 注册CTE:WITH name AS
	 *              (内层查询)。内层为独立lambdaQuery(组合时共享参数序号,先装配内层再续号外层),
	 *              外层经fromCte(name)引用;多CTE以逗号串联,CTE可再引用在先CTE
	 * @param name     CTE名称
	 * @param cteQuery 内层查询(不直接执行,仅贡献SQL)
	 */
	public LambdaQuery<T> with(String name, LambdaQuery<?> cteQuery) {
		return with(name, cteQuery, null);
	}

	/**
	 * @description 注册CTE并指定物化hint(materialized仅PG12+及openGauss/金仓/Vastbase/瀚高等同内核库生效:AS
	 *              MATERIALIZED/AS NOT
	 *              MATERIALIZED,其余库忽略不渲染,详见SqlWithAnalysis.supportsCteMaterialized)
	 */
	public LambdaQuery<T> with(String name, LambdaQuery<?> cteQuery, Boolean materialized) {
		requireIdentifier(name, "CTE name");
		if (cteQuery == null) {
			throw new DataAccessException("CTE [" + name + "] requires a non-null inner query");
		}
		if (cteQuery == this) {
			throw new DataAccessException("CTE [" + name + "] cannot reference the query that declares it");
		}
		for (CteDef existing : ctes) {
			if (existing.name.equalsIgnoreCase(name)) {
				throw new DataAccessException("Duplicate CTE name [" + name + "]");
			}
		}
		// CTE体必须是完整SELECT..FROM,强制内层与当前查询都走join/SQL通道
		cteQuery.joinContext.forceJoinChannel();
		joinContext.forceJoinChannel();
		ctes.add(new CteDef(name, cteQuery, false, materialized, null));
		composed = true;
		return this;
	}

	/**
	 * @description 注册递归CTE:WITH RECURSIVE name (列清单) AS
	 *              (...)(Oracle/SQLServer递归不带RECURSIVE关键字,按dbType适配)。
	 *              <p>columns为CTE列清单:H2等库的递归CTE强制要求列清单(WITH RECURSIVE t(a,b) AS),
	 *              PG/MySQL/SQLServer亦标准支持,故递归CTE应显式给出列名(与内层select别名顺序一致)</p>
	 * @param name     CTE名称
	 * @param cteQuery 内层查询(base UNION ALL recursive)
	 * @param columns  CTE列清单,如 "id","pid","name"
	 */
	public LambdaQuery<T> withRecursive(String name, LambdaQuery<?> cteQuery, String... columns) {
		requireIdentifier(name, "CTE name");
		if (cteQuery == null) {
			throw new DataAccessException("Recursive CTE [" + name + "] requires a non-null inner query");
		}
		if (cteQuery == this) {
			throw new DataAccessException("Recursive CTE [" + name + "] cannot reference the query that declares it");
		}
		for (CteDef existing : ctes) {
			if (existing.name.equalsIgnoreCase(name)) {
				throw new DataAccessException("Duplicate CTE name [" + name + "]");
			}
		}
		if (columns != null) {
			for (String column : columns) {
				requireIdentifier(column, "CTE column");
			}
		}
		cteQuery.joinContext.forceJoinChannel();
		joinContext.forceJoinChannel();
		ctes.add(new CteDef(name, cteQuery, true, null, columns));
		composed = true;
		return this;
	}

	/**
	 * @description 外层FROM引用CTE(旁路实体表名,列解析走结果类型VO的EntityMeta);触发join/SQL通道
	 */
	public LambdaQuery<T> fromCte(String name) {
		requireIdentifier(name, "CTE name");
		joinContext.registerFrom(getEntityClass());
		joinContext.setFromRawSource(name);
		joinContext.forceJoinChannel();
		composed = true;
		return this;
	}

	/**
	 * @description @fast分页下推派生表:FROM @fast(内层sql) 别名——内层先分页取pageSize条再被外层join,
	 *              复用sqltoy既有@fast机制(分页下推+count用内层简化SQL);内层为独立lambdaQuery(过滤+排序),
	 *              外层列/关联经结果类型VO字段引用派生表列
	 * @param inner 内层查询(被分页下推)
	 * @param alias 派生表别名
	 */
	public LambdaQuery<T> fromFast(LambdaQuery<?> inner, String alias) {
		if (inner == null) {
			throw new DataAccessException("fromFast requires a non-null inner query");
		}
		if (inner == this) {
			throw new DataAccessException("fromFast inner query cannot be the outer query itself");
		}
		requireIdentifier(alias, "fromFast alias");
		// @fast内层必须是完整SELECT..FROM(被分页下推的子查询),强制走join/SQL通道
		inner.joinContext.forceJoinChannel();
		this.fastInner = inner;
		joinContext.registerFrom(getEntityClass(), alias);
		joinContext.forceJoinChannel();
		composed = true;
		return this;
	}

	/**
	 * @description UNION组合(去重):(本查询) UNION (other);两侧须映射同一结果类型,参数序号组合时统一续号;
	 *              union要求两侧都是完整SELECT,故同时把当前查询与分支强制切换到join/SQL通道。
	 *              注意:根构建器上的orderBy/last作用于整个union结果(追加在union之后);
	 *              分支构建器上的orderBy/last作用于该分支(SQLServer用派生表包裹)
	 */
	public LambdaQuery<T> union(LambdaQuery<?> other) {
		return addUnion("UNION", other);
	}

	/**
	 * @description UNION ALL组合(不去重,更快);count可复用sqltoy的union-all-count优化,排序/limit语义同union
	 */
	public LambdaQuery<T> unionAll(LambdaQuery<?> other) {
		return addUnion("UNION ALL", other);
	}

	private LambdaQuery<T> addUnion(String connector, LambdaQuery<?> other) {
		if (other == null) {
			throw new DataAccessException("union requires a non-null other query");
		}
		if (other == this) {
			throw new DataAccessException("union cannot combine a query with itself");
		}
		// union两侧都必须是完整SELECT..FROM,否则组合后的SQL不合法
		joinContext.forceJoinChannel();
		other.joinContext.forceJoinChannel();
		unions.add(new Object[] { connector, other });
		composed = true;
		return this;
	}

	/**
	 * @description CTE名称/派生表别名必须是普通标识符,避免原始文本拼入SQL造成注入或语法错误
	 */
	private static String requireIdentifier(String value, String what) {
		if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new DataAccessException(what + " must be a plain identifier but got: " + value);
		}
		return value;
	}

	@Override
	public String getSql() {
		return composed ? composeSql() : super.getSql();
	}

	@Override
	public Map<String, Object> getParams() {
		if (!composed) {
			return super.getParams();
		}
		Map<String, Object> merged = new LinkedHashMap<>();
		for (CteDef cte : ctes) {
			SegmentList.mergeParams(merged, cte.query.getParams());
		}
		if (fastInner != null) {
			SegmentList.mergeParams(merged, fastInner.getParams());
		}
		SegmentList.mergeParams(merged, super.getParams());
		for (Object[] u : unions) {
			SegmentList.mergeParams(merged, ((LambdaQuery<?>) u[1]).getParams());
		}
		return merged;
	}

	/**
	 * @description 组合SQL:[WITH name AS [MATERIALIZED] (inner)... ] mainSql [ UNION
	 *              [ALL] (other)...][ORDER BY ...][last]。union根的排序/limit必须位于整个union之后,
	 *              且只能引用输出列(不使用分支表别名);分支自带排序/limit时按方言包裹成完整的查询表达式
	 */
	private String composeSql() {
		int dbType = resolveDbType();
		StringBuilder sb = new StringBuilder();
		if (!ctes.isEmpty()) {
			// WITH必须位于语句位置0:sqltoy靠SqlWithAnalysis/hasWith从文本检测WITH以在count/分页时把WITH剥离到包裹外,
			// 前导分号会破坏该检测;SQLServer单条语句以WITH开头本就不需要前置分号(分号要求仅针对WITH前尚有未结束语句的批处理)
			sb.append("WITH ");
			boolean anyRecursive = ctes.stream().anyMatch(c -> c.recursive);
			if (anyRecursive && dbType != DBType.ORACLE && dbType != DBType.ORACLE11 && dbType != DBType.SQLSERVER) {
				sb.append("RECURSIVE ");
			}
			for (int i = 0; i < ctes.size(); i++) {
				CteDef cte = ctes.get(i);
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(cte.name);
				// 递归CTE列清单(H2等强制):WITH RECURSIVE t(a,b) AS
				if (cte.columns != null && cte.columns.length > 0) {
					sb.append(" (").append(String.join(", ", cte.columns)).append(")");
				}
				sb.append(" AS ");
				// update 2026-10-18 物化hint门禁与SqlWithAnalysis白名单单一事实源对齐:PG12+及
				// openGauss/金仓/Vastbase/瀚高(真库实测支持)才渲染MATERIALIZED,其余库忽略(不渲染即不报错)
				if (cte.materialized != null && SqlWithAnalysis.supportsCteMaterialized(dbType)) {
					sb.append(cte.materialized ? "MATERIALIZED " : "NOT MATERIALIZED ");
				}
				sb.append("(").append(cte.query.getSql()).append(")");
			}
			sb.append(" ");
		}
		if (unions.isEmpty()) {
			sb.append(super.getSql());
			return sb.toString();
		}
		// union根排序/limit:从首分支剥离,统一追加到union之后,交给sqltoy的count/分页二次包装处理
		boolean hasTail = hasOrderByOrLastSql();
		sb.append(hasTail
				? joinContext.getSql(getWhereSql(), getGroupByColumns(), getHavingCondition(), null, null)
				: super.getSql());
		int branchIndex = 0;
		for (Object[] u : unions) {
			branchIndex++;
			LambdaQuery<?> unionQuery = (LambdaQuery<?>) u[1];
			String branchSql = unionQuery.getSql();
			// 分支自带order/last时必须包裹成完整查询表达式;分支自含CTE时也需包裹(CTE不能直接跟在union后)
			if (unionQuery.hasOrderByOrLastSql() || unionQuery.hasCte()) {
				branchSql = wrapUnionBranch(unionQuery, branchSql, branchIndex, dbType);
			}
			sb.append(" ").append(u[0]).append(" ").append(branchSql);
		}
		if (hasTail) {
			sb.append(" ").append(getOrderByTailSql());
		}
		return sb.toString();
	}

	/**
	 * @description union分支包裹:普通库用括号;SQLServer不允许set操作数直接带括号且派生表内order by
	 *              必须伴随TOP/OFFSET,故用派生表包裹并复用sqltoy的legalizeDerivedInnerSql补齐offset 0
	 */
	private String wrapUnionBranch(LambdaQuery<?> branch, String branchSql, int branchIndex, int dbType) {
		if (dbType != DBType.SQLSERVER) {
			return "(" + branchSql + ")";
		}
		if (branch.hasCte()) {
			throw new DataAccessException("SQL Server does not allow a CTE inside a union branch; "
					+ "move the WITH definition to the outer query or execute the branch separately");
		}
		return "SELECT * FROM (" + SqlServerDialectUtils.legalizeDerivedInnerSql(branchSql) + ") ub" + branchIndex;
	}

	/**
	 * @return 执行库类型(未绑定dao时-1,WITH按通用形态渲染)
	 */
	private int resolveDbType() {
		if (dao == null) {
			return -1;
		}
		// 显式dataSource优先:多数据源/异构库场景下WITH RECURSIVE与MATERIALIZED的门禁必须按实际执行库判断
		DataSource target = (dataSource != null) ? dataSource : dao.getDataSource();
		return DataSourceUtils.getDBType(dao.getSqlToyContext(), target);
	}

	/**
	 * @description 左连接,须紧跟on()完成关联;首次调用即切换为join通道
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
	 * @description 查询结果去重:单表通道转EntityQuery.distinct(),join通道渲染SELECT DISTINCT
	 * @return 自身
	 */
	public LambdaQuery<T> distinct() {
		this.distinct = true;
		return this;
	}

	public boolean isDistinct() {
		return distinct;
	}

	/**
	 * @description 设置分页优化配置(仅对findPage生效):并行count、count缓存keep-alive(aliveSeconds)、
	 *              缓存容量(aliveMax)、零计数跳过(skipZeroCount)等,复用sqltoy既有{@link PageOptimize};
	 *              不设置时用全局SqlToyConfig.pageOptimize。单表通道经EntityQuery透传、join通道经QueryExecutor透传
	 * @param pageOptimize 分页优化配置,如 new
	 *                     PageOptimize().parallel(true).aliveSeconds(120)
	 * @return 自身
	 */
	public LambdaQuery<T> pageOptimize(PageOptimize pageOptimize) {
		this.pageOptimize = pageOptimize;
		return this;
	}

	public PageOptimize getPageOptimize() {
		return pageOptimize;
	}

	/**
	 * @description 设置自定义精简count
	 *              SQL(仅join/SQL通道的findPage生效):分页算总数时用它替代包裹整条查询SQL的自动count,
	 *              对应sqltoy的count-sql配置;比@fast内层count仍不够精简的极特殊场景使用。
	 *              单表通道走EntityQuery的实体count,无法承载自定义count SQL,设置后在单表findPage会快速失败
	 * @param countSql 精简的count SQL,如 select count(1) from table_a where status=:p1
	 * @return 自身
	 */
	public LambdaQuery<T> countSql(String countSql) {
		this.countSql = countSql;
		return this;
	}

	/**
	 * @description 按select别名排序(升序),用于分组聚合结果按聚合列排序,如orderByAsc("userCount");
	 *              仅join通道有效(单表通道无列别名),别名须为普通标识符
	 * @param alias select中as定义的别名
	 * @return 自身
	 */
	public LambdaQuery<T> orderByAsc(String alias) {
		return orderByAlias(true, alias);
	}

	/**
	 * @description 按select别名排序(降序),见{@link #orderByAsc(String)}
	 * @param alias select中as定义的别名
	 * @return 自身
	 */
	public LambdaQuery<T> orderByDesc(String alias) {
		return orderByAlias(false, alias);
	}

	private LambdaQuery<T> orderByAlias(boolean isAsc, String alias) {
		if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new DataAccessException("Order-by alias must be a plain identifier but got: " + alias);
		}
		aliasOrderUsed = true;
		// 延迟装配,保证与lambda orderBy的调用顺序一致
		return addAssembler(resolver -> appendSqlSegments(SqlKeyword.ORDER_BY, () -> alias,
				isAsc ? SqlKeyword.ASC : SqlKeyword.DESC));
	}

	/**
	 * @description 指定查询字段,属性名形式(单表通道与join通道通用)
	 * @param columns 查询字段引用
	 * @return 自身
	 */
	@SafeVarargs
	public final LambdaQuery<T> select(LambdaFn<T, ?>... columns) {
		if (columns != null) {
			for (LambdaFn<T, ?> column : columns) {
				if (column == null) {
					throw new DataAccessException("select columns must not contain null elements");
				}
				selectFields.add(LambdaUtils.extractProperty(column));
			}
		}
		return this;
	}

	/**
	 * @description 缺省输出主表全部字段(别名.列 AS 属性),可与select(JoinCol...)的额外列并存
	 *              (主表字段在前,额外列在后);不调用本方法且未指定select时,结果类型参与join 同样缺省查其全部字段
	 * @return 自身
	 */
	public LambdaQuery<T> selectAll() {
		joinContext.markSelectAll();
		return this;
	}

	/**
	 * @description 将某个被join实体的全部字段以"别名.列 AS 属性"并入select(补sqltoy-plus
	 *              select(mappingClass,...)的能力),可与selectAll()/select(JoinCol...)叠加;
	 *              该实体须参与join(装配期校验),与主表重复时自动跳过避免重复列
	 * @param entityClass 被join的实体
	 * @return 自身
	 */
	public LambdaQuery<T> selectAll(Class<?> entityClass) {
		if (entityClass == null) {
			throw new DataAccessException("selectAll entityClass must not be null");
		}
		joinContext.markSelectAll(entityClass);
		return this;
	}

	/**
	 * @description 缺省输出主表全部字段并排除指定字段(别名.列 AS 属性),可与select(JoinCol...)额外列并存;
	 *              排除字段引用须属于主表实体(结果类型与主表不同时,排除的是主表字段,装配期校验)
	 * @param excludeColumns 需要排除的字段定义
	 * @return 自身
	 */
	public LambdaQuery<T> selectAll(ExcludeCols<?> excludeColumns) {
		if (excludeColumns == null) {
			throw new DataAccessException("selectAll(ExcludeCols) requires non-null exclude columns");
		}
		joinContext.markSelectAll(excludeColumns);
		for (LambdaFn<?, ?> column : excludeColumns.getColumns()) {
			excludeFields.add(LambdaUtils.extractProperty(column));
		}
		return this;
	}

	/**
	 * @return 排除字段名列表(单表通道转unselect)
	 */
	public List<String> getExcludeFields() {
		return excludeFields;
	}

	/**
	 * @description 以聚合/别名列定义查询字段(JoinCol.count/sum/avg/min/max/distinct().as()),
	 *              存在本类定义时走join通道;结果别名用于查询结果对象按属性名映射
	 * @param columns 查询列定义
	 * @return 自身
	 */
	public LambdaQuery<T> select(JoinCol... columns) {
		if (columns != null) {
			for (JoinCol column : columns) {
				if (column == null) {
					throw new DataAccessException("select columns must not contain null elements");
				}
				joinContext.addSelectColumn(column);
			}
		}
		return this;
	}

	/**
	 * @return select指定的查询属性名列表
	 */
	public List<String> getSelectFields() {
		return selectFields;
	}

	/**
	 * @return 查询全部符合条件的记录
	 */
	public List<T> find() {
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			return (List<T>) joinQuery().find();
		}
		return dao.findEntity(getEntityClass(), LambdaConvert.toEntityQuery(this));
	}

	/**
	 * @return 查询单条记录:无匹配返回null,匹配多条视为查询条件存在缺陷,抛出DataAccessException
	 */
	public T getOne() {
		List<T> results = find();
		if (results == null || results.isEmpty()) {
			return null;
		}
		if (results.size() > 1) {
			throw new DataAccessException("Lambda getOne matched " + results.size()
					+ " records (expected 0 or 1); multiple matches indicate a flawed query condition. Check the condition uniqueness or use find() instead");
		}
		return results.get(0);
	}

	/**
	 * @return 按当前orderBy取首条记录,无匹配返回null;不校验是否多条(需要严格单条语义请用getOne)
	 */
	public T getFirst() {
		List<T> results = findTop(1);
		return (results == null || results.isEmpty()) ? null : results.get(0);
	}

	/**
	 * @description 查询单列并以列表返回;仅支持单表实体通道,结果按当前排序返回。
	 *              适合select max/min之外的普通列取数;聚合标量后续单独提供
	 * @param column 单列属性引用
	 * @return 单列值列表
	 */
	public <R> List<R> findColumns(LambdaFn<T, R> column) {
		if (column == null) {
			throw new DataAccessException("Lambda findColumns column must not be null");
		}
		if (isJoinMode()) {
			throw new DataAccessException(
					"Lambda findColumns only supports the single-table entity channel; for join use select(JoinCol...) + find");
		}
		if (!selectFields.isEmpty()) {
			throw new DataAccessException(
					"Lambda findColumns cannot be combined with explicit select(...); call it on a fresh query or use find()");
		}
		select(column);
		List<T> rows;
		try {
			rows = find();
		} finally {
			// 终端方法注入的select列只对本次取数有效,执行后还原,避免复用构建器时静默只查单列
			selectFields.remove(selectFields.size() - 1);
		}
		List<R> values = new ArrayList<>(rows.size());
		for (T row : rows) {
			values.add(column.apply(row));
		}
		return values;
	}

	/**
	 * @description 查询单列单值:无匹配返回null,匹配多条抛出DataAccessException(与getOne一致);
	 *              仅支持单表实体通道,聚合max/min请使用后续的聚合终端
	 * @param column 单列属性引用
	 * @return 单值
	 */
	public <R> R getScalar(LambdaFn<T, R> column) {
		List<R> values = findColumns(column);
		if (values.size() > 1) {
			throw new DataAccessException("Lambda getScalar matched " + values.size()
					+ " records (expected 0 or 1); multiple matches indicate a flawed query condition. Check the condition uniqueness or use findColumns instead");
		}
		return values.isEmpty() ? null : values.get(0);
	}

	/**
	 * @description 聚合max,返回属性类型对应的值;无记录返回null
	 * @param column 聚合列
	 * @return max值
	 */
	@SuppressWarnings("unchecked")
	public <R> R max(LambdaFn<T, R> column) {
		return aggregate(JoinCol.max(column), (Class<R>) propertyType(column));
	}

	/**
	 * @description 聚合min,返回属性类型对应的值;无记录返回null
	 * @param column 聚合列
	 * @return min值
	 */
	@SuppressWarnings("unchecked")
	public <R> R min(LambdaFn<T, R> column) {
		return aggregate(JoinCol.min(column), (Class<R>) propertyType(column));
	}

	/**
	 * @description 聚合sum,统一返回BigDecimal;无记录返回null
	 * @param column 聚合列
	 * @return sum值
	 */
	public BigDecimal sum(LambdaFn<T, ?> column) {
		return aggregate(JoinCol.sum(column), BigDecimal.class);
	}

	/**
	 * @description 聚合avg,统一返回BigDecimal;无记录返回null
	 * @param column 聚合列
	 * @return avg值
	 */
	public BigDecimal avg(LambdaFn<T, ?> column) {
		return aggregate(JoinCol.avg(column), BigDecimal.class);
	}

	/**
	 * 聚合终端:select子句只放一个聚合列,走SQL通道取第一行第一列
	 */
	private <R> R aggregate(JoinCol column, Class<R> resultType) {
		requireDao();
		if (!selectFields.isEmpty() || joinContext.hasSelectColumns() || joinContext.isSelectAll()) {
			throw new DataAccessException(
					"Lambda aggregate cannot be combined with select(...) / select(JoinCol...) / selectAll(); call it on a fresh query");
		}
		if (isComposed()) {
			throw new DataAccessException(
					"Lambda aggregate cannot be combined with union/with (composed queries); aggregate each branch first");
		}
		select(column);
		assemble(dao::getEntityMeta);
		// 标量聚合按定义只返回一行;groupBy/having会让结果变成多行,必须在执行前拒绝
		if (getGroupByColumns() != null || getHavingCondition() != null) {
			throw new DataAccessException(
					"Lambda aggregate cannot be combined with groupBy/having; use select(JoinCol...) + find() for grouped aggregates");
		}
		return dao.query().dataSource(getDataSource()).sql(getSql())
				.entity((Serializable) new IgnoreKeyCaseMap(getParams())).getValue(resultType);
	}

	/**
	 * 从属性引用推断Java类型;无法推断时使用Object.class
	 */
	private Class<?> propertyType(LambdaFn<T, ?> column) {
		try {
			FieldNature nature = LambdaUtils.extractNature(column);
			PropertyDescriptor descriptor = new PropertyDescriptor(nature.fieldName(), nature.entityClass());
			Class<?> type = descriptor.getPropertyType();
			if (type == null || type == Object.class) {
				return Object.class;
			}
			if (type.isPrimitive()) {
				if (type == int.class) {
					return Integer.class;
				}
				if (type == long.class) {
					return Long.class;
				}
				if (type == short.class) {
					return Short.class;
				}
				if (type == byte.class) {
					return Byte.class;
				}
				if (type == float.class) {
					return Float.class;
				}
				if (type == double.class) {
					return Double.class;
				}
				if (type == boolean.class) {
					return Boolean.class;
				}
				if (type == char.class) {
					return Character.class;
				}
			}
			return type;
		} catch (IntrospectionException | RuntimeException e) {
			return Object.class;
		}
	}

	/**
	 * @param page 分页对象
	 * @return 分页查询结果
	 */
	public Page<T> findPage(Page<?> page) {
		if (page == null) {
			throw new DataAccessException("Lambda findPage page must not be null");
		}
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			// join分页走findPageByQuery(QueryExecutor):QueryExecutor可同时携带dataSource与pageOptimize
			// (link Query构建器无pageOptimize);pageOptimize为null时回退全局SqlToyConfig配置,行为与未设置一致
			return (Page<T>) dao
					.findPageByQuery(page,
							new QueryExecutor(getSql(), getParams()).resultType(getEntityClass())
									.dataSource(getDataSource()).pageOptimize(pageOptimize).countSql(countSql))
					.getPageResult();
		}
		if (countSql != null) {
			throw new DataAccessException(
					"countSql is only supported by the join/SQL channel; the single-table channel uses EntityQuery's entity count and cannot accept a custom count sql");
		}
		return dao.findPageEntity(page, getEntityClass(), LambdaConvert.toEntityQuery(this));
	}

	/**
	 * @description 取前topSize条记录:topSize大于1取整数条,小于1按比例取(如0.2取20%);
	 *              单表与join通道均支持,排序由orderBy决定
	 * @param topSize 记录条数或比例(必须大于0)
	 * @return 结果集
	 */
	public List<T> findTop(double topSize) {
		if (topSize <= 0) {
			throw new DataAccessException("Lambda findTop topSize must be greater than 0");
		}
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			return (List<T>) joinQuery().findTop(topSize);
		}
		return dao.findEntity(getEntityClass(), LambdaConvert.toEntityQuery(this).top(topSize));
	}

	/**
	 * @description 随机取randomSize条记录:randomSize大于1取整数条,小于1按比例取(如0.2取20%)
	 * @param randomSize 记录条数或比例(必须大于0)
	 * @return 结果集
	 */
	public List<T> findRandom(double randomSize) {
		if (randomSize <= 0) {
			throw new DataAccessException("Lambda findRandom randomSize must be greater than 0");
		}
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			return (List<T>) joinQuery().findRandom(randomSize);
		}
		return dao.findEntity(getEntityClass(), LambdaConvert.toEntityQuery(this).random(randomSize));
	}

	/**
	 * @return 符合条件的记录数
	 */
	public long count() {
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			return joinQuery().count();
		}
		return dao.getCount(getEntityClass(), LambdaConvert.toEntityQuery(this));
	}

	/**
	 * @description 构建join通道的查询执行器:走sqltoy既有的query()链式构建器(原生sql+命名参数+结果类型),
	 *              并经dataSource(ds)透传显式数据源(null时回退dao默认数据源,行为与未指定完全一致);
	 *              query()构建器内部即new
	 *              QueryExecutor(sql,params).resultType(..),与既有find(sql,map,resultType)同源,
	 *              故无需给LightDao新增任何方法即可支持find/findPage/findTop/findRandom/count全终端的数据源路由
	 */
	private Query joinQuery() {
		return dao.query().dataSource(getDataSource()).sql(getSql())
				.entity((Serializable) new IgnoreKeyCaseMap(getParams())).resultType(getEntityClass());
	}

	/**
	 * @return 是否存在符合条件的记录
	 */
	public boolean exists() {
		// top-1命中即停,避免count全量聚合
		return !findTop(1).isEmpty();
	}

	private void requireDao() {
		if (dao == null) {
			throw new DataAccessException(
					"LambdaQuery is not bound to a dao; create it via lightDao.lambdaQuery(entityClass) or bind a dao with bind(dao) before executing");
		}
	}

	/**
	 * @description join关联步骤,on()完成后返回查询构建器继续添加条件或叠加join
	 */
	public final class JoinOn {

		private JoinOn(SqlKeyword joinKeyword, Class<?> entityClass) {
			joinContext.registerJoin(joinKeyword, entityClass);
		}

		/**
		 * @description 登记on等值关联字段对,完成后返回查询构建器
		 * @param columnA 关联字段A
		 * @param columnB 关联字段B
		 * @return 查询构建器
		 */
		public <A, B> LambdaQuery<T> on(LambdaFn<A, ?> columnA, LambdaFn<B, ?> columnB) {
			joinContext.registerOn(columnA, columnB);
			return LambdaQuery.this;
		}

		/**
		 * @description 登记同一join的两个on等值关联字段对(自动AND),完成后返回查询构建器
		 */
		public <A, B, C, D> LambdaQuery<T> on(LambdaFn<A, ?> columnA1, LambdaFn<B, ?> columnB1, LambdaFn<C, ?> columnA2,
				LambdaFn<D, ?> columnB2) {
			joinContext.registerOn(columnA1, columnB1, columnA2, columnB2);
			return LambdaQuery.this;
		}
	}
}
