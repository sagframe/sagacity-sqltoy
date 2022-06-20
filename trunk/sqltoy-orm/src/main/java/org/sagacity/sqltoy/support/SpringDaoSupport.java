package org.sagacity.sqltoy.support;

import org.sagacity.sqltoy.SqlToyContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @project sagacity-sqltoy
 * @description 提供spring框架下写dao的基类，如StaffInfoDao extends SpringDaoSupport
 * @author zhongxuchen
 * @version v1.0, Date:2022年6月14日
 * @modify 2022年6月14日,修改说明
 */
public class SpringDaoSupport extends SqlToyDaoSupport {
	/**
	 * 针对spring提供sqlToyContext注入(差异点)
	 */
	@Override
	@Autowired
	@Qualifier(value = "sqlToyContext")
	public void setSqlToyContext(SqlToyContext sqlToyContext) {
		super.setSqlToyContext(sqlToyContext);
	}
}
