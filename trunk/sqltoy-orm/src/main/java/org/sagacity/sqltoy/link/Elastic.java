/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.plugins.nosql.ElasticSearchPlugin;
import org.sagacity.sqltoy.plugins.nosql.ElasticSqlPlugin;

/**
 * @project sagacity-sqltoy
 * @description 提供基于elasticSearch的查询服务(利用sqltoy组织查询的语句机制的优势提供查询相关功能,增删改暂时不提供)
 * @author zhongxuchen
 * @version v1.0,Date:2018年1月1日
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
	private Type resultType;

	/**
	 * 返回结果是Map类型，属性标签是否需要驼峰化命名处理
	 */
	private Boolean humpMapLabel;

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

	public Elastic resultType(Type resultType) {
		this.resultType = resultType;
		return this;
	}

	public Elastic humpMapLabel(Boolean humpMapLabel) {
		this.humpMapLabel = humpMapLabel;
		return this;
	}

	/**
	 * @todo 获取单条记录
	 * @return
	 */
	public Object getOne() {
		List<?> result = find();
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("getOne查询出:" + result.size() + " 条记录,不符合getOne查询预期!");
	}

	/**
	 * @todo 集合记录查询
	 * @return
	 */
	public List<?> find() {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "");
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
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "");
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
	public Page findPage(Page pageModel) {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "");
		NoSqlConfigModel noSqlConfig = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlConfig == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		Page pageResult = null;
		try {
			if (noSqlConfig.isSqlMode()) {
				ElasticEndpoint esConfig = sqlToyContext.getElasticEndpoint(noSqlConfig.getEndpoint());
				if (esConfig.isNativeSql()) {
					throw new UnsupportedOperationException("elastic native sql pagination is not support!");
				}
				pageResult = ElasticSqlPlugin.findPage(sqlToyContext, sqlToyConfig, pageModel, queryExecutor);
			} else {
				pageResult = ElasticSearchPlugin.findPage(sqlToyContext, sqlToyConfig, pageModel, queryExecutor);
			}
			if (pageResult.getRecordCount() == 0 && pageModel.isOverPageToFirst()) {
				pageResult.setPageNo(1L);
			}
			return pageResult;
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
			queryExecutor = new QueryExecutor(sql).names(names).values(values);
		}
		if (resultType != null) {
			queryExecutor.resultType(resultType);
		}
		queryExecutor.humpMapLabel(humpMapLabel);
		return queryExecutor;
	}
}
