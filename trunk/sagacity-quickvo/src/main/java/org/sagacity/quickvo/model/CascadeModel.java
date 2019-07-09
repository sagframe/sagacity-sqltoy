/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;

/**
 * @project sagacity-quickvo
 * @description <p>级联关系模型</p>
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:CascadeModel.java,Revision:v1.0,Date:2016年12月1日
 */
public class CascadeModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5784993887439945140L;

	/**
	 * 级联的字表名称，正则表达式
	 */
	private String tableName;

	/**
	 * 主表删除子表是否删除
	 */
	private boolean delete = false;

	/**
	 * 主表保存，子表是否保存
	 */
	private boolean save = true;

	/**
	 * 主表加载，子表是否自动加载
	 */
	private String load;
	
	/**
	 * 修改主表时对于字表级联保存时是否先做删除操作
	 */
	private String updateSql;

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @param tableName the tableName to set
	 */
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	/**
	 * @return the delete
	 */
	public boolean isDelete() {
		return delete;
	}

	/**
	 * @param delete the delete to set
	 */
	public void setDelete(boolean delete) {
		this.delete = delete;
	}

	/**
	 * @return the save
	 */
	public boolean isSave() {
		return save;
	}

	/**
	 * @param save the save to set
	 */
	public void setSave(boolean save) {
		this.save = save;
	}

	

	/**
	 * @return the load
	 */
	public String getLoad() {
		return load;
	}

	/**
	 * @param load the load to set
	 */
	public void setLoad(String load) {
		this.load = load;
	}

	/**
	 * @return the updateSql
	 */
	public String getUpdateSql() {
		return updateSql;
	}

	/**
	 * @param updateSql the updateSql to set
	 */
	public void setUpdateSql(String updateSql) {
		this.updateSql = updateSql;
	}


}
