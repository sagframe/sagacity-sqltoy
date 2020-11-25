/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.sql.PreparedStatement;

/**
 * @project sagacity-sqltoy
 * @description 提供类型处理匹配，主要针对json等类型
 * @author zhong
 * @version v1.0, Date:2020-11-25
 * @modify 2020-11-25,修改说明
 */
public interface TypeHandler {
	public boolean setValue(PreparedStatement pst, int paramIndex, int jdbcType, Object value);

	public Object toJavaType(Object jdbcValue);
}
