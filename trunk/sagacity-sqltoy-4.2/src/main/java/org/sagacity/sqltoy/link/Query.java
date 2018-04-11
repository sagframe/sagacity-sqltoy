/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.QueryResult;

/**
 * @project sagacity-sqltoy
 * @description 普通查询
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Query.java,Revision:v1.0,Date:2017年10月9日
 */
public class Query extends BaseLink {

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
	private Serializable entity;

	/**
	 * 返回结果类型
	 */
	private Class resultType;

	/**
	 * 结果自定义处理器,一般不使用(作为特殊情况下的备用策略)
	 */
	@Deprecated
	private RowCallbackHandler handler;

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
	private boolean humpMapLabel = true;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Query(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	@Deprecated
	public Query fetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
		return this;
	}

	@Deprecated
	public Query maxRows(int maxRows) {
		this.maxRows = maxRows;
		return this;
	}

	public Query rowhandle(RowCallbackHandler handler) {
		this.handler = handler;
		return this;
	}

	/**
	 * @todo 设置sql语句
	 * @param sql
	 * @return
	 */
	public Query sql(String sql) {
		this.sql = sql;
		return this;
	}

	public Query humpMapLabel(boolean isHump) {
		this.humpMapLabel = isHump;
		return this;
	}

	/**
	 * @todo sql语句中的参数名称
	 * @param names
	 * @return
	 */
	public Query names(String... names) {
		this.names = names;
		return this;
	}

	/**
	 * @todo sql语句中的参数对应的值
	 * @param values
	 * @return
	 */
	public Query values(Object... values) {
		this.values = values;
		return this;
	}

	/**
	 * @todo 通过对象传递参数(对象属性名跟sql中的参数别名对应)
	 * @param entityVO
	 * @return
	 */
	public Query entity(Serializable entityVO) {
		this.entity = entityVO;
		return this;
	}

	public Query resultType(Class resultType) {
		this.resultType = resultType;
		return this;
	}

	public Query dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	/**
	 * @todo 获取单值
	 * @return
	 * @throws Exception
	 */
	public Object getValue() throws Exception {
		QueryExecutor queryExecutor = new QueryExecutor(sql, names, values);
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecutor, dataSource);
		List rows = result.getRows();
		if (rows != null && rows.size() > 0)
			return ((List) rows.get(0)).get(0);
		return null;
	}

	/**
	 * @todo 获取一条记录
	 * @return
	 * @throws Exception
	 */
	public Object getOne() throws Exception {
		QueryExecutor queryExecute = build();
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecute, dataSource);
		List rows = result.getRows();
		if (rows != null && rows.size() > 0)
			return rows.get(0);
		return null;
	}

	/**
	 * @todo 查询结果
	 * @return
	 * @throws Exception
	 */
	public Long count() throws Exception {
		QueryExecutor queryExecute = build();
		return dialectFactory.getCountBySql(sqlToyContext, queryExecute, dataSource);
	}

	/**
	 * @todo 查询结果
	 * @return
	 * @throws Exception
	 */
	public List find() throws Exception {
		QueryExecutor queryExecute = build();
		QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecute, dataSource);
		return result.getRows();
	}

	/**
	 * @todo 取前多少条记录
	 * @param topSize
	 * @return
	 * @throws Exception
	 */
	public List findTop(final double topSize) throws Exception {
		QueryExecutor queryExecute = build();
		QueryResult result = dialectFactory.findTop(sqlToyContext, queryExecute, topSize, dataSource);
		return result.getRows();
	}

	/**
	 * @todo 随机取记录
	 * @param randomSize
	 * @return
	 * @throws Exception
	 */
	public List findRandom(final double randomSize) throws Exception {
		QueryExecutor queryExecute = build();
		QueryResult result = dialectFactory.getRandomResult(sqlToyContext, queryExecute, new Double(randomSize),
				dataSource);
		return result.getRows();
	}

	private QueryExecutor build() throws Exception {
		QueryExecutor queryExecutor = null;
		if (entity != null)
			queryExecutor = new QueryExecutor(sql, entity);
		else
			queryExecutor = new QueryExecutor(sql, names, values);
		if (resultType != null)
			queryExecutor.resultType(resultType);
		if (handler != null)
			queryExecutor.rowCallbackHandler(handler);
		queryExecutor.humpMapLabel(humpMapLabel);
		queryExecutor.maxRows(maxRows);
		queryExecutor.fetchSize(fetchSize);
		return queryExecutor;
	}
}
