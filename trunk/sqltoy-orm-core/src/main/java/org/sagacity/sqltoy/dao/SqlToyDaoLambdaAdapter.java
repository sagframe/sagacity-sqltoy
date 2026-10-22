package org.sagacity.sqltoy.dao;

import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.link.Execute;
import org.sagacity.sqltoy.link.Query;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;

/**
 * @description 仅SqlToyDao需要的命名适配:SqlToyDao规范命名为findList/findPage/count,
 *              而LambdaDao契约为findEntity/findPageEntity/getCount;SqlToyDao接口自身
 *              禁止出现旧命名,故用包内适配器隔离,不扩大SqlToyDao公共API
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class SqlToyDaoLambdaAdapter implements LambdaDao {

	private final SqlToyDao dao;

	SqlToyDaoLambdaAdapter(SqlToyDao dao) {
		this.dao = dao;
	}

	@Override
	public SqlToyContext getSqlToyContext() {
		return dao.getSqlToyContext();
	}

	@Override
	public DataSource getDataSource() {
		return dao.getDataSource();
	}

	@Override
	public EntityMeta getEntityMeta(Class entityClass) {
		return dao.getEntityMeta(entityClass);
	}

	@Override
	public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery) {
		return dao.findList(entityClass, entityQuery);
	}

	@Override
	public <T> Page<T> findPageEntity(Page page, Class<T> entityClass, EntityQuery entityQuery) {
		return (Page<T>) dao.findPage(page, entityClass, entityQuery);
	}

	@Override
	public QueryResult findPageByQuery(Page page, QueryExecutor queryExecutor) {
		return dao.findPage(page, queryExecutor);
	}

	@Override
	public Long getCount(Class entityClass, EntityQuery entityQuery) {
		return dao.count(entityClass, entityQuery);
	}

	@Override
	public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate) {
		return dao.updateByQuery(entityClass, entityUpdate);
	}

	@Override
	public Long deleteByQuery(Class entityClass, EntityQuery entityQuery) {
		return dao.deleteByQuery(entityClass, entityQuery);
	}

	@Override
	public Query query() {
		return dao.query();
	}

	@Override
	public Execute execute() {
		return dao.execute();
	}
}
