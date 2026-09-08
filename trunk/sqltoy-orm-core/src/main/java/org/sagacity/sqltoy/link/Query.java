package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.plugins.CrossDbAdapter;
import org.sagacity.sqltoy.utils.BeanUtil;

/**
 * @project sagacity-sqltoy
 * @description 普通查询
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-09
 */
public class Query extends BaseLink {

	private static final long serialVersionUID = -8128694559008281052L;

	/**
	 * sql语句或者sqlId
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
	private Class<?> resultType;

	/**
	 * jdbc 查询时默认加载到内存中的记录数量 -1表示不设置，采用数据库默认的值
	 */
	private int fetchSize = -1;

	/**
	 * jdbc查询最大返回记录数量
	 */
	private int maxRows = -1;

	/**
	 * 返回hashMap数据集合时key的格式是否变成驼峰模式
	 */
	private Boolean humpMapLabel;

	/**
	 * 锁表
	 */
	private LockMode lockMode;

	private int lockWaitTimeout = -1;

	private int queryTimeout = -1;

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    查询绑定的数据源，null表示使用默认数据源
	 */
	public Query(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Query fetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
		return this;
	}

	@Deprecated
	public Query maxRows(int maxRows) {
		this.maxRows = maxRows;
		return this;
	}

	public Query lock(LockMode lockMode) {
		this.lockMode = lockMode;
		return this;
	}

	public Query lockWaitTimeout(int lockWaitTimeout) {
		this.lockWaitTimeout = lockWaitTimeout;
		return this;
	}

	public Query queryTimeout(int queryTimeout) {
		this.queryTimeout = queryTimeout;
		return this;
	}

	/**
	 * 设置sql语句
	 * 
	 * @param sql 具体执行的sql语句或者xml中定义的sqlId
	 * @return 当前Query对象，支持链式调用
	 */
	public Query sql(String sql) {
		this.sql = sql;
		return this;
	}

	public Query humpMapLabel(Boolean isHump) {
		this.humpMapLabel = isHump;
		return this;
	}

	/**
	 * sql语句中的参数名称
	 * 
	 * @param names sql中:name形式参数对应的参数名称数组
	 * @return 当前Query对象，支持链式调用
	 */
	public Query names(String... names) {
		this.names = names;
		return this;
	}

	/**
	 * sql语句中的参数对应的值
	 * 
	 * @param values 参数值数组，顺序与names中参数名称一一对应；当传入单个Map时作为命名参数对象整体处理
	 * @return 当前Query对象，支持链式调用
	 */
	public Query values(Object... values) {
		this.values = values;
		return this;
	}

	/**
	 * 通过对象传递参数(对象属性名跟sql中的参数别名对应)
	 * 
	 * @param entityVO 作为参数传递的对象，按属性名与sql中参数名称匹配提取值作为查询条件
	 * @return 当前Query对象，支持链式调用
	 */
	public Query entity(Serializable entityVO) {
		this.params = entityVO;
		return this;
	}

	public Query resultType(Class<?> resultType) {
		this.resultType = resultType;
		return this;
	}

	public Query dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	/**
	 * 获取单值
	 * 
	 * @param <T>        单值的目标类型
	 * @param resultType 单值要转换的目标类型，如：String.class、Long.class
	 * @return 查询结果第一行第一列的值并转换为指定类型，无记录时返回null
	 */
	public <T> T getValue(final Class<T> resultType) {
		Object result = getValue();
		try {
			return (T) BeanUtil.convertType(result, JdbcTypes.OTHER, DataType.getType(resultType),
					resultType.getTypeName());
		} catch (Exception e) {
			throw new DataAccessException("getValue failed to get a single value:" + e.getMessage(), e);
		}
	}

