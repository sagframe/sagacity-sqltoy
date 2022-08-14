/**
 * 
 */
package org.sagacity.sqltoy.dialect.executor;

import java.util.concurrent.Callable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.ParallQuery;
import org.sagacity.sqltoy.model.ParallQueryResult;
import org.sagacity.sqltoy.model.inner.ParallQueryExtend;

/**
 * @project sagacity-sqltoy
 * @description 并行查询执行器
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-25
 * @modify 2020-8-25,修改说明
 */
public class ParallQueryExecutor implements Callable<ParallQueryResult> {

	/**
	 * sqltoy上下文
	 */
	private SqlToyContext sqlToyContext;

	private DialectFactory dialectFactory;

	private DataSource dataSource;
	private ParallQuery parallQuery;
	private SqlToyConfig sqlToyConfig;
	private String[] paramNames;

	private Object[] paramValues;

	public ParallQueryExecutor(SqlToyContext sqlToyContext, DialectFactory dialectFactory, SqlToyConfig sqlToyConfig,
			ParallQuery parallQuery, String[] paramNames, Object[] paramValues, DataSource dataSource) {
		this.sqlToyContext = sqlToyContext;
		this.dialectFactory = dialectFactory;
		this.sqlToyConfig = sqlToyConfig;
		this.parallQuery = parallQuery;
		this.dataSource = dataSource;
		this.paramNames = paramNames;
		this.paramValues = paramValues;
	}

	@Override
	public ParallQueryResult call() {
		ParallQueryResult result = new ParallQueryResult();
		try {
			ParallQueryExtend extend = parallQuery.getExtend();
			QueryExecutor queryExecutor = new QueryExecutor(extend.sql).resultType(extend.resultType).names(paramNames)
					.values(paramValues);
			// 分页
			if (extend.pageModel != null) {
				// 不取总记录数分页模式
				if (extend.pageModel.getSkipQueryCount() != null && extend.pageModel.getSkipQueryCount()) {
					result.setResult(dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecutor, sqlToyConfig,
							extend.pageModel.getPageNo(), extend.pageModel.getPageSize(), dataSource));
				} else {
					result.setResult(dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig,
							extend.pageModel.getPageNo(), extend.pageModel.getPageSize(), dataSource));
				}
			} else {
				result.setResult(
						dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null, dataSource));
			}
		} catch (Exception e) {
			result.setSuccess(false);
			result.setMessage(e.getMessage());
		}
		return result;
	}
}
