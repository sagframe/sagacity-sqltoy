/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @project sagacity-quickvo
 * @description 任务配置数据模型
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:ConfigModel.java,Revision:v1.0,Date:2010-7-21 下午02:19:13 $
 */
@SuppressWarnings("rawtypes")
public class ConfigModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1745793126948000038L;

	/**
	 * 输出路径
	 */
	private String targetDir;

	/**
	 * 抽象vo的路径
	 */
	private String abstractPath = "base";

	/**
	 * 编码格式
	 */
	private String encoding = "UTF-8";

	/**
	 * 类型匹配
	 */
	private List typeMapping;

	/**
	 * 任务信息
	 */
	private List tasks;

	/**
	 * 包含的表
	 */
	private String[] tableIncludes;

	/**
	 * 排除的表
	 */
	private String[] tableExcludes;

	/**
	 * 级联设置
	 */
	private HashMap<String, List<CascadeModel>> cascadeConfig = new HashMap<String, List<CascadeModel>>();

	/**
	 * 主键生成策略
	 */
	private List<PrimaryKeyStrategy> pkGeneratorStrategyList = new ArrayList<PrimaryKeyStrategy>();

	/**
	 * 业务主键id策略配置
	 */
	private HashMap<String, BusinessIdConfig> buisnessIdsMap = new HashMap<String, BusinessIdConfig>();

	/**
	 * @return the targetDir
	 */
	public String getTargetDir() {
		return targetDir;
	}

	/**
	 * @param targetDir
	 *            the targetDir to set
	 */
	public void setTargetDir(String targetDir) {
		this.targetDir = targetDir;
	}

	/**
	 * @return the encoding
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * @param encoding
	 *            the encoding to set
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	/**
	 * @return the typeMapping
	 */
	public List getTypeMapping() {
		return typeMapping;
	}

	/**
	 * @param typeMapping
	 *            the typeMapping to set
	 */
	public void setTypeMapping(List typeMapping) {
		this.typeMapping = typeMapping;
	}

	/**
	 * @return the tasks
	 */
	public List getTasks() {
		return tasks;
	}

	/**
	 * @param tasks
	 *            the tasks to set
	 */
	public void setTasks(List tasks) {
		this.tasks = tasks;
	}

	/**
	 * @return the tableIncludes
	 */
	public String[] getTableIncludes() {
		return tableIncludes;
	}

	/**
	 * @param tableIncludes
	 *            the tableIncludes to set
	 */
	public void setTableIncludes(String[] tableIncludes) {
		this.tableIncludes = tableIncludes;
	}

	/**
	 * @return the tableExcludes
	 */
	public String[] getTableExcludes() {
		return tableExcludes;
	}

	/**
	 * @param tableExcludes
	 *            the tableExcludes to set
	 */
	public void setTableExcludes(String[] tableExcludes) {
		this.tableExcludes = tableExcludes;
	}

	/**
	 * @return the abstractPath
	 */
	public String getAbstractPath() {
		return abstractPath;
	}

	/**
	 * @param abstractPath
	 *            the abstractPath to set
	 */
	public void setAbstractPath(String abstractPath) {
		this.abstractPath = abstractPath;
	}

	/**
	 * @return the pkGeneratorStrategyMap
	 */
	public List<PrimaryKeyStrategy> getPkGeneratorStrategy() {
		return pkGeneratorStrategyList;
	}

	/**
	 * @param pkGeneratorStrategyMap
	 *            the pkGeneratorStrategyMap to set
	 */
	public void addPkGeneratorStrategy(PrimaryKeyStrategy primaryKeyStrategy) {
		this.pkGeneratorStrategyList.add(primaryKeyStrategy);
	}

	/**
	 * @return the cascadeConfig
	 */
	public List<CascadeModel> getCascadeConfig(String mainTable) {
		List<CascadeModel> result = cascadeConfig.get(mainTable.toLowerCase());
		// 兼容老的cascade不按照主表进行配置模式
		if (result == null)
			result = cascadeConfig.get("quickvo_temp_maintable0");
		return result;
	}

	public void addCascadeConfig(String mainTable, List<CascadeModel> cascadeModels) {
		this.cascadeConfig.put(mainTable.toLowerCase(), cascadeModels);
	}

	public void addBusinessId(BusinessIdConfig businessIdConfig) {
		this.buisnessIdsMap.put(businessIdConfig.getTableName().toLowerCase(), businessIdConfig);
	}

	public BusinessIdConfig getBusinessId(String tableName) {
		return this.buisnessIdsMap.get(tableName.toLowerCase());
	}
}
