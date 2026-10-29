package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description TDengine IoT 物联网时序数据库方言。
 *
 *              <p>
 *              支持范围(基于 TDengine 3.3.6.x + taos-jdbcdriver 3.3.0 实测验证):
 *              <ul>
 *              <li><b>查询</b>:findBySql/findPageBySql/findTopBySql/getRandomResult/getCountBySql/load/loadAll
 *              均委托 DefaultDialect 走通用路径,实测可用——分页 {@code limit ? offset ?}、
 *              {@code count(1)}、随机查询 {@code order by rand()} TDengine 3.x 均支持。</li>
 *              <li><b>对象插入</b>:save/saveAll 生成标准单表列清单 insert,写已存在子表/普通表可用。
 *              时序库典型的“自动建子表 + 标签”写入({@code insert into :tb using 超表 tags(...) values(...)})
 *              与多表批量,经 sql xml 命名参数 + executeSql/batchUpdate 透传即可,实测全链路打通
 *              (:tb 经命名参数转 ? 后由 JDBC 驱动正确绑定表名,支持 addBatch 跨子表自动建表)。</li>
 *              </ul>
 *
 *              <p>
 *              使用约束:
 *              <ul>
 *              <li>实体主键必须是时间戳列且用 <b>ASSIGN</b> 策略——TDengine 无自增/序列,
 *              {@code getGeneratedKeys} 不支持(实测报 "this operation is NOT supported")。</li>
 *              <li>子表名(:tb)由驱动内联进 SQL,务必在 service 层做白名单校验防注入。</li>
 *              </ul>
 *
 *              <p>
 *              以下操作为 <b>TDengine 引擎本身不具备</b>的语义(非框架缺陷),统一抛明确异常说明原因与替代方案:
 *              UPDATE 语句、行级锁(SELECT ... FOR UPDATE)、存储过程、saveOrUpdate/UPSERT、INSERT IGNORE。
 *              时序库为追加写(append-only)模型:数据更新以“相同时间戳重新 INSERT 覆盖”实现(3.x 为列级合并),
 *              数据过期用库级 KEEP 策略,删除用 DELETE(主要按时间范围)或 sql xml 手写。
 *
 * @author zhongxuchen
 * @version v1.0,Date:2022-09-13
 * @modify Date:2026-10-28,依据 TDengine 3.3.6.x 实测厘清支持边界:查询/对象插入可用,
 *         读操作忽略行锁,不支持操作改为 TDengine 具体原因说明(原为通用未支持文案)
 */
@SuppressWarnings({ "rawtypes" })
public class TDengineDialect extends DefaultDialect {

	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(TDengineDialect.class);

