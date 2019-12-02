package org.sagacity.sqltoy.configure;

import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sqltoy.context.config")
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
	private List<String> functionConverts;

	/**
	 * SqltoyEntity包路径,非必须属性
	 */
	private String[] packagesToScan;

	private List<ElasticEndpoint> elasticConfigs = new ArrayList<ElasticEndpoint>();

	/**
	 * 缓存管理器
	 */
	private String cacheManager;

	private String debug;
	private int batchSize;

	/**
	 * 统一字段处理器
	 */
	private String unifyFieldsHandler;

	/**
	 * @return the sqlResourcesDir
	 */
	public String getSqlResourcesDir() {
		return sqlResourcesDir;
	}

	/**
	 * @param sqlResourcesDir
	 *            the sqlResourcesDir to set
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
	 * @param translateConfig
	 *            the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		this.translateConfig = translateConfig;
	}

	/**
	 * @return the cacheManager
	 */
	public String getCacheManager() {
		return cacheManager;
	}

	/**
	 * @param cacheManager
	 *            the cacheManager to set
	 */
	public void setCacheManager(String cacheManager) {
		this.cacheManager = cacheManager;
	}

	/**
	 * @return the debug
	 */
	public String getDebug() {
		return debug;
	}

	/**
	 * @param debug
	 *            the debug to set
	 */
	public void setDebug(String debug) {
		this.debug = debug;
	}

	/**
	 * @return the batchSize
	 */
	public int getBatchSize() {
		return batchSize;
	}

	/**
	 * @param batchSize
	 *            the batchSize to set
	 */
	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	/**
	 * @return the functionConverts
	 */
	public List<String> getFunctionConverts() {
		return functionConverts;
	}

	/**
	 * @param functionConverts
	 *            the functionConverts to set
	 */
	public void setFunctionConverts(List<String> functionConverts) {
		this.functionConverts = functionConverts;
	}

	/**
	 * @return the packagesToScan
	 */
	public String[] getPackagesToScan() {
		return packagesToScan;
	}

	/**
	 * @param packagesToScan
	 *            the packagesToScan to set
	 */
	public void setPackagesToScan(String[] packagesToScan) {
		this.packagesToScan = packagesToScan;
	}

	/**
	 * @return the unifyFieldsHandler
	 */
	public IUnifyFieldsHandler getUnifyFieldsHandler() {
		try {
			if (StringUtil.isNotBlank(unifyFieldsHandler))
				return (IUnifyFieldsHandler) Class.forName(unifyFieldsHandler).getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @param unifyFieldsHandler
	 *            the unifyFieldsHandler to set
	 */
	public void setUnifyFieldsHandler(String unifyFieldsHandler) {
		this.unifyFieldsHandler = unifyFieldsHandler;
	}

	/**
	 * @return the elasticConfigs
	 */
	public List<ElasticEndpoint> getElasticConfigs() {
		return elasticConfigs;
	}

	/**
	 * @param elasticConfigs
	 *            the elasticConfigs to set
	 */
	public void setElasticConfigs(List<ElasticEndpoint> elasticConfigs) {
		this.elasticConfigs = elasticConfigs;
	}
}
