/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.util.HashSet;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * @project sqltoy-orm
 * @description 提供sqlite数据库统一的数据库操作功能实现，便于sqlite今后多版本的共用
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqliteDialectUtils.java,Revision:v1.0,Date:2015年3月5日
 */
public class SqliteDialectUtils {
	/**
	 * @todo 利用sqlite3 的on conflict(id) DO UPDATE SET 语法,但只能用于关联子表更新
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
		String columnName;
		sql.append(realTable);
		sql.append(" (");
		for (int i = 0, n = entityMeta.getFieldsArray().length; i < n; i++) {
			if (i > 0) {
				sql.append(",");
				values.append(",");
			}
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			values.append("?");
		}
		sql.append(") values (").append(values).append(") ");
		// 非全部是主键
		if (!allIds) {
			sql.append(" ON CONFLICT (");
			for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
				if (i > 0) {
					sql.append(",");
				}
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
				sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			}
			sql.append(" ) DO UPDATE SET ");
			// 需要被强制修改的字段
			HashSet<String> fupc = new HashSet<String>();
			if (forceUpdateFields != null) {
				for (String field : forceUpdateFields) {
					fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
				}
			}
			for (int i = 0, n = entityMeta.getRejectIdFieldArray().length; i < n; i++) {
				columnName = entityMeta.getColumnName(entityMeta.getRejectIdFieldArray()[i]);
				columnName = ReservedWordsUtil.convertWord(columnName, dbType);
				if (i > 0) {
					sql.append(",");
				}
				sql.append(columnName).append("=");
				// 强制修改
				if (fupc.contains(columnName)) {
					sql.append("excluded.").append(columnName);
				} else {
					sql.append("ifnull(excluded.");
					sql.append(columnName).append(",");
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
		String columnName;
		sql.append(realTable);
		sql.append(" (");
		for (int i = 0, n = entityMeta.getFieldsArray().length; i < n; i++) {
			if (i > 0) {
				sql.append(",");
				values.append(",");
			}
			columnName = entityMeta.getColumnName(entityMeta.getFieldsArray()[i]);
			sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			values.append("?");
		}
		sql.append(") values (").append(values).append(") ");
		return sql.toString();
	}
}