	/**
	 * TDengine 无行级锁(实测 SELECT ... FOR UPDATE 报 syntax error near "for update", code 9728)。
	 * 读操作忽略锁模式并告警,避免 DefaultDialect 拼出 " for update" 坏 SQL;时序库读取本就无需加锁。
	 *
	 * @param lockMode 调用方请求的锁模式(null 表示未请求锁)
	 * @param operate  操作名(用于日志)
	 * @return 恒为 null,即按无锁执行
	 */
	private LockMode ignoreLock(LockMode lockMode, String operate) {
		if (lockMode != null) {
			logger.warn("TDengine 不支持行级锁,{} 忽略 LockMode={}(时序库读取无需加锁)", operate, lockMode);
		}
		return null;
	}

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			DBProfile profile, String tableName, final Integer queryTimeout) {
		// TDengine 以时间戳为主键、重复时间戳为覆盖写,无关系库的唯一约束校验语义
		throw new UnsupportedOperationException(
				"TDengine 无唯一约束校验语义(时序库以时间戳为主键,重复时间戳为覆盖写),isUnique 不适用!");
	}

	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, DBProfile profile, int fetchSize, int maxRows) throws Exception {
		// 实测 TDengine 3.x 支持 order by rand(),通用路径可用
		return super.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, totalCount,
				randomCount, conn, profile, fetchSize, maxRows);
	}

	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, DecryptHandler decryptHandler, Long pageNo, Integer pageSize, Connection conn,
			DBProfile profile, int fetchSize, int maxRows) throws Exception {
		// 实测 TDengine 支持 limit ? offset ?,通用分页路径可用
		return super.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo, pageSize, conn,
				profile, fetchSize, maxRows);
	}

	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			DecryptHandler decryptHandler, Integer topSize, Connection conn, DBProfile profile, int fetchSize,
			int maxRows) throws Exception {
		return super.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize, conn, profile,
				fetchSize, maxRows);
	}

	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, QueryExecutorExtend queryExecutorExtend, DecryptHandler decryptHandler,
			Connection conn, LockMode lockMode, DBProfile profile, int fetchSize, int maxRows) throws Exception {
		// TDengine 无行锁,忽略 lockMode 避免拼出 " for update" 坏 SQL
		return super.findBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, queryExecutorExtend, decryptHandler, conn,
				ignoreLock(lockMode, "findBySql"), profile, fetchSize, maxRows);
	}

	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql, Object[] paramsValue,
			boolean isLastSql, final QueryExecutorExtend extend, Connection conn, DBProfile profile) throws Exception {
		// 实测 TDengine 3.x 支持 count(1),通用 count 路径可用
		return super.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, extend, conn, profile);
	}

	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, int lockWaitTimeout, Connection conn, DBProfile profile,
			String tableName, final Integer queryTimeout) throws Exception {
		// TDengine 无行锁,忽略 lockMode
		return super.load(sqlToyContext, entity, onlySubTables, cascadeTypes, ignoreLock(lockMode, "load"),
				lockWaitTimeout, conn, profile, tableName, queryTimeout);
	}

	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, boolean onlySubTables,
			List<Class> cascadeTypes, LockMode lockMode, final int lockWaitTimeout, Connection conn, DBProfile profile,
			String tableName, int fetchSize, int maxRows, final Integer queryTimeout) throws Exception {
		// TDengine 无行锁,忽略 lockMode
		return super.loadAll(sqlToyContext, entities, onlySubTables, cascadeTypes, ignoreLock(lockMode, "loadAll"),
				lockWaitTimeout, conn, profile, tableName, fetchSize, maxRows, queryTimeout);
	}

	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			String tableName) throws Exception {
		// 标准单表列清单 insert,写已存在子表/普通表可用;主键须为 ASSIGN 时间戳策略
		return super.save(sqlToyContext, entity, conn, profile, tableName);
	}

	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, Boolean autoCommit,
			String tableName) throws Exception {
		return super.saveAll(sqlToyContext, entities, batchSize, reflectPropsHandler, conn, profile, autoCommit,
				tableName);
	}

	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClass, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 无 UPDATE 语句(时序库追加写模型),更新请用相同时间戳重新 INSERT 覆盖(3.x 为列级合并)!");
	}

	@Override
	public Serializable updateSaveFetch(SqlToyContext sqlToyContext, Serializable entity,
			UpdateRowHandler updateRowHandler, int lockWaitTimeout, String[] uniqueProps, Connection conn,
			DBProfile profile, String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 无 UPDATE 语句且无行级锁,updateSaveFetch 不适用;更新请用相同时间戳重新 INSERT 覆盖!");
	}

	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, String[] uniqueFields,
			String[] forceUpdateFields, ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile,
			Boolean autoCommit, String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 无 UPDATE 语句(时序库追加写模型),更新请用相同时间戳重新 INSERT 覆盖(3.x 为列级合并)!");
	}

	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, DBProfile profile, Boolean autoCommit, String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 无 UPDATE/UPSERT 语句,saveOrUpdate 不适用;如需覆盖写请用相同时间戳重新 INSERT!");
	}

	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, DBProfile profile,
			Boolean autoCommit, String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 无 UPDATE/UPSERT 语句,saveOrUpdateAll 不适用;如需覆盖写请用相同时间戳重新 INSERT!");
	}

	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
			ReflectPropsHandler reflectPropsHandler, Connection conn, DBProfile profile, Boolean autoCommit,
			String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 无 INSERT IGNORE/ON CONFLICT 语义(重复时间戳即覆盖写),saveAllIgnoreExist 不适用!");
	}

	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, DBProfile profile,
			String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 3.x 支持 DELETE(主要按时间范围),但 ORM 按主键删除未启用;数据过期建议用库级 KEEP 策略,或经 sql xml 手写 DELETE!");
	}

	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, Connection conn,
			DBProfile profile, Boolean autoCommit, String tableName) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 3.x 支持 DELETE(主要按时间范围),但 ORM 批量按主键删除未启用;数据过期建议用库级 KEEP 策略,或经 sql xml 手写 DELETE!");
	}

	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, DBProfile profile,
			LockMode lockMode, int lockWaitTimeout, int fetchSize, int maxRows) throws Exception {
		throw new UnsupportedOperationException(
				"TDengine 无 UPDATE 语句且无行级锁(SELECT ... FOR UPDATE 报错),updateFetch 不适用!");
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] inParamsValue, Integer[] outParamsType, final boolean moreResult, Connection conn,
			DBProfile profile, int fetchSize, final Integer timeout) throws Exception {
		throw new UnsupportedOperationException("TDengine 无存储过程,executeStore 不适用!");
	}

}
