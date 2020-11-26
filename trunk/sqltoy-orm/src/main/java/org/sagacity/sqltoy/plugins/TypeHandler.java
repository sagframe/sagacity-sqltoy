/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @project sagacity-sqltoy
 * @description 提供类型处理匹配，主要针对json、数组等类型
 * @author zhong
 * @version v1.0, Date:2020-11-25
 * @modify 2020-11-25,修改说明
 */
public abstract class TypeHandler {
	/**
	 * @TODO 当数据为null时,pst.setNull(index,java.sql.Types.xxxx)
	 *       <li>返回true表示完成了setNull操作，框架不再继续处理</li>
	 *       <li>返回false表示类型未匹配，交由框架完成setNull</li>
	 * @param pst
	 * @param paramIndex
	 * @param jdbcType
	 * @return
	 */
	public boolean setNull(PreparedStatement pst, int paramIndex, int jdbcType) {
		return false;
	}

	/**
	 * @TODO 自行定义对特定类型的setValue操作
	 * @param pst
	 * @param paramIndex
	 * @param jdbcType
	 * @param value
	 * @return
	 * @throws SQLException
	 */
	public abstract boolean setValue(PreparedStatement pst, int paramIndex, int jdbcType, Object value)
			throws SQLException;

	/**
	 * @TODO 将例如json等resultSet中的结果转为java对象，映射到VO属性上
	 * @param javaTypeName
	 * @param genericType
	 * @param jdbcValue
	 * @return
	 * @throws Exception
	 */
	public abstract Object toJavaType(String javaTypeName, Class genericType, Object jdbcValue) throws Exception;
}
