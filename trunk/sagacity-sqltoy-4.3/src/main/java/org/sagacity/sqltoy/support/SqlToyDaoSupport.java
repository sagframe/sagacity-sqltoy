/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.support;

import java.io.Serializable;
import java.sql.Connection;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.executor.UniqueExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.plugin.IdGenerator;
import org.sagacity.sqltoy.plugin.TranslateHandler;
import org.sagacity.sqltoy.plugin.id.RedisIdGenerator;
import org.sagacity.sqltoy.utils.BeanPropsWrapper;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @project sagacity-sqltoy4.0
 * @description 通过sqlToy提供强大的数据操作支持,从2.0版本后接口将保持稳定
 *              sqltoy比hibernate和myBatis如何?用一用我相信你一定会深深的爱上sqlToy(太贴切项目实践了,
 *              一看就是经历无数项目苦难的人总结出的实用货)! 3.0版本开始对代码结构进行彻底整理,分离各种数据库的处理策略，使代码更加清晰,
 *              并在3.2版本开始对外开源
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @publish Copyright@2009 版权归陈仁飞,反对任何不尊重版权的抄袭，如引用请注明出处。
 * @version id:SqlToyDaoSupport.java,Revision:v2.0,Date:2012-6-1 下午3:15:13
 * @Modification Date:2012-8-8 {增强对象级联查询、删除、保存操作机制,不支持2层以上级联}
 * @Modification Date:2012-8-23 {新增loadAll(List entities) 方法，可以批量通过主键取回详细信息}
 * @Modification Date:2014-12-17 {1、增加sharding功能,改进saveOrUpdate功能，2、采用merge
 *               into策略;3、优化查询 条件和查询结果，变为一个对象，返回结果支持json输出}
 * @Modification Date:2016-3-07
 *               {优化存储过程调用,提供常用的执行方式,剔除过往复杂的实现逻辑和不必要的兼容性,让调用过程更加可读
 *               ,存储过程调用过程优化后将全部代码开始对外开放 }
 * @Modification Date:2016-11-25
 *               {增加了分页优化功能,缓存相同查询条件的总记录数,在一定周期情况下无需再查询总记录数,从而提升分页查询的整体效率 }
 * @Modification Date:2017-7-13 {增加saveAllNotExist功能,批量保存数据时忽视已经存在的,避免重复性数据主键冲突}
 * @Modification Date:2017-11-1 {增加对象操作分库分表功能实现,精简和优化代码}
 * @Modification Date:2019-3-1 {增加通过缓存获取Key然后作为查询条件cache-arg 功能，从而避免二次查询或like检索}
 * @Modification Date:2019-6-25 {将异常统一转化成RuntimeException,不在方法上显式的抛异常}
 */
@SuppressWarnings("rawtypes")
public class SqlToyDaoSupport {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LogManager.getLogger(SqlToyDaoSupport.class);

	/**
	 * 数据源
	 */
	protected DataSource dataSource;

	/**
	 * sqlToy上下文定义
	 */
	protected SqlToyContext sqlToyContext;

	/**
	 * 各种数据库方言实现
	 */
	private DialectFactory dialectFactory = DialectFactory.getInstance();

	@Autowired(required = false)
	@Qualifier(value = "dataSource")
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @todo 获取数据源,如果参数dataSource为null则返回默认的dataSource
	 * @param dataSource
	 * @return
	 */
	public DataSource getDataSource(DataSource dataSource) {
		DataSource result = dataSource;
		if (null == result)
			result = this.dataSource;
		if (null == result)
			result = sqlToyContext.getDefaultDataSource();
		return result;
	}

	/**
	 * @param sqlToyContext
	 *            the sqlToyContext to set
	 */
	@Autowired
	@Qualifier(value = "sqlToyContext")
	public void setSqlToyContext(SqlToyContext sqlToyContext) {
		this.sqlToyContext = sqlToyContext;
	}

	/**
	 * @return the sqlToyContext
	 */
	public SqlToyContext getSqlToyContext() {
		return sqlToyContext;
	}

	/**
	 * @todo 获取sqlId 在sqltoy中的配置模型
	 * @param sqlKey
	 * @param sqlType
	 * @return
	 */
	protected SqlToyConfig getSqlToyConfig(final String sqlKey, final SqlType sqlType) {
		return sqlToyContext.getSqlToyConfig(sqlKey, sqlType);
	}

	/**
	 * @see isUnique(final Serializable entity, final String[] paramsNamed)
	 * @param entity
	 * @return
	 */
	protected boolean isUnique(final Serializable entity) {
		return isUnique(new UniqueExecutor(entity));
	}

