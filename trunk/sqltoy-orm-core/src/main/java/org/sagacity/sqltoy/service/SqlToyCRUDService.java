package org.sagacity.sqltoy.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.model.CacheMatchFilter;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.translate.TranslateHandler;

/**
 * @project sagacity-sqltoy
 * @description 通过SqlToy提供通用的增删改查操作Service接口,从而减少针对一些非常
 *              简单的操作自行编写service实现，减少了代码开发量
 * @author zhongxuchen
 * @version v1.0,Date:2012-07-16
 * @see 推荐自定义service中引入sqltoy自带的lightDao，而非直接使用sqltoy提供的SqlToyCRUDService
 * @modify Date:2020-04-23 对分页查询增加泛型支持
 */
@SuppressWarnings("rawtypes")
public interface SqlToyCRUDService {
	/**
	 * 保存单条记录对象
	 *
	 * @param entity 待保存的实体对象，主键为空时依据主键策略自动产生主键值
	 * @return 实际保存的记录数量
	 */
	public Object save(Serializable entity);

	/**
	 * 批量保存对象
	 *
	 * @param <T>      泛型标记
	 * @param entities 待保存的对象集合
	 * @return 实际保存的记录数量
	 */
	public <T extends Serializable> Long saveAll(List<T> entities);

	/**
	 * 批量保存对象并忽视已经存在的记录
	 *
	 * @param <T>      泛型标记
	 * @param entities 待保存的对象集合
	 * @return 实际保存的记录数量(已存在而被忽视的记录不计入)
	 */
	public <T extends Serializable> Long saveAllIgnoreExist(List<T> entities);

	/**
	 * 修改对象，设置强制修改的属性
	 *
	 * @param entity           待修改的实体对象
	 * @param forceUpdateProps 强制修改的属性名称(属性值为null也会更新到数据库)
	 * @return 实际修改的记录数量
	 */
	public Long update(Serializable entity, String... forceUpdateProps);

	/**
	 * 提供级联修改
	 *
	 * @param entity           待修改的实体对象
	 * @param forceUpdateProps 强制修改的属性名称
	 * @return 实际修改的记录数量(包含级联子表记录)
	 */
	public Long updateCascade(Serializable entity, String... forceUpdateProps);

	/**
	 * 对属性进行强制修改,属性值为null则强制更新数据库字段值
	 *
	 * @param entity 待修改的实体对象
	 * @return 实际修改的记录数量
	 */
	public Long updateDeeply(Serializable entity);

	/**
	 * 批量对象修改，通过forceUpdateProps指定哪些字段需要强制修改
	 *
	 * @param <T>              泛型标记
	 * @param entities         待修改的对象集合
	 * @param forceUpdateProps 强制修改的字段
	 * @return 实际修改的记录数量
	 */
	public <T extends Serializable> Long updateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * 批量深度集合修改，属性值为null将直接覆盖数据库中的值
	 *
	 * @param <T>      泛型标记
	 * @param entities 待修改的对象集合
	 * @return 实际修改的记录数量
	 */
	public <T extends Serializable> Long updateAllDeeply(List<T> entities);

	/**
	 * 修改或保存单条记录
	 *
	 * @param entity           实体对象
	 * @param forceUpdateProps 强制修改的对象属性
	 * @return 实际保存或修改的记录数量
	 */
	public Long saveOrUpdate(Serializable entity, String... forceUpdateProps);

	/**
	 * 批量保存或修改对象
	 *
	 * @param <T>              泛型标记
	 * @param entities         对象集合
	 * @param forceUpdateProps 需强制修改的属性
	 * @return 实际保存或修改的记录数量
	 */
	public <T extends Serializable> Long saveOrUpdateAll(List<T> entities, String... forceUpdateProps);

	/**
	 * 获取对象数据
	 *
	 * @param entity 以对象主键作为查询条件的实体对象，加载后属性值回填到该对象并返回
	 * @return 加载了属性值的对象
	 */
	public <T extends Serializable> T load(T entity);

	/**
	 * 级联加载对象
	 *
	 * @param entity 以对象主键作为查询条件的实体对象，其关联的子表对象一并加载
	 * @return 加载了属性值的对象(含级联子表数据)
	 */
	public <T extends Serializable> T loadCascade(T entity);

	/**
	 * 删除单条对象
	 *
	 * @param entity 以对象主键作为删除条件的实体对象
	 * @return 实际删除的记录数量
	 */
	public Long delete(Serializable entity);

	/**
	 * 批量删除对象
	 *
	 * @param <T>      泛型标记
	 * @param entities 待删除的对象集合
	 * @return 实际删除的记录数量
	 */
	public <T extends Serializable> Long deleteAll(List<T> entities);

	/**
	 * 根据主键值集合批量删除对象
	 *
	 * @param entityClass 实体对象类型
	 * @param ids         主键值集合
	 * @return 实际删除的记录数量
	 */
	public Long deleteByIds(final Class entityClass, Object... ids);

	/**
	 * 清除表的记录
	 *
	 * @param entityClass 实体对象类型
	 */
	public void truncate(final Class entityClass);

	/**
	 * 判断是否唯一 true 表示唯一不重复；false 表示不唯一，即数据库中已经存在
	 *
	 * @param entity      待判断的实体对象
	 * @param paramsNamed group+uniqueField 对象属性名称(不是数据库表字段名称)
	 * @return true 表示唯一不重复；false 表示数据库中已经存在
	 */
	public boolean isUnique(Serializable entity, final String... paramsNamed);

