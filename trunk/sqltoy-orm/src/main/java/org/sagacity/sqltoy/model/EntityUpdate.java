/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.model.inner.EntityUpdateExtend;

/**
 * @description 提供给代码中组织sql进行数据库update操作
 * @author zhongxuchen
 * @version v1.0,Date:2020-5-15
 */
public class EntityUpdate implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6476698994760985087L;

	public static EntityUpdate create() {
		return new EntityUpdate();
	}

	/**
	 * 内部参数对象模型,减少开发时大量的get对开发的影响
	 */
	private EntityUpdateExtend innerModel = new EntityUpdateExtend();

	public EntityUpdate set(String param, Object value) {
		innerModel.updateValues.put(param, value);
		return this;
	}

	/**
	 * @TODO 设置条件
	 * @param where
	 * @return
	 */
	public EntityUpdate where(String where) {
		innerModel.where = where;
		return this;
	}

	/**
	 * @TODO 设置参数值为空白是否转null
	 * @return
	 */
	public EntityUpdate blankToNull(Boolean blankToNull) {
		innerModel.blankToNull = blankToNull;
		return this;
	}

	public EntityUpdate values(Object... values) {
		if (values != null && values.length == 1 && values[0] != null && values[0] instanceof Map) {
			innerModel.values = new Object[] { new IgnoreKeyCaseMap((Map) values[0]) };
		} else {
			innerModel.values = values;
		}
		return this;
	}

	public EntityUpdate dataSource(DataSource dataSource) {
		innerModel.dataSource = dataSource;
		return this;
	}

	public EntityUpdateExtend getInnerModel() {
		return innerModel;
	}

}
