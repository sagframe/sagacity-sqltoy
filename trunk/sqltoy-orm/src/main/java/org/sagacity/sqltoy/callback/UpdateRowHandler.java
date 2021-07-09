/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.ResultSet;

/**
 * @project sagacity-sqltoy
 * @description 提供对lock记录的结果集合进行修改的的反调方式,用于updateFetch
 * @author zhongxuchen
 * @version v1.0,Date:2015年4月4日
 */
@FunctionalInterface
public interface UpdateRowHandler {
	/**
	 * @todo 行处理抽象方法接口定义，用于updateFetch
	 * @param rs
	 * @param index
	 * @throws Exception
	 */
	public void updateRow(ResultSet rs, int index) throws Exception;
}
