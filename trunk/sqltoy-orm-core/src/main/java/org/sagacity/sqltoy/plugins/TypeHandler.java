package org.sagacity.sqltoy.plugins;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.sagacity.sqltoy.config.model.FieldMeta;

/**
 * @project sagacity-sqltoy
 * @description 提供类型处理匹配，主要针对json、数组等类型
 * @author zhongxuchen
 * @version v1.0, Date:2020-11-25
 * @modify {Date:2022-11-19,setNull和setValue增加了一个dbType参数，便于同样代码适用多个数据库，提供不同数据库逻辑判断}
 */
public abstract class TypeHandler {
	/**
	 * @TODO 当数据为null时,pst.setNull(index,java.sql.Types.xxxx)
	 *       <li>返回true表示完成了setNull操作，框架不再继续处理</li>
	 *       <li>返回false表示类型未匹配，交由框架完成setNull</li>
	 * @param dbType
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
	 * @TODO 自行定义对特定类型的setValue操作，
	 *       <li>返回true表示已经匹配到特殊类型并完成处理，</li>
	 *       <li>返回false则表示未处理，需继续后续框架自动处理</li>
	 * @param dbType
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

	/**
	 * 针对没有用VO/DTO对象接数据场景，返回的类型如:List<List>或List<Map>，需要进行转换
	 * 
	 * @TODO 针对ResultSet.get(index) 返回的数据库特定类型(如:Struct、数组等)，转化成java常见的类型
	 *       (oracle.sql.TIMESTAMP 框架已经默认处理),
	 * @param dbType
	 * @param jdbcValue
	 * @return
	 * @throws Exception
	 */
	public Object toJavaType(Integer dbType, Object jdbcValue) throws Exception {
		return jdbcValue;
	}

	/**
	 * <p>
	 * 一般此方法不会涉及 json等框架已经处理，主要针对向量类型、地理位置类型等特殊场景 用于updateSaveFetch中rs.update操作
	 * false：即没有处理，交由框架处理; true: 自定义完成处理，后续不再进行处理
	 * </p>
	 * 
	 * @param dbType
	 * @param conn
	 * @param rs
	 * @param fieldMeta
	 * @param value
	 * @return
	 */
	public boolean updateValue(Integer dbType, Connection conn, ResultSet rs, FieldMeta fieldMeta, Object value) {
		// fieldMeta.getType() 对应java.sql.Types.xxx
		// rs.updateObject(fieldMeta.getColumnName(),value)
		return false;
	}
}
