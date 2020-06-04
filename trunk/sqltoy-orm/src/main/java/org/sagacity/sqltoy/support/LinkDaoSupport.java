/**
 * 
 */
package org.sagacity.sqltoy.support;

import java.sql.Connection;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.link.Batch;
import org.sagacity.sqltoy.link.Delete;
import org.sagacity.sqltoy.link.Elastic;
import org.sagacity.sqltoy.link.Execute;
import org.sagacity.sqltoy.link.Load;
import org.sagacity.sqltoy.link.Mongo;
import org.sagacity.sqltoy.link.Page;
import org.sagacity.sqltoy.link.Query;
import org.sagacity.sqltoy.link.Save;
import org.sagacity.sqltoy.link.Store;
import org.sagacity.sqltoy.link.TreeTable;
import org.sagacity.sqltoy.link.Unique;
import org.sagacity.sqltoy.link.Update;
import org.sagacity.sqltoy.model.SaveMode;
import org.sagacity.sqltoy.utils.BeanPropsWrapper;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @project sagacity-sqltoy
 * @description 全部采用链式编程模式,将不同类型的操作区分开，同时保持support代码的简洁和可读性
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:LinkDaoSupport.java,Revision:v1.0,Date:2017年10月9日
 */
public class LinkDaoSupport {

	/**
	 * 数据源
	 */
	protected DataSource dataSource;

	/**
	 * sqlToy上下文定义
	 */
	protected SqlToyContext sqlToyContext;

	protected SaveMode UPDATE = SaveMode.UPDATE;

	protected SaveMode IGNORE = SaveMode.IGNORE;

	/**
	 * @param sqlToyContext the sqlToyContext to set
	 */
	@Autowired
	@Qualifier(value = "sqlToyContext")
	public void setSqlToyContext(SqlToyContext sqlToyContext) {
		this.sqlToyContext = sqlToyContext;
	}

	/**
	 * @param dataSource the dataSource to set
	 */
	@Autowired(required = false)
	@Qualifier(value = "dataSource")
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @todo 对象加载操作集合
	 * @return
	 */
	protected Load load() {
		return new Load(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 保存操作集合
	 * @return
	 */
	protected Save save() {
		return new Save(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 修改操作集合
	 * @return
	 */
	protected Update update() {
		return new Update(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 删除操作集合
	 * @return
	 */
	protected Delete delete() {
		return new Delete(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 查询操作集合
	 * @return
	 */
	protected Query query() {
		return new Query(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 分页操作集合
	 * @return
	 */
	@Deprecated
	protected Page page() {
		return new Page(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 唯一性验证操作集合
	 * @return
	 */
	protected Unique unique() {
		return new Unique(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 存储过程操作集合
	 * @return
	 */
	protected Store store() {
		return new Store(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 树形表结构封装操作集合
	 * @return
	 */
	protected TreeTable treeTable() {
		return new TreeTable(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo sql语句直接执行修改数据库操作集合
	 * @return
	 */
	protected Execute execute() {
		return new Execute(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 批量执行操作集合
	 * @return
	 */
	protected Batch batch() {
		return new Batch(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 提供基于ES的查询(仅针对查询部分)
	 * @return
	 */
	protected Elastic elastic() {
		return new Elastic(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 提供基于mongo的查询(仅针对查询部分)
	 * @return
	 */
	protected Mongo mongo() {
		return new Mongo(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 协助完成对对象集合的属性批量赋予相应数值
	 * @param names
	 * @return
	 */
	protected BeanPropsWrapper wrapBeanProps(String... names) {
		return new BeanPropsWrapper(names);
	}

	/**
	 * @todo <b>手工提交数据库操作，只提供当前DataSource提交</b>
	 * @throws Exception
	 */
	protected void flush() {
		flush(null);
	}

	/**
	 * @todo <b>手工提交数据库操作，只提供当前DataSource提交</b>
	 * @param dataSource
	 */
	protected void flush(DataSource dataSource) {
		DataSourceUtils.processDataSource(sqlToyContext, getDataSource(dataSource), new DataSourceCallbackHandler() {
			public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
				if (!conn.isClosed())
					conn.commit();
			}
		});
	}

	/**
	 * @todo 获取数据源,如果参数dataSource为null则返回默认的dataSource
	 * @param dataSource
	 * @return
	 */
	public DataSource getDataSource(DataSource dataSource) {
		DataSource result = dataSource;
		if (null == result)
			result = this.dataSource;
		if (null == result)
			result = sqlToyContext.getDefaultDataSource();
		return result;
	}
}
