/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;

/**
 * @project sagacity-quickvo
 * @description 单个任务的配置
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:QuickModel.java,Revision:v1.0,Date:Apr 15, 2009 8:44:57 PM $
 */
public class QuickModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2416884401329432969L;
	private String entityName;
	private String entityPackage;
	private String voPackage;
	private String voSubstr;
	private String voName;
	private boolean voActive;
	private String voTemplate;

	private String dataSource;

	/**
	 * 是否支持swagger 注解
	 */
	private boolean swaggerApi = false;

	/**
	 * 作者，主要针对Dao层提供任务责任人
	 */
	private String author;

	/**
	 * 包含的表
	 */
	private String includeTables;

	/**
	 * 排除的表
	 */
	private String excludeTables;

	/**
	 * @return the voActive
	 */
	public boolean getVoActive() {
		return voActive;
	}

	/**
	 * @param voActive the voActive to set
	 */
	public void setVoActive(boolean voActive) {
		this.voActive = voActive;
	}

	public String getEntityName() {
		return entityName;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	public String getEntityPackage() {
		return entityPackage;
	}

	public void setEntityPackage(String entityPackage) {
		this.entityPackage = entityPackage;
	}

	public String getVoPackage() {
		return voPackage;
	}

	public void setVoPackage(String voPackage) {
		this.voPackage = voPackage;
	}

	public String getVoSubstr() {
		return voSubstr;
	}

	public void setVoSubstr(String voSubstr) {
		this.voSubstr = voSubstr;
	}

	public String getVoName() {
		return voName;
	}

	public void setVoName(String voName) {
		this.voName = voName;
	}

	public String getVoTemplate() {
		return voTemplate;
	}

	public void setVoTemplate(String voTemplate) {
		this.voTemplate = voTemplate;
	}

	/**
	 * @return the includeTables
	 */
	public String getIncludeTables() {
		return includeTables;
	}

	/**
	 * @param includeTables the includeTables to set
	 */
	public void setIncludeTables(String includeTables) {
		this.includeTables = includeTables;
	}

	/**
	 * @return the excludeTables
	 */
	public String getExcludeTables() {
		return excludeTables;
	}

	/**
	 * @param excludeTables the excludeTables to set
	 */
	public void setExcludeTables(String excludeTables) {
		this.excludeTables = excludeTables;
	}

	/**
	 * @return the author
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * @param author the author to set
	 */
	public void setAuthor(String author) {
		this.author = author;
	}

	/**
	 * @return the dataSource
	 */
	public String getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource the dataSource to set
	 */
	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the swaggerApi
	 */
	public boolean isSwaggerApi() {
		return swaggerApi;
	}

	/**
	 * @param swaggerApi the swaggerApi to set
	 */
	public void setSwaggerApi(boolean swaggerApi) {
		this.swaggerApi = swaggerApi;
	}

}
