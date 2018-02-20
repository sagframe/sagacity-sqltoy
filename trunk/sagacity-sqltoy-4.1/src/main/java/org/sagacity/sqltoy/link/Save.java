/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.model.SaveMode;

/**
 * @project sagacity-sqltoy
 * @description 对象保存操作
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Save.java,Revision:v1.0,Date:2017年10月9日
 */
public class Save extends BaseLink {

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
	private ReflectPropertyHandler reflectPropertyHandler;

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

	public Save reflectHandle(ReflectPropertyHandler reflectPropertyHandler) {
		this.reflectPropertyHandler = reflectPropertyHandler;
		return this;
	}

	/**
	 * 设置强制修改的属性
	 * 
	 * @param forceUpdateProps
	 * @return
	 */
	public Save forceUpdateProps(String... forceUpdateProps) {
		this.forceUpdateProps = forceUpdateProps;
		return this;
	}

	/**
	 * 设置数据源
	 * 
	 * @param dataSource
	 * @return
	 */
	public Save dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	public Save autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	/**
	 * 保存模式
	 * 
	 * @param existMode
	 * @return
	 */
	public Save saveMode(SaveMode saveMode) {
		this.saveMode = saveMode;
		return this;
	}

	/**
	 * 保存模式
	 * 
	 * @param existMode
	 * @return
	 */
	public Save batchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	/**
	 * 保存单条记录
	 * 
	 * @param entity
	 * @return
	 * @throws Exception
	 */
	public Object one(final Serializable entity) throws Exception {
		if (entity == null)
			throw new Exception("save entity is null!");
		if (saveMode == SaveMode.APPEND)
			return dialectFactory.save(sqlToyContext, entity, dataSource);
		else if (saveMode == SaveMode.UPDATE)
			return dialectFactory.save(sqlToyContext, entity, dataSource);
		return null;
	}

	/**
	 * 批量保存
	 * 
	 * @param entities
	 * @throws Exception
	 */
	public Long many(final List<?> entities) throws Exception {
		if (entities == null || entities.isEmpty())
			throw new Exception("saveAll entities is null or empty!");
		int realBatchSize = (batchSize > 0) ? batchSize : sqlToyContext.getBatchSize();
		if (saveMode == SaveMode.IGNORE)
			return dialectFactory.saveAllNotExist(sqlToyContext, entities, realBatchSize, reflectPropertyHandler, dataSource,
					autoCommit);
		else {
			if (saveMode == SaveMode.UPDATE)
				return  dialectFactory.saveOrUpdateAll(sqlToyContext, entities, realBatchSize, forceUpdateProps,
						reflectPropertyHandler, dataSource, autoCommit);
			else
				return dialectFactory.saveAll(sqlToyContext, entities, realBatchSize, reflectPropertyHandler, dataSource,
						autoCommit);
		}
	}
}
