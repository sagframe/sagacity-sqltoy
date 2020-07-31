/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 将原本DialectUtils中的部分功能抽离出来,从而避免DialectUtils跟一些类之间的互相调用
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020年7月30日
 * @modify 2020年7月30日,修改说明
 */
public class DialectExtUtils {
	/**
	 * 判断日期格式
	 */
	public static final Pattern DATE_PATTERN = Pattern.compile("(\\:|\\-|\\.|\\/|\\s+)?\\d+");

	/**
	 * @todo 产生对象对应的insert sql语句
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param isNullFunction
	 * @param sequence
	 * @param isAssignPK
	 * @param tableName
	 * @return
	 */
	public static String generateInsertSql(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String isNullFunction, String sequence, boolean isAssignPK, String tableName) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append("insert into ");
		sql.append(entityMeta.getSchemaTable(tableName));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		boolean isStart = true;
		boolean isSupportNULL = StringUtil.isBlank(isNullFunction) ? false : true;
		String columnName;
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					// 目前只有mysql支持
					if (isAssignPK) {
						if (!isStart) {
							sql.append(",");
							values.append(",");
						}
						sql.append(columnName);
						values.append("?");
						isStart = false;
					}
				} // sequence 策略，oracle12c之后的identity机制统一转化为sequence模式
				else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					if (isAssignPK && isSupportNULL) {
						values.append(isNullFunction);
						values.append("(?,").append(sequence).append(")");
					} else {
						values.append(sequence);
					}
					isStart = false;
				} else {
					if (!isStart) {
						sql.append(",");
						values.append(",");
					}
					sql.append(columnName);
					values.append("?");
					isStart = false;
				}
			} else {
				if (!isStart) {
					sql.append(",");
					values.append(",");
				}
				sql.append(columnName);
				if (isSupportNULL && StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
					values.append(isNullFunction);
					values.append("(?,");
					processDefaultValue(values, dbType, fieldMeta.getType(), fieldMeta.getDefaultValue());
					values.append(")");
				} else {
					values.append("?");
				}
				isStart = false;
			}
		}
		// OVERRIDING SYSTEM VALUE
		sql.append(") ");
		/*
		 * if ((dbType == DBType.POSTGRESQL || dbType == DBType.GAUSSDB) && isAssignPK)
		 * { sql.append(" OVERRIDING SYSTEM VALUE "); }
		 */
		sql.append(" values (");
		sql.append(values);
		sql.append(")");
		return sql.toString();
	}

	/**
	 * @todo 统一对表字段默认值进行处理
	 * @param sql
	 * @param dbType
	 * @param fieldType
	 * @param defaultValue
	 */
	public static void processDefaultValue(StringBuilder sql, int dbType, int fieldType, String defaultValue) {
		if (fieldType == java.sql.Types.CHAR || fieldType == java.sql.Types.CLOB || fieldType == java.sql.Types.VARCHAR
				|| fieldType == java.sql.Types.NCHAR || fieldType == java.sql.Types.NVARCHAR
				|| fieldType == java.sql.Types.LONGVARCHAR || fieldType == java.sql.Types.LONGNVARCHAR
				|| fieldType == java.sql.Types.NCLOB) {
			if (!defaultValue.startsWith("'")) {
				sql.append("'");
			}
			sql.append(defaultValue);
			if (!defaultValue.endsWith("'")) {
				sql.append("'");
			}
		} else {
			String tmpValue = SqlToyConstants.getDefaultValue(dbType, defaultValue);
			if (tmpValue.startsWith("'") && tmpValue.endsWith("'")) {
				sql.append(tmpValue);
			}
			// 时间格式,避免默认日期没有单引号问题
			else if (fieldType == java.sql.Types.TIME || fieldType == java.sql.Types.DATE
					|| fieldType == java.sql.Types.TIME_WITH_TIMEZONE || fieldType == java.sql.Types.TIMESTAMP
					|| fieldType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE) {
				if (StringUtil.matches(tmpValue, DATE_PATTERN)) {
					sql.append("'").append(tmpValue).append("'");
				} else {
					sql.append(tmpValue);
				}
			} else {
				sql.append(tmpValue);
			}
		}
	}
}
