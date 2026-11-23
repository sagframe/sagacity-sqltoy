package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.NativeTypeUtils;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供h2数据库相关的特殊逻辑处理封装
 * @author zhongxuchen
 * @version v1.0,Date:2023-06-08
 * @modify Date:2023-06-08,修改说明
 */
public class H2DialectUtils {
	/**
	 * 主键策略是identity或sequence时，主键值允许不由数据库内部自动产生，可人工赋值
	 * 
	 * @param pkStrategy
	 * @return
	 */
	public static boolean allowAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}

	/**
	 * 组织merge into 语句中select 的字段，进行类型转换
	 * 
	 * @param sql
	 * @param columnName
	 * @param fieldMeta
	 */
	public static void wrapSelectFields(StringBuilder sql, String columnName, FieldMeta fieldMeta) {
		int jdbcType = fieldMeta.getType();
		int length = fieldMeta.getLength();
		// update 2026-11-21 特殊族nativeType跨方言翻译前置:quickvo按源库生成(如pg的jsonb列生成
		// String字段+nativeType=jsonb),按jdbcType分支裸?或原样cast在目标方言产生类型错配
		// (实测pg上COALESCE(varchar,jsonb)报错);族可翻译按目标cast,数组族null回落原链
		// (h2进组件链),族不可识别(null)保持原链nativeType精确控制
		String specialCast = (fieldMeta.getSpecialFamily() == null) ? null
				: NativeTypeUtils.specialCastType(DataSourceUtils.DBType.H2, fieldMeta.getSpecialFamily(),
						fieldMeta.getNativeType());
		if (StringUtil.isNotBlank(specialCast)) {
			sql.append("cast(? as " + specialCast + ")");
		} else if (jdbcType == java.sql.Types.VARCHAR || jdbcType == java.sql.Types.NVARCHAR
				|| jdbcType == java.sql.Types.LONGVARCHAR || jdbcType == java.sql.Types.LONGNVARCHAR) {
			if (length > 0) {
				sql.append("cast(? as VARCHAR(" + length + "))");
			} else {
				sql.append("cast(? as VARCHAR)");
			}
		} else if (jdbcType == java.sql.Types.CHAR || jdbcType == java.sql.Types.NCHAR) {
			if (length > 0) {
				sql.append("cast(? as CHAR(" + length + "))");
			} else {
				sql.append("cast(? as CHAR)");
			}
		} else if (jdbcType == java.sql.Types.DATE) {
			sql.append("cast(? as DATE)");
		} else if (jdbcType == java.sql.Types.NUMERIC || jdbcType == java.sql.Types.DECIMAL) {
			// update 2026-9-14 裸cast(? as
			// DECIMAL)默认标度为0,小数值被取整(h2真库实测88.88四舍五入为89);精度/标度已知按列定义cast,未知时大标度兜底
			if (fieldMeta.getPrecision() > 0) {
				sql.append("cast(? as DECIMAL(" + fieldMeta.getPrecision() + "," + fieldMeta.getScale() + "))");
			} else {
				sql.append("cast(? as DECIMAL(31,6))");
			}
		} else if (jdbcType == java.sql.Types.BIGINT) {
			sql.append("cast(? as BIGINT)");
		} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.TINYINT
				|| jdbcType == java.sql.Types.SMALLINT) {
			sql.append("cast(? as INT)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP) {
			sql.append("cast(? as TIMESTAMP)");
		} else if (jdbcType == JdbcTypes.UUID) {
			// update 2026-11-22 uuid列jdbcType分派(族parse期上调可达):pg内核系/h2原生列
			// cast定型;其余方言uuid为varchar(36)承载列裸?即正确(else的nativeType原样cast
			// 在dm/db2等目标非法,须在此拦截)
			sql.append("cast(? as UUID)");
		} else if (jdbcType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
			// update 2026-11-22 2014此前落else以nativeType原样cast(? as timestamptz),
			// h2无timestamptz类型报Unknown data type(真库实证),h2形态为TIMESTAMP WITH TIME ZONE
			sql.append("cast(? as TIMESTAMP WITH TIME ZONE)");
		} else if (jdbcType == java.sql.Types.DOUBLE) {
			sql.append("cast(? as DOUBLE PRECISION)");
		} else if (jdbcType == java.sql.Types.FLOAT) {
			sql.append("cast(? as DOUBLE PRECISION)");
		} else if (jdbcType == java.sql.Types.REAL) {
			sql.append("cast(? as REAL)");
		} else if (jdbcType == java.sql.Types.TIME) {
			sql.append("cast(? as TIME)");
		} else if (jdbcType == java.sql.Types.CLOB) {
			sql.append("cast(? as CLOB)");
		} else if (jdbcType == java.sql.Types.BOOLEAN) {
			sql.append("cast(? as BOOLEAN)");
		} else if (jdbcType == java.sql.Types.BINARY) {
			sql.append("cast(? as BINARY)");
		} else if (jdbcType == java.sql.Types.BLOB) {
			sql.append("cast(? as BLOB)");
		} else if (jdbcType == JdbcTypes.JSON || jdbcType == JdbcTypes.JSONB) {
			sql.append("cast(? as JSON)");
		} else if (jdbcType == JdbcTypes.VECTOR) {
			// update 2026-11-22 h2无vector类型,DDL以VARCHAR承载'[1,2,3]'文本;merge using select子查询
			// 裸?在prepare期无法定型报"Unknown data type"(与geometry同因),以VARCHAR cast定型与DDL承载一致
			sql.append("cast(? as VARCHAR)");
		} else if (jdbcType == JdbcTypes.GEOMETRY) {
			// update 2026-9-8 实测h2的merge using select子查询对geometry列裸?报"Unknown data
			// type"(参数无法定型),以GEOMETRY cast定型
			// (H2的GEOMETRY类型接受WKT文本参数)
			sql.append("cast(? as GEOMETRY)");
		} else {
			// 数组、json等特殊类型
			// update 2026-11-21 到达此处族为null(精确控制原样透传)或数组族(前置翻译null,
			// 如pg源text[]到h2)——族可翻译场景已在前置处理,数组族回落下方组件链
			if (StringUtil.isNotBlank(fieldMeta.getNativeType()) && fieldMeta.getSpecialFamily() == null) {
				sql.append("cast(? as " + fieldMeta.getNativeType() + ")");
			} else {
				// update 2026-11-21 数组列cast定型:与geometry同因,h2的merge using(select ? as 数组列)
				// 裸?在prepare期无法推断数组类型报"Unknown data type"(真库实证cast(? as VARCHAR ARRAY)
				// prepare+执行+写入全通),按字段组件类型映射h2数组元素类型cast;未识别组件(如
				// 日期时间元素,绑定形态未实证)维持裸?现状,可经@Column(nativeType=..)显式指定
				String h2ElementType = h2ArrayElementTypeName(fieldMeta.getArrayComponentType());
				if (h2ElementType != null) {
					sql.append("cast(? as " + h2ElementType + " ARRAY)");
				} else {
					sql.append("?");
				}
			}
		}
		sql.append(" as ");
		sql.append(columnName);
	}

	/**
	 * update 2026-11-21 数组字段组件类型对应的h2数组元素类型名,未识别(含null)返回null (维持裸?现状交nativeType口子)
	 *
	 * @param componentType 数组/集合字段的元素组件类型全限定名(含原始类型简名如int)
	 * @return h2元素类型名(VARCHAR/INT/BIGINT/NUMERIC/DOUBLE/REAL/BOOLEAN),未识别null
	 */
	public static String h2ArrayElementTypeName(String componentType) {
		if (componentType == null) {
			return null;
		}
		switch (componentType) {
		case "java.lang.String":
		case "char":
			// update 2026-11-22 java.lang.Character 移出元素映射(与pg工具同因:Character[]无驱动绑定
			// 能力、List<Character>以Object[]到达,自动数组列建得出但绑不上/读不回)
			return "VARCHAR";
		case "java.lang.Integer":
		case "int":
		case "java.lang.Short":
		case "short":
		case "java.lang.Byte":
		case "byte":
			return "INT";
		case "java.lang.Long":
		case "long":
		case "java.math.BigInteger":
			return "BIGINT";
		case "java.math.BigDecimal":
			return "NUMERIC";
		case "java.lang.Double":
		case "double":
			return "DOUBLE";
		case "java.lang.Float":
		case "float":
			return "REAL";
		case "java.lang.Boolean":
		case "boolean":
			return "BOOLEAN";
		default:
			return null;
		}
	}
}