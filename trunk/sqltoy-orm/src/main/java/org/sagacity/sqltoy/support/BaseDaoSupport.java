/**
 * 
 */
package org.sagacity.sqltoy.support;

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

/**
 * @project sagacity-sqltoy
 * @description 提供一个集成SqlToyDaoSupport的功能，同时也提供linkDaoSupport的功能的合集
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:BaseDaoSupport.java,Revision:v1.0,Date:2017年10月30日
 */
public class BaseDaoSupport extends SqlToyDaoSupport {
	// 修改模式
	protected SaveMode UPDATE = SaveMode.UPDATE;

	// 忽视已经存在的记录
	protected SaveMode IGNORE = SaveMode.IGNORE;

	/**
	 * @todo 对象加载操作集合
	 * @return
	 */
	protected Load load() {
		return new Load(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 删除操作集合
	 * @return
	 */
	protected Delete delete() {
		return new Delete(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 修改操作集合
	 * @return
	 */
	protected Update update() {
		return new Update(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 保存操作集合
	 * @return
	 */
	protected Save save() {
		return new Save(sqlToyContext, getDataSource(dataSource));
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
	 * @todo 存储过程操作集合
	 * @return
	 */
	protected Store store() {
		return new Store(sqlToyContext, getDataSource(dataSource));
	}

	/**
	 * @todo 唯一性验证操作集合
	 * @return
	 */
	protected Unique unique() {
		return new Unique(sqlToyContext, getDataSource(dataSource));
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
}
