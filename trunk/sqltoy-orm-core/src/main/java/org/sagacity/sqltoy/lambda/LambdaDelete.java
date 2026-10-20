package org.sagacity.sqltoy.lambda;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.engine.AbstractWhere;
import org.sagacity.sqltoy.lambda.engine.SqlKeyword;
import org.sagacity.sqltoy.lambda.join.JoinContext;
import org.sagacity.sqltoy.lambda.join.JoinContext.Operate;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.utils.DBTransUtils;

/**
 * @description lambda删除条件构建器,单表与多表join统一入口:不调用join步骤即单表
 *              (条件转EntityQuery复用deleteByQuery链路),调用leftJoin/rightJoin/innerJoin即切换为
 *              join删除(生成可移植的<code>DELETE FROM 目标表 WHERE EXISTS
 *              (SELECT 1 FROM (join派生表) sqd WHERE ...)</code>经executeSql执行,
 *              不依赖MySQL多表DELETE语法;主表隐式为查询实体,无需from,deleteTables指定删除目标缺省主表);
 *              通过<code>lightDao.lambdaDelete(entityClass)</code>创建时已绑定执行dao,
 *              可直接调用delete终端方法
 * @param <T> 实体类型
 * @author zhongxuchen
 */
public final class LambdaDelete<T> extends AbstractWhere<T, LambdaDelete<T>> {

	/**
	 * 绑定的执行dao,为null时终端方法不可用
	 */
	private LightDao dao;

	private LambdaDelete(Class<T> entityClass, JoinContext joinContext) {
		super(entityClass, joinContext);
	}

	public static <T> LambdaDelete<T> create(Class<T> entityClass) {
		if (entityClass == null) {
			throw new DataAccessException("LambdaDelete entityClass must not be null");
		}
		return new LambdaDelete<>(entityClass, new JoinContext(Operate.DELETE, null));
	}

	/**
	 * @description 绑定执行dao,由LightDao的lambdaDelete工厂调用
	 */
	public LambdaDelete<T> bind(LightDao dao) {
		this.dao = dao;
		return this;
	}

	@Override
	protected LambdaDelete<T> instance() {
		// 嵌套条件与当前对象共享参数序号与join上下文
		return new LambdaDelete<>(entityClass, joinContext);
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
			throw new DataAccessException("groupBy/having/orderBy are only supported by LambdaQuery, not by delete");
		}
		if (isJoinMode()) {
			// 初始化join上下文(缺on校验与元信息注入)
			joinContext.assemble(metaLoader);
		}
		super.assemble(metaLoader);
	}

	/**
	 * @return 是否走join通道:注册了join关联表
	 */
	public boolean isJoinMode() {
		return joinContext.hasJoin();
	}

	/**
	 * @description 左连接,须紧跟on()完成关联;首次调用即切换为join删除通道,主表隐式为查询实体
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
	 * @description 指定删除的目标表(须参与join),可省略,缺省为主表;仅join通道有效
	 * @param entities 删除目标实体
	 * @return 自身
	 */
	public LambdaDelete<T> deleteTables(Class<?>... entities) {
		if (entities != null) {
			for (Class<?> entity : entities) {
				joinContext.addDeleteClass(entity);
			}
		}
		return this;
	}

	/**
	 * @description 按条件删除,无任何条件时须先调用matchAll()(执行时校验)
	 * @return 删除的记录数
	 */
	public long delete() {
		if (dao == null) {
			throw new DataAccessException(
					"LambdaDelete is not bound to a dao; create it via lightDao.lambdaDelete(entityClass) or bind a dao with bind(dao) before executing");
		}
		assemble(dao::getEntityMeta);
		if (isJoinMode()) {
			requireCondition("delete");
			List<String> deleteSqls = getDeleteSqls();
			if (deleteSqls.size() == 1) {
				return executeJoinDelete(deleteSqls.get(0));
			}
			// 多删除目标:逐表删除置于同一事务,保证原子性;事务数据源与删除一致(显式指定优先,否则dao默认)
			DataSource transDataSource = (getDataSource() != null) ? getDataSource() : dao.getDataSource();
			Object total = DBTransUtils.doTrans(transDataSource, () -> {
				long sum = 0;
				for (String deleteSql : deleteSqls) {
					sum += executeJoinDelete(deleteSql);
				}
				return sum;
			});
			return ((Number) total).longValue();
		}
		return dao.deleteByQuery(getEntityClass(), LambdaConvert.toDeleteQuery(this));
	}

	/**
	 * @description 执行单条join删除:走sqltoy既有execute()链式构建器(原生sql+命名参数),
	 *              经dataSource(ds)透传显式数据源(null时回退dao默认),无需给LightDao新增方法
	 */
	private long executeJoinDelete(String deleteSql) {
		return dao.execute().dataSource(getDataSource()).sql(deleteSql)
				.entity((Serializable) new IgnoreKeyCaseMap(getParams())).submit();
	}

	/**
	 * @return 可移植join删除语句列表(多删除目标时每表一条);空条件会先被拒绝,避免生成1=1的全表删除
	 */
	public List<String> getDeleteSqls() {
		requireCondition("delete");
		return joinContext.getDeleteSqls(getSqlSegment());
	}

	/**
	 * @description join关联步骤,on()完成后返回删除构建器继续添加条件或叠加join
	 */
	public final class JoinOn {

		private JoinOn(SqlKeyword joinKeyword, Class<?> entityClass) {
			// join删除的主表隐式为查询实体
			if (joinContext.getFromClass() == null) {
				joinContext.registerFrom(getEntityClass());
			}
			joinContext.registerJoin(joinKeyword, entityClass);
		}

		/**
		 * @description 登记on等值关联字段对,完成后返回删除构建器
		 */
		public <A, B> LambdaDelete<T> on(LambdaFn<A, ?> columnA, LambdaFn<B, ?> columnB) {
			joinContext.registerOn(columnA, columnB);
			return LambdaDelete.this;
		}

		/**
		 * @description 登记同一join的两个on等值关联字段对(自动AND),完成后返回删除构建器
		 */
		public <A, B, C, D> LambdaDelete<T> on(LambdaFn<A, ?> columnA1, LambdaFn<B, ?> columnB1,
				LambdaFn<C, ?> columnA2, LambdaFn<D, ?> columnB2) {
			joinContext.registerOn(columnA1, columnB1, columnA2, columnB2);
			return LambdaDelete.this;
		}
	}
}
