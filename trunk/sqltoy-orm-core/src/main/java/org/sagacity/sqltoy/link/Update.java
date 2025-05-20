/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.ParallelConfig;
import org.sagacity.sqltoy.model.PropsMapperConfig;
import org.sagacity.sqltoy.utils.MapperUtils;

/**
 * @project sagacity-sqltoy
 * @description 数据修改操作
 * @author zhongxuchen
 * @version v1.0,Date:2017年10月9日
 */
public class Update extends BaseLink {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5391575924738181611L;

	/**
	 * 强制修改的字段属性
	 */
	private String[] forceUpdateFields;

	private String[] updateFields;

	/**
	 * 是否深度修改
	 */
	private boolean deeply = false;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = null;

	/**
	 * 批处理提交记录数量
	 */
	private int batchSize = 0;

	/**
	 * 实现基于唯一性索引字段进行数据修改(非主键)，暂时不支持
	 */
	private String[] uniqueFields;

	/**
	 * (强制需要修改的子对象,当子集合数据为null,则进行清空或置为无效处理,否则则忽视对存量数据的处理)
	 */
	private Class[] forceCascadeClasses;

	/**
	 * 子表分别需要强制修改的属性
	 */
	private HashMap<Class, String[]> subTableForceUpdateFields;

	private ParallelConfig parallelConfig;