	/**
	 * @todo 判断数据库中数据是否唯一，true 表示唯一(可以插入)，false表示不唯一(数据库已经存在该数据)，用法
	 *       isUnique(dictDetailVO,new
	 *       String[]{"dictTypeCode","dictName"})，将会根据给定的2个参数
	 *       通过VO取到相应的值，作为组合条件到dictDetailVO对应的表中查询记录是否存在
	 * @param entity
	 * @param paramsNamed
	 * @return
	 */
	protected boolean isUnique(final Serializable entity, final String[] paramsNamed) {
		return isUnique(new UniqueExecutor(entity, paramsNamed));
	}

	/*
	 * @see isUnique(final Serializable entity, final String[] paramsNamed)
	 */
	protected boolean isUnique(final UniqueExecutor uniqueExecutor) {
		return dialectFactory.isUnique(sqlToyContext, uniqueExecutor,
				this.getDataSource(uniqueExecutor.getDataSource()));
	}

	/**
	 * @todo 获取数据库查询语句的总记录数
	 * @param sqlOrNamedQuery
	 * @param paramsNamed
	 * @param paramsValue
	 * @return Long
	 */
	protected Long getCountBySql(final String sqlOrNamedQuery, final String[] paramsNamed, final Object[] paramsValue) {
		return getCountByQuery(new QueryExecutor(sqlOrNamedQuery, paramsNamed, paramsValue));
	}

	/**
	 * @todo 指定数据源查询记录数量
	 * @param queryExecutor
	 * @return
	 */
	protected Long getCountByQuery(final QueryExecutor queryExecutor) {
		return dialectFactory.getCountBySql(sqlToyContext, queryExecutor,
				this.getDataSource(queryExecutor.getDataSource()));
	}

	protected StoreResult executeStore(final String storeNameOrKey, final Object[] inParamValues,
			final Integer[] outParamsType, final Class resultType) {
		return executeStore(storeNameOrKey, inParamValues, outParamsType, resultType, null);
	}

	protected StoreResult executeStore(final String storeNameOrKey, final Object[] inParamValues) {
		return executeStore(storeNameOrKey, inParamValues, null, null, null);
	}

	/**
	 * @todo 通用存储过程调用,一般数据库{?=call xxxStore(? in,? in,? out)} 针对oracle数据库只能{call
	 *       xxxStore(? in,? in,? out)} 同时结果集必须通过OracleTypes.CURSOR out 参数返回
	 *       目前此方法只能返回一个结果集(集合类数据),可以返回多个非集合类数据，如果有特殊用法，则自行封装调用
	 * @param storeNameOrKey
	 * @param inParamsValue
	 * @param outParamsType(可以为null)
	 * @param resultType
	 *            (VOClass,HashMap或null)
	 * @param dataSource
	 * @return
	 */
	protected StoreResult executeStore(final String storeNameOrKey, final Object[] inParamsValue,
			final Integer[] outParamsType, final Class resultType, final DataSource dataSource) {
		return dialectFactory.executeStore(sqlToyContext, getSqlToyConfig(storeNameOrKey, SqlType.search),
				inParamsValue, outParamsType, resultType, this.getDataSource(dataSource));
	}

