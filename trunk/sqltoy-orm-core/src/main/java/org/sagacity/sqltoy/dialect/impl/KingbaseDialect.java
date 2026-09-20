package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.GenerateSavePKStrategy;
import org.sagacity.sqltoy.callback.GenerateSqlHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.KingbaseDialectUtils;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.SavePKStrategy;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 北大金仓数据库方言支持
 * @author zhongxuchen
 * @version v1.0,Date:2020-11-06
 * @modify Date:2020-11-06 修改说明
 * @update Date:2026-9-13 归并为PostgreSqlDialect子类:分页/top/随机/isUnique/load/
 *         delete/executeStore等与PG完全一致,直接继承;差异点仅为主键sequence形态
 *         (NEXTVAL大写、identity是sequence的变化实现取defaultValue)及元数据来源,
 *         save/saveAll/saveAllIgnoreExist/saveOrUpdateAll/updateAll/update/
 *         getTables/getLockSql保留独立覆写
 */
@SuppressWarnings({ "rawtypes" })
public class KingbaseDialect extends PostgreSqlDialect {

	/**
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param forceUpdateFields
	 * @param conn
	 * @param profile
	 * @param autoCommit
	 * @param tableName
	 * @return 批量保存或修改记录
	 */
	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "NEXTVAL('" + entityMeta.getSequence() + "')";
						// kingbase identity 是sequence的一种变化实现
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							String defaultValue = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
							if (StringUtil.isNotBlank(defaultValue)) {
								pkStrategy = PKStrategy.SEQUENCE;
								sequence = "NEXTVAL('" + defaultValue + "')";
							}
						}
						boolean isAssignPK = KingbaseDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, pkStrategy, forceUpdateFields, null, sequence, isAssignPK,
								tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/**
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param conn
	 * @param profile
	 * @param autoCommit
	 * @param tableName
	 * @return 批量保存,主键冲突的则忽视
	 */
	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "NEXTVAL('" + entityMeta.getSequence() + "')";
						// kingbase identity 是sequence的一种变化实现
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							String defaultValue = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
							if (StringUtil.isNotBlank(defaultValue)) {
								pkStrategy = PKStrategy.SEQUENCE;
								sequence = "NEXTVAL('" + defaultValue + "')";
							}
						}
						boolean isAssignPK = KingbaseDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
								pkStrategy, null, sequence, isAssignPK, tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param profile
	 * @param tableName
	 * @return 保存单个对象记录
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// save行为根据主键是否赋值情况调整最终的主键策略
		PKStrategy pkStrategy = DialectUtils.getSavePKStrategy(entityMeta, entity, profile);
		boolean isAssignPK = KingbaseDialectUtils.allowAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, "NEXTVAL('" + entityMeta.getSequence() + "')", isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile,
								entityMeta, entityMeta.getIdStrategy(), "NEXTVAL('" + entityMeta.getSequence() + "')",
								KingbaseDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								KingbaseDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()));
					}
				}, conn, profile);
	}

	/**
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param reflectPropsHandler
	 * @param conn
	 * @param profile
	 * @param autoCommit
	 * @param tableName
	 * @return 批量保存对象
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		boolean isAssignPK = KingbaseDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy());
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				entityMeta.getIdStrategy(), "NEXTVAL('" + entityMeta.getSequence() + "')", isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, entityMeta.getIdStrategy(), isAssignPK, insertSql,
				entities, batchSize, reflectPropsHandler, conn, profile, autoCommit);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateFields
	 * @param cascade
	 * @param forceCascadeClasses
	 * @param subTableForceUpdateProps
	 * @param conn
	 * @param profile
	 * @param tableName
	 * @return 修改单个对象
	 */
	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClasses,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "NEXTVAL('" + entityMeta.getSequence() + "')";
						// kingbase identity 是sequence的一种变化实现
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							String defaultValue = entityMeta.getFieldMeta(entityMeta.getIdArray()[0]).getDefaultValue();
							if (StringUtil.isNotBlank(defaultValue)) {
								pkStrategy = PKStrategy.SEQUENCE;
								sequence = "NEXTVAL('" + defaultValue + "')";
							}
						}
						boolean isAssignPK = KingbaseDialectUtils.allowAssignPKValue(pkStrategy);
						// update 级联操作过程中会自动判断数据库类型
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, pkStrategy, forceUpdateFields, null, sequence, isAssignPK, null);
					}
				}, forceCascadeClasses, subTableForceUpdateProps, conn, profile, tableName);
	}

	/**
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param uniqueFields
	 * @param forceUpdateFields
	 * @param reflectPropsHandler
	 * @param conn
	 * @param profile
	 * @param autoCommit
	 * @param tableName
	 * @return 批量修改对象(保留覆写:引用本类NVL_FUNCTION="NVL",与父类COALESCE不同)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			final String[] uniqueFields, final String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler,
			Connection conn, DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropsHandler, conn,
				profile, autoCommit, tableName, false);
	}

	/**
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param profile
	 * @return 获得数据库的表信息(kingbase走jdbc标准接口,与pg的schema过滤形态不同)
	 */
	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, profile);
	}

	/**
	 * update 2026-9-13 覆写父类(protected)锁语句:kingbase的UPGRADE模式追加 wait N,
	 * 经虚分派使继承自父类的findBySql/load/loadAll/updateFetch命中本实现
	 *
	 * @param sql
	 * @param dbType
	 * @param lockMode
	 * @param lockWaitTimeout
	 * @return
	 */
	@Override
	protected String getLockSql(String sql, Integer dbType, LockMode lockMode, int lockWaitTimeout) {
		return DefaultDialectUtils.getLockSql(sql, dbType, lockMode, lockWaitTimeout, true, " for update skip locked",
				true);
	}
}
