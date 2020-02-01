/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.util.HashMap;

import org.sagacity.sqltoy.config.model.EntityMeta;

/**
 * @project sqltoy-orm
 * @description 提供sqlite数据库统一的数据库操作功能实现，便于sqlite今后多版本的共用
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqliteDialectUtils.java,Revision:v1.0,Date:2015年3月5日
 */
public class SqliteDialectUtils {
	/**
	 * @todo 处理加工对象基于mysql的saveOrUpdateSql
	 * @param dbType
	 * @param entityMeta
	 * @param forceUpdateFields
	 * @param tableName
	 * @return
	 */
	public static String getSaveOrUpdateSql(Integer dbType, EntityMeta entityMeta, String[] forceUpdateFields,
			String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName);
		// 无主键表全部采用insert机制
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
		sql.append(") values (").append(values).append(") ");
		// 非全部是主键
		if (!allIds) {
			sql.append("ON DUPLICATE KEY UPDATE ");
			// 需要被强制修改的字段
			HashMap<String, String> forceUpdateColumnMap = new HashMap<String, String>();
			if (forceUpdateFields != null) {
				for (String forceUpdatefield : forceUpdateFields) {
					forceUpdateColumnMap.put(entityMeta.getColumnName(forceUpdatefield), "1");
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
				if (forceUpdateColumnMap.containsKey(columnName)) {
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

	/**
	 * @todo 构造保存并忽视已经存在记录的插入sql语句
	 * @param dbType
	 * @param entityMeta
	 * @param tableName
	 * @return
	 */
	public static String getSaveIgnoreExistSql(Integer dbType, EntityMeta entityMeta, String tableName) {
		// 无主键表全部采用insert机制
		String realTable = entityMeta.getSchemaTable(tableName);
		if (entityMeta.getIdArray() == null) {
			return DialectUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(), "ifnull", null, false,
					realTable);
		}
		StringBuilder sql = new StringBuilder("insert or ignore into ");
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
		sql.append(") values (").append(values).append(") ");
		return sql.toString();
	}
}
