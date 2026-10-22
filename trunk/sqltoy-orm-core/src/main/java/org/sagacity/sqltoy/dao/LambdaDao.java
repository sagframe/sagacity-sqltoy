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
 * @project sagacity-sqltoy
 * @description lambda构建器执行所需的最小dao能力集合。LightDao、SqlToyDao、SqlToyLazyDao直接实现
 *              该接口,不引入适配器;各接口按自身命名完成方法桥接
 * @author zhongxuchen
 */
@SuppressWarnings({ "rawtypes" })
public interface LambdaDao {

	SqlToyContext getSqlToyContext();

	DataSource getDataSource();

	EntityMeta getEntityMeta(Class entityClass);

	<T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery);

	<T> Page<T> findPageEntity(Page page, Class<T> entityClass, EntityQuery entityQuery);

	QueryResult findPageByQuery(Page page, QueryExecutor queryExecutor);

	Long getCount(Class entityClass, EntityQuery entityQuery);

	Long updateByQuery(Class entityClass, EntityUpdate entityUpdate);

	Long deleteByQuery(Class entityClass, EntityQuery entityQuery);

	Query query();

	Execute execute();
}
