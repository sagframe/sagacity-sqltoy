/**
 * 
 */
package org.sagacity.sqltoy.dialect.executor;

import java.util.concurrent.Callable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallelQueryResult;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.inner.ParallelQueryExtend;
import org.sagacity.sqltoy.plugins.CrossDbAdapter;

/**
 * @project sagacity-sqltoy
 * @description 并行查询执行器
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-25
 * @modify 2020-8-25,修改说明
 */
public class ParallelQueryExecutor implements Callable<ParallelQueryResult> {

	/**
	 * sqltoy上下文
	 */
	private SqlToyContext sqlToyContext;

	private DialectFactory dialectFactory;

	private DataSource dataSource;
	private ParallQuery parallelQuery;
	private SqlToyConfig sqlToyConfig;
	private String[] paramNames;

	private Object[] paramValues;

	public ParallelQueryExecutor(SqlToyContext sqlToyContext, DialectFactory dialectFactory, SqlToyConfig sqlToyConfig,
			ParallQuery parallelQuery, String[] paramNames, Object[] paramValues, DataSource dataSource) {
		this.sqlToyContext = sqlToyContext;
		this.dialectFactory = dialectFactory;
		this.sqlToyConfig = sqlToyConfig;
		this.parallelQuery = parallelQuery;
		this.dataSource = dataSource;
		this.paramNames = paramNames;
		this.paramValues = paramValues;
	}

	@Override
	public ParallelQueryResult call() {
		ParallelQueryResult result = new ParallelQueryResult();
		try {
			ParallelQueryExtend extend = parallelQuery.getExtend();
			QueryExecutor queryExecutor = new QueryExecutor(extend.sql).resultType(extend.resultType).names(paramNames)
					.values(paramValues).showSql(extend.showSql);
			// 分页
			if (extend.page != null) {
				// 不取总记录数分页模式
				if (extend.page.getSkipQueryCount() != null && extend.page.getSkipQueryCount()) {
					result.setResult(dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecutor, sqlToyConfig,
							extend.page.getPageNo(), extend.page.getPageSize(), dataSource));
				} else {
					result.setResult(
							dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig, extend.page.getPageNo(),
									extend.page.getPageSize(), extend.page.getOverPageToFirst(), dataSource));
				}
				// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
				CrossDbAdapter.redoPageQuery(sqlToyContext, dialectFactory, queryExecutor, extend.page);
			} // 取top记录
			else if (extend.topSize > 0) {
				result.setResult(
						dialectFactory.findTop(sqlToyContext, queryExecutor, sqlToyConfig, extend.topSize, dataSource));
				// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
				CrossDbAdapter.redoTopQuery(sqlToyContext, dialectFactory, queryExecutor, extend.topSize);
			} // 取随机记录
			else if (extend.randomSize > 0) {
				result.setResult(dialectFactory.getRandomResult(sqlToyContext, queryExecutor, sqlToyConfig,
						extend.randomSize, dataSource));
				// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
				CrossDbAdapter.redoRandomQuery(sqlToyContext, dialectFactory, queryExecutor, extend.randomSize);
			} else {
				result.setResult(
						dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null, dataSource));
				// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
				CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecutor);
			}
		} catch (Exception e) {
			result.setSuccess(false);
			result.setMessage(e.getMessage());
		}
		return result;
	}
}
