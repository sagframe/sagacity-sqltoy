/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;

/**
 * @project sagacity-sqltoy
 * @description 数据修改操作
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Update.java,Revision:v1.0,Date:2017年10月9日
 */
public class Update extends BaseLink {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5391575924738181611L;

	/**
	 * 强制修改的字段属性
	 */
	private String[] forceUpdateProps;

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
	 * (强制需要修改的子对象,当子集合数据为null,则进行清空或置为无效处理,否则则忽视对存量数据的处理)
	 */
	private Class[] forceCascadeClasses;

	/**
	 * 子表分别需要强制修改的属性
	 */
	private HashMap<Class, String[]> subTableForceUpdateProps;

	/**
	 * 针对个别属性强制统一赋值
	 */
	@Deprecated
	private ReflectPropertyHandler reflectPropertyHandler;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Update(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Update dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
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
	 * @todo 设置是否深度修改
	 * @param deeply
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
	public Update cascadeForceUpdate(HashMap<Class, String[]> subTableForceUpdateProps) {
		this.subTableForceUpdateProps = subTableForceUpdateProps;
		return this;
	}

	public Update reflectHandler(ReflectPropertyHandler reflectPropertyHandler) {
		this.reflectPropertyHandler = reflectPropertyHandler;
		return this;
	}

	/**
	 * @todo 设置强制修改的属性
	 * @param forceUpdateProps
	 * @return
	 */
	public Update forceUpdateProps(String... forceUpdateProps) {
		this.forceUpdateProps = forceUpdateProps;
		return this;
	}

	/**
	 * @todo 单个对象修改
	 * @param entity
	 */
	public Long one(final Serializable entity) {
		if (entity == null)
			throw new IllegalArgumentException("update operate entity is null!");
		boolean cascade = false;
		if ((forceCascadeClasses != null && forceCascadeClasses.length > 0)
				|| (subTableForceUpdateProps != null && !subTableForceUpdateProps.isEmpty())) {
			cascade = true;
		}
		String[] forceUpdate = forceUpdateProps;
		// 深度修改
		if (deeply) {
			forceUpdate = sqlToyContext.getEntityMeta(entity.getClass()).getRejectIdFieldArray();
		}
		return dialectFactory.update(sqlToyContext, entity, forceUpdate, cascade, forceCascadeClasses,
				subTableForceUpdateProps, dataSource);
	}

	/**
	 * @todo 批量修改(批量不做级联)
	 * @param entities
	 */
	public Long many(final List<?> entities) {
		if (entities == null || entities.isEmpty())
			throw new IllegalArgumentException("updateAll operate entities is null or empty!");
		String[] forceUpdate = forceUpdateProps;
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
		return dialectFactory.updateAll(sqlToyContext, entities, realBatchSize, forceUpdate, reflectPropertyHandler,
				dataSource, autoCommit);
	}
}
