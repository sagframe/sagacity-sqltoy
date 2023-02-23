/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.util.HashSet;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * @project sqltoy-orm
 * @description mysql数据库各类操作的统一函数实现（便于今后mysql版本以及变种数据库统一使用，减少主体代码重复量）
 * @author zhongxuchen
 * @version v1.0,Date:2015年2月13日
 */
public class MySqlDialectUtils {

	/**
	 * @todo 产生mysql数据库的saveOrUpdate操作sql语句
	 * @param dbType
	 * @param entityMeta
	 * @param forceUpdateFields
	 * @param tableName
	 * @return
	 */
	@Deprecated
	public static String getSaveOrUpdateSql(Integer dbType, EntityMeta entityMeta, String[] forceUpdateFields,
			String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		if (entityMeta.getIdArray() == null) {
			return DialectExtUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), "ifnull", null,
					false, realTable);
		}

		StringBuilder sql;
		// 是否全部是ID
		boolean allIds = (entityMeta.getRejectIdFieldArray() == null);
		// 全部是主键采用replace into 策略进行保存或修改,不考虑只有一个字段且是主键的表情况
		if (allIds) {
			sql = new StringBuilder("replace into ");
		} else {
			sql = new StringBuilder("insert into ");
		}
		StringBuilder values = new StringBuilder();
		sql.append(realTable);
		sql.append(" (");
		FieldMeta fieldMeta;
		String fieldName;
		for (int i = 0, n = entityMeta.getFieldsArray().length; i < n; i++) {
			if (i > 0) {
				sql.append(",");
				values.append(",");
			}
			fieldName = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(fieldName);
			// sql中的关键字处理
			sql.append(ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType));
			// 默认值处理
			if (null != fieldMeta.getDefaultValue()) {
				values.append("ifnull(?,");
				DialectExtUtils.processDefaultValue(values, dbType, fieldMeta, fieldMeta.getDefaultValue());
				values.append(")");
			} else {
				values.append("?");
			}
		}
		sql.append(") values (");
		sql.append(values);
		sql.append(") ");
		// 非全部是主键
		if (!allIds) {
			// 当主键存在则进行修改操作
			sql.append(" ON DUPLICATE KEY UPDATE ");
			// 需要被强制修改的字段
			HashSet<String> fupc = new HashSet<String>();
			if (forceUpdateFields != null) {
				for (String field : forceUpdateFields) {
					fupc.add(entityMeta.getColumnName(field));
				}
			}
			String columnName;
			for (int i = 0, n = entityMeta.getRejectIdFieldArray().length; i < n; i++) {
				columnName = entityMeta.getColumnName(entityMeta.getRejectIdFieldArray()[i]);
				if (i > 0) {
					sql.append(",");
				}
				sql.append(columnName).append("=");
				// 强制修改
				if (fupc.contains(columnName)) {
					sql.append("values(").append(columnName).append(")");
				} else {
					sql.append("ifnull(values(");
					sql.append(columnName).append("),");
					sql.append(columnName).append(")");
				}
			}
		}
		return sql.toString();
	}

	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// 目前不支持sequence模式
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return false;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return true;
		}
		return true;
	}
}
