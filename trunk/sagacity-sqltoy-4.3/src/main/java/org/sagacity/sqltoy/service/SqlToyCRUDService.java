/**
 * 
 */
package org.sagacity.sqltoy.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.exception.BaseException;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.plugin.TranslateHandler;

/**
 * @project sqltoy-orm
 * @description 通过SqlToy提供通用的增删改查操作Service接口,从而减少针对一些非常
 *              简单的操作自行编写service实现，减少了代码开发量
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyCRUDService.java,Revision:v1.0,Date:2012-7-16
 */
@SuppressWarnings("rawtypes")
public interface SqlToyCRUDService {
	/**
	 * @todo 保存单条记录对象
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public Object save(Serializable entity);

	/**
	 * @todo 批量保存对象
	 * @param entities
	 * @param reflectPropertyHandler
	 * @throws BaseException
	 */
	public Long saveAll(List<?> entities, ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 批量保存对象
	 * @param entities
	 * @throws BaseException
	 */
	public Long saveAll(List<?> entities);

	/**
	 * @todo 非深度修改对象
	 * @param entity
	 * @throws BaseException
	 */
	public Long update(Serializable entity);

	/**
	 * @todo 修改对象，设置强制修改的属性
	 * @param entity
	 * @param forceUpdateProps
	 * @throws BaseException
	 */
	public Long update(Serializable entity, String[] forceUpdateProps);

	/**
	 * @todo 是否深度修改对象
	 * @param entity
	 * @throws BaseException
	 */
	public Long updateDeeply(Serializable entity);

	/**
	 * @todo 批量对对象进行修改(以首条记录为基准决定哪些字段会被修改)
	 * @param entities
	 * @throws BaseException
	 */
	public Long updateAll(List<?> entities);

	/**
	 * @todo 批量对象修改，通过forceUpdateProps指定哪些字段需要强制修改
	 * @param entities
	 * @param forceUpdateProps
	 * @throws BaseException
	 */
	public Long updateAll(List<?> entities, String[] forceUpdateProps);

	/**
	 * @todo 批量修改对象
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @throws BaseException
	 */
	public Long updateAll(List<?> entities, String[] forceUpdateProps, ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 批量深度集合修改
	 * @param entities
	 * @throws Exception
	 */
	public Long updateAllDeeply(List<?> entities);

	public Long saveOrUpdate(Serializable entity);

	/**
	 * @todo 修改或保存单条记录
	 * @param entity
	 * @param forceUpdateProps
	 * @throws BaseException
	 */
	public Long saveOrUpdate(Serializable entity, String[] forceUpdateProps);

	public Long saveOrUpdateAll(List<?> entities);

	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps);

	/**
	 * @todo 批量修改或保存(通过主键进行判断，对象对应数据库表必须存在主键)
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @throws BaseException
	 */
	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 获取对象数据
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public Serializable load(Serializable entity);

	/**
	 * @todo 级联加载对象
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public Serializable loadCascade(Serializable entity);

	/**
	 * @todo 删除对象
	 * @param entity
	 * @throws BaseException
	 */
	public Long delete(Serializable entity);

	/**
	 * @todo 批量删除对象
	 * @param entities
	 * @throws Exception
	 */
	public Long deleteAll(List<?> entities);

	/**
	 * 清除表的记录
	 * 
	 * @param entityClass
	 * @throws BaseException
	 */
	public void truncate(final Class entityClass);

	/**
	 * @todo 判断是否唯一
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public boolean isUnique(Serializable entity);

	/**
	 * @todo 判断是否唯一 true 表示唯一不重复；false 表示不唯一，即数据库中已经存在
	 * @param entity
	 * @param paramsNamed group+uniqueField
	 * @return
	 * @throws BaseException
	 */
	public boolean isUnique(Serializable entity, final String[] paramsNamed);

	/**
	 * @todo 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 * @param entity
	 * @param pid
	 * @return
	 * @throws BaseException
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pid);

	/**
	 * @todo 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 * @param entity
	 * @param pid
	 * @param appendIdSize
	 * @return
	 * @throws BaseException
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pid, int appendIdSize);

	/**
	 * @todo 根据对象主键获取对象详细信息
	 * @param entities
	 * @return
	 * @throws BaseException
	 */
	public List loadAll(List<?> entities);

	/**
	 * @todo 通过实体对象中的@list 或@page 定义的sql查询结果集
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public List findFrom(Serializable entity);

	public List findFrom(Serializable entity, ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 通过实体对象中的@page/或@list 定义的sql查询分页结果集
	 * @param paginationModel
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public PaginationModel findPageFrom(PaginationModel paginationModel, Serializable entity);

	public PaginationModel findPageFrom(PaginationModel paginationModel, Serializable entity,
			ReflectPropertyHandler reflectPropertyHandler);

	/**
	 * @todo 通过实体对象中@page/@list 定义的sql 查询top记录
	 * @param entity
	 * @param topSize
	 * @return
	 * @throws BaseException
	 */
	public List findTopFrom(Serializable entity, double topSize);

	/**
	 * @todo 通过实体对象中@page/@list 定义的sql进行随机记录查询
	 * @param entity
	 * @param randomCount
	 * @return
	 * @throws BaseException
	 */
	public List getRandomFrom(Serializable entity, double randomCount);

	/**
	 * @todo 获取业务ID
	 * @param signature
	 * @param increment
	 * @return
	 * @throws Exception
	 */
	public long generateBizId(String signature, int increment);

	/**
	 * @todo 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 * @param entity
	 * @return
	 * @throws Exception
	 */
	public String generateBizId(Serializable entity);

	/**
	 * @todo 对记录进行翻译
	 * @param dataSet
	 * @param cacheName
	 * @param cacheType
	 * @param cacheNameIndex
	 * @param handler
	 * @throws Exception
	 */
	public void translate(Collection dataSet, String cacheName, String dictType, Integer cacheNameIndex,
			TranslateHandler handler);
}
