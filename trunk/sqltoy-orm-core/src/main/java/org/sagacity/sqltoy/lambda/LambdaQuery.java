package org.sagacity.sqltoy.lambda;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.ColumnResolver;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.engine.SqlKeyword;
import org.sagacity.sqltoy.lambda.join.ExcludeCols;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.lambda.join.JoinContext;
import org.sagacity.sqltoy.lambda.join.JoinContext.Operate;
import org.sagacity.sqltoy.model.Page;

/**
 * @description lambda查询构建器,单表与多表join统一入口:单表只是join的退化形态,是否join由是否
 *              调用leftJoin/rightJoin/innerJoin自动判定,无需预先选择构建器类型。
 *              无join(单表)走sqltoy实体查询通道(EntityQuery,继承分片/缓存翻译/加密脱敏等内核能力);
 *              有join拼装完整sql走原生sql通道(结果类型可与主表不同,select缺省查询主表全部字段);
 *              注意在单表查询上追加join会切换执行通道,实体级增强不再生效;
 *              条件动态化推荐IfPresent语法糖(eqIfPresent/likeIfPresent/inIfPresent等),
 *              复杂动态场景请使用sqltoy的SQL模式(sqlId加#[...]动态段与@if宏,见包说明)
 * <p>
 * 单表示例: lightDao.lambdaQuery(User.class).eq(User::getStatus, 1)
 * .like(User::getStaffName, "张").orderByDesc(User::getCreateTime).find();
 * </p>
 * <p>
 * join示例: lightDao.lambdaQuery(StaffInfo.class).leftJoin(OrganInfo.class)
 * .on(StaffInfo::getOrganId, OrganInfo::getOrganId).eq(StaffInfo::getStatus, 1).find();
 * </p>
 * @param <T> 查询结果类型:单表即查询实体;join时缺省作为主表与结果类型,主表不同时通过from(entityClass)指定
 * @author zhongxuchen
 */
public final class LambdaQuery<T> extends AbstractWhere<T, LambdaQuery<T>> {

	/**
	 * 绑定的执行dao,为null时终端方法不可用
	 */
	private LightDao dao;

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

	private LambdaQuery(Class<T> entityClass, AtomicInteger paramSeq, JoinContext joinContext) {
		super(entityClass, paramSeq, joinContext);
	}

	public static <T> LambdaQuery<T> create(Class<T> entityClass) {
		return new LambdaQuery<>(entityClass, new AtomicInteger(0),
				new JoinContext(Operate.SELECT, entityClass));
	}

	/**
	 * @description 绑定执行dao,由LightDao的lambdaQuery工厂调用
	 */
	public LambdaQuery<T> bind(LightDao dao) {
		this.dao = dao;
		return this;
	}

	@Override
	protected LambdaQuery<T> instance() {
		// 嵌套条件与当前对象共享参数序号与join上下文
		return new LambdaQuery<>(entityClass, paramSeq, joinContext);
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
	}

	/**
	 * @return 是否走join通道:注册了join关联表,或使用了聚合/别名列定义
	 */
	public boolean isJoinMode() {
		return joinContext.hasJoin() || joinContext.hasSelectColumns();
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
	 * @description 左连接,须紧跟on()完成关联;首次调用即切换为join通道
	 */
	public JoinOn leftJoin(Class<?> entityClass) {
		return new JoinOn("LEFT JOIN", entityClass);
	}

	/**
	 * @description 右连接,须紧跟on()完成关联
	 */
	public JoinOn rightJoin(Class<?> entityClass) {
		return new JoinOn("RIGHT JOIN", entityClass);
	}

	/**
	 * @description 内连接,须紧跟on()完成关联
	 */
	public JoinOn innerJoin(Class<?> entityClass) {
		return new JoinOn("INNER JOIN", entityClass);
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
	 *              (主表字段在前,额外列在后);不调用本方法且未指定select时,结果类型参与join
	 *              同样缺省查其全部字段
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
		joinContext.markSelectAll(excludeColumns);
		if (excludeColumns != null) {
			for (LambdaFn<?, ?> column : excludeColumns.getColumns()) {
				excludeFields.add(LambdaUtils.extractProperty(column));
			}
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
			return dao.find(getSql(), getParams(), getEntityClass());
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
	 * @param page 分页对象
	 * @return 分页查询结果
	 */
	public Page<T> findPage(Page<?> page) {
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			return dao.findPage(page, getSql(), getParams(), getEntityClass());
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
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			return dao.findTop(getSql(), getParams(), getEntityClass(), topSize);
		}
		return dao.findEntity(getEntityClass(), LambdaConvert.toEntityQuery(this).top(topSize));
	}

	/**
	 * @description 随机取randomSize条记录:randomSize大于1取整数条,小于1按比例取(如0.2取20%)
	 * @param randomSize 记录条数或比例(必须大于0)
	 * @return 结果集
	 */
	public List<T> findRandom(double randomSize) {
		requireDao();
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			return dao.findRandom(getSql(), getParams(), getEntityClass(), randomSize);
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
			return dao.getCount(getSql(), getParams());
		}
		return dao.getCount(getEntityClass(), LambdaConvert.toEntityQuery(this));
	}

	/**
	 * @return 是否存在符合条件的记录
	 */
	public boolean exists() {
		return count() > 0;
	}


	private void requireDao() {
		if (dao == null) {
			throw new DataAccessException(
					"LambdaQuery is not bound to a dao; create it via lightDao.lambdaQuery(entityClass) or execute with lightDao.find(query)");
		}
	}

	/**
	 * @description join关联步骤,on()完成后返回查询构建器继续添加条件或叠加join
	 */
	public final class JoinOn {

		private JoinOn(String joinKeyword, Class<?> entityClass) {
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
		public <A, B, C, D> LambdaQuery<T> on(LambdaFn<A, ?> columnA1, LambdaFn<B, ?> columnB1,
				LambdaFn<C, ?> columnA2, LambdaFn<D, ?> columnB2) {
			joinContext.registerOn(columnA1, columnB1, columnA2, columnB2);
			return LambdaQuery.this;
		}
	}
}
