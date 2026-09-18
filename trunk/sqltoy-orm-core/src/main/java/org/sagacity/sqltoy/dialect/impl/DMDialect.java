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
import org.sagacity.sqltoy.dialect.utils.DMDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.SavePKStrategy;
import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sagacity-sqltoy
 * @description 达梦数据库支持(以oracle为蓝本)
 * @author zhongxuchen
 * @version v1.0,Date:2020-06-05
 * @update Date:2026-9-13 归并为OracleDialect子类:分页/top/随机/load/delete/
 *         updateSaveFetch/executeStore等与Oracle一致直接继承(锁语句同为wait N形态);
 *         差异点为唯一性校验用limit、主键sequence按达梦规则探测(DMDialectUtils专属
 *         allowAssignPKValue)、元数据走jdbc标准接口,isUnique/save/saveAll/
 *         saveAllIgnoreExist/saveOrUpdateAll/update/getTables/getTableColumns
 *         保留独立覆写
 */
@SuppressWarnings({ "rawtypes" })
public class DMDialect extends OracleDialect {

	public static final String NEXT_VAL = ".nextval";

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param paramsNamed
	 * @param conn
	 * @param profile
	 * @param tableName
	 * @param queryTimeout
	 * @return 判断唯一性(dm取数语法与oracle不同,唯一性取数用limit形态)
	 */
	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			DBProfile profile, String tableName, final Integer queryTimeout) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, profile, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, profile, table);
					return queryStr + " limit " + topSize;
				}, queryTimeout);
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
		boolean isAssignPK = DMDialectUtils.allowAssignPKValue(pkStrategy);
		String sequence = entityMeta.getSequence().concat(NEXT_VAL);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, sequence, isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence().concat(NEXT_VAL);
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile,
								entityMeta, pkStrategy, sequence, DMDialectUtils.allowAssignPKValue(pkStrategy), null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								DMDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()));
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
		// oracle12c 开始支持identity机制
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = entityMeta.getSequence().concat(NEXT_VAL);
		boolean isAssignPK = DMDialectUtils.allowAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, sequence, isAssignPK, tableName);
		return DialectUtils.saveAll(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entities, batchSize,
				reflectPropsHandler, conn, profile, autoCommit);
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
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, Boolean autoCommit,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXT_VAL;
						boolean isAssignPK = DMDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
								pkStrategy, "dual", sequence, isAssignPK, tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

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
						String sequence = entityMeta.getSequence() + NEXT_VAL;
						boolean isAssignPK = DMDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, pkStrategy, forceUpdateFields, "dual", sequence, isAssignPK,
								tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
	}

	/**
	 * @param sqlToyContext
	 * @param entity
	 * @param forceUpdateFields
	 * @param cascade
	 * @param forceCascadeClass
	 * @param subTableForceUpdateProps
	 * @param conn
	 * @param profile
	 * @param tableName
	 * @return 修改单个对象
	 */
	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			final boolean cascade, final Class[] forceCascadeClass,
			final HashMap<Class, String[]> subTableForceUpdateProps, Connection conn, DBProfile profile,
			final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence().concat(NEXT_VAL);
						boolean isAssignPK = DMDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, pkStrategy, forceUpdateFields, "dual", sequence, isAssignPK, null);
					}
				}, forceCascadeClass, subTableForceUpdateProps, conn, profile, tableName);
	}

	/**
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param profile
	 * @return 获得数据库的表信息(dm走jdbc标准接口,与oracle的schema过滤形态不同)
	 */
	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, profile);
	}

	/**
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param profile
	 * @return 获得表的字段信息(dm走jdbc标准接口)
	 */
	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, profile);
	}
}
