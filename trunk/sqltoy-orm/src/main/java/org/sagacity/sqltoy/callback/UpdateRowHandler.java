/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.ResultSet;

/**
 * @project sagacity-sqltoy
 * @description 提供对lock记录的结果集合进行修改的的反调方式
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:UpdateRowHandler.java,Revision:v1.0,Date:2015年4月4日
 */
public abstract class UpdateRowHandler {
	/**
	 * @todo 行处理抽象方法接口定义
	 * @param rs
	 * @param index
	 * @throws Exception
	 */
	public abstract void updateRow(ResultSet rs, int index) throws Exception;
}