	public Update parallelConfig(ParallelConfig parallelConfig) {
		this.parallelConfig = parallelConfig;
		return this;
	}

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Update(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Update dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Update autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	/**
	 * @todo 设置是否深度修改
	 * @param deeply
	 * @return
	 */
	public Update deeply(boolean deeply) {
		this.deeply = deeply;
		return this;
	}

	/**
	 * 仅仅修改指定字段
	 * 
	 * @param updateFields
	 * @return
	 */
	public Update updateFields(String... updateFields) {
		this.updateFields = updateFields;
		this.forceUpdateFields = updateFields;
		this.deeply = false;
		return this;
	}

	// 暂时不开放
//	public Update uniqueFields(String... uniqueFields) {
//		if (uniqueFields != null && uniqueFields.length > 0) {
//			this.uniqueFields = uniqueFields;
//		}
//		return this;
//	}

	/**
	 * @todo 设置每批记录量
	 * @param batchSize
	 * @return
	 */
	public Update batchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	/**
	 * @todo 级联修改的对象
	 * @param forceCascadeClasses
	 * @return
	 */
	public Update cascadeClasses(Class... forceCascadeClasses) {
		this.forceCascadeClasses = forceCascadeClasses;
		return this;
	}

	/**
	 * @todo 级联修改对象需要强制修改的属性
	 * @param subTableForceUpdateProps
	 * @return
	 */
	public Update cascadeForceUpdate(HashMap<Class, String[]> subTableForceUpdateFields) {
		this.subTableForceUpdateFields = subTableForceUpdateFields;
		return this;
	}

	/**
	 * @see forceUpdateFields(String... forceUpdateFields)
	 * @todo 设置强制修改的属性
	 * @param forceUpdateFields
	 * @return
	 */
	@Deprecated
	public Update forceUpdateProps(String... forceUpdateProps) {
		this.forceUpdateFields = forceUpdateProps;
		return this;
	}

	public Update forceUpdateFields(String... forceUpdateFields) {
		this.forceUpdateFields = forceUpdateFields;
		return this;
	}

	/**
	 * @todo 单个对象修改
	 * @param entity
	 */
	public Long one(final Serializable entity) {
		if (entity == null) {
			throw new IllegalArgumentException("update operate entity is null!");
		}
		boolean cascade = false;
		if ((forceCascadeClasses != null && forceCascadeClasses.length > 0)
				|| (subTableForceUpdateFields != null && !subTableForceUpdateFields.isEmpty())) {
			cascade = true;
		}
		String[] forceUpdate = forceUpdateFields;
		// 深度修改
		if (deeply) {
			forceUpdate = sqlToyContext.getEntityMeta(entity.getClass()).getRejectIdFieldArray();
		}
		if (uniqueFields != null && uniqueFields.length > 0) {
			List entities = new ArrayList();
			// 仅仅修改指定字段
			if (this.updateFields != null && updateFields.length > 0) {
				String[] copyFields = mergeArray(uniqueFields, updateFields);
				// 复制指定部分属性
				entities.add(MapperUtils.map(entity, entity.getClass(), new PropsMapperConfig(copyFields)));
			} else {
				entities.add(entity);
			}
			return dialectFactory.updateAll(sqlToyContext, entities, 1, uniqueFields, forceUpdate, null, null,
					getDataSource(null), null);
		}
		if (this.updateFields != null && updateFields.length > 0) {
			String[] copyFields = mergeArray(sqlToyContext.getEntityMeta(entity.getClass()).getIdArray(), updateFields);
			// 考虑级联复制
			Serializable realEntity = MapperUtils.map(entity, entity.getClass(), cascade ? 1 : 0,
					new PropsMapperConfig(copyFields));
			return dialectFactory.update(sqlToyContext, realEntity, forceUpdate, cascade, forceCascadeClasses,
					subTableForceUpdateFields, getDataSource(null));
		}
		return dialectFactory.update(sqlToyContext, entity, forceUpdate, cascade, forceCascadeClasses,
				subTableForceUpdateFields, getDataSource(null));
	}

	/**
	 * @todo 批量修改(批量不做级联)
	 * @param entities
	 */
	public Long many(final List<?> entities) {
		if (entities == null || entities.isEmpty()) {
			throw new IllegalArgumentException("updateAll operate entities is null or empty!");
		}
		String[] forceUpdate = forceUpdateFields;
		// 深度修改
		if (deeply) {
			Object entity = null;
			for (Object o : entities) {
				if (o != null) {
					entity = o;
					break;
				}
			}
			forceUpdate = sqlToyContext.getEntityMeta(entity.getClass()).getRejectIdFieldArray();
		}
		int realBatchSize = (batchSize > 0) ? batchSize : sqlToyContext.getBatchSize();
		if (this.updateFields != null && this.updateFields.length > 0) {
			Class resultType = entities.get(0).getClass();
			String[] copyFields = mergeArray(sqlToyContext.getEntityMeta(resultType).getIdArray(), updateFields);
			// 复制指定属性形成新的POJO集合
			List realEntities = MapperUtils.mapList(entities, resultType, new PropsMapperConfig(copyFields));
			return dialectFactory.updateAll(sqlToyContext, realEntities, realBatchSize, uniqueFields, forceUpdate, null,
					parallelConfig, getDataSource(null), autoCommit);
		}
		return dialectFactory.updateAll(sqlToyContext, entities, realBatchSize, uniqueFields, forceUpdate, null,
				parallelConfig, getDataSource(null), autoCommit);
	}

	/**
	 * @TODO 合并两个数组
	 * @param sourceAry
	 * @param targetAry
	 * @return
	 */
	public static String[] mergeArray(String[] sourceAry, String[] targetAry) {
		Set<String> copyFields = new HashSet<>();
		Set<String> ignoreKeySet = new HashSet<>();
		if (sourceAry != null) {
			for (String str : sourceAry) {
				if (!ignoreKeySet.contains(str.toLowerCase())) {
					ignoreKeySet.add(str.toLowerCase());
					copyFields.add(str);
				}
			}
		}
		if (targetAry != null) {
			for (String str : targetAry) {
				if (!ignoreKeySet.contains(str.toLowerCase())) {
					ignoreKeySet.add(str.toLowerCase());
					copyFields.add(str);
				}
			}
		}
		String[] result = new String[copyFields.size()];
		copyFields.toArray(result);
		return result;
	}
}
