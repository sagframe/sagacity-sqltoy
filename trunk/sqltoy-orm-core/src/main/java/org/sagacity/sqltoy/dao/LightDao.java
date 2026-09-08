package org.sagacity.sqltoy.dao;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.EntityUpdateCallback;
import org.sagacity.sqltoy.callback.StreamResultHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.link.Batch;
import org.sagacity.sqltoy.link.Delete;
import org.sagacity.sqltoy.link.Elastic;
import org.sagacity.sqltoy.link.Execute;
import org.sagacity.sqltoy.link.Load;
import org.sagacity.sqltoy.link.Mongo;
import org.sagacity.sqltoy.link.Query;
import org.sagacity.sqltoy.link.Save;
import org.sagacity.sqltoy.link.Store;
import org.sagacity.sqltoy.link.TableApi;
import org.sagacity.sqltoy.link.TreeTable;
import org.sagacity.sqltoy.link.Unique;
import org.sagacity.sqltoy.link.Update;
import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sagacity-sqltoy
 * @description 提供一个更加简洁直观的Dao实现，保持原SqlToyLazyDao不动，提供更加规范统一的API
 * @author zhongxuchen
 * @version v1.0,Date:2023-03-15
 */
@SuppressWarnings({ "rawtypes" })
public interface LightDao {
	/**
	 * es操作
	 * 
	 * @return Elastic链式操作对象，用于构造并执行es查询
	 */
	public Elastic elastic();

	/**
	 * mongo操作
	 * 
	 * @return Mongo链式操作对象，用于构造并执行mongo查询
	 */
	public Mongo mongo();

	/**
	 * 提供链式操作模式删除操作集合 示例:
	 * <li>lightDao.delete().dataSource(xxxx).one(entity);</li>
	 * <li>lightDao.delete().batchSize(1000).autoCommit(true).many(entities);</li>
	 * <li>lightDao.delete().parallelConfig(ParallelConfig.create().groupSize(5000).maxThreads(10)).many(entities);</li>
	 * 
	 * @return Delete链式操作对象，通过链式配置执行对象删除
	 */
	public Delete delete();

	/**
	 * 提供链式操作模式修改记录
	 * <li>lightDao.update().dataSource(xxx).forceUpdateProps("status").one(entity)</li>
	 * <li>lightDao.update().dataSource(xxx).forceUpdateProps("status").many(entities)</li>
	 * <li>大批量并行:lightDao.update().parallelConfig(ParallelConfig.create().groupSize(5000).maxThreads(10)).forceUpdateProps("status").many(entities)</li>
	 * 
	 * @return Update链式操作对象，通过链式配置执行对象修改
	 */
	public Update update();

	/**
	 * 提供链式操作模式存储过程操作集合,示例:
	 * <li>lightDao.store().dataSource(xxx).sql(xxxStoreSql).inParams(x,b,c).resultTypes(xxx.Class).submit();</li>
	 * 
	 * @return Store链式操作对象，通过链式配置调用存储过程
	 */
	public Store store();

	/**
	 * 提供链式操作模式保存操作集合,如:
	 * <li>lightDao.save().dataSource(xxx).one(entity)</li>
	 * <li>lightDao.save().dataSource(xxx).many(entities)</li>
	 * <li>lightDao.save().dataSource(xxx).saveMode(SaveMode.UPDATE).many(entities)</li>
	 * <li>lightDao.save().dataSource(xxx).saveMode(SaveMode.UPDATE).deeply(true).many(entities)</li>
	 * <li>lightDao.save().saveMode(SaveMode.UPDATE).deeply(true).many(entities)</li>
	 * <li>大批量并行:lightDao.save().parallelConfig(ParallelConfig.create().groupSize(5000).maxThreads(10)).many(entities)</li>
	 * 
	 * @return Save链式操作对象，通过链式配置执行对象保存
	 */
	public Save save();

	/**
	 * 提供链式操作模式查询操作集合
	 * 
	 * @return Query链式操作对象，通过链式配置执行查询
	 */
	public Query query();

	/**
	 * 提供链式操作模式对象加载操作集合
	 * <li>lightDao.load().dataSource(xxxx).lock(LockMode.UPGRADE_NOWAIT).one(new
	 * StaffInfo("S0001"));</li>
	 * <li>lightDao.load().parallelConfig(ParallelConfig.create().groupSize(5000).maxThreads(10)).many(entities);</li>
	 * <li>加载主对象，同时级联加载子对象:lightDao.load().cascade(OrderItem.class,OrderDeliveryPlan.class).many(entities);</li>
	 * <li>只根据主对象的主键级联加载子对象:lightDao.load().cascade(OrderItem.class,OrderDeliveryPlan.class).onlyCascade().many(entities);</li>
	 * 
	 * @return Load链式操作对象，通过链式配置执行对象加载
	 */
	public Load load();

	/**
	 * 提供链式操作模式唯一性验证操作集合
	 * <li>lightDao.unique().entity(entity).fields("staffCode","tenantId").submit();</li>
	 * 
	 * @return Unique链式操作对象，通过链式配置执行唯一性验证
	 */
	public Unique unique();

	/**
	 * 提供链式操作模式树形表结构封装操作集合
	 * <li>lightDao.treeTable().treeModel(new
	 * TreeTableModel(organInfoVO).idField("organCode").pidField("organPid")).submit();</li>
	 * 
	 * @return TreeTable链式操作对象，通过链式配置执行树形表包装
	 */
	public TreeTable treeTable();

