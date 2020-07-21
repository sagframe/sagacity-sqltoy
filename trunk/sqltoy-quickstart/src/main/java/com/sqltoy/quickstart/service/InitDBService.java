/**
 * 
 */
package com.sqltoy.quickstart.service;

/**
 * @project sqltoy-showcase
 * @description 初始化数据库数据
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:InitDataService.java,Revision:v1.0,Date:2019年8月6日
 */
public interface InitDBService {
	/**
	 * @TODO 初始化数据库
	 * @param dataSqlFile
	 */
	public void initDatabase(String dataSqlFile);

	public Long initOrderData();
}
