/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.sql.PreparedStatement;

/**
 * @project sagacity-sqltoy
 * @description 针对fastJson的默认实现
 * @author zhongxuchen
 * @version v1.0, Date:2020-11-25
 * @modify 2020-11-25,修改说明
 */
public class FastJsonTypeHandler implements TypeHandler {

	@Override
	public boolean setValue(PreparedStatement pst, int paramIndex, int jdbcType, Object value) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object toJavaType(Object jdbcValue) {
		// TODO Auto-generated method stub
		return null;
	}

}
