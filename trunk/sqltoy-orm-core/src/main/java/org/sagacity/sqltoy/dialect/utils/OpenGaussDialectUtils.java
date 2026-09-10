package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.opengauss.util.PGobject;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @author ming
 * @version v1.0,Date:2024-10-25
 * @project sagacity-sqltoy
 * @description 提供gaussdb数据库相关的特殊逻辑处理封装
 * @modify Date:2024-10-25, 修改说明
 */
public class OpenGaussDialectUtils {
	/**
	 * 主键策略是sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * 
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// sequence
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		// update 2026-9-6 openGauss(5.0/7.0)无generated as identity列语法,建表用bigserial
		// (隐式序列默认值),identity策略须省略主键列让默认值生效(实测insert省略id列时自动生成);
		// 原照搬PG10+返回true会显式含id列插null违反非空(此前openGauss identity被误判不可用)
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}

	/**
	 * 组织获取gaussdb、mogdb类型数据库的save场景下的主键策略
	 * 
	 * @param entityMeta
	 * @param entity
	 * @param dbType
	 * @param conn
	 * @return
	 */
	public static PKStrategy getSavePkStrategy(EntityMeta entityMeta, Serializable entity, Integer dbType,
			Connection conn) {
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		// gaussdb\mogdb\vastbase\opengauss 主键策略是sequence模式需要先获取主键值
		if (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE)) {
			// 取实体对象的主键值
			Object id = BeanUtil.getProperty(entity, entityMeta.getIdArray()[0]);
			// 为null通过sequence获取
			if (StringUtil.isBlank(id)) {
				id = SqlUtil.getSequenceValue(conn, entityMeta.getSequence(), dbType);
				BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], id);
			}
			pkStrategy = PKStrategy.ASSIGN;
		}
		return pkStrategy;
	}

	/**
	 * 组织merge into 语句中select 的字段，进行类型转换
	 * 
	 * @param sql
	 * @param columnName
	 * @param fieldMeta
	 * @param dbType
	 */
	public static void wrapSelectFields(StringBuilder sql, String columnName, FieldMeta fieldMeta, Integer dbType) {
		int jdbcType = fieldMeta.getType();
		if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
				|| jdbcType == java.sql.Types.LONGVARCHAR || jdbcType == java.sql.Types.LONGNVARCHAR) {
			sql.append("?");
		} else if (jdbcType == java.sql.Types.CHAR || jdbcType == java.sql.Types.NCHAR) {
			sql.append("?");
		} else if (jdbcType == java.sql.Types.DATE) {
			sql.append("cast(? as date)");
		} else if (jdbcType == java.sql.Types.NUMERIC) {
			sql.append("cast(? as numeric)");
		} else if (jdbcType == java.sql.Types.DECIMAL) {
			sql.append("cast(? as decimal)");
		} else if (jdbcType == java.sql.Types.BIGINT) {
			sql.append("cast(? as bigint)");
		} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.TINYINT
				|| jdbcType == java.sql.Types.SMALLINT) {
			sql.append("cast(? as integer)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP) {
			sql.append("cast(? as timestamp)");
		} else if (jdbcType == java.sql.Types.DOUBLE) {
			sql.append("cast(? as double precision)");
		} else if (jdbcType == java.sql.Types.FLOAT) {
			sql.append("cast(? as double precision)");
		} else if (jdbcType == java.sql.Types.REAL) {
			sql.append("cast(? as real)");
		} else if (jdbcType == java.sql.Types.TIME) {
			sql.append("cast(? as time)");
		} else if (jdbcType == java.sql.Types.CLOB) {
			sql.append("cast(? as text)");
		} else if (jdbcType == java.sql.Types.BOOLEAN) {
			sql.append("cast(? as boolean)");
		} else if (jdbcType == java.sql.Types.BINARY) {
			sql.append("cast(? as bytea)");
		} else if (jdbcType == java.sql.Types.BLOB) {
			sql.append("cast(? as bytea)");
		} else if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			// update 2026-9-10 oscar排除cast:传统Oscar(com.oscar.Driver)无json类型(json数据以
			// CLOB/VARCHAR列承载),cast(? as json)必败,裸?+setString绑定为正确形态
			// (驱动jar解包实证非og同源内核,详见DialectUtils同日注释)
			if (dbType != null && dbType == DBType.OSCAR) {
				sql.append("?");
			} else {
				sql.append((jdbcType == JdbcTypes.JSONB) ? "cast(? as jsonb)" : "cast(? as json)");
			}
		} else if (jdbcType == JdbcTypes.VECTOR) {
			// update 2026-9-10 oscar排除cast:传统Oscar无vector类型,cast必败,回归裸?+setString
			if (dbType != null && dbType == DBType.OSCAR) {
				sql.append("?");
			} else {
				// gaussdb企业版向量类型名为floatvector,其余为vector
				// update 2026-9-10 vastbase G100 3.0实测向量类型名同为floatvector(无vector别名)
				// cast用于兜底setString等字符串参数场景,PGobject包装参数类型已正确
				sql.append("cast(? as ")
						.append(dbType != null && (dbType == DBType.GAUSSDB || dbType == DBType.VASTBASE)
								? "floatvector"
								: "vector")
						.append(")");
			}
		} else if (jdbcType == JdbcTypes.GEOMETRY) {
			// geometry类型参数通过PGobject包装后类型已正确,cast用于兜底setString等字符串参数场景
			sql.append("cast(? as geometry)");
		} else {
			// 数组、json等特殊类型
			if (StringUtil.isNotBlank(fieldMeta.getNativeType())) {
				sql.append("cast(? as " + fieldMeta.getNativeType() + ")");
			} else {
				sql.append("?");
			}
		}
		sql.append(" as ");
		sql.append(columnName);
	}

	/**
	 * 针对openGauss系新包路径驱动(org.opengauss.util.PGobject)的vector向量参数赋值
	 * 
	 * @param pst
	 * @param paramIndex
	 * @param ogTypeName 数据库端向量类型名(openGauss/MogDB为vector,gaussdb企业版为floatvector)
	 * @param vectorStr  '[1,2,3]'形式的向量字符串
	 * @throws SQLException
	 */
	public static void setVectorValue(PreparedStatement pst, int paramIndex, String ogTypeName, String vectorStr)
			throws SQLException {
		PGobject pgObject = new PGobject();
		pgObject.setType(ogTypeName);
		pgObject.setValue(vectorStr);
		pst.setObject(paramIndex, pgObject);
	}

	/**
	 * 针对openGauss系新包路径驱动(org.opengauss.util.PGobject)的vector向量列回写
	 * 
	 * @param rs
	 * @param columnName
	 * @param ogTypeName 数据库端向量类型名(openGauss/MogDB为vector,gaussdb企业版为floatvector)
	 * @param vectorStr  '[1,2,3]'形式的向量字符串
	 * @throws SQLException
	 */
	public static void updateVector(ResultSet rs, String columnName, String ogTypeName, String vectorStr)
			throws SQLException {
		PGobject pgObject = new PGobject();
		pgObject.setType(ogTypeName);
		pgObject.setValue(vectorStr);
		rs.updateObject(columnName, pgObject);
	}

	/**
	 * 针对openGauss系新包路径驱动(org.opengauss.util.PGobject)的geometry空间参数赋值
	 *
	 * @param pst
	 * @param paramIndex
	 * @param geomStr    WKT形式的geometry字符串
	 * @throws SQLException
	 */
	public static void setGeometryValue(PreparedStatement pst, int paramIndex, String geomStr) throws SQLException {
		PGobject pgObject = new PGobject();
		pgObject.setType("geometry");
		pgObject.setValue(geomStr);
		pst.setObject(paramIndex, pgObject);
	}

	/**
	 * 针对openGauss系新包路径驱动(org.opengauss.util.PGobject)的geometry空间列回写
	 *
	 * @param rs
	 * @param columnName
	 * @param geomStr    WKT形式的geometry字符串
	 * @throws SQLException
	 */
	public static void updateGeometry(ResultSet rs, String columnName, String geomStr) throws SQLException {
		PGobject pgObject = new PGobject();
		pgObject.setType("geometry");
		pgObject.setValue(geomStr);
		rs.updateObject(columnName, pgObject);
	}
}