	/**
	 * 获取单值
	 * 
	 * @return 查询结果第一行第一列的值，无记录时返回null，多于一行的查询结果会抛出异常
	 */
	public Object getValue() {
		QueryExecutor queryExecute = new QueryExecutor(sql).names(names).values(values);
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.search, getDialect());
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecute, sqlToyConfig, null,
				getDataSource(sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecute);
		List rows = result.getRows();
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		if (rows.size() == 1) {
			return ((List) rows.get(0)).get(0);
		}
		throw new IllegalArgumentException("getValue expect a single row with a single value but found [" + rows.size()
				+ "] rows, please check the query conditions!");
	}

	/**
	 * 获取一条记录
	 * 
	 * @return 查询结果的第一条记录，无记录时返回null，多于一行的查询结果会抛出异常
	 */
	public Object getOne() {
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.search, getDialect());
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecute, sqlToyConfig, lockMode,
				getDataSource(sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecute);
		List rows = result.getRows();
		if (rows == null || rows.isEmpty()) {
			return null;
		}
		if (rows.size() == 1) {
			return rows.get(0);
		}
		throw new IllegalArgumentException("getOne expect a single record but found [" + rows.size()
				+ "] rows, please check the query conditions!");
	}

	/**
	 * 查询记录集的数量
	 * 
	 * @return 符合查询条件的记录总数
	 */
	public Long count() {
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.search, getDialect());
		Long result = dialectFactory.getCountBySql(sqlToyContext, queryExecute, sqlToyConfig,
				getDataSource(sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoCountQuery(sqlToyContext, dialectFactory, queryExecute);
		return result;
	}

	/**
	 * 查询结果集合
	 * 
	 * @return 查询结果行集合，设置了resultType时行为其类型实例，否则每行为List结构
	 */
	public List<?> find() {
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.search, getDialect());
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecute, sqlToyConfig, lockMode,
				getDataSource(sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoQuery(sqlToyContext, dialectFactory, queryExecute);
		return result.getRows();
	}

	/**
	 * 取前多少条记录
	 * 
	 * @param topSize 获取最前面的记录数量
	 * @return 符合条件的前topSize条记录集合
	 */
	public List<?> findTop(final double topSize) {
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.search, getDialect());
		QueryResult result = dialectFactory.findTop(sqlToyContext, queryExecute, sqlToyConfig, topSize,
				getDataSource(sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoTopQuery(sqlToyContext, dialectFactory, queryExecute, topSize);
		return result.getRows();
	}

	/**
	 * 随机取记录
	 * 
	 * @param randomSize 随机抽取的记录数量
	 * @return 从符合条件的结果中随机抽取的记录集合
	 */
	public List<?> findRandom(final double randomSize) {
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.search, getDialect());
		QueryResult result = dialectFactory.getRandomResult(sqlToyContext, queryExecute, sqlToyConfig,
				Double.valueOf(randomSize), getDataSource(sqlToyConfig));
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoRandomQuery(sqlToyContext, dialectFactory, queryExecute, Double.valueOf(randomSize));
		return result.getRows();
	}

	/**
	 * 进行分页查询
	 * 
	 * @param page 分页模型对象，提供页号(pageNo)、每页记录数(pageSize)等分页参数
	 * @return 分页查询结果，包含符合条件的记录总数及当页数据
	 */
	public Page<?> findPage(final Page page) {
		QueryExecutor queryExecute = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecute, SqlType.search, getDialect());
		Page<?> result;
		if (page.getSkipQueryCount()) {
			result = (Page<?>) dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecute, sqlToyConfig,
					page.getPageNo(), page.getPageSize(), getDataSource(sqlToyConfig)).getPageResult();
		} else {
			result = (Page<?>) dialectFactory.findPage(sqlToyContext, queryExecute, sqlToyConfig, page.getPageNo(),
					page.getPageSize(), page.getOverPageToFirst(), getDataSource(sqlToyConfig)).getPageResult();
		}
		// 产品化场景，适配其他数据库验证查询(仅仅在设置了redoDataSources时生效)
		CrossDbAdapter.redoPageQuery(sqlToyContext, dialectFactory, queryExecute, page);
		return result;
	}

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
		queryExecutor.timeout(queryTimeout);
		queryExecutor.lockWaitTimeout(lockWaitTimeout);
		queryExecutor.maxRows(maxRows);
		queryExecutor.fetchSize(fetchSize);
		return queryExecutor;
	}
}
