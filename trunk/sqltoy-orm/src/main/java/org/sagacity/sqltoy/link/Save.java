/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.model.SaveMode;

/**
 * @project sagacity-sqltoy
 * @description 对象保存操作
 * @author zhongxuchen
 * @version v1.0,Date:2017年10月9日
 */
public class Save extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3086927739096637361L;

	/**
	 * 记录已经存在时的处理策略
	 */
	private SaveMode saveMode = SaveMode.APPEND;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = null;

	/**
	 * 强制修改的字段属性
	 */
	private String[] forceUpdateProps;

	/**
	 * 针对个别属性强制统一赋值
	 */
	@Deprecated
	private ReflectPropsHandler reflectPropsHandler;

	/**
	 * 批处理提交记录数量
	 */
	private int batchSize = 0;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Save(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Save reflectHandler(ReflectPropsHandler reflectPropsHandler) {
		this.reflectPropsHandler = reflectPropsHandler;
		return this;
	}

	/**
	 * @todo 设置强制修改的属性
	 * @param forceUpdateProps
	 * @return
	 */
	public Save forceUpdateProps(String... forceUpdateProps) {
		this.forceUpdateProps = forceUpdateProps;
		return this;
	}

	/**
	 * @todo 设置数据源
	 * @param dataSource
	 * @return
	 */
	public Save dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Save autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	/**
	 * @todo 保存时遇到已经存在时的三种模式(append:依然追加/update:修改/ignore:忽视)
	 * @param saveMode
	 * @return
	 */
	public Save saveMode(SaveMode saveMode) {
		this.saveMode = saveMode;
		return this;
	}

	/**
	 * @todo 批量值
	 * @param batchSize
	 * @return
	 */
	public Save batchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	/**
	 * @todo 保存单条记录
	 * @param entity
	 * @return
	 */
	public Object one(final Serializable entity) {
		if (entity == null) {
			throw new IllegalArgumentException("save entity is null!");
		}
		if (saveMode == SaveMode.APPEND) {
			return dialectFactory.save(sqlToyContext, entity, getDataSource(null));
		}
		if (saveMode == SaveMode.UPDATE) {
			return dialectFactory.saveOrUpdate(sqlToyContext, entity, forceUpdateProps, getDataSource(null));
		}
		if (saveMode == SaveMode.IGNORE) {
			throw new IllegalArgumentException("单条对象记录保存不支持IGNORE 模式,请通过自身逻辑判断SaveMode是append(insert) 还是 update!");
		}
		return null;
	}

	/**
	 * @todo 批量保存
	 * @param entities
	 */
	public <T extends Serializable> Long many(final List<T> entities) {
		if (entities == null || entities.isEmpty()) {
			throw new IllegalArgumentException("saveAll entities is null or empty!");
		}
		int realBatchSize = (batchSize > 0) ? batchSize : sqlToyContext.getBatchSize();
		if (saveMode == SaveMode.IGNORE) {
			return dialectFactory.saveAllIgnoreExist(sqlToyContext, entities, realBatchSize, reflectPropsHandler,
					getDataSource(null), autoCommit);
		}
		if (saveMode == SaveMode.UPDATE) {
			return dialectFactory.saveOrUpdateAll(sqlToyContext, entities, realBatchSize, forceUpdateProps,
					reflectPropsHandler, getDataSource(null), autoCommit);
		}
		return dialectFactory.saveAll(sqlToyContext, entities, realBatchSize, reflectPropsHandler, getDataSource(null),
				autoCommit);
	}
}
