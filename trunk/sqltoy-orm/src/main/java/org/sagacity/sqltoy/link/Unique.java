/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.executor.UniqueExecutor;

/**
 * @project sagacity-sqltoy
 * @description 唯一性验证操作
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Unique.java,Revision:v1.0,Date:2017年10月9日
 */
public class Unique extends BaseLink {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1489170834481063214L;

	/**
	 * 判断唯一性的对象实体
	 */
	private Serializable entity;

	/**
	 * 附加判断属性名称(复合唯一性索引)
	 */
	private String[] fields;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Unique(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Unique dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
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

	/**
	 * @todo 提交执行返回结果
	 * @return
	 */
	public Boolean submit() {
		if (entity == null)
			throw new IllegalArgumentException("Unique check operate entity is null!");
		return dialectFactory.isUnique(sqlToyContext, new UniqueExecutor(entity, fields), dataSource);
	}
}
