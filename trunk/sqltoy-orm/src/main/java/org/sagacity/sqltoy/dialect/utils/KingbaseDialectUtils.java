package org.sagacity.sqltoy.dialect.utils;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 北大金仓数据库方言支持
 * @author zhongxuchen
 * @version v1.0, Date:2020-11-6
 * @modify 2020-11-6,修改说明
 */
public class KingbaseDialectUtils {
	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "isnull";

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

	/**
	 * @todo 组织kingbase saveIgnoreExist的插入语句
	 * @param dbType
	 * @param entityMeta
	 * @param pkStrategy
	 * @param sequence
	 * @param tableName
	 * @return
	 */
	public static String getSaveIgnoreExist(Integer dbType, EntityMeta entityMeta, PKStrategy pkStrategy,
			String sequence, String tableName) {
		int columnSize = entityMeta.getFieldsArray().length;
		StringBuilder sql = new StringBuilder(columnSize * 20 + 30);
		StringBuilder values = new StringBuilder(columnSize * 2 - 1);
		sql.append("insert into ");
		sql.append(entityMeta.getSchemaTable(tableName));
		sql.append(" (");
		FieldMeta fieldMeta;
		String field;
		String columnName;
		boolean isAssignPK = isAssignPKValue(pkStrategy);
		for (int i = 0; i < columnSize; i++) {
			field = entityMeta.getFieldsArray()[i];
			fieldMeta = entityMeta.getFieldMeta(field);
			columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
			if (i > 0) {
				sql.append(",");
				values.append(",");
			}
			sql.append(columnName);
			if (fieldMeta.isPK()) {
				// identity主键策略，且支持主键手工赋值
				if (pkStrategy.equals(PKStrategy.IDENTITY)) {
					if (isAssignPK) {
						values.append("?");
					}
				} // sequence 策略，oracle12c之后的identity机制统一转化为sequence模式
				else if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
					values.append("isnull(?,").append(sequence).append(")");
				} else {
					values.append("?");
				}
			} else {
				if (StringUtil.isNotBlank(fieldMeta.getDefaultValue())) {
					values.append("isnull(?,");
					DialectExtUtils.processDefaultValue(values, dbType, fieldMeta.getType(),
							fieldMeta.getDefaultValue());
					values.append(")");
				} else {
					values.append("?");
				}
			}
		}
		sql.append(") values ( ");
		sql.append(values);
		sql.append(")");

		// 非全部是主键
		if (entityMeta.getRejectIdFieldArray() != null) {
			sql.append(" ON CONFLICT (");
			for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
				if (i > 0) {
					sql.append(",");
				}
				columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
				sql.append(ReservedWordsUtil.convertWord(columnName, dbType));
			}
			sql.append(" ) DO NOTHING ");
		}

		return sql.toString();
	}
}
