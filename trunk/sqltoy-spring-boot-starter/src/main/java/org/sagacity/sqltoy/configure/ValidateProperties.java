package org.sagacity.sqltoy.configure;

import java.io.Serializable;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 
 * @author zhong
 * @version v1.0,Date:2020年11月23日
 */
@ConfigurationProperties(prefix = "sqltoy")
public class ValidateProperties implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4109470995442699797L;

	/**
	 * 指定sql.xml 文件路径,多个路径用逗号分隔
	 */
	private String sqlResourcesDir;

	/**
	 * 缓存翻译的配置文件
	 */
	private String translateConfig;

	/**
	 * 针对不同数据库函数进行转换,非必须属性,close 表示关闭
	 */
	private Object functionConverts;

	/**
	 * 数据库方言，一般无需设置
	 */
	private String dialect;

	/**
	 * 是否开启debug模式(默认为false)
	 */
	private Boolean debug;

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
	 * @return the functionConverts
	 */
	public Object getFunctionConverts() {
		return functionConverts;
	}

	/**
	 * @param functionConverts the functionConverts to set
	 */
	public void setFunctionConverts(Object functionConverts) {
		this.functionConverts = functionConverts;
	}

	/**
	 * @return the dialect
	 */
	public String getDialect() {
		return dialect;
	}

	/**
	 * @param dialect the dialect to set
	 */
	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the debug
	 */
	public Boolean getDebug() {
		return debug;
	}

	/**
	 * @param debug the debug to set
	 */
	public void setDebug(Boolean debug) {
		this.debug = debug;
	}
	
	
}
