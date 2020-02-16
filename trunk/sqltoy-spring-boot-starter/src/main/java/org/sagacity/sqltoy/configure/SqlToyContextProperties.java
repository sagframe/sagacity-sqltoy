package org.sagacity.sqltoy.configure;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.sqltoy")
public class SqlToyContextProperties {

	/**
	 * 指定sql.xml 文件的路径实现目录的递归查找,非必须属性
	 */
	private String sqlResourcesDir;

	/**
	 * 
	 */
	private String translateConfig;

	/**
	 * 针对不同数据库函数进行转换,非必须属性
	 */
	private Object functionConverts;

	/**
	 * 数据库方言
	 */
	private String dialect;

	/**
	 * SqltoyEntity包路径,非必须属性
	 */
	private String[] packagesToScan;

	private Elastic elastic;

	private String debug;

	private Integer batchSize;

	private Integer pageSizeLimit;

	/**
	 * 统一字段处理器
	 */
	private String unifyFieldsHandler;

	private Map<String, String> dialectProperties;

	/**
	 * @return the sqlResourcesDir
	 */
	public String getSqlResourcesDir() {
		return sqlResourcesDir;
	}

	/**
	 * @param sqlResourcesDir the sqlResourcesDir to set
	 */
	public void setSqlResourcesDir(String sqlResourcesDir) {
		this.sqlResourcesDir = sqlResourcesDir;
	}

	/**
	 * @return the translateConfig
	 */
	public String getTranslateConfig() {
		return translateConfig;
	}

	/**
	 * @param translateConfig the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		this.translateConfig = translateConfig;
	}

	/**
	 * @return the debug
	 */
	public String getDebug() {
		return debug;
	}

	/**
	 * @param debug the debug to set
	 */
	public void setDebug(String debug) {
		this.debug = debug;
	}

	/**
	 * @return the batchSize
	 */
	public Integer getBatchSize() {
		return batchSize;
	}

	/**
	 * @param batchSize the batchSize to set
	 */
	public void setBatchSize(Integer batchSize) {
		this.batchSize = batchSize;
	}

	public Object getFunctionConverts() {
		return functionConverts;
	}

	public void setFunctionConverts(Object functionConverts) {
		this.functionConverts = functionConverts;
	}

	/**
	 * @return the packagesToScan
	 */
	public String[] getPackagesToScan() {
		return packagesToScan;
	}

	/**
	 * @param packagesToScan the packagesToScan to set
	 */
	public void setPackagesToScan(String[] packagesToScan) {
		this.packagesToScan = packagesToScan;
	}

	/**
	 * @return the unifyFieldsHandler
	 */
	public String getUnifyFieldsHandler() {
		return this.unifyFieldsHandler;
	}

	/**
	 * @param unifyFieldsHandler the unifyFieldsHandler to set
	 */
	public void setUnifyFieldsHandler(String unifyFieldsHandler) {
		this.unifyFieldsHandler = unifyFieldsHandler;
	}

	public Elastic getElastic() {
		return elastic;
	}

	public void setElastic(Elastic elastic) {
		this.elastic = elastic;
	}

	public String getDialect() {
		return dialect;
	}

	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	public Map<String, String> getDialectProperties() {
		return dialectProperties;
	}

	public void setDialectProperties(Map<String, String> dialectProperties) {
		this.dialectProperties = dialectProperties;
	}

	public Integer getPageSizeLimit() {
		return pageSizeLimit;
	}

	public void setPageSizeLimit(Integer pageSizeLimit) {
		this.pageSizeLimit = pageSizeLimit;
	}

}
