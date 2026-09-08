package org.sagacity.sqltoy.dialect.impl;

import java.sql.Connection;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.MySqlDialectUtils;

/**
 * @project sagacity-sqltoy
 * @description 提供适配Doris方言的数据库适配(跟mysql一致)
 * @author zhongxuchen
 * @version v1.0,Date:2025-06-08
 * @modify Date:2025-06-08 初始创建
 * @modify Date:2026-9-6 实测StarRocks 4.1.4/Doris均不支持insert ignore语法
 *         (报"Unexpected input 'ignore'"),其PK/UNIQUE模型表的insert天然为主键upsert
 *         (存在即整行覆盖),saveAllIgnoreExist覆写为普通insert,同时救活继承此路径的
 *         saveOrUpdate/saveOrUpdateAll
 */
public class DorisDialect extends MySqlDialect {

	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, final Integer dbType, final String dialect,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = MySqlDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy(), dbType);
		// 无insert ignore前缀:PK/UNIQUE模型insert即主键upsert,天然幂等
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), dbType, entityMeta,
				entityMeta.getIdStrategy(), NVL_FUNCTION, NEXT_VAL + entityMeta.getSequence(), isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, dbType, autoCommit);
	}
}