	protected Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed,
			final Object[] paramsValue) {
		return getSingleValue(sqlOrNamedSql, paramsNamed, paramsValue, null);
	}

	/**
	 * @todo 返回单行单列值，如果结果集存在多条数据则返回null
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @param dataSource
	 * @return
	 */
	protected Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			final DataSource dataSource) {
		Object queryResult = loadByQuery(
				new QueryExecutor(sqlOrNamedSql, paramsNamed, paramsValue).dataSource(dataSource));
		if (null != queryResult) {
			return ((List) queryResult).get(0);
		}
		return null;
	}

	/**
	 * @todo 根据给定的对象中的主键值获取对象完整信息
	 * @param entity
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected <T extends Serializable> T load(final T entity) {
		if (entity == null)
			return null;
		EntityMeta entityMeta = this.getEntityMeta(entity.getClass());
		if (SqlConfigParseUtils.isNamedQuery(entityMeta.getLoadSql(null)))
			return (T) this.loadBySql(entityMeta.getLoadSql(null), entity);
		return load(entity, null, null);
	}

	/**
	 * @todo 提供锁定功能的加载
	 * @param entity
	 * @param lockMode
	 * @return
	 */
	protected <T extends Serializable> T load(final T entity, final LockMode lockMode) {
		return load(entity, lockMode, null);
	}

	/**
	 * @todo <b>根据主键值获取对应的记录信息</b>
	 * @param entity
	 * @param lockMode
	 * @param dataSource
	 * @return
	 */
	protected <T extends Serializable> T load(final T entity, final LockMode lockMode, final DataSource dataSource) {
		return dialectFactory.load(sqlToyContext, entity, null, lockMode, this.getDataSource(dataSource));
	}

	/**
	 * @todo 级联加载(通过名称来区别，目的是防止没有必要的级联加载影响性能)
	 * @param entity
	 * @param lockMode
	 * @return
	 */
	protected <T extends Serializable> T loadCascade(T entity, LockMode lockMode) {
		if (entity == null)
			return null;
		return dialectFactory.load(sqlToyContext, entity,
				sqlToyContext.getEntityMeta(entity.getClass()).getCascadeTypes(), lockMode, this.getDataSource(null));
	}

	/**
	 * @todo 指定需要级联加载的类型，通过主对象加载自身和相应的子对象集合
	 * @param entity
	 * @param cascadeTypes
	 * @param lockMode
	 * @return
	 */
	protected <T extends Serializable> T loadCascade(T entity, Class[] cascadeTypes, LockMode lockMode) {
		return dialectFactory.load(sqlToyContext, entity, cascadeTypes, lockMode, this.getDataSource(null));
	}

	/**
	 * @todo 批量根据实体对象的主键获取对象的详细信息
	 * @param entities
	 * @param lockMode
	 * @return
	 */
	protected <T extends Serializable> List<T> loadAll(final List<T> entities, final LockMode lockMode) {
		return dialectFactory.loadAll(sqlToyContext, entities, null, lockMode, this.getDataSource(null));
	}

	protected <T extends Serializable> List<T> loadAllCascade(final List<T> entities, final LockMode lockMode) {
		if (entities == null || entities.isEmpty())
			return entities;
		return dialectFactory.loadAll(sqlToyContext, entities,
				sqlToyContext.getEntityMeta(entities.get(0).getClass()).getCascadeTypes(), lockMode,
				this.getDataSource(null));
	}

	/**
	 * @todo 批量对象级联加载,指定级联加载的子表
	 * @param entities
	 * @param cascadeTypes
	 * @param lockMode
	 * @return
	 */
	protected <T extends Serializable> List<T> loadAllCascade(final List<T> entities, final Class[] cascadeTypes,
			final LockMode lockMode) {
		return dialectFactory.loadAll(sqlToyContext, entities, cascadeTypes, lockMode, this.getDataSource(null));
	}

	/**
	 * @todo 根据sql语句查询并返回单个VO对象(可指定自定义对象,sqltoy根据查询label跟对象的属性名称进行匹配映射)
	 * @param sql
	 * @param paramNames
	 * @param paramValues
	 * @param resultType
	 * @return
	 */
	protected Object loadBySql(final String sql, final String[] paramNames, final Object[] paramValues,
			final Class resultType) {
		return loadByQuery(new QueryExecutor(sql, paramNames, paramValues).resultType(resultType));
	}

	/**
	 * @todo 解析sql中:named 属性到entity对象获取对应的属性值作为查询条件,并将查询结果以entity的class类型返回
	 * @param sql
	 * @param entity
	 * @return
	 */
	protected Object loadBySql(final String sql, final Serializable entity) {
		return loadByQuery(new QueryExecutor(sql, entity));
	}

	protected Object loadByQuery(final QueryExecutor queryExecutor) {
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecutor,
				this.getDataSource(queryExecutor.getDataSource()));
		List rows = result.getRows();
		if (rows != null && rows.size() > 0)
			return rows.get(0);
		return null;
	}

	/**
	 * @todo 执行无条件的sql语句,一般是一个修改、删除等操作，并返回修改的记录数量
	 * @param sqlOrNamedSql
	 * @return
	 */
	protected Long executeSql(final String sqlOrNamedSql) {
		return executeSql(sqlOrNamedSql, null, null, false, this.getDataSource(null));
	}

	/**
	 * @todo 解析sql中的参数名称，以此名称到entity中提取对应的值作为查询条件值执行sql
	 * @param sqlOrNamedSql
	 * @param entity
	 * @param reflectPropertyHandler
	 * @return
	 */
	protected Long executeSql(final String sqlOrNamedSql, final Serializable entity,
			final ReflectPropertyHandler reflectPropertyHandler) {
		SqlToyConfig sqlToyConfig = getSqlToyConfig(sqlOrNamedSql, SqlType.update);
		// 根据sql中的变量从entity对象中提取参数值
		Object[] paramValues = SqlConfigParseUtils.reflectBeanParams(sqlToyConfig.getParamsName(), entity,
				reflectPropertyHandler);
		return executeSql(sqlOrNamedSql, sqlToyConfig.getParamsName(), paramValues, false, this.getDataSource(null));
	}

	/**
	 * @todo 执行无返回结果的SQL(返回updateCount)
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 */
	protected Long executeSql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue) {
		return executeSql(sqlOrNamedSql, paramsNamed, paramsValue, false, this.getDataSource(null));
	}

	/**
	 * @todo 执行无返回结果的SQL(返回updateCount),根据autoCommit设置是否自动提交
	 * @param sqlOrNamedSql
	 * @param paramsNamed
	 * @param paramsValue
	 * @param autoCommit
	 *            是否自动提交
	 * @param dataSource
	 */
	protected Long executeSql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			final Boolean autoCommit, final DataSource dataSource) {
		return dialectFactory.executeSql(sqlToyContext, sqlOrNamedSql, paramsNamed, paramsValue, autoCommit,
				this.getDataSource(dataSource));
	}

	/**
	 * @todo 批量执行sql修改或删除操作(返回updateCount)
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param reflectPropertyHandler
	 * @param autoCommit
	 */
	protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet,
			final ReflectPropertyHandler reflectPropertyHandler, final Boolean autoCommit) {
		return batchUpdate(sqlOrNamedSql, dataSet, sqlToyContext.getBatchSize(), reflectPropertyHandler, null,
				autoCommit, this.getDataSource(null));
	}

	/**
	 * @TODO 批量执行sql修改或删除操作(返回updateCount)
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param reflectPropertyHandler
	 * @param insertCallhandler
	 * @param autoCommit
	 */
	protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet,
			final ReflectPropertyHandler reflectPropertyHandler, final InsertRowCallbackHandler insertCallhandler,
			final Boolean autoCommit) {
		return batchUpdate(sqlOrNamedSql, dataSet, sqlToyContext.getBatchSize(), reflectPropertyHandler,
				insertCallhandler, autoCommit, this.getDataSource(null));
	}

	/**
	 * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param batchSize
	 * @param insertCallhandler
	 * @param autoCommit
	 */
	protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet, final int batchSize,
			final InsertRowCallbackHandler insertCallhandler, final Boolean autoCommit) {
		return batchUpdate(sqlOrNamedSql, dataSet, batchSize, null, insertCallhandler, autoCommit, null);
	}

	/**
	 * @todo 批量执行sql修改或删除操作
	 * @param sqlOrNamedSql
	 * @param dataSet
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param insertCallhandler
	 * @param autoCommit
	 * @param dataSource
	 */
	protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet, final int batchSize,
			final ReflectPropertyHandler reflectPropertyHandler, final InsertRowCallbackHandler insertCallhandler,
			final Boolean autoCommit, final DataSource dataSource) {
		return dialectFactory.batchUpdate(sqlToyContext, sqlOrNamedSql, dataSet, batchSize, reflectPropertyHandler,
				insertCallhandler, autoCommit, getDataSource(dataSource));
	}

	protected boolean wrapTreeTableRoute(final TreeTableModel treeModel) {
		return wrapTreeTableRoute(treeModel, null);
	}

	/**
	 * @todo 构造树形表的节点路径、节点层级、节点类别(是否叶子节点)
	 * @param treeModel
	 * @param dataSource
	 * @return
	 */
	protected boolean wrapTreeTableRoute(final TreeTableModel treeModel, final DataSource dataSource) {
		return dialectFactory.wrapTreeTableRoute(sqlToyContext, treeModel, this.getDataSource(dataSource));
	}

	/**
	 * @todo 以entity对象的属性给sql中的:named 传参数，进行查询，并返回entityClass类型的集合
	 * @param sql
	 * @param entity
	 * @return
	 */
	protected List findBySql(final String sql, final Serializable entity) {
		return findByQuery(new QueryExecutor(sql, entity)).getRows();
	}

	protected List findBySql(final String sql, final String[] paramsNamed, final Object[] paramsValue,
			final Class voClass) {
		return findByQuery(new QueryExecutor(sql, paramsNamed, paramsValue).resultType(voClass)).getRows();
	}

	protected QueryResult findByQuery(final QueryExecutor queryExecutor) {
		return dialectFactory.findByQuery(sqlToyContext, queryExecutor,
				this.getDataSource(queryExecutor.getDataSource()));
	}

	/**
	 * @todo 以QueryExecutor 封装sql、参数等条件，实现分页查询
	 * @param paginationModel
	 * @param queryExecutor
	 * @return
	 */
	protected QueryResult findPageByQuery(final PaginationModel paginationModel, final QueryExecutor queryExecutor) {
		return dialectFactory.findPage(sqlToyContext, queryExecutor, paginationModel.getPageNo(),
				paginationModel.getPageSize(), this.getDataSource(queryExecutor.getDataSource()));
	}

	/**
	 * @todo 指定sql和参数名称以及名称对应的值和返回结果的类型(类型可以是java.util.HashMap),进行分页查询
	 *       sql可以是一个具体的语句也可以是xml中定义的sqlId
	 * @param paginationModel
	 * @param sql
	 * @param paramsNamed
	 * @param paramsValue
	 * @param voClass(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	 * @return
	 */
	protected PaginationModel findPageBySql(final PaginationModel paginationModel, final String sql,
			final String[] paramsNamed, final Object[] paramsValue, Class voClass) {
		return findPageByQuery(paginationModel, new QueryExecutor(sql, paramsNamed, paramsValue).resultType(voClass))
				.getPageResult();
	}

	protected PaginationModel findPageBySql(final PaginationModel paginationModel, final String sql,
			final Serializable entity) {
		return findPageByQuery(paginationModel, new QueryExecutor(sql, entity)).getPageResult();
	}

	/**
	 * @todo 取符合条件的结果前多少数据,topSize>1 则取整数返回记录数量，topSize<1 则按比例返回结果记录(topSize必须是大于0)
	 * @param sql
	 * @param paramsNamed
	 * @param paramsValue
	 * @param voClass(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	 * @param topSize
	 * @return
	 */
	protected List findTopBySql(final String sql, final String[] paramsNamed, final Object[] paramsValue,
			final Class voClass, final double topSize) {
		return findTopByQuery(new QueryExecutor(sql, paramsNamed, paramsValue).resultType(voClass), topSize).getRows();
	}

	protected List findTopBySql(final String sql, final Serializable entity, final double topSize) {
		return findTopByQuery(new QueryExecutor(sql, entity), topSize).getRows();
	}

	protected QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize) {
		return dialectFactory.findTop(sqlToyContext, queryExecutor, topSize,
				this.getDataSource(queryExecutor.getDataSource()));
	}

	/**
	 * @todo 在符合条件的结果中随机提取多少条记录,randomCount>1 则取整数记录，randomCount<1 则按比例提取随机记录
	 *       如randomCount=0.05 总记录数为100,则随机取出5条记录
	 * @param queryExecutor
	 * @param randomCount
	 * @return
	 */
	protected QueryResult getRandomResult(final QueryExecutor queryExecutor, final double randomCount) {
		return dialectFactory.getRandomResult(sqlToyContext, queryExecutor, randomCount,
				this.getDataSource(queryExecutor.getDataSource()));
	}

	// voClass(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
	protected List getRandomResult(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
			Class voClass, final double randomCount) {
		return getRandomResult(new QueryExecutor(sqlOrNamedSql, paramsNamed, paramsValue).resultType(voClass),
				randomCount).getRows();
	}

	protected void truncate(final Class entityClass, final Boolean autoCommit) {
		if (null == entityClass)
			throw new IllegalArgumentException("entityClass is null!Please enter the correct!");
		truncate(sqlToyContext.getEntityMeta(entityClass).getTableName(), autoCommit, null);
	}

	/**
	 * @todo <b>快速删除表中的数据,autoCommit为null表示按照连接的默认值(如dbcp可以配置默认是否autoCommit)</b>
	 * @param tableName
	 * @param autoCommit
	 * @param dataSource
	 */
	protected void truncate(final String tableName, final Boolean autoCommit, final DataSource dataSource) {
		this.executeSql("truncate table ".concat(tableName), null, null, autoCommit, this.getDataSource(dataSource));
	}

	/**
	 * @todo 保存对象数据(返回插入的主键值),会针对对象的子集进行级联保存
	 * @param entity
	 * @return
	 */
	protected Object save(final Serializable entity) {
		return this.save(entity, null);
	}

	/**
	 * @todo <b>指定数据库插入单个对象并返回主键值,会针对对象的子表集合数据进行级联保存</b>
	 * @param entity
	 * @param dataSource
	 * @return
	 */
	protected Object save(final Serializable entity, final DataSource dataSource) {
		return dialectFactory.save(sqlToyContext, entity, this.getDataSource(dataSource));
	}

	/**
	 * @todo 批量插入对象(会自动根据主键策略产生主键值,并填充对象集合),不做级联操作
	 * @param entities
	 */
	protected Long saveAll(final List<Serializable> entities) {
		return this.saveAll(entities, null, null);
	}

	/**
	 * @todo 批量保存对象,并可以通过反调函数对插入值进行灵活干预修改
	 * @param entities
	 * @param reflectPropertyHandler
	 */
	protected Long saveAll(final List<Serializable> entities, final ReflectPropertyHandler reflectPropertyHandler) {
		return this.saveAll(entities, reflectPropertyHandler, null);
	}

	/**
	 * @todo <b>指定数据库进行批量插入</b>
	 * @param entities
	 * @param reflectPropertyHandler
	 * @param dataSource
	 */
	protected Long saveAll(final List<Serializable> entities, final ReflectPropertyHandler reflectPropertyHandler,
			final DataSource dataSource) {
		return dialectFactory.saveAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), reflectPropertyHandler,
				this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 保存对象数据(返回插入的主键值),忽视已经存在的
	 * @param entity
	 * @return
	 */
	protected Long saveAllNotExist(final List<Serializable> entities) {
		return this.saveAllNotExist(entities, null, null);
	}

	/**
	 * @todo 保存对象数据(返回插入的主键值),忽视已经存在的
	 * @param entity
	 * @param dataSource
	 * @return
	 */
	protected Long saveAllNotExist(final List<Serializable> entities, final DataSource dataSource) {
		return this.saveAllNotExist(entities, null, dataSource);
	}

	/**
	 * @todo 保存对象数据(返回插入的主键值),忽视已经存在的
	 * @param entities
	 * @param reflectPropertyHandler
	 * @param dataSource
	 */
	protected Long saveAllNotExist(final List<Serializable> entities,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource) {
		return dialectFactory.saveAllNotExist(sqlToyContext, entities, sqlToyContext.getBatchSize(),
				reflectPropertyHandler, this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 简单的对象修改操作(属性值为null的不被修改)
	 * @param entity
	 */
	protected Long update(final Serializable entity) {
		return this.update(entity, null, null);
	}

	/**
	 * @todo update对象(值为null的属性不修改,通过forceUpdateProps指定要进行强制修改属性)
	 * @param entity
	 * @param forceUpdateProps
	 */
	protected Long update(final Serializable entity, final String[] forceUpdateProps) {
		return this.update(entity, forceUpdateProps, null);
	}

	/**
	 * @todo <b>根据传入的对象，通过其主键值查询并修改其它属性的值</b>
	 * @param entity
	 * @param forceUpdateProps
	 * @param dataSource
	 */
	protected Long update(final Serializable entity, final String[] forceUpdateProps, final DataSource dataSource) {
		return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, false, null, null,
				this.getDataSource(dataSource));
	}

	/**
	 * @todo 修改对象,并通过指定级联的子对象做级联修改
	 * @param entity
	 * @param forceUpdateProps
	 * @param forceCascadeClasses
	 *            (强制需要修改的子对象,当子集合数据为null,则进行清空或置为无效处理,否则则忽视对存量数据的处理)
	 * @param subTableForceUpdateProps
	 */
	protected Long updateCascade(final Serializable entity, final String[] forceUpdateProps,
			final Class[] forceCascadeClasses, final HashMap<Class, String[]> subTableForceUpdateProps) {
		return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, true, forceCascadeClasses,
				subTableForceUpdateProps, this.getDataSource(null));
	}

	/**
	 * @todo 深度更新实体对象数据,根据对象的属性值全部更新对应表的字段数据,不涉及级联修改
	 * @param entity
	 */
	protected Long updateDeeply(final Serializable entity) {
		return this.updateDeeply(entity, null);
	}

	/**
	 * @todo <b>深度修改,即对象所有属性值都映射到数据库中,如果是null则数据库值被改为null</b>
	 * @param entity
	 * @param dataSource
	 */
	protected Long updateDeeply(final Serializable entity, final DataSource dataSource) {
		return this.update(entity, sqlToyContext.getEntityMeta(entity.getClass()).getRejectIdFieldArray(),
				this.getDataSource(dataSource));
	}

	/**
	 * @todo 批量根据主键值修改对象(具体更新哪些属性以第一条记录为准，如10个属性，第一条记录 中只有5个属性有值，则只更新这5个属性的值)
	 * @param entities
	 */
	protected Long updateAll(final List<Serializable> entities) {
		return this.updateAll(entities, null, null, null);
	}

	/**
	 * @todo 批量根据主键更新每条记录,通过forceUpdateProps设置强制要修改的属性
	 * @param entities
	 * @param forceUpdateProps
	 */
	protected Long updateAll(final List<Serializable> entities, final String[] forceUpdateProps) {
		return this.updateAll(entities, forceUpdateProps, null, null);
	}

	protected Long updateAll(final List<Serializable> entities, final String[] forceUpdateProps,
			final ReflectPropertyHandler reflectPropertyHandler) {
		return this.updateAll(entities, forceUpdateProps, reflectPropertyHandler, null);
	}

	/**
	 * @todo <b>指定数据库,通过集合批量修改数据库记录</b>
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @param dataSource
	 */
	protected Long updateAll(final List<Serializable> entities, final String[] forceUpdateProps,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource) {
		return dialectFactory.updateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), forceUpdateProps,
				reflectPropertyHandler, this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 批量深度修改(参见updateDeeply,直接将集合VO中的字段值修改到数据库中,未null则置null)
	 * @param entities
	 * @param reflectPropertyHandler
	 */
	protected Long updateAllDeeply(final List<Serializable> entities,
			final ReflectPropertyHandler reflectPropertyHandler) {
		return updateAllDeeply(entities, reflectPropertyHandler, null);
	}

	/**
	 * @todo 指定数据源进行批量深度修改(对象属性值为null则设置表对应的字段为null)
	 * @param entities
	 * @param reflectPropertyHandler
	 * @param dataSource
	 */
	protected Long updateAllDeeply(final List<Serializable> entities,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource) {
		if (entities == null || entities.isEmpty())
			return Long.valueOf(0);
		return updateAll(entities, this.getEntityMeta(entities.get(0).getClass()).getRejectIdFieldArray(),
				reflectPropertyHandler, null);
	}

	/**
	 * @todo 对象修改或保存,sqltoy自动根据主键判断数据是否已经存在，存在则修改， 不存在则保存
	 * @param entity
	 */
	protected Long saveOrUpdate(final Serializable entity) {
		return this.saveOrUpdate(entity, null, null);
	}

	protected Long saveOrUpdate(final Serializable entity, final String[] forceUpdateProps) {
		return this.saveOrUpdate(entity, forceUpdateProps, null);
	}

	/**
	 * @todo 指定数据库,对对象进行保存或修改，forceUpdateProps:当修改操作时强制修改的属性
	 * @param entity
	 * @param forceUpdateProps
	 * @param dataSource
	 */
	protected Long saveOrUpdate(final Serializable entity, final String[] forceUpdateProps,
			final DataSource dataSource) {
		return dialectFactory.saveOrUpdate(sqlToyContext, entity, forceUpdateProps, this.getDataSource(dataSource));
	}

	protected Long saveOrUpdateAll(final List<Serializable> entities) {
		return this.saveOrUpdateAll(entities, null, null, null);
	}

	/**
	 * @todo 批量保存或修改，并指定强迫修改的字段属性
	 * @param entities
	 * @param forceUpdateProps
	 */
	protected Long saveOrUpdateAll(final List<Serializable> entities, final String[] forceUpdateProps) {
		return this.saveOrUpdateAll(entities, forceUpdateProps, null, null);
	}

	/**
	 * @todo 批量保存或修改,自动根据主键来判断是修改还是保存，没有主键的直接插入记录，
	 *       存在主键值的先通过数据库查询判断记录是否存在，不存在则插入记录，存在则修改
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 */
	protected Long saveOrUpdateAll(final List<Serializable> entities, final String[] forceUpdateProps,
			final ReflectPropertyHandler reflectPropertyHandler) {
		return this.saveOrUpdateAll(entities, forceUpdateProps, reflectPropertyHandler, null);
	}

	/**
	 * @todo <b>批量保存或修改</b>
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @param dataSource
	 */
	protected Long saveOrUpdateAll(final List<Serializable> entities, final String[] forceUpdateProps,
			final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource) {
		return dialectFactory.saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), forceUpdateProps,
				reflectPropertyHandler, this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 通过主键删除单条记录(会自动级联删除子表,根据数据库配置)
	 * @param entity
	 */
	protected Long delete(final Serializable entity) {
		return dialectFactory.delete(sqlToyContext, entity, this.getDataSource(null));
	}

	protected Long delete(final Serializable entity, final DataSource dataSource) {
		return dialectFactory.delete(sqlToyContext, entity, this.getDataSource(dataSource));
	}

	protected Long deleteAll(final List<Serializable> entities) {
		return this.deleteAll(entities, null);
	}

	/**
	 * @todo <b>批量删除数据</b>
	 * @param entities
	 * @param dataSource
	 */
	protected Long deleteAll(final List<Serializable> entities, final DataSource dataSource) {
		return dialectFactory.deleteAll(sqlToyContext, entities, sqlToyContext.getBatchSize(),
				this.getDataSource(dataSource), null);
	}

	/**
	 * @todo 锁定记录查询，并对记录进行修改,最后将结果返回
	 * @param queryExecutor
	 * @param updateRowHandler
	 * @return
	 */
	protected List updateFetch(final QueryExecutor queryExecutor, final UpdateRowHandler updateRowHandler) {
		return dialectFactory.updateFetch(sqlToyContext, queryExecutor, updateRowHandler,
				this.getDataSource(queryExecutor.getDataSource())).getRows();
	}

	/**
	 * @todo 取符合条件的前固定数量的记录，锁定并进行修改
	 * @param queryExecutor
	 * @param topSize
	 * @param updateRowHandler
	 * @return
	 */
	@Deprecated
	protected List updateFetchTop(final QueryExecutor queryExecutor, final Integer topSize,
			final UpdateRowHandler updateRowHandler) {
		return dialectFactory.updateFetchTop(sqlToyContext, queryExecutor, topSize, updateRowHandler,
				this.getDataSource(queryExecutor.getDataSource())).getRows();
	}

	/**
	 * @todo 随机提取符合条件的记录,锁定并进行修改
	 * @param queryExecutor
	 * @param random
	 * @param updateRowHandler
	 * @return
	 */
	@Deprecated
	protected List updateFetchRandom(final QueryExecutor queryExecutor, final Integer random,
			final UpdateRowHandler updateRowHandler) {
		return dialectFactory.updateFetchRandom(sqlToyContext, queryExecutor, random, updateRowHandler,
				this.getDataSource(queryExecutor.getDataSource())).getRows();
	}

	/**
	 * @todo 获取对象信息(对应的表以及字段、主键策略等等的信息)
	 * @param entityClass
	 * @return
	 */
	protected EntityMeta getEntityMeta(Class entityClass) {
		return sqlToyContext.getEntityMeta(entityClass);
	}

	/**
	 * @todo 获取sqltoy配置的批处理每批记录量(默认为50)
	 * @return
	 */
	protected int getBatchSize() {
		return sqlToyContext.getBatchSize();
	}

	/**
	 * @todo 协助完成对对象集合的属性批量赋予相应数值
	 * @param names
	 * @return
	 */
	protected BeanPropsWrapper wrapBeanProps(String... names) {
		return new BeanPropsWrapper(names);
	}

	/**
	 * @todo <b>手工提交数据库操作,只提供当前DataSource提交</b>
	 */
	protected void flush() {
		flush(null);
	}

	/**
	 * @todo <b>手工提交数据库操作,只提供当前DataSource提交</b>
	 */
	protected void flush(DataSource dataSource) {
		DataSourceUtils.processDataSource(sqlToyContext, this.getDataSource(dataSource),
				new DataSourceCallbackHandler() {
					public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
						if (!conn.isClosed()) {
							conn.commit();
						}
					}
				});
	}

	/**
	 * @todo 产生ID(可以指定增量范围)
	 * @param signature
	 * @param increment
	 * @return
	 */
	protected long generateBizId(String signature, int increment) {
		if (StringUtil.isBlank(signature))
			throw new IllegalArgumentException("signature 必须不能为空,请正确指定业务标志符号!");
		return ((RedisIdGenerator) RedisIdGenerator.getInstance(sqlToyContext)).generateId(signature, increment);
	}

	/**
	 * @todo 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * @param entity
	 * @return
	 */
	protected String generateBizId(Serializable entity) {
		EntityMeta entityMeta = this.getEntityMeta(entity.getClass());
		if (entityMeta == null || !entityMeta.isHasBizIdConfig())
			throw new IllegalArgumentException(
					StringUtil.fillArgs("对象:{},没有配置业务主键生成策略,请检查POJO 的业务主键配置!", entity.getClass().getName()));
		int businessIdType = entityMeta.getColumnType(entityMeta.getBusinessIdField());
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(), null, null);
		// 提取关联属性的值
		Object[] relatedColValue = null;
		if (relatedColumn != null) {
			relatedColValue = new Object[relatedColumn.length];
			for (int meter = 0; meter < relatedColumn.length; meter++) {
				relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
				if (relatedColValue[meter] == null)
					throw new IllegalArgumentException("对象:" + entity.getClass().getName() + " 生成业务主键依赖的关联字段:"
							+ relatedColumn[meter] + " 值为null!");
			}
		}
		IdGenerator idGenerator = (entityMeta.getBusinessIdGenerator() == null) ? entityMeta.getIdGenerator()
				: entityMeta.getBusinessIdGenerator();
		return idGenerator
				.getId(entityMeta.getTableName(), entityMeta.getBizIdSignature(), entityMeta.getBizIdRelatedColumns(),
						relatedColValue, new Date(), businessIdType, entityMeta.getBizIdLength())
				.toString();
	}

	/**
	 * @todo 获取缓存数据
	 * @param cacheName
	 * @param cacheType
	 * @return
	 */
	protected HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType) {
		return this.sqlToyContext.getTranslateManager().getCacheData(this.sqlToyContext, cacheName, cacheType);
	}

	/**
	 * @todo 利用sqltoy的translate缓存，通过显式调用对集合数据的列进行翻译
	 * @param dataSet
	 * @param cacheName
	 * @param cacheType
	 * @param cacheNameIndex
	 * @param handler
	 */
	protected void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler) {
		// 数据以及合法性校验
		if (dataSet == null || dataSet.isEmpty())
			return;
		if (cacheName == null)
			throw new IllegalArgumentException("缓存名称不能为空!");
		if (handler == null)
			throw new IllegalArgumentException("缓存翻译行取key和设置name的反调函数不能为null!");
		// 获取缓存,框架会自动判断null并实现缓存数据的加载和更新检测
		final HashMap<String, Object[]> cache = getTranslateCache(cacheName, cacheType);
		if (cache == null || cache.isEmpty())
			return;
		Iterator iter = dataSet.iterator();
		Object row;
		Object key;
		Object name;
		// 默认名称字段列为1
		int cacheIndex = (cacheNameIndex == null) ? 1 : cacheNameIndex.intValue();
		// 循环获取行数据
		while (iter.hasNext()) {
			row = iter.next();
			if (row != null) {
				// 反调获取需要翻译的key
				key = handler.getKey(row);
				if (key != null) {
					// 从缓存中获取对应的名称
					name = cache.get(key.toString())[cacheIndex];
					// 反调设置行数据中具体列或属性翻译后的名称
					handler.setName(row, (name == null) ? "" : name.toString());
				}
			}
		}
	}
}
