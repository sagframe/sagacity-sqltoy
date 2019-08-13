/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description 行结果集反调处理接口
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:RowCallbackHandler.java,Revision:v1.0,Date:2008-12-9
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public abstract class RowCallbackHandler {
	/**
	 * 结果集
	 */
	private List result = new ArrayList();

	/**
	 * @todo 行处理抽象方法接口定义
	 * @param rs
	 * @param index
	 * @throws SQLException
	 */
	public abstract void processRow(ResultSet rs, int index) throws SQLException;

	/**
	 * @todo 返回结果
	 * @return
	 */
	public List getResult() {
		return result;
	}

	/**
	 * @todo 加入行数据
	 * @param rowData
	 */
	public void addRow(Object rowData) {
		result.add(rowData);
	}
}
