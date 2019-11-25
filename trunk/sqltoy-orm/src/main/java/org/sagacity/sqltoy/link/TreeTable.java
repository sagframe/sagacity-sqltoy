/**
 * 
 */
package org.sagacity.sqltoy.link;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.model.TreeTableModel;

/**
 * @project sagacity-sqltoy
 * @description 树形表封装操作
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TreeTable.java,Revision:v1.0,Date:2017年10月9日
 */
public class TreeTable extends BaseLink {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2471677449407100687L;
	/**
	 * 树结构表模型
	 */
	private TreeTableModel treeModel;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public TreeTable(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public TreeTable dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		return this;
	}

	public TreeTable treeModel(TreeTableModel treeModel) {
		this.treeModel = treeModel;
		return this;
	}

	/**
	 * @todo 提交执行并返回是否成功
	 * @return
	 */
	public boolean submit() {
		if (treeModel == null)
			throw new IllegalArgumentException("treeTable wrap:treeModel is null!");
		return dialectFactory.wrapTreeTableRoute(sqlToyContext, treeModel, dataSource);
	}
}
