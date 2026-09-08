package org.sagacity.sqltoy.link;

import java.io.Serializable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.UniqueExecutor;

/**
 * @project sagacity-sqltoy
 * @description 唯一性验证操作
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-09
 */
public class Unique extends BaseLink {
	private static final long serialVersionUID = 1489170834481063214L;

	/**
	 * 判断唯一性的对象实体
	 */
	private Serializable entity;

	/**
	 * 附加判断属性名称(复合唯一性索引)
	 */
	private String[] fields;

	private int queryTimeout = -1;

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    唯一性验证绑定的数据源，null表示使用默认数据源
	 */
	public Unique(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Unique dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Unique entity(Serializable entity) {
		this.entity = entity;
		return this;
	}

	public Unique fields(String... fields) {
		this.fields = fields;
		return this;
	}

	public Unique queryTimeout(int queryTimeout) {
		this.queryTimeout = queryTimeout;
		return this;
	}

	/**
	 * 提交执行返回结果
	 * 
	 * @return true表示记录唯一（不存在重复），false表示已存在相同记录
	 */
	public Boolean submit() {
		if (entity == null) {
			throw new IllegalArgumentException("Unique check operate entity is null!");
		}
		UniqueExecutor uniqueExecutor = new UniqueExecutor(entity, fields);
		uniqueExecutor.queryTimeout(queryTimeout);
		return dialectFactory.isUnique(sqlToyContext, uniqueExecutor, getDataSource(null));
	}
}
