package org.sagacity.sqltoy.dialect.impl;

import java.sql.Connection;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.model.DBProfile;

/**
 * @project sagacity-sqltoy
 * @description 提供适配Tidb数据库方言的实现(类似于mysql,以mysql8为参照版本进行实现)
 * @author zhongxuchen
 * @version v1.0,Date:2020-06-09
 * @modify Date:2020-06-09 初始创建
 * @update Date:2026-9-13 归并为MySqlDialect子类:Tidb与mysql8语法谱系一致(分页/top/随机/
 *         锁语句/load及updateFetch锁后缀、saveOrUpdate链路),除saveOrUpdateAll外
 *         全部继承父类实现;saveOrUpdateAll保留独立覆写,因mysql版含OB按mysql方言
 *         配置时的计数豁免(2026-9-14已由isOceanBaseAsMysql统一为profile.isOceanBase()),
 *         对tidb方言配置场景语义不同
 */
public class TidbDialect extends MySqlDialect {

	public static final String NEXT_VAL = "NEXTVAL FOR ";

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
	 * @return 批量保存或修改记录(先update后save模式,不依赖数据库特定语法)
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
}
