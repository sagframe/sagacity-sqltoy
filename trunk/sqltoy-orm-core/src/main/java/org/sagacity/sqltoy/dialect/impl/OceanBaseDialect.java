package org.sagacity.sqltoy.dialect.impl;

import java.sql.Connection;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.TableMeta;

/**
 * @project sagacity-sqltoy
 * @description 提供适配OceanBaseDialect数据库方言的实现(类似于oracle12+)
 * @author zhongxuchen
 * @version v1.0,Date:2020-06-09
 * @modify Date:2020-06-09 初始创建
 * @update Date:2026-9-13 归并为OracleDialect子类:分页/top/随机/load/save/saveAll/
 *         update/delete/executeStore等与Oracle完全一致直接继承(NVL/.nextval/dual
 *         常量同值);差异点为saveOrUpdateAll——oracle租户走merge,mysql租户场景
 *         保留先update后save形态,另元数据走jdbc标准接口
 */
public class OceanBaseDialect extends OracleDialect {

	public static final String NEXT_VAL = ".nextval";

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
	 * @return 批量保存或修改记录(先update后save形态,兼容ob的mysql租户模式)
	 */
	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, final String[] forceUpdateFields, Connection conn,
			DBProfile profile, final Boolean autoCommit, final String tableName) throws Exception {
		Long updateCnt = DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields,
				reflectPropsHandler, conn, profile, autoCommit, tableName, true);
		// 如果修改的记录数量跟总记录数量一致,表示全部是修改
		if (updateCnt >= entities.size()) {
			SqlExecuteStat.debug("update record",
					"update rows:" + updateCnt + " equals the size of entities collection, skip the insert operation!");
			return updateCnt;
		}
		Long saveCnt = saveAllIgnoreExist(sqlToyContext, entities, batchSize, reflectPropsHandler, conn, profile,
				autoCommit, tableName);
		SqlExecuteStat.debug("insert record", "insert rows:" + saveCnt + "!");
		return updateCnt + saveCnt;
	}

	/**
	 * @param catalog
	 * @param schema
	 * @param tableName
	 * @param conn
	 * @param profile
	 * @return 获得数据库的表信息(ob走jdbc标准接口,与oracle的schema过滤形态不同)
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
	 * @return 获得表的字段信息(ob走jdbc标准接口)
	 */
	@Override
	public List<ColumnMeta> getTableColumns(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		return DefaultDialectUtils.getTableColumns(catalog, schema, tableName, conn, profile);
	}
}