	/**
	 * 提供链式操作模式sql语句直接执行修改数据库操作集合
	 * 
	 * @return Execute链式操作对象，通过链式配置执行sql修改操作
	 */
	public Execute execute();

	/**
	 * 提供链式操作模式批量执行操作集合
	 * 
	 * @return Batch链式操作对象，通过链式配置执行批量操作
	 */
	public Batch batch();

	/**
	 * 提供数据库表信息查询和更新操作的API集合
	 * 
	 * @return TableApi链式操作对象，提供表信息和表数据操作入口
	 */
	public TableApi tableApi();

	/**
	 * 获取sql对应的配置模型
	 * 
	 * @param sqlKey  对应sqlId
	 * @param sqlType SqlType.search或传null
	 * @return SqlToyConfig
	 */
	public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType sqlType);

	/**
	 * 获取实体对象的跟数据库相关的信息
	 * 
	 * @param entityClass 需要解析的实体类
	 * @return EntityMeta
	 */
	public EntityMeta getEntityMeta(Class entityClass);

	/**
	 * 判断对象属性在数据库中是否唯一
	 * 
	 * @param entity      待验证唯一性的实体对象，以唯一性字段对应的属性值作为查询条件
	 * @param paramsNamed 对象属性名称(不是数据库表字段名称)
	 * @return boolean true：唯一；false：不唯一
	 */
	public boolean isUnique(Serializable entity, String... paramsNamed);

	/**
	 * 通过map传参获取记录数量
	 * 
	 * @param sqlOrSqlId sql中可通过注释:-- #print或#debug 打开sql显示日志通过#not_print
	 *                   或#not_debug 关闭--
	 * @param paramsMap  sql中命名参数对应的参数值Map，key为参数名称
	 * @return Long 查询符合条件的记录数量
	 */
	public Long getCount(String sqlOrSqlId, Map<String, Object> paramsMap);

	/**
	 * 通过POJO产生count语句
	 * 
	 * @param entityClass 实体类，对应要统计记录数量的表
	 * @param entityQuery 例如:EntityQuery.create().where("status=:status").names("status").values(1)
	 * @return Long 查询符合条件的记录数量
	 */
	public Long getCount(Class entityClass, EntityQuery entityQuery);

	/**
	 * 存储过程调用
	 * 
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues 存储过程输入参数值数组，顺序与存储过程定义的参数一致
	 * @return StoreResult 用:getRows()获得查询结果
	 */
	public StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamValues);

	/**
	 * 存储过程调用，outParams可以为null
	 * 
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues 存储过程输入参数值数组，顺序与存储过程定义的参数一致
	 * @param outParamsType 可以为null
	 * @param resultType    可以是VO、Map.class、LinkedHashMap.class、Array.class,null(二维List)
	 * @return StoreResult 用:getRows()获得查询结果
	 */
	public StoreResult executeStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class resultType);

	/**
	 * 存储过程调用，outParams可以为null
	 * 
	 * @param storeSqlOrKey 可以是xml中的sqlId 或者直接{call storeName (?,?)}
	 * @param inParamValues 存储过程输入参数值数组，顺序与存储过程定义的参数一致
	 * @param outParamsType 可以为null
	 * @param resultTypes   可以是VO、Map.class、LinkedHashMap.class、Array.class,null(二维List)
	 * @return StoreResult 用:getRows()获取主记录、List[] getMoreResults()获取全部结果
	 */
	public StoreResult executeMoreResultStore(String storeSqlOrKey, Object[] inParamValues, Integer[] outParamsType,
			Class... resultTypes);

	/**
	 * 流式获取查询结果
	 * 
	 * @param queryExecutor       可通过queryExecutor.showsql()开关sql输出日志
	 * @param streamResultHandler 流式读取每行查询结果的回调处理器，逐行回调进行业务处理
	 */
	public void fetchStream(final QueryExecutor queryExecutor, final StreamResultHandler streamResultHandler);

	/**
	 * 保存对象,并返回主键值
	 * 
	 * @param entity 待保存的实体对象，主键值为空时按主键生成策略自动产生值
	 * @return Object 返回主键值
	 */
	public Object save(Serializable entity);

	/**
	 * 批量保存对象，并返回数据更新记录量
	 * 
	 * @param <T>      实体对象的类型
	 * @param entities 待批量保存的实体对象集合，主键值为空时按主键生成策略自动产生值
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long saveAll(List<T> entities);

	/**
	 * 批量保存对象并忽视已经存在的记录
	 * 
	 * @param <T>      实体对象的类型
	 * @param entities 待批量保存的实体对象集合，已存在的记录跳过不处理
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities);

	/**
	 * 修改数据并返回数据库记录变更数量(非强制修改属性，当属性值为null不参与修改)
	 * 
	 * @param entity           待修改的实体对象，以主键值作为更新条件
	 * @param forceUpdateProps 强制修改的字段属性
	 * @return Long 数据库发生变更的记录量
	 */
	public Long update(Serializable entity, String... forceUpdateProps);

	/**
	 * 深度修改,不管是否为null全部字段强制修改
	 * 
	 * @param entity 待深度修改的实体对象，全部非主键字段（包括null值属性）强制参与修改
	 * @return Long 数据库发生变更的记录量
	 */
	public Long updateDeeply(Serializable entity);

	// 注意:updateFetch查询核心目的是用于数据逻辑校验，页面展示性查询不要用此方法
	// sqltoy的updateFetch是jpa没有的，可以深入了解其原理，一次交互完成查询、锁定、修改并返回修改后结果
	/**
	 * 获取并锁定数据并进行修改(一般为简单查询，查询的目的是锁住记录进行:逻辑校验(如扣减后不能<0)，符合条件进行修改)
	 * 
	 * @param queryExecutor    查询执行器，定义查询的sql、条件参数和锁策略
	 * @param updateRowHandler 行数据修改回调处理器，通过其校验并修改行数据后提交更新
	 * @return 修改后的行记录集合
	 */
	public List updateFetch(final QueryExecutor queryExecutor, final UpdateRowHandler updateRowHandler);

	/**
	 * 适用于库存台账、客户资金账等高并发强事务场景，一次数据库交互实现：
	 * <p>
	 * <li>1、锁查询；</li>
	 * <li>2、记录存在则修改；</li>
	 * <li>3、记录不存在则执行insert；</li>
	 * <li>4、返回修改或插入的记录信息</li>
	 * </p>
	 * 
	 * @param <T>              实体对象的类型
	 * @param entity           尽量不要使用identity、sequence主键
	 * @param updateRowHandler 行数据修改回调处理器，对锁定的记录修改后保存
	 * @param uniqueProps      唯一性字段，用于做唯一性检索，不设置则按照主键进行查询
	 * @return 修改或插入后的实体对象
	 */
	public <T extends Serializable> T updateSaveFetch(final T entity, final UpdateRowHandler updateRowHandler,
			final String... uniqueProps);

	/**
	 * <p>
	 * 示例行更新回调使用：
	 * 
	 * <pre>{@code
	 * lightDao.updateSaveFetch(invStock, (entity, rowIndex) -> {
	 * 		// 这里entity取值，实际通过代理，走的是rs.getInt("stock_num")获取的值
	 * 		Integer stockNum = entity.getStockNum();
	 *   	if (stockNum != null && stockNum > 0) {
	 *    		// 这里set，实际是rs.updateInt("stock_num", stockNum - 10)进行更新			
	 *    		entity.setStockNum(stockNum - 10);
	 *      }
	 * }
	 * }</pre>
	 * </p>
	 * 
	 * @param <T>         实体对象的类型
	 * @param entity      待修改或插入的实体对象，以主键或唯一性字段值检索记录
	 * @param callback    行更新回调处理器，读取记录当前值修改后保存
	 * @param uniqueProps 唯一性字段属性名称数组，不设置则按照主键检索
	 * @return 修改或插入后的实体对象
	 */
	public <T extends Serializable> T updateSaveFetch(final T entity, final EntityUpdateCallback<T> callback,
			final String... uniqueProps);

	/**
	 * 带设置锁时长的:锁查询、存在则修改、不存在则插入操作
	 * 
	 * @param <T>             实体对象的类型
	 * @param entity          待修改或插入的实体对象，以主键或唯一性字段值检索记录
	 * @param callback        行更新回调处理器，读取记录当前值修改后保存
	 * @param lockWaitTimeout 单位秒
	 * @param uniqueProps     唯一性字段属性名称数组，不设置则按照主键检索
	 * @return 修改或插入后的实体对象
	 */
	public <T extends Serializable> T updateSaveFetch(final T entity, final EntityUpdateCallback<T> callback,
			final int lockWaitTimeout, final String... uniqueProps);

	/**
	 * 基于对象单表对象查询进行数据更新
	 * <li>自更新:EntityUpdate.create().set("totalAmt=totalAmt+?",10).where("staffName
	 * like ?").values("张")</li>
	 * <li>常规:EntityUpdate.create().set("createBy", "S0001").where("staffName like
	 * ?").values("张")</li>
	 * 
	 * @param entityClass  实体类，对应要修改数据的表
	 * @param entityUpdate 修改模型对象，定义set赋值字段和where条件
	 * @return Long 数据库发生变更的记录量
	 */
	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate);

	/**
	 * 级联修改数据并返回数据库记录变更数量
	 * 
	 * @param entity                   待级联修改的实体对象，以主键值作为更新条件
	 * @param forceUpdateProps         强制修改的属性名称数组，属性值为null时也参与修改
	 * @param forceCascadeClasses      级联对象为null或空时，是否表示强制删除级联记录
	 * @param subTableForceUpdateProps 设置级联修改对象强制修改的属性
	 * @return Long 数据库发生变更的记录量
	 */
	public Long updateCascade(Serializable entity, String[] forceUpdateProps, Class[] forceCascadeClasses,
			HashMap<Class, String[]> subTableForceUpdateProps);

	/**
	 * 批量修改操作，并可以指定强制修改的属性(非强制修改属性，当属性值为null不参与修改)
	 * 
	 * @param <T>              实体对象的类型
	 * @param entities         待批量修改的实体对象集合，以主键值作为更新条件
	 * @param forceUpdateProps 强制修改的属性名称数组，属性值为null时也参与修改
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * 批量深度修改，即全部字段参与修改(包括为null的属性)
	 * 
	 * @param <T>      实体对象的类型
	 * @param entities 待批量深度修改的实体对象集合，全部字段参与修改
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long updateAllDeeply(List<T> entities);

	/**
	 * 保存或修改数据并返回数据库记录变更数量
	 * 
	 * @param entity           待保存或修改的实体对象，主键值对应记录已存在时转为修改
	 * @param forceUpdateProps 强制修改的字段
	 * @return Long 数据库发生变更的记录量
	 */
	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps);

	/**
	 * 批量保存或修改操作(当已经存在就执行修改)
	 * 
	 * @param <T>              实体对象的类型
	 * @param entities         待批量保存或修改的实体对象集合，已存在同主键记录时转为修改
	 * @param forceUpdateProps 强制修改的字段
	 * @return Long 数据库发生变更的记录量
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * 删除单条对象并返回数据库记录影响的数量
	 * 
	 * @param entity 待删除的实体对象，以主键值作为删除条件
	 * @return Long 数据库发生变更的记录量(删除数据量)
	 */
	public Long delete(final Serializable entity);

	/**
	 * 批量删除对象并返回数据库记录影响的数量
	 * 
	 * @param entities 待批量删除的实体对象集合，按主键值分批提交删除
	 * @return Long 数据库记录变更量(删除数据量)
	 */
	public <T extends Serializable> Long deleteAll(final List<T> entities);

	/**
	 * 根据id集合批量删除
	 * 
	 * @param entityClass 实体类，对应要删除数据的表
	 * @param ids         待删除记录的主键值数组
	 * @return 实际删除的记录数量
	 */
	public Long deleteByIds(Class entityClass, Object... ids);

	/**
	 * 基于单表查询进行删除操作,提供在代码中进行快捷操作
	 * 
	 * @param entityClass 实体类，对应要删除数据的表
	 * @param entityQuery 例如:EntityQuery.create().where("status=?").values(0)
	 * @return Long 数据库记录变更量(插入数据量)
	 */
	public Long deleteByQuery(Class entityClass, EntityQuery entityQuery);

	/**
	 * truncate 刪除全表记录,通过entityClass获得表名
	 * 
	 * @param entityClass 实体类，通过其对象关系映射解析出实际清空的表名称
	 */
	public void truncate(final Class entityClass);

	/**
	 * 根据实体对象的主键值获取对象的详细信息
	 * 
	 * @param entity 承载主键值的实体对象，查询结果回填到该对象并返回
	 * @return entity
	 */
	public <T extends Serializable> T load(final T entity);

	/**
	 * 根据主键获取对象,提供读取锁设定
	 * 
	 * @param entity   承载主键值的实体对象，查询结果回填到该对象并返回
	 * @param lockMode LockMode.UPGRADE 或LockMode.UPGRADE_NOWAIT等
	 * @return entity
	 */
	public <T extends Serializable> T load(final T entity, final LockMode lockMode);

	/**
	 * 对象加载同时指定加载子类，实现级联加载
	 * 
	 * @param entity       承载主键值的实体对象，查询结果回填到该对象并返回
	 * @param lockMode     加载记录时对数据加锁的模式，如：UPGRADE、UPGRADE_NOWAIT
	 * @param cascadeTypes 需要级联加载的子对象实体类型
	 * @return entity
	 */
	public <T extends Serializable> T loadCascade(final T entity, final LockMode lockMode, final Class... cascadeTypes);

	/**
	 * 根据集合中的主键获取实体的详细信息(底层是批量加载优化了性能,同时控制了in 1000个问题)
	 * 
	 * @param entities 承载主键值的实体对象集合，批量按主键加载并回填查询结果
	 * @return entities
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities);

	/**
	 * 提供带锁记录的批量加载功能
	 * 
	 * @param <T>      实体对象的类型
	 * @param entities 承载主键值的实体对象集合，批量按主键加载并回填查询结果
	 * @param lockMode 读取记录时对数据加锁的模式，如：UPGRADE、UPGRADE_NOWAIT
	 * @return 加载到的实体对象集合
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities, final LockMode lockMode);

	/**
	 * 通过EntityQuery模式加载单条记录
	 * 
	 * @param <T>         实体对象的类型
	 * @param entityClass 实体类，对应要查询的表
	 * @param entityQuery 例如:EntityQuery.create().select(a,b,c).where("tenantId=?
	 *                    and staffId=?).values("1","S0001")
	 * @return 符合条件的实体对象，无匹配记录时返回null
	 */
	public <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery);

	public <T extends Serializable> T loadEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	/**
	 * 通过EntityQuery 组织查询条件对POJO进行单表查询,为代码中进行逻辑处理提供便捷
	 * <li>如果要查询整个表记录:findEntity(entityClass,null) 即可</li>
	 * 
	 * @param <T>         实体对象的类型
	 * @param entityClass 实体类，对应要查询的表
	 * @param entityQuery EntityQuery.create().where("status=:status #[and staffName
	 *                    like
	 *                    :staffName]").names("status","staffName").values(1,null).orderBy()
	 *                    链式设置查询逻辑
	 * @return 符合条件的实体对象集合，entityQuery为null时查询整表
	 */
	public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * 通过entity实体进行查询，但返回结果类型可自行指定
	 * 
	 * @param <T>         查询结果行的目标类型
	 * @param entityClass 实体类，对应要查询的表
	 * @param entityQuery 查询条件构造对象，null时查询整表
	 * @param resultType  指定返回结果类型
	 * @return 查询结果集合，行为resultType指定类型的对象
	 */
	public <T> List<T> findEntity(Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	/**
	 * 单表分页查询
	 * <p>
	 * 1、对象传参: findPageEntity(new
	 * Page(),StaffInfo.class,EntityQuery.create().where("status=:status").values(staffInfo))
	 * 2、数组传参: findPageEntity(new
	 * Page(),StaffInfo.class,EntityQuery.create().where("status=?").values(1))
	 * <p>
	 * 
	 * @param <T>         实体对象的类型
	 * @param page        分页对象，提供页号、每页记录数等分页参数
	 * @param entityClass 实体类，对应要查询的表
	 * @param entityQuery 查询条件构造对象
	 * @return 分页查询结果，包含符合条件的记录总数及当页实体数据
	 */
	public <T> Page<T> findPageEntity(final Page page, Class<T> entityClass, EntityQuery entityQuery);

	/**
	 * 单表分页查询，同时可以指定返回对象类型为非实体对象
	 * 
	 * @param <T>         查询结果行的目标类型
	 * @param page        分页对象，提供页号、每页记录数等分页参数
	 * @param entityClass 实体类，对应要查询的表
	 * @param entityQuery 查询条件构造对象
	 * @param resultType  记录返回的目标类型，可为非实体类型
	 * @return 分页查询结果，包含符合条件的记录总数及当页数据
	 */
	public <T> Page<T> findPageEntity(final Page page, Class entityClass, EntityQuery entityQuery, Class<T> resultType);

	/**
	 * 选择性的加载子表信息
	 * 
	 * @param entities     承载主键值的实体对象集合，批量按主键加载
	 * @param cascadeTypes 需要级联加载的子对象实体类型
	 * @return 加载了级联子对象数据的实体对象集合
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final Class... cascadeTypes);

	/**
	 * 锁住主表记录并级联加载子表数据
	 * 
	 * @param <T>          实体对象的类型
	 * @param entities     承载主键值的实体对象集合，批量按主键加载
	 * @param lockMode     读取记录时对数据加锁的模式，如：UPGRADE、UPGRADE_NOWAIT
	 * @param cascadeTypes 需要级联加载的子对象实体类型
	 * @return 加载了级联子对象数据的实体对象集合
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final LockMode lockMode,
			final Class... cascadeTypes);

	/**
	 * 根据主键获取单个实体对象
	 * 
	 * @param <T>         实体对象的类型
	 * @param entityClass 实体类，对应按主键查询的表
	 * @param id          主键值
	 * @return 对应主键的实体对象，未找到返回null
	 */
	public <T extends Serializable> T loadById(final Class<T> entityClass, Object id);

	/**
	 * 根据id集合批量加载对象
	 * 
	 * @param <T>         实体对象的类型
	 * @param entityClass 实体类，对应按主键批量加载的表
	 * @param ids         主键值数组
	 * @return 按主键加载到的实体对象集合
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, Object... ids);

	/**
	 * 根据id集合批量加载对象,并加锁
	 * 
	 * @param <T>         实体对象的类型
	 * @param entityClass 实体类，对应按主键批量加载的表
	 * @param lockMode    读取记录时对数据加锁的模式，如：UPGRADE、UPGRADE_NOWAIT
	 * @param ids         主键值数组
	 * @return 按主键加载到的实体对象集合
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> entityClass, final LockMode lockMode,
			Object... ids);

	/**
	 * 通过map传参模式获取单条对象记录
	 * 
	 * @param <T>        查询结果行的目标类型
	 * @param sqlOrSqlId 可以直接传sql语句，也可以是xml中定义的sql id
	 * @param paramsMap  sql中命名参数对应的参数值Map，key为参数名称
	 * @param resultType 可以是vo、dto、Map(默认驼峰命名)
	 * @return 符合条件的第一条记录并转为resultType类型，无记录时返回null
	 */
	public <T> T findOne(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> resultType);

	/**
	 * 通过对象实体传参数,框架结合sql中的参数名称来映射对象属性取值
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @param resultType 记录返回的目标类型，如vo、dto、Map
	 * @return 符合条件的第一条记录并转为resultType类型，无记录时返回null
	 */
	public <T> T findOne(final String sqlOrSqlId, final Serializable params, final Class<T> resultType);

	/**
	 * 根据QueryExecutor来链式操作灵活定义查询sql、条件、数据源等
	 * 
	 * @param query new QueryExecutor(sql).dataSource().names().values().filters()
	 *              链式设置查询
	 * @return 符合条件的第一条记录，行结构由queryExecutor中resultType决定，无记录时返回null
	 */
	public Object loadByQuery(final QueryExecutor query);

	public Object getValue(final String sqlOrSqlId, final Map<String, Object> paramsMap);

	/**
	 * 获取查询结果的第一条、第一列的值，一般用select max(x) from 等
	 * <li>lightDao.getValue("select max(amt) from
	 * table",null,BigDecimal.class)</li>
	 * 
	 * @param <T>        单值的目标类型
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param paramsMap  sql中命名参数对应的参数值Map，key为参数名称
	 * @param resultType 单值要转换的目标类型，如：String.class、BigDecimal.class
	 * @return 第一行第一列的值并转为指定类型，无记录时返回null
	 */
	public <T> T getValue(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> resultType);

	/**
	 * 通过Query构造查询条件进行数据查询
	 * 
	 * @param query 范例1:new QueryExecutor(sql).names(xxx).values(xxx).filters()
	 *              链式设置查询 范例2:可以将完整的sql xml配置动态注册并查询new QueryExecutor(new
	 *              XMLBinding(xml).id(id).lastUpdateTime(lastUpdateTime))
	 * 
	 * @return 查询结果对象QueryResult，通过其getRows()获取行数据集合
	 */
	public QueryResult findByQuery(final QueryExecutor query);

	/**
	 * 通过对象传参数,简化paramName[],paramValue[] 模式传参
	 * 
	 * @param <T>        查询结果行的目标类型
	 * @param sqlOrSqlId 可以是具体sql也可以是对应xml中的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @param resultType 记录返回的目标类型
	 * @return 查询结果集合，行为resultType指定类型的对象
	 */
	public <T> List<T> find(final String sqlOrSqlId, final Serializable params, final Class<T> resultType);

	/**
	 * 提供基于Map传参查询
	 * 
	 * @param <T>        查询结果行的目标类型
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param paramsMap  可以使用MapKit.keys().values()等进行构造
	 * @param resultType 可以是vo、dto、Map(默认驼峰命名)
	 * @return 查询结果集合，行为resultType指定类型的对象
	 */
	public <T> List<T> find(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> resultType);

	public List find(final String sqlOrSqlId, final Map<String, Object> paramsMap);

	/**
	 * 通过QueryExecutor来构造查询逻辑进行分页查询
	 * 
	 * @param page          分页对象，提供页号、每页记录数等分页参数
	 * @param queryExecutor 范例:new
	 *                      QueryExecutor(sql).dataSource(dataSource).names(xxx).values(xxx).secureDecrypt(password).filters()
	 *                      链式设置查询
	 * @return 分页查询结果，包含符合条件的记录总数及当页数据
	 */
	public QueryResult findPageByQuery(final Page page, final QueryExecutor queryExecutor);

	public Page findPage(final Page page, final String sqlOrSqlId, final Map<String, Object> paramsMap);

	/**
	 * 提供基于Map传参的分页查询
	 * 
	 * @param <T>        查询结果行的目标类型
	 * @param page       分页对象，提供页号、每页记录数等分页参数
	 * @param sqlOrSqlId sqlToy统一的逻辑:可以是xml中的sqlId 也可以直接是具体sql
	 * @param paramsMap  以Map形式传参
	 * @param resultType 返回结果类型:可以是vo、dto、Map(默认驼峰命名)、List.class、Array.class 等
	 * @return 分页查询结果，包含符合条件的记录总数及当页数据
	 */
	public <T> Page<T> findPage(final Page page, final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType);

	/**
	 * 通过VO对象传参模式的分页，返回结果是VO类型的集合
	 * 
	 * @param <T>        查询结果行的目标类型
	 * @param page       分页对象，提供页号、每页记录数等分页参数
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @param resultType 记录返回的目标类型
	 * @return 分页查询结果，包含符合条件的记录总数及当页数据
	 */
	public <T> Page<T> findPage(final Page page, final String sqlOrSqlId, final Serializable params,
			final Class<T> resultType);

	/**
	 * 提供基于Map传参的top查询
	 * 
	 * @param <T>        查询结果行的目标类型
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param paramsMap  sql中命名参数对应的参数值Map，key为参数名称
	 * @param resultType 可以是vo、dto、Map(默认驼峰命名)
	 * @param topSize    获取最前面的记录数量，大于1按固定条数提取，小于1按比例提取
	 * @return 符合条件的前topSize条记录集合
	 */
	public <T> List<T> findTop(final String sqlOrSqlId, final Map<String, Object> paramsMap, final Class<T> resultType,
			final double topSize);

	/**
	 * 基于对象传参数模式(内部会根据sql中的参数提取对象对应属性的值),并返回对象对应类型的List
	 * 
	 * @param <T>        查询结果行的目标类型
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @param resultType 记录返回的目标类型
	 * @param topSize    (大于1则取固定数量的记录，小于1，则表示按比例提取)
	 * @return 符合条件的前topSize条记录集合
	 */
	public <T> List<T> findTop(final String sqlOrSqlId, final Serializable params, final Class<T> resultType,
			final double topSize);

	/*
	 * 用QueryExecutor组织查询逻辑,findTopByQuery(new
	 * QueryExecutor(sqlOrSqlId,MapKit.keys(...).values(...)).resultType(resultDTO),
	 * 10)
	 */
	public QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize);

	public QueryResult findRandomByQuery(final QueryExecutor queryExecutor, final double randomCount);

	/**
	 * 通过对象传参模式取随机记录
	 * 
	 * @param <T>         查询结果行的目标类型
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param params      查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @param resultType  记录返回的目标类型
	 * @param randomCount 小于1表示按比例提取，大于1则按整数部分提取记录数量
	 * @return 随机抽取的记录集合
	 */
	public <T> List<T> findRandom(final String sqlOrSqlId, final Serializable params, final Class<T> resultType,
			final double randomCount);

	/**
	 * 提供基于Map传参的随机记录查询
	 * 
	 * @param <T>         查询结果行的目标类型
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsMap   sql中命名参数对应的参数值Map，key为参数名称
	 * @param resultType  可以是vo、dto、Map(默认驼峰命名)
	 * @param randomCount 随机提取的记录数量，大于1按整数提取固定条数，小于1按比例提取
	 * @return 随机抽取的记录集合
	 */
	public <T> List<T> findRandom(final String sqlOrSqlId, final Map<String, Object> paramsMap,
			final Class<T> resultType, final double randomCount);

	/**
	 * 批量集合通过sql进行修改操作,调用:batchUpdate(sqlId,List)
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @return 批量执行影响的数据库记录数量
	 */
	public Long batchUpdate(final String sqlOrSqlId, final List dataSet);

	/**
	 * 批量集合通过sql进行修改操作,调用:batchUpdate(sqlId,List,null,null)
	 * <p>
	 * <li>1、VO传参模式，即:batchUpdate(sql,List<VO> dataSet),sql中用:paramName</li>
	 * <li>2、List<List>模式，sql中直接用? 形式传参,弊端就是严格顺序</li>
	 * </p>
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param dataSet    支持List<List>、List<Object[]>(sql中?传参) ;List<VO>、List<Map>
	 *                   形式(sql中:paramName传参)
	 * @param autoCommit (一般为null)
	 */
	public Long batchUpdate(final String sqlOrSqlId, final List dataSet, final Boolean autoCommit);

	/**
	 * 针对类似时序数据库执行插入操作并返回主键值
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param entity     作为插入参数的实体对象，产生的主键值会回填到其对应属性
	 * @param pkField    需要获取值的主键数据库字段名称
	 * @return 插入记录后产生的主键字段值
	 */
	public Object insertReturnPrimaryKey(final String sqlOrSqlId, final Serializable entity, String pkField);

	public Long executeSql(final String sqlOrSqlId);

	/**
	 * 执行sql,并返回被修改的记录数量
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param params     查询参数对象（支持任意实现了Serializable的Bean，如VO、DTO、QueryParam等，对象的属性名将与SQL中的命名参数进行匹配）
	 * @return Long 数据库发生变更的记录数
	 */
	public Long executeSql(final String sqlOrSqlId, final Serializable params);

	/**
	 * 通过数组传参执行sql,并返回更新记录量
	 * 
	 * @param sqlOrSqlId  sql语句或xml中定义的sqlId
	 * @param paramsValue sql中?占位符对应的参数值数组，顺序与占位符一一对应
	 * @return 执行sql影响的记录数量
	 */
	public Long executeSql(final String sqlOrSqlId, final Object... paramsValue);

	/**
	 * 提供基于Map传参的sql执行
	 * 
	 * @param sqlOrSqlId sql语句或xml中定义的sqlId
	 * @param paramsMap  sql中命名参数对应的参数值Map，key为参数名称
	 * @return 执行sql影响的记录数量
	 */
	public Long executeSql(final String sqlOrSqlId, final Map<String, Object> paramsMap);

	/**
	 * 构造树形表的节点路径、层次等级、是否叶子节点等必要信息
	 * <li>lightDao.wrapTreeTableRoute(new
	 * TreeTableModel(organInfoVO).idField("organCode").pidField("organPid"));</li>
	 * <li>配置了统一字段处理器(unifyFieldsHandler)时,路由级联与叶子标记更新的语句
	 * 会自动附加updateUnifyFields()中的公共更新字段(如最后修改人、最后修改时间),
	 * 按实体属性名匹配列名,实体中不存在的字段自动忽略;无实体模型不处理公共更新字段</li>
	 * 
	 * @param treeTableModel 树形表模型对象，提供id字段、pid父级字段等层级结构配置
	 * @return 包装处理成功返回true，失败返回false
	 */
	public boolean wrapTreeTableRoute(final TreeTableModel treeTableModel);

	/**
	 * 数据库提交(针对特殊场景使用,正常情况下此方法不要使用)
	 */
	public void flush();

	/**
	 * 获取sqltoy的上下文
	 * 
	 * @return sqltoy全局上下文对象，提供框架配置及核心能力入口
	 */
	public SqlToyContext getSqlToyContext();

	/**
	 * 获取当前dataSource
	 * 
	 * @return 当前dao绑定的数据源
	 */
	public DataSource getDataSource();

	/**
	 * 获取业务ID(当一个表里面涉及多个业务主键时，sqltoy在配置层面只支持单个，但开发者可以调用此方法自行获取后赋值)
	 * 
	 * @param signature 唯一标识符号
	 * @param increment 增量
	 * @return 按规则产生的业务主键值
	 */
	public long generateBizId(String signature, int increment);

	/**
	 * 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * 
	 * @param entity 配置了业务主键策略的实体对象，其属性值参与主键生成
	 * @return 产生的业务主键值
	 */
	public String generateBizId(Serializable entity);

	/**
	 * 根据指定的表名、业务码，业务码的属性和值map，动态获取业务主键值 例如:generateBizId("sag_test",
	 * "HW@case(orderType,SALE,SC,BUY,PO)@day(yyMMdd)", MapKit.map("orderType",
	 * "SALE"), null, 12, 2);
	 * 
	 * @param tableName    业务主键关联的表名称，业务主键按表维度进行隔离管理
	 * @param signature    一个表达式字符串，支持@case(name,value1,then1,val2,then2)
	 *                     和 @day(yyMMdd)或@day(yyyyMMdd)、@substr(name,start,length)
	 *                     等
	 * @param keyValues    表达式中@case等引用的属性名称和对应值Map
	 * @param bizDate      在signature为空时生效
	 * @param length       产生业务主键值的总长度
	 * @param sequenceSize 主键尾部序列部分的位数
	 * @return 产生的业务主键值
	 */
	public String generateBizId(String tableName, String signature, Map<String, Object> keyValues, LocalDate bizDate,
			int length, int sequenceSize);

	/**
	 * 获取sqltoy中用于翻译的缓存,方便用于页面下拉框选项、checkbox选项、suggest组件等
	 * 
	 * @param cacheName 翻译缓存名称，对应sqltoy翻译配置中定义的缓存
	 * @param cacheType 如是数据字典,则传入字典类型否则为null即可
	 * @return 缓存数据Map，key为缓存key值，value为名称、别名等数组
	 */
	public HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType);

	/**
	 * 将缓存数据以对象形式获取
	 * 
	 * @param <T>        缓存数据返回的目标类型
	 * @param cacheName  翻译缓存名称，对应sqltoy翻译配置中定义的缓存
	 * @param cacheType  如是数据字典,则传入字典类型否则为null即可
	 * @param reusltType 缓存定义时的sql属性名称或自定义的properties属性要跟resultType的属性对应
	 * @return 以resultType对象形式组织的缓存数据集合
	 */
	public <T> List<T> getTranslateCache(String cacheName, String cacheType, Class<T> reusltType);

	/**
	 * 通过反调对集合数据进行翻译处理
	 * 
	 * @param dataSet   待翻译的数据集合
	 * @param cacheName 翻译使用的缓存名称
	 * @param handler   翻译回调处理器，通过其提供key值并回设翻译名称
	 */
	public void translate(Collection dataSet, String cacheName, TranslateHandler handler);

	/**
	 * 对数据集合通过反调函数对具体属性进行翻译
	 * <p>
	 * <li>lightDao.translate(staffVOs<StaffInfoVO>, "staffIdName",
	 * <li>----new TranslateHandler() {
	 * <li>----//告知key值
	 * <li>----public Object getKey(Object row) {
	 * <li>--------return ((StaffInfoVO)row).getStaffId();
	 * <li>----}
	 * <li>----// 将翻译后的名称值设置到对应的属性上
	 * <li>----public void setName(Object row, String name) {
	 * <li>--------((StaffInfoVO)row).setStaffName(name);
	 * <li>----}
	 * <li>});
	 * </p>
	 * 
	 * @param dataSet        数据集合
	 * @param cacheName      缓存名称
	 * @param cacheType      例如数据字典存在分类的缓存填写字典分类，其它的如员工、机构等填null
	 * @param cacheNameIndex 默认为1，缓存名称在缓存数组的第几列(因为有:名称、别名、简称、全称之说)
	 * @param handler        翻译回调处理器，通过其提供key值并回设翻译名称
	 */
	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler);

	/**
	 * 通过缓存将名称进行模糊匹配取得key的集合
	 * 
	 * @param cacheMatchFilter 缓存匹配过滤器，定义匹配的缓存及列范围
	 * @param matchRegexes     数组
	 * @return 匹配到的缓存key值数组
	 */
	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes);

	/**
	 * 判断缓存是否存在
	 * 
	 * @param cacheName 缓存名称
	 * @return 缓存已加载存在返回true，否则返回false
	 */
	public boolean existCache(String cacheName);

	/**
	 * 获取所有缓存的名称
	 * 
	 * @return 所有缓存名称的集合
	 */
	public Set<String> getCacheNames();

	/**
	 * 实现VO和POJO之间属性值的复制
	 * 
	 * @param <T>              属性值复制到的目标类型
	 * @param source           待转换的源对象
	 * @param resultType       属性值复制到的目标类型
	 * @param ignoreProperties 忽略映射匹配的属性
	 * @return 属性值复制后的目标类型对象
	 */
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType, String... ignoreProperties);

	/**
	 * 实现VO和POJO 集合之间属性值的复制
	 * 
	 * @param <T>              属性值复制到的目标类型
	 * @param sourceList       待转换的源对象集合
	 * @param resultType       属性值复制到的目标类型
	 * @param ignoreProperties 忽略映射匹配的属性
	 * @return 转换后的目标类型对象集合
	 */
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType,
			String... ignoreProperties);

	/**
	 * 实现分页对象的类型转换
	 * 
	 * @param <T>              属性值复制到的目标类型
	 * @param sourcePage       待转换的分页对象
	 * @param resultType       属性值复制到的目标类型
	 * @param ignoreProperties 忽略映射匹配的属性
	 * @return 行记录转换为目标类型后的分页对象
	 */
	public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType,
			String... ignoreProperties);

	/**
	 * 提供基于Map传参的并行查询
	 * 
	 * @param <T>                            查询结果行的目标类型
	 * @param parallelQueryList<ParallQuery> ParallQuery中可以单独对本查询设置条件参数
	 * @param paramsMap                      sql中命名参数对应的参数值Map，key为参数名称
	 * @return 各并行查询的结果集合，顺序与parallelQueryList一致
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallelQueryList, Map<String, Object> paramsMap);

	/**
	 * 提供基于Map传参的并行查询,并提供并行线程数、最大等待时长等参数设置
	 * 
	 * @param <T>                            查询结果行的目标类型
	 * @param parallelQueryList<ParallQuery> ParallQuery中可以单独对本查询设置条件参数
	 * @param paramsMap                      sql中命名参数对应的参数值Map，key为参数名称
	 * @param parallelConfig                 例如:ParallelConfig.create().maxThreads(20)
	 * @return 各并行查询的结果集合，顺序与parallelQueryList一致
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallelQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig);
}
