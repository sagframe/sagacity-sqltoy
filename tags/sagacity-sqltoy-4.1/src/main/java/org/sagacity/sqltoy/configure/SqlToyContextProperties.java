package org.sagacity.sqltoy.configure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.cache.TranslateCacheManager;
import org.sagacity.sqltoy.config.model.ElasticConfig;
import org.sagacity.sqltoy.plugin.IUnifyFieldsHandler;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @project sagacity-sqltoy4.1
 * @description 基于springboot 的sqltoy配置属性加载模式
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyContextProperties.java,Revision:v1.0,Date:2018年3月7日
 */
@ConfigurationProperties(prefix = "sqltoy.context")
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

	/**
	 * elasticsearch的连接配置
	 */
	private List<ElasticConfig> elasticConfigs = new ArrayList<ElasticConfig>();

	/**
	 * 缓存管理器配置
	 */
	private List<TranslateCacheManager> translateCacheManagers = new ArrayList<TranslateCacheManager>();

	/**
	 * 缓存管理器
	 */
	private String cacheManager;

	/**
	 * 是否debug模式
	 */
	private boolean debug = false;

	/**
	 * 批量操作时每批数量
	 */
	private int batchSize = 50;

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
	public boolean getDebug() {
		return debug;
	}

	/**
	 * @param debug
	 *            the debug to set
	 */
	public void setDebug(boolean debug) {
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
				return (IUnifyFieldsHandler) Class.forName(unifyFieldsHandler).newInstance();
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
	public List<ElasticConfig> getElasticConfigs() {
		return elasticConfigs;
	}

	/**
	 * @param elasticConfigs
	 *            the elasticConfigs to set
	 */
	public void setElasticConfigs(List<ElasticConfig> elasticConfigs) {
		this.elasticConfigs = elasticConfigs;
	}

	/**
	 * @return the translateCacheManagers
	 */
	public HashMap<String, TranslateCacheManager> getTranslateCacheManagers() {
		HashMap<String, TranslateCacheManager> trans = new HashMap<String, TranslateCacheManager>();
		if (translateCacheManagers == null || translateCacheManagers.isEmpty()) {
			for (TranslateCacheManager translateCacheManager : translateCacheManagers) {
				trans.put(translateCacheManager.getName(), translateCacheManager);
			}
		}
		return trans;
	}

	/**
	 * @param translateCacheManagers
	 *            the translateCacheManagers to set
	 */
	public void setTranslateCacheManagers(List<TranslateCacheManager> translateCacheManagers) {
		this.translateCacheManagers = translateCacheManagers;
	}

}
