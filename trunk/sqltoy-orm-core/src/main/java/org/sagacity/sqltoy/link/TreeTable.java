package org.sagacity.sqltoy.link;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.TreeTableModel;

/**
 * @project sagacity-sqltoy
 * @description 树形表封装操作
 * @author zhongxuchen
 * @version v1.0,Date:2017-10-09
 */
public class TreeTable extends BaseLink {
	private static final long serialVersionUID = 2471677449407100687L;
	/**
	 * 树结构表模型
	 */
	private TreeTableModel treeModel;

	/**
	 * @param sqlToyContext sqltoy全局上下文对象
	 * @param dataSource    树形表操作绑定的数据源，null表示使用默认数据源
	 */
	public TreeTable(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public TreeTable dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public TreeTable treeModel(TreeTableModel treeModel) {
		this.treeModel = treeModel;
		return this;
	}

	/**
	 * 提交执行并返回是否成功
	 * 
	 * @return 树形表层级字段包装成功返回true，失败返回false
	 */
	public boolean submit() {
		if (treeModel == null) {
			throw new IllegalArgumentException("treeTable wrap:treeModel is null!");
		}
		return dialectFactory.wrapTreeTableRoute(sqlToyContext, treeModel, getDataSource(null));
	}
}
