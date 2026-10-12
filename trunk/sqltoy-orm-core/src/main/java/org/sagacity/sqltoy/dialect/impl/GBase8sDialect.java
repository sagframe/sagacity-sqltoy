package org.sagacity.sqltoy.dialect.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.utils.DefaultDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.GBase8sRsProxy;
import org.sagacity.sqltoy.model.DBProfile;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;

/**
 * @project sagacity-sqltoy
 * @description GBase 8s(南大通用,Informix 12.10内核)方言实现:CRUD/update等主体复用
 *              Oracle系(真库探针2026-10-12:decode/nvl/instr/add_months/last_day原生,
 *              序列/identity/rowid,merge语句语法支持)。差异点覆写: 1)分页用limit ? offset
 *              ?(12.10原生); 2)随机取数走通用limit形态(无dbms_random);
 *              3)saveOrUpdate走真merge into(DialectUtils按GBASE8S分派:伪表from
 *              systables/on无括号/nvl弹性/decimal cast),但逐条执行——驱动batch
 *              模式多元素merge参数错位(真库实测),单元素批量完全正确(saveAllIgnoreExist 的insert-only
 *              merge不受batch参数错位影响,实测多元素批量正确,不覆写); 4)锁等待经set lock mode to wait
 *              n会话级(语句无wait子句形态);
 *              5)tableApi走JDBC元数据(系统表systables的varchar列经jdbc读取为空);
 *              6)updateFetch见方法注释(updateSaveFetch由DefaultDialectUtils按GBASE8S
 *              包GBase8sRsProxy代理,不在此覆写)。
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-12
 */
public class GBase8sDialect extends OracleDialect {

	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long pageNo, Integer pageSize,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, pageNo,
				pageSize, conn, profile, fetchSize, maxRows);
	}

	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			final DecryptHandler decryptHandler, Integer topSize, Connection conn, DBProfile profile,
			final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler, topSize,
				conn, profile, fetchSize, maxRows);
	}

	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, final DecryptHandler decryptHandler, Long totalCount, Long randomCount,
			Connection conn, DBProfile profile, final int fetchSize, final int maxRows) throws Exception {
		return DefaultDialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, decryptHandler,
				totalCount, randomCount, conn, profile, fetchSize, maxRows);
	}

	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
			ReflectPropsHandler reflectPropsHandler, String[] forceUpdateFields, Connection conn, DBProfile profile,
			final Boolean autoCommit, final String tableName) throws Exception {
		// update 2026-10-12 GBase 8s支持merge into(DialectUtils按GBASE8S分派已修复
		// 伪表/on括号/nvl/decimal cast),但驱动batch模式多元素merge参数错位(真库实测
		// 两遍批量saveOrUpdateAll第二遍更新写回旧值),逐条走父类单元素批量(每次完整
		// merge语句,独立绑定独立执行,已证正确);驱动修复后可回批量。
		// 注意saveAllIgnoreExist(mergeIgnore,insert-only)不受batch参数错位影响(实测),不在此覆写
		long cnt = 0;
		for (Object entity : entities) {
			List<java.io.Serializable> one = new ArrayList<>(1);
			one.add((java.io.Serializable) entity);
			super.saveOrUpdateAll(sqlToyContext, one, 1, reflectPropsHandler, forceUpdateFields, conn, profile,
					autoCommit, tableName);
			cnt++;
		}
		return cnt;
	}

	@Override
	public List<TableMeta> getTables(String catalog, String schema, String tableName, Connection conn,
			DBProfile profile) throws Exception {
		// update 2026-10-12 该容器镜像的系统目录(systables)varchar列经jdbc读取为空
		// (真库实测tabname读回空串),tableApi改走JDBC元数据通用实现
		return DefaultDialectUtils.getTables(catalog, schema, tableName, conn, profile);
	}

	/**
	 * updateFetch的目的:一次交互完成"查询→锁定→判断→修改→返回修改后结果"(典型如
	 * 取号、扣减库存),执行骨架与公共路径DialectUtils.updateFetchBySql完全一致:
	 * 锁定语句拼装→sql签名→打印→CONCUR_UPDATABLE预编译→SqlUtil参数绑定→
	 * ResultUtils.processResultSet行循环(handler逐行修改→落库→组装返回行)。
	 * 唯一不能直接委托公共实现的原因:该驱动(3.70)CONCUR_UPDATABLE结果集的
	 * rs.updateRow()存在SQL组装缺陷(真库实测无论列型/有无for update均报"Found a quote for which there
	 * is no matching quote"),而updateRow()的调用点在
	 * ResultUtils公共行循环内部、结果集rs也是updateFetchBySql内部创建的,没有仅
	 * 替换落库一步的注入点。故此处复制其执行骨架(签名/打印/参数绑定/结果组装全部
	 * 调用同一公共设施),仅把rs以GBase8sRsProxy代理包裹后交给processResultSet:
	 * 代理只分叉落库动作(updateRow→独立update语句),其余全部透传,并承接JDBC可
	 * 更新结果集的读可见性契约(该驱动updateXxx后行内读回旧值,不实现读可见性,
	 * 代理按下标/列名先查变更集)。where以select首列(updateFetch场景查询主键列)
	 * 的原始值定位——代理在next()时即捕获,即使handler改写了首列,锁定读后数据库 当前仍是旧值,以旧值定位语义正确
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, UpdateRowHandler updateRowHandler, Connection conn, DBProfile profile,
			final LockMode lockMode, int lockWaitTimeout, final int fetchSize, final int maxRows) throws Exception {
		// 锁定语句:for update后缀;语句级wait n该库无形态,超时走会话级
		// set lock mode to wait n(DefaultDialectUtils.setSessionLockWait按GBASE8S分派)
		String realSql = sql.concat(DefaultDialectUtils.getLockSql(sql, profile.getDbType(),
				(lockMode == null) ? LockMode.UPGRADE : lockMode, 0, false, null, false));
		DefaultDialectUtils.setSessionLockWait(profile, conn, lockMode, lockWaitTimeout);
		if (updateRowHandler == null) {
			return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, null, null, conn, profile,
					0, fetchSize, maxRows);
		}
		Integer dbType = profile.getDbType();
		String lastSql = SqlUtilsExt.signSql(realSql, dbType, sqlToyConfig);
		SqlExecuteStat.showSql("execute updateFetch", lastSql, paramsValue);
		try (PreparedStatement pst = conn.prepareStatement(lastSql, ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_UPDATABLE)) {
			if (fetchSize > 0) {
				pst.setFetchSize(fetchSize);
			}
			if (maxRows > 0) {
				pst.setMaxRows(maxRows);
			}
			SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, profile, pst, paramsValue, null, 0);
			try (ResultSet rs = pst.executeQuery()) {
				// 代理只分叉updateRow的落库动作(首列定位,见方法注释),行循环/结果
				// 组装仍走公共processResultSet
				return ResultUtils.processResultSet(dbType, sqlToyContext, sqlToyConfig, conn,
						GBase8sRsProxy.wrap(rs, conn, extractUpdateTable(sql), null), null, updateRowHandler, null, 0);
			}
		}
	}

	/** updateFetch源SQL中from后的表名提取(嵌套子查询非可更新结果集,不适用于本场景) */
	private static String extractUpdateTable(String sql) {
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(?i)\\sfrom\\s+([a-zA-Z_][\\w$]*(\\.[a-zA-Z_][\\w$]*)?)").matcher(sql);
		return m.find() ? m.group(1) : null;
	}
}
