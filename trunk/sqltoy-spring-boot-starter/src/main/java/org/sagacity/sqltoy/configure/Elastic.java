/**
 * 
 */
package org.sagacity.sqltoy.configure;

import java.io.Serializable;
import java.util.List;

/**
 * @author zhongxuchen
 * @version v1.0,Date:2020年2月20日
 */
public class Elastic implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7130685535362259100L;

	private String defaultId;

	private List<ElasticConfig> endpoints;

	public String getDefaultId() {
		return defaultId;
	}

	public void setDefaultId(String defaultId) {
		this.defaultId = defaultId;
	}

	public List<ElasticConfig> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(List<ElasticConfig> endpoints) {
		this.endpoints = endpoints;
	}
	
	

}