	/**
	 * 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 *
	 * @param entity   树形表对应的实体对象
	 * @param pidField 父节点属性名称(java对象属性名称)
	 * @return true 表示封装成功
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pidField);

	/**
	 * 对树形数据进行封装，构造对象对应表的nodeRoute，nodeLevel，isLeaf等信息 便于对树形结构数据快速查询
	 *
	 * @param entity       树形表对应的实体对象
	 * @param pidField     父节点属性名称(java对象属性名称)
	 * @param appendIdSize 构造成nodeRoute时单个id值的长度，如：1001,1002如果长度设置为6，则001001,001002
	 * @return true 表示封装成功
	 */
	public boolean wrapTreeTableRoute(final Serializable entity, String pidField, int appendIdSize);

	/**
	 * 根据对象主键获取对象详细信息
	 *
	 * @param entities 以对象主键作为查询条件的实体对象集合
	 * @return 加载了属性值的对象集合
	 */
	public <T extends Serializable> List<T> loadAll(List<T> entities);

	/**
	 * 选择性的加载子表信息
	 *
	 * @param entities     实体对象集合
	 * @param cascadeTypes 级联加载的子表对象类型
	 * @return 加载了属性值的对象集合(含指定类型的级联子表数据)
	 */
	public <T extends Serializable> List<T> loadAllCascade(List<T> entities, final Class... cascadeTypes);

	/**
	 * 根据id集合批量加载对象
	 *
	 * @param <T>     泛型标记
	 * @param voClass 实体对象类型
	 * @param ids     主键值集合
	 * @return 加载到的对象集合
	 */
	public <T extends Serializable> List<T> loadByIds(final Class<T> voClass, Object... ids);

	/**
	 * 获取业务ID
	 *
	 * @param signature 格式:tableName_yyyyMMdd,如：staff_info20210701
	 * @param increment 单次获取的流水数量
	 * @return 业务ID值
	 */
	public long generateBizId(String signature, int increment);

	/**
	 * 根据实体对象对应的POJO配置的业务主键策略,提取对象的属性值产生业务主键
	 *
	 * @param entity 配置了@BusinessId注解的实体对象
	 * @return 业务主键值
	 */
	public String generateBizId(Serializable entity);

	/**
	 * 利用缓存通过反调模式对集合数据进行编码转名称翻译
	 *
	 * @param dataSet          待翻译的数据集合
	 * @param cacheName        缓存名称
	 * @param translateHandler 反调方法:取key 和回写名称
	 */
	public void translate(Collection dataSet, String cacheName, TranslateHandler translateHandler);

	/**
	 * 对记录进行翻译(可以)
	 *
	 * @param dataSet          待翻译的数据集合
	 * @param cacheName        缓存名称
	 * @param cacheType        针对类似数据字典性质的有分类的缓存
	 * @param cacheNameIndex   手动指定缓存中名称对应的列(缓存默认格式为:key,name,extName1,extName2
	 *                         默认cacheNameIndex为1)
	 * @param translateHandler
	 */
	public void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
			TranslateHandler translateHandler);

	/**
	 * 判断缓存是否存在
	 *
	 * @param cacheName 缓存名称
	 * @return true 表示缓存存在
	 */
	public boolean existCache(String cacheName);

	/**
	 * 获取所有缓存的名称
	 *
	 * @return 缓存名称集合
	 */
	public Set<String> getCacheNames();

	/**
	 * 通过缓存将名称进行模糊匹配取得key的集合
	 *
	 * @param cacheMatchFilter 缓存模糊匹配条件
	 * @param matchRegexes     匹配表达式，如:中国 上海,xxx公司
	 * @return 匹配到的key集合
	 */
	public String[] cacheMatchKeys(CacheMatchFilter cacheMatchFilter, String... matchRegexes);

	/**
	 * 实现VO和POJO之间属性值的复制,如名称不一致，在VO中字段上使用@SqlToyFieldAlias 注解来处理
	 *
	 * @param <T>        泛型标记
	 * @param source     源对象
	 * @param resultType 目标对象类型
	 * @return 属性值复制后的目标对象
	 */
	public <T extends Serializable> T convertType(Serializable source, Class<T> resultType);

	/**
	 * 实现VO和POJO 集合之间属性值的复制，如名称不一致，在VO中字段上使用@SqlToyFieldAlias 注解来处理
	 *
	 * @param <T>        泛型标记
	 * @param sourceList 源对象集合
	 * @param resultType 目标对象类型
	 * @return 属性值复制后的目标对象集合
	 */
	public <T extends Serializable> List<T> convertType(List sourceList, Class<T> resultType);

	/**
	 * 实现分页对象的类型转换
	 *
	 * @param <T>        泛型标记
	 * @param sourcePage 源分页对象
	 * @param resultType 目标对象类型
	 * @return 记录类型转换后的分页对象
	 */
	public <T extends Serializable> Page<T> convertType(Page sourcePage, Class<T> resultType);

	/**
	 * 基于map传参的并行查询
	 *
	 * @param <T>             泛型标记
	 * @param parallQueryList 并行查询条件集合，每个ParallQuery对应一个查询
	 * @param paramsMap       全部查询共享的条件参数
	 * @param parallelConfig  例如:ParallelConfig.create().maxThreads(20)
	 * @return 并行查询结果集合，顺序与parallQueryList一一对应
	 */
	public <T> List<QueryResult<T>> parallQuery(List<ParallQuery> parallQueryList, Map<String, Object> paramsMap,
			ParallelConfig parallelConfig);
}
