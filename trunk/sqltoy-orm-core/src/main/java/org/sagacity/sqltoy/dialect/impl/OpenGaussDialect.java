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
import org.sagacity.sqltoy.dialect.utils.OpenGaussDialectUtils;
import org.sagacity.sqltoy.dialect.utils.PostgreSqlDialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.SavePKStrategy;
import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sagacity-sqltoy
 * @description 华为OpenGauss数据库方言，整体复用PostgreSQL方言逻辑，差异部分单独处理
 * @author zhongxuchen
 * @version v1.0,Date:2024-10-29
 * @update Date:2026-9-13 归并为PostgreSqlDialect子类:分页/top/随机/isUnique/load/
 *         delete/findBySql/executeStore等与PG一致直接继承;差异点为主键sequence形态
 *         (".nextval"直拼、openGauss专用主键策略探测)及元数据表名小写规整,
 *         save/saveAll/saveAllIgnoreExist/saveOrUpdateAll/update/updateAll/
 *         getTables/getTableColumns保留独立覆写
 */
@SuppressWarnings({ "rawtypes" })
public class OpenGaussDialect extends PostgreSqlDialect {

	public static final String NEXT_VAL = ".nextval";

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
		PKStrategy pkStrategy = OpenGaussDialectUtils.getSavePkStrategy(entityMeta, entity, profile, conn);
		String sequence = entityMeta.getSequence() + NEXT_VAL;
		boolean isAssignPK = OpenGaussDialectUtils.allowAssignPKValue(pkStrategy);
		String insertSql = DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
				pkStrategy, sequence, isAssignPK, tableName);
		return DialectUtils.save(sqlToyContext, entityMeta, pkStrategy, isAssignPK, insertSql, entity,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateField) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXT_VAL;
						return DialectExtUtils.generateInsertSql(sqlToyContext.getUnifyFieldsHandler(), profile,
								entityMeta, pkStrategy, sequence, OpenGaussDialectUtils.allowAssignPKValue(pkStrategy),
								null);
					}
				}, new GenerateSavePKStrategy() {
					@Override
					public SavePKStrategy generate(EntityMeta entityMeta) {
						return new SavePKStrategy(entityMeta.getIdStrategy(),
								OpenGaussDialectUtils.allowAssignPKValue(entityMeta.getIdStrategy()));
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
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		String sequence = entityMeta.getSequence() + NEXT_VAL;
		boolean isAssignPK = OpenGaussDialectUtils.allowAssignPKValue(pkStrategy);
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
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, final Boolean autoCommit,
			final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXT_VAL;
						boolean isAssignPK = OpenGaussDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.mergeIgnore(sqlToyContext.getUnifyFieldsHandler(), profile, entityMeta,
								pkStrategy, null, sequence, isAssignPK, tableName);
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
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, DBProfile profile,
			final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveOrUpdateAll(sqlToyContext, entities, batchSize, entityMeta, forceUpdateFields,
				new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXT_VAL;
						boolean isAssignPK = OpenGaussDialectUtils.allowAssignPKValue(pkStrategy);
						return DialectUtils.getSaveOrUpdateSql(sqlToyContext, sqlToyContext.getUnifyFieldsHandler(),
								profile, entityMeta, pkStrategy, forceUpdateFields, null, sequence, isAssignPK,
								tableName);
					}
				}, reflectPropsHandler, conn, profile, autoCommit);
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
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClasses, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			DBProfile profile, final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, forceUpdateFields, cascade,
				(cascade == false) ? null : new GenerateSqlHandler() {
					@Override
					public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = entityMeta.getSequence() + NEXT_VAL;
						boolean isAssignPK = OpenGaussDialectUtils.allowAssignPKValue(pkStrategy);
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
			final String[] uniqueFields, String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler,
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
	 * @return 获得数据库的表信息(openGauss走pg的schema过滤形态)
	 */
	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		// 这里tableName不是具体的名字，而是正则表达式,要变小写则(?i)
		return PostgreSqlDialectUtils.getTables(catalog, schema, tableName, conn, profile);
	}

	/**
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param profile
	 * @return 获得表的字段信息(openGauss表名统一小写)
	 */
	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		// 表名转小写
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, profile);
	}
}
