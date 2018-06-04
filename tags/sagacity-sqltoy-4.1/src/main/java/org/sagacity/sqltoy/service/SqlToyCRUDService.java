/**
 * 
 */
package org.sagacity.sqltoy.service;

import java.io.Serializable;
import java.util.List;

import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.exception.BaseException;
import org.sagacity.sqltoy.model.PaginationModel;

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
	public Object save(Serializable entity) throws BaseException;

	/**
	 * @todo 批量保存对象
	 * @param entities
	 * @param reflectPropertyHandler
	 * @throws BaseException
	 */
	public Long saveAll(List<?> entities, ReflectPropertyHandler reflectPropertyHandler) throws BaseException;

	/**
	 * @todo 批量保存对象
	 * @param entities
	 * @throws BaseException
	 */
	public Long saveAll(List<?> entities) throws BaseException;

	/**
	 * @todo 非深度修改对象
	 * @param entity
	 * @throws BaseException
	 */
	public Long update(Serializable entity) throws BaseException;

	/**
	 * @todo 修改对象，设置强制修改的属性
	 * @param entity
	 * @param forceUpdateProps
	 * @throws BaseException
	 */
	public Long update(Serializable entity, String[] forceUpdateProps) throws BaseException;

	/**
	 * @todo 是否深度修改对象
	 * @param entity
	 * @throws BaseException
	 */
	public Long updateDeeply(Serializable entity) throws BaseException;

	/**
	 * @todo 批量对对象进行修改(以首条记录为基准决定哪些字段会被修改)
	 * @param entities
	 * @throws BaseException
	 */
	public Long updateAll(List<?> entities) throws BaseException;

	/**
	 * @todo 批量对象修改，通过forceUpdateProps指定哪些字段需要强制修改
	 * @param entities
	 * @param forceUpdateProps
	 * @throws BaseException
	 */
	public Long updateAll(List<?> entities, String[] forceUpdateProps) throws BaseException;

	/**
	 * @todo 批量修改对象
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @throws BaseException
	 */
	public Long updateAll(List<?> entities, String[] forceUpdateProps, ReflectPropertyHandler reflectPropertyHandler)
			throws BaseException;

	/**
	 * @todo 批量深度集合修改
	 * @param entities
	 * @throws Exception
	 */
	public Long updateAllDeeply(List<?> entities) throws Exception;

	public Long saveOrUpdate(Serializable entity) throws BaseException;

	/**
	 * @todo 修改或保存单条记录
	 * @param entity
	 * @param forceUpdateProps
	 * @throws BaseException
	 */
	public Long saveOrUpdate(Serializable entity, String[] forceUpdateProps) throws BaseException;

	public Long saveOrUpdateAll(List<?> entities) throws BaseException;

	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps) throws BaseException;

	/**
	 * @todo 批量修改或保存(通过主键进行判断，对象对应数据库表必须存在主键)
	 * @param entities
	 * @param forceUpdateProps
	 * @param reflectPropertyHandler
	 * @throws BaseException
	 */
	public Long saveOrUpdateAll(List<?> entities, String[] forceUpdateProps,
			ReflectPropertyHandler reflectPropertyHandler) throws BaseException;

	/**
	 * @todo 获取对象数据
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public Serializable load(Serializable entity) throws BaseException;

	/**
	 * @todo 级联加载对象
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public Serializable loadCascade(Serializable entity) throws BaseException;

	/**
	 * @todo 删除对象
	 * @param entity
	 * @throws BaseException
	 */
	public Long delete(Serializable entity) throws BaseException;

	/**
	 * @todo 批量删除对象
	 * @param entities
	 * @throws Exception
	 */
	public Long deleteAll(List<?> entities) throws BaseException;

	/**
	 * 清除表的记录
	 * 
	 * @param entityClass
	 * @throws BaseException
	 */
	public void truncate(final Class entityClass) throws BaseException;

	/**
	 * @todo 判断是否唯一
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public boolean isUnique(Serializable entity) throws BaseException;

	/**
	 * @todo 判断是否唯一 true 表示唯一不重复；false 表示不唯一，即数据库中已经存在
	 * @param entity
	 * @param paramsNamed
	 *            group+uniqueField
	 * @return
	 * @throws BaseException
	 */
	public boolean isUnique(Serializable entity, final String[] paramsNamed) throws BaseException;

	/**
	 * @todo 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 * @param entity
	 * @param pid
	 * @return
	 * @throws BaseException
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pid) throws BaseException;

	/**
	 * @todo 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 * @param entity
	 * @param pid
	 * @param appendIdSize
	 * @return
	 * @throws BaseException
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pid, int appendIdSize) throws BaseException;

	/**
	 * @todo 根据对象主键获取对象详细信息
	 * @param entities
	 * @return
	 * @throws BaseException
	 */
	public List loadAll(List<?> entities) throws BaseException;

	/**
	 * @todo 通过实体对象中的@list 或@page 定义的sql查询结果集
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public List findFrom(Serializable entity) throws BaseException;

	public List findFrom(Serializable entity, ReflectPropertyHandler reflectPropertyHandler) throws BaseException;

	/**
	 * @todo 通过实体对象中的@page/或@list 定义的sql查询分页结果集
	 * @param paginationModel
	 * @param entity
	 * @return
	 * @throws BaseException
	 */
	public PaginationModel findPageFrom(PaginationModel paginationModel, Serializable entity) throws BaseException;

	public PaginationModel findPageFrom(PaginationModel paginationModel, Serializable entity,
			ReflectPropertyHandler reflectPropertyHandler) throws BaseException;

	/**
	 * @todo 通过实体对象中@page/@list 定义的sql 查询top记录
	 * @param entity
	 * @param topSize
	 * @return
	 * @throws BaseException
	 */
	public List findTopFrom(Serializable entity, double topSize) throws BaseException;

	/**
	 * @todo 通过实体对象中@page/@list 定义的sql进行随机记录查询
	 * @param entity
	 * @param randomCount
	 * @return
	 * @throws BaseException
	 */
	public List getRandomFrom(Serializable entity, double randomCount) throws BaseException;

}
