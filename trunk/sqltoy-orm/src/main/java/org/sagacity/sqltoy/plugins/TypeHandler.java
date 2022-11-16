package org.sagacity.sqltoy.plugins;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @project sagacity-sqltoy
 * @description 提供类型处理匹配，主要针对json、数组等类型
 * @author zhongxuchen
 * @version v1.0, Date:2020-11-25
 * @modify 2022-11-16 setNull、setValue增加参数dbType便于提供给开发者根据数据库类型做逻辑判断
 */
public abstract class TypeHandler {
	/**
	 * @TODO 当数据为null时,pst.setNull(index,java.sql.Types.xxxx)
	 *       <li>返回true表示完成了setNull操作，框架不再继续处理</li>
	 *       <li>返回false表示类型未匹配，交由框架完成setNull</li>
	 * @param dbType 2022-11-16 新增数据库类型便于逻辑判断
	 * @param pst
	 * @param paramIndex
	 * @param jdbcType
	 * @return
	 * @throws SQLException
	 */
	public boolean setNull(Integer dbType, PreparedStatement pst, int paramIndex, int jdbcType) throws SQLException {
		return false;
	}

	/**
	 * @TODO 自行定义对特定类型的setValue操作
	 * @param dbType 2022-11-16 新增数据库类型便于逻辑判断
	 * @param pst
	 * @param paramIndex
	 * @param jdbcType
	 * @param value
	 * @return
	 * @throws SQLException
	 */
	public abstract boolean setValue(Integer dbType, PreparedStatement pst, int paramIndex, int jdbcType, Object value)
			throws SQLException;

	/**
	 * @TODO 将例如json等resultSet中的结果转为java对象，映射到VO属性上
	 *       <li>返回null，表示没有做处理，返回交框架继续处理</li>
	 *       <li>返回非null结果，表示完成了转换，作为最终结果映射VO属性</li>
	 * @param javaTypeName
	 * @param genericType  泛型类型，当没有泛型时其为null
	 * @param jdbcValue
	 * @return
	 * @throws Exception
	 */
	public Object toJavaType(String javaTypeName, Class genericType, Object jdbcValue) throws Exception {
		return null;
	}
}
