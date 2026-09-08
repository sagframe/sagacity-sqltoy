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
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供基于elasticSearch的查询服务(利用sqltoy组织查询的语句机制的优势提供查询相关功能,增删改暂时不提供)
 * @author zhongxuchen
 * @version v1.0,Date:2018-01-01
 */
public class Elastic extends BaseLink {
	private final static Logger logger = LoggerFactory.getLogger(Elastic.class);

	private static final long serialVersionUID = -3963816230256439625L;

	private final String ERROR_MESSAGE = "elastic query requires <eql></eql> configuration!";

	private String endPoint;

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
	private Serializable params;

	/**
	 * 返回结果类型
	 */
	private Type resultType;

	/**
	 * 返回结果是Map类型，属性标签是否需要驼峰化命名处理
	 */
	private Boolean humpMapLabel;

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    elastic查询绑定的数据源，null表示使用默认数据源
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
		this.params = entityVO;
		return this;
	}

	public Elastic endPoint(String endPoint) {
		this.endPoint = endPoint;
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
	 * 获取单条记录
	 * 
	 * @return 查询结果的第一条记录，无记录时返回null，多于一行的查询结果会抛出异常
	 */
	public Object getOne() {
		List<?> result = find();
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.size() == 1) {
			return result.get(0);
		}
		throw new IllegalArgumentException("getOne expect a single record but found [" + result.size()
				+ "] rows, please check the query conditions!");
	}

	/**
	 * 集合记录查询
	 * 
	 * @return 查询结果集合，设置了resultType时行为其类型实例，否则为Map结构
	 */
	public List<?> find() {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "", null);
		if (sqlToyConfig.getNoSqlConfigModel() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		SqlToyConfig realSqlConfig = null;
		if (StringUtil.isNotBlank(endPoint)) {
			realSqlConfig = sqlToyConfig.clone();
			realSqlConfig.getNoSqlConfigModel().setEndpoint(endPoint);
		} else {
			realSqlConfig = sqlToyConfig;
		}
		try {
			if (realSqlConfig.getNoSqlConfigModel().isSqlMode()) {
				return ElasticSqlPlugin.findTop(sqlToyContext, realSqlConfig, queryExecutor, null);
			}
			return ElasticSearchPlugin.findTop(sqlToyContext, realSqlConfig, queryExecutor, null);
		} catch (Exception e) {
			logger.error("find method execution failed", e);
			throw new DataAccessException(e);
		}
	}

	/**
	 * 查询前多少条记录
	 * 
	 * @param topSize 获取最前面的记录数量
	 * @return 符合条件的前topSize条记录集合
	 */
	public List<?> findTop(final int topSize) {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "", null);
		if (sqlToyConfig.getNoSqlConfigModel() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		SqlToyConfig realSqlConfig = null;
		if (StringUtil.isNotBlank(endPoint)) {
			realSqlConfig = sqlToyConfig.clone();
			realSqlConfig.getNoSqlConfigModel().setEndpoint(endPoint);
		} else {
			realSqlConfig = sqlToyConfig;
		}
		try {
			if (realSqlConfig.getNoSqlConfigModel().isSqlMode()) {
				return ElasticSqlPlugin.findTop(sqlToyContext, realSqlConfig, queryExecutor, topSize);
			}
			return ElasticSearchPlugin.findTop(sqlToyContext, realSqlConfig, queryExecutor, topSize);
		} catch (Exception e) {
			logger.error("findTop method execution failed", e);
			throw new DataAccessException(e);
		}
	}

	/**
	 * 分页查询
	 * 
	 * @param pageModel 分页模型对象，提供页号(pageNo)、每页记录数(pageSize)等分页参数
	 * @return 分页查询结果，包含符合条件的记录总数及当页数据
	 */
	public Page findPage(Page pageModel) {
		Page pageResult = null;
		if (pageModel.getPageNo() == -1) {
			pageResult = new Page();
			List rows = find();
			int rowSize = (rows == null) ? 0 : rows.size();
			pageResult.setRows(rows);
			pageResult.setPageSize(rowSize);
			pageResult.setRecordCount(rowSize);
			return pageResult;
		}
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "", null);
		if (sqlToyConfig.getNoSqlConfigModel() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		SqlToyConfig realSqlConfig = null;
		if (StringUtil.isNotBlank(endPoint)) {
			realSqlConfig = sqlToyConfig.clone();
			realSqlConfig.getNoSqlConfigModel().setEndpoint(endPoint);
		} else {
			realSqlConfig = sqlToyConfig;
		}
		NoSqlConfigModel noSqlConfig = realSqlConfig.getNoSqlConfigModel();
		try {
			boolean isOverPageToFirst = false;
			// 使用全局默认值
			if (sqlToyContext.getOverPageToFirst() != null) {
				isOverPageToFirst = sqlToyContext.getOverPageToFirst();
			}
			// 以pageModel中指定的为准
			if (pageModel.getOverPageToFirst() != null) {
				isOverPageToFirst = pageModel.getOverPageToFirst();
			}
			if (noSqlConfig.isSqlMode()) {
				ElasticEndpoint esConfig = sqlToyContext.getElasticEndpoint(noSqlConfig.getEndpoint());
				if (esConfig.isNativeSql()) {
					throw new UnsupportedOperationException("elastic native sql pagination is not support!");
				}
				pageResult = ElasticSqlPlugin.findPage(sqlToyContext, realSqlConfig, pageModel, queryExecutor);
			} else {
				pageResult = ElasticSearchPlugin.findPage(sqlToyContext, realSqlConfig, pageModel, queryExecutor);
			}
			if (pageResult.getRecordCount() == 0 && isOverPageToFirst) {
				pageResult.setPageNo(1L);
			}
			return pageResult;
		} catch (Exception e) {
			logger.error("findPage method execution failed", e);
			throw new DataAccessException(e);
		}
	}

	/**
	 * 构造统一的查询条件
	 * 
	 * @return 组装了sql、参数和结果类型的QueryExecutor查询执行对象
	 */
	private QueryExecutor build() {
		QueryExecutor queryExecutor = null;
		if (params != null) {
			queryExecutor = new QueryExecutor(sql, params);
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
