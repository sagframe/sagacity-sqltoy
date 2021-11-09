package org.sagacity.sqltoy.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description 通过SqlToy提供通用的增删改查操作Service接口,从而减少针对一些非常
 *              简单的操作自行编写service实现，减少了代码开发量
 * @author zhongxuchen
 * @version v1.0,Date:2012-7-16
 * @modify Date:2020-4-23 {对分页查询增加泛型支持}
 */
@SuppressWarnings("rawtypes")
public interface SqlToyCRUDService {
	/**
	 * @todo 保存单条记录对象
	 * @param entity
	 * @return
	 */
	public Object save(Serializable entity);

	/**
	 * @todo 批量保存对象
	 * @param entities
	 * @param reflectPropsHandler
	 */
	public <T extends Serializable> Long saveAll(List<T> entities, ReflectPropsHandler reflectPropsHandler);

	/**
	 * @todo 批量保存对象
	 * @param entities
	 */
	public <T extends Serializable> Long saveAll(List<T> entities);

	/**
	 * @todo 批量保存对象并忽视已经存在的记录
	 * @param entities
	 */
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities);

	/**
	 * @todo 修改对象，设置强制修改的属性
	 * @param entity
	 * @param forceUpdateProps
	 */
	public Long update(Serializable entity, String... forceUpdateProps);

	/**
	 * @TODO 提供级联修改
	 * @param serializableVO
	 * @param forceUpdateProps
	 * @return
	 */
	public Long updateCascade(Serializable serializableVO, String... forceUpdateProps);

	/**
	 * @todo 对属性进行强制修改,属性值为null则强制更新数据库字段值
	 * @param entity
	 */
	public Long updateDeeply(Serializable entity);

	/**
	 * @todo 批量对象修改，通过forceUpdateProps指定哪些字段需要强制修改
	 * @param entities
	 * @param forceUpdateProps
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @todo 批量修改对象
	 * @param entities
	 * @param reflectPropsHandler
	 * @param forceUpdateProps
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, ReflectPropsHandler reflectPropsHandler,
			String... forceUpdateProps);

	/**
	 * @todo 批量深度集合修改
	 * @param entities 批量对象集合
	 */
	public <T extends Serializable> Long updateAllDeeply(List<T> entities);

	/**
	 * @todo 修改或保存单条记录
	 * @param entity           实体对象
	 * @param forceUpdateProps 强制修改的对象属性
	 */
	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps);

	/**
	 * @todo 批量保存或修改对象
	 * @param <T>
	 * @param entities         对象集合
	 * @param forceUpdateProps 需强制修改的属性
	 * @return
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * @todo 批量修改或保存(通过主键进行判断，对象对应数据库表必须存在主键)
	 * @param entities
	 * @param reflectPropsHandler
	 * @param forceUpdateProps
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, ReflectPropsHandler reflectPropsHandler,
			String... forceUpdateProps);

	/**
	 * @todo 获取对象数据
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T load(T entity);

	/**
	 * @todo 级联加载对象
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> T loadCascade(T entity);

	/**
	 * @todo 删除单条对象
	 * @param entity
	 */
	public Long delete(Serializable entity);

	/**
	 * @todo 批量删除对象
	 * @param entities
	 */
	public <T extends Serializable> Long deleteAll(List<T> entities);

	/**
	 * @todo 清除表的记录
	 * @param entityClass
	 */
	public void truncate(final Class entityClass);

	/**
	 * @todo 判断是否唯一 true 表示唯一不重复；false 表示不唯一，即数据库中已经存在
	 * @param entity
	 * @param paramsNamed group+uniqueField 对象属性名称(不是数据库表字段名称)
	 * @return
	 */
	public boolean isUnique(Serializable entity, final String... paramsNamed);

	/**
	 * @todo 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 * @param entity
	 * @param pid
	 * @return
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pid);

	/**
	 * @todo 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 * @param entity
	 * @param pid
	 * @param appendIdSize
	 * @return
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pid, int appendIdSize);

	/**
	 * @todo 根据对象主键获取对象详细信息
	 * @param entities
	 * @return
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities);

	/**
	 * @todo 选择性的加载子表信息
	 * @param entities
	 * @param cascadeTypes
	 * @return
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final Class... cascadeTypes);

	/**
	 * @TODO 根据id集合批量加载对象
	 * @param <T>
	 * @param voClass
	 * @param ids
	 * @return
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> voClass, Object... ids);

	/**
	 * 在controller层不允许直接暴露sql,因此sql必须是通过注解在POJO上的
	 * 
	 * @todo 通过实体对象中的@list 或@page 定义的sql查询结果集
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> List<T> findFrom(T entity);

	public <T extends Serializable> List<T> findFrom(T entity, ReflectPropsHandler reflectPropsHandler);

	/**
	 * @todo 通过实体对象中的@page/或@list 定义的sql查询分页结果集
	 * @param paginationModel
	 * @param entity
	 * @return
	 */
	public <T extends Serializable> PaginationModel<T> findPageFrom(PaginationModel paginationModel, T entity);

	public <T extends Serializable> PaginationModel<T> findPageFrom(PaginationModel paginationModel, T entity,
			ReflectPropsHandler reflectPropsHandler);

	/**
	 * @todo 通过实体对象中@page/@list 定义的sql 查询top记录
	 * @param entity
	 * @param topSize
	 * @return
	 */
	public <T extends Serializable> List<T> findTopFrom(T entity, double topSize);

	/**
	 * @todo 通过实体对象中@page/@list 定义的sql进行随机记录查询
	 * @param entity
	 * @param randomCount
	 * @return
	 */
	public <T extends Serializable> List<T> getRandomFrom(T entity, double randomCount);

	/**
	 * @todo 获取业务ID
	 * @param signature
	 * @param increment
	 * @return
	 */
	public long generateBizId(String signature, int increment);

	/**
	 * @todo 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * @param entity
	 * @return
	 */
	public String generateBizId(Serializable entity);

	/**
	 * @TODO 利用缓存通过反调模式对集合数据进行编码转名称翻译
	 * @param dataSet
	 * @param cacheName
	 * @param handler   反调方法:取key 和回写名称
	 */
	public void translate(Collection dataSet, String cacheName, TranslateHandler handler);

	/**
	 * @todo 对记录进行翻译(可以)
	 * @param dataSet
	 * @param cacheName
	 * @param cacheType      针对类似数据字典性质的有分类的缓存
	 * @param cacheNameIndex 手动指定缓存中名称对应的列(缓存默认格式为:key,name,extName1,extName2
	 *                       默认cacheNameIndex为1)
	 * @param handler
	 */
	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler handler);

	/**
	 * @todo 判断缓存是否存在
	 * @param cacheName
	 * @return
	 */
	public boolean existCache(String cacheName);

	/**
	 * @todo 获取所有缓存的名称
	 * @return
	 */
	public Set<String> getCacheNames();

	/**
	 * @TODO 通过缓存将名称进行模糊匹配取得key的集合
	 * @param matchRegex
	 * @param cacheMatchFilter 例如:
	 *                         CacheMatchFilter.create().cacheName("staffIdNameCache")
	 * @return
	 */
	public String[] cacheMatchKeys(String matchRegex, CacheMatchFilter cacheMatchFilter);

	/**
	 * @TODO 实现VO和POJO之间属性值的复制,如名称不一致，在VO中字段上使用@SqlToyFieldAlias 注解来处理
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @return
	 * @throws Exception
	 */
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType);

	/**
	 * @TODO 实现VO和POJO 集合之间属性值的复制，如名称不一致，在VO中字段上使用@SqlToyFieldAlias 注解来处理
	 * @param <T>
	 * @param sourceList
	 * @param resultType
	 * @return
	 */
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType);

	/**
	 * @TODO 实现分页对象的类型转换
	 * @param <T>
	 * @param sourcePage
	 * @param resultType
	 * @return
	 */
	public <T extends Serializable> PaginationModel<T> convertType(PaginationModel sourcePage, Class<T> resultType);

	// parallQuery 面向查询(不要用于事务操作过程中),sqltoy提供强大的方法，但是否恰当使用需要使用者做合理的判断
	/**
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * @param parallQueryList
	 * @param paramNames
	 * @param paramValues
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues);

	/**
	 * @TODO 并行查询并返回一维List，有几个查询List中就包含几个结果对象，paramNames和paramValues是全部sql的条件参数的合集
	 * @param parallQueryList
	 * @param paramNames
	 * @param paramValues
	 * @param parallelConfig
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, String[] paramNames,
			Object[] paramValues, ParallelConfig parallelConfig);

	/**
	 * @TODO 基于map传参的并行查询
	 * @param <T>
	 * @param parallQueryList
	 * @param paramsMap
	 * @param parallelConfig
	 * @return
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig);
}
