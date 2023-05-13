/**
 * 
 */
package org.sagacity.sqltoy.dialect.utils;

import java.util.HashSet;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * @project sqltoy-orm
 * @description 提供sqlite数据库统一的数据库操作功能实现，便于sqlite今后多版本的共用
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月5日
 */
public class SqliteDialectUtils {
	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// 目前不支持sequence模式
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return false;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}

	/**
	 * @todo 利用sqlite3 的on conflict(id) DO UPDATE SET 语法,但只能用于关联子表更新(未实际使用)
	 * @param dbType
	 * @param entityMeta
	 * @param forceUpdateFields
	 * @param tableName
	 * @return
	 */
	@Deprecated
	public static String getSaveOrUpdateSql(IUnifyFieldsHandler unifyFieldsHandler, Integer dbType,
			EntityMeta entityMeta, String[] forceUpdateFields, String tableName) {
		String realTable = entityMeta.getSchemaTable(tableName, dbType);
		// 无主键表全部采用insert机制
		if (entityMeta.getIdArray() == null) {
			return DialectExtUtils.generateInsertSql(unifyFieldsHandler, dbType, entityMeta, entityMeta.getIdStrategy(),
					"ifnull", null, false, realTable);
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
		sql.append(") values (").append(values).append(") ");
		// 非全部是主键
		if (!allIds) {
			String columnName;
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
}
