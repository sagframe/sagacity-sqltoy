package org.sagacity.sqltoy.dialect.utils;

import java.io.Serializable;
import java.sql.Connection;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供gaussdb数据库相关的特殊逻辑处理封装
 * @author zhongxuchen
 * @version v1.0, Date:2023年6月8日
 * @modify 2023年6月8日,修改说明
 */
public class GaussDialectUtils {
	/**
	 * @TODO 定义当使用sequence或identity时,是否允许自定义值(即不通过sequence或identity产生，而是由外部直接赋值)
	 * @param pkStrategy
	 * @return
	 */
	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		// sequence
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		// postgresql10+ 支持identity
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return true;
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
		// gaussdb\mogdb\vastbase 主键策略是sequence模式需要先获取主键值
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
}
