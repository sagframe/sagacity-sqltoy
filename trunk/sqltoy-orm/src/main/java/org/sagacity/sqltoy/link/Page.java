/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.PaginationModel;

/**
 * @project sagacity-sqltoy
 * @description 分页查询
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Page.java,Revision:v1.0,Date:2017年10月9日
 * @See Query.findPage()
 */
@Deprecated
public class Page extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3406616717764570698L;

	/**
	 * 分页模型
	 */
	private PaginationModel pageModel = new PaginationModel();

	/**
	 * 分页查询语句
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
	 * 结果自定义处理器,一般不使用(作为特殊情况下的备用策略)
	 */
	@Deprecated
	private RowCallbackHandler handler;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Page(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Page pageModel(PaginationModel pageModel) {
		this.pageModel = pageModel;
		return this;
	}

	public Page pageNo(long pageNo) {
		this.pageModel.setPageNo(pageNo);
		return this;
	}

	public Page rowhandler(RowCallbackHandler handler) {
		this.handler = handler;
		return this;
	}

	public Page size(int pageSize) {
		this.pageModel.setPageSize(pageSize);
		return this;
	}

	public Page names(String... names) {
		this.names = names;
		return this;
	}

	public Page values(Object... values) {
		this.values = values;
		return this;
	}

	public Page entity(Serializable entityVO) {
		this.entity = entityVO;
		return this;
	}

	public Page resultType(Class<?> resultType) {
		this.resultType = resultType;
		return this;
	}

	public Page dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Page sql(String sql) {
		this.sql = sql;
		return this;
	}

	/**
	 * @todo 提交执行,并返回结果
	 * @return
	 */
	public PaginationModel submit() {
		if (sql == null)
			throw new IllegalArgumentException("pagination operate sql is null!");
		QueryExecutor queryExecutor = null;
		if (entity != null) {
			queryExecutor = new QueryExecutor(sql, entity);
		} else {
			queryExecutor = new QueryExecutor(sql, names, values);
		}
		if (resultType != null) {
			queryExecutor.resultType(resultType);
		}
		if (handler != null) {
			queryExecutor.rowCallbackHandler(handler);
		}
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getSql(), SqlType.search);
		return dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig, pageModel.getPageNo(),
				pageModel.getPageSize(), getDataSource(sqlToyConfig)).getPageResult();
	}

}
