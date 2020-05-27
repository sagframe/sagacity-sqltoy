/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.PaginationModel;
import org.sagacity.sqltoy.plugins.nosql.ElasticSearchPlugin;
import org.sagacity.sqltoy.plugins.nosql.ElasticSqlPlugin;

/**
 * @project sagacity-sqltoy4.1
 * @description 提供基于elasticSearch的查询服务(利用sqltoy组织查询的语句机制的优势提供查询相关功能,增删改暂时不提供)
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Elastic.java,Revision:v1.0,Date:2018年1月1日
 */
public class Elastic extends BaseLink {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3963816230256439625L;

	private final String ERROR_MESSAGE = "ES查询请使用<eql></eql>配置!";

	/**
	 * 查询语句
	 */
	private String sql;

	/**
	 * sql中的参数名称
	 */
	private String[] names;

	/**
	 * 参数对应的值
	 */
	private Object[] values;

	/**
	 * 查询条件赋值的对象,自动根据sql中的参数名称跟对象的属性进行匹配提取响应的值作为条件
	 */
	private Serializable entity;

	/**
	 * 返回结果类型
	 */
	private Class<?> resultType;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Elastic(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Elastic sql(String sql) {
		this.sql = sql;
		return this;
	}

	public Elastic names(String... names) {
		this.names = names;
		return this;
	}

	public Elastic values(Object... values) {
		this.values = values;
		return this;
	}

	public Elastic entity(Serializable entityVO) {
		this.entity = entityVO;
		return this;
	}

	public Elastic resultType(Class<?> resultType) {
		this.resultType = resultType;
		return this;
	}

	/**
	 * @todo 获取单条记录
	 * @return
	 */
	public Object getOne() {
		List<?> result = find();
		if (result != null && !result.isEmpty()) {
			return result.get(0);
		}
		return null;
	}

	/**
	 * @todo 集合记录查询
	 * @return
	 */
	public List<?> find() {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search);
		if (sqlToyConfig.getNoSqlConfigModel() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		try {
			if (sqlToyConfig.getNoSqlConfigModel().isSqlMode()) {
				return ElasticSqlPlugin.findTop(sqlToyContext, sqlToyConfig, queryExecutor, null);
			}
			return ElasticSearchPlugin.findTop(sqlToyContext, sqlToyConfig, queryExecutor, null);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException(e);
		}
	}

	/**
	 * @todo 查询前多少条记录
	 * @param topSize
	 * @return
	 */
	public List<?> findTop(final int topSize) {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search);
		if (sqlToyConfig.getNoSqlConfigModel() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		try {
			if (sqlToyConfig.getNoSqlConfigModel().isSqlMode()) {
				return ElasticSqlPlugin.findTop(sqlToyContext, sqlToyConfig, queryExecutor, topSize);
			}
			return ElasticSearchPlugin.findTop(sqlToyContext, sqlToyConfig, queryExecutor, topSize);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException(e);
		}
	}

	/**
	 * @todo 分页查询
	 * @param pageModel
	 * @return
	 */
	public PaginationModel findPage(PaginationModel pageModel) {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search);
		NoSqlConfigModel noSqlConfig = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlConfig == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		try {
			if (noSqlConfig.isSqlMode()) {
				ElasticEndpoint esConfig = sqlToyContext.getElasticEndpoint(noSqlConfig.getEndpoint());
				if (esConfig.isNativeSql()) {
					throw new UnsupportedOperationException("elastic native sql pagination is not support!");
				}
				return ElasticSqlPlugin.findPage(sqlToyContext, sqlToyConfig, pageModel, queryExecutor);
			}
			return ElasticSearchPlugin.findPage(sqlToyContext, sqlToyConfig, pageModel, queryExecutor);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException(e);
		}
	}

	/**
	 * @todo 构造统一的查询条件
	 * @return
	 */
	private QueryExecutor build() {
		QueryExecutor queryExecutor = null;
		if (entity != null) {
			queryExecutor = new QueryExecutor(sql, entity);
		} else {
			queryExecutor = new QueryExecutor(sql, names, values);
		}
		if (resultType != null) {
			queryExecutor.resultType(resultType);
		}
		return queryExecutor;
	}
}
