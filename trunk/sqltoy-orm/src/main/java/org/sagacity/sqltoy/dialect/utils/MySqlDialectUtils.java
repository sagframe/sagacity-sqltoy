/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.util.HashSet;

import org.sagacity.sqltoy.config.model.EntityMeta;

/**
 * @project sqltoy-orm
 * @description mysql数据库各类操作的统一函数实现（便于今后mysql版本以及变种数据库统一使用，减少主体代码重复量）
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:MySqlDialectUtils.java,Revision:v1.0,Date:2015年2月13日
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
	public static String getSaveOrUpdateSql(Integer dbType, EntityMeta entityMeta, String[] forceUpdateFields,
			String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName);
		if (entityMeta.getIdArray() == null) {
			return DialectUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), "ifnull", null, false,
					realTable);
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
		for (int i = 0, n = entityMeta.getFieldsArray().length; i < n; i++) {
			if (i > 0) {
				sql.append(",");
				values.append(",");
			}
			sql.append(entityMeta.getColumnName(entityMeta.getFieldsArray()[i]));
			values.append("?");
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
}
