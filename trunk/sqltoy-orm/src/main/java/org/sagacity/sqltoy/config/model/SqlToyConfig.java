/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.Dialect;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 单个sql被解析后的模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyConfig.java,Revision:v1.0,Date:2014年12月9日
 */
@SuppressWarnings({ "rawtypes" })
public class SqlToyConfig implements Serializable, java.lang.Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3168222164418634488L;

	/**
	 * 数据库方言
	 */
	private String dialect;

	/**
	 * 查询参数条件过滤器
	 */
	private ParamFilterModel[] filters = null;

	/**
	 * 翻译器
	 */
	private HashMap<String, SqlTranslate> translateMap = null;

	/**
	 * 安全脱敏配置
	 */
	private SecureMask[] secureMasks = null;

	/**
	 * 格式化定义
	 */
	private FormatModel[] formatModels = null;

	/**
	 * sql特定使用的dataSource(一般在项目跨多个数据库且是查询语句时使用)
	 */
	private String dataSource;

	/**
	 * 基于dataSource的sharding策略
	 */
	private String dataSourceShardingStragety = null;

	/**
	 * 基于dataSource的sharding参数
	 */
	private String[] dataSourceShardingParams = null;

	private String[] dataSourceShardingParamsAlias = null;

	/**
	 * 自定义策略辨别值
	 */
	private String dataSourceShardingStrategyValue = null;

	private String[] tableShardingParams = null;

	/**
	 * 基于表的sharding配置
	 */
	private List<QueryShardingModel> tablesShardings = null;

	/**
	 * 对列进行拼接方式定义
	 */
	private LinkModel linkModel = null;

	/**
	 * sqlId
	 */
	private String id;

	/**
	 * 默认为查询语句
	 */
	private SqlType sqlType;

	/**
	 * sql内容
	 */
	private String sql;

	/**
	 * 针对多方言存放sql
	 */
	private ConcurrentHashMap<String, String> dialectSqlMap = new ConcurrentHashMap<String, String>();

	/**
	 * 快速分页部分的sql
	 */
	private String fastSql;

	/**
	 * 快速分页用到的with as 部分sql
	 */
	private String fastWithSql;

	/**
	 * 快速分页前部分sql
	 */
	private String fastPreSql;

	/**
	 * 快速分页后部分sql
	 */
	private String fastTailSql;

	/**
	 * 针对极端性能要求的查询，提供独立的取总记录数的sql
	 */
	private String countSql;

	/**
	 * 是否union all形式的分页查询
	 */
	private boolean isUnionAllCount = false;

	/**
	 * 参数名称,按照参数出现的顺序排列
	 */
	private String[] paramsName;

	/**
	 * 缓存条件参数名称
	 */
	private List<String> cacheArgNames = new ArrayList<String>();

	/**
	 * 是否有分页
	 */
	private boolean hasFast = false;

	/**
	 * 是否存在with查询
	 */
	private boolean hasWith = false;

	/**
	 * 判定sql是否有union 语法
	 */
	private boolean hasUnion = false;

	/**
	 * @return the hasUnion
	 */
	public boolean isHasUnion() {
		return hasUnion;
	}

	/**
	 * @param hasUnion the hasUnion to set
	 */
	public void setHasUnion(boolean hasUnion) {
		this.hasUnion = hasUnion;
	}

	/**
	 * 快速分页部分的sql引用with as表名的位置，即用到第几个with
	 */
	private int fastWithIndex = -1;
	// <page-optimize alive-max="100" alive-seconds="90"/>

	/**
	 * 是否分页优化
	 */
	private boolean pageOptimize = false;

	/**
	 * 100个不同条件查询
	 */
	private int pageAliveMax = 100;

	/**
	 * 1.5分钟
	 */
	private int pageAliveSeconds = 90;

	/**
	 * debug模式下是否打印，通过sql注释中增加#not_print#或 #not_debug#进行关闭
	 */
	private boolean showSql = true;

	/**
	 * 忽视空集合
	 */
	private boolean ignoreEmpty = false;

	/**
	 * @return the ignoreEmpty
	 */
	public boolean isIgnoreEmpty() {
		return ignoreEmpty;
	}

	/**
	 * @param ignoreEmpty the ignoreEmpty to set
	 */
	public void setIgnoreEmpty(boolean ignoreEmpty) {
		this.ignoreEmpty = ignoreEmpty;
	}

	/**
	 * 查询结果处理器
	 */
	private List resultProcessor;

	private NoSqlConfigModel noSqlConfigModel;

	public SqlToyConfig(String dialect) {
		this.dialect = dialect;
	}

	public SqlToyConfig(String id, String sql) {
		this.id = id;
		this.sql = sql;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	@Deprecated
	public String getSql() {
		return getDialectSql("sql", sql, dialect);
	}

	/**
	 * @return the sql
	 */
	public String getSql(String dialect) {
		return getDialectSql("sql", sql, dialect);
	}

	/**
	 * @param sql the sql to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
	}

	/**
	 * @return the hasFast
	 */
	public boolean isHasFast() {
		return hasFast;
	}

	/**
	 * @param hasFastPage the hasFastPage to set
	 */
	public void setHasFast(boolean hasFast) {
		this.hasFast = hasFast;
	}

	/**
	 * @return the isUnionAllCount
	 */
	public boolean isUnionAllCount() {
		return isUnionAllCount;
	}

	/**
	 * @param isUnionAllCount the isUnionAllCount to set
	 */
	public void setUnionAllCount(boolean isUnionAllCount) {
		this.isUnionAllCount = isUnionAllCount;
	}

	/**
	 * @return the paramsName
	 */
	public String[] getParamsName() {
		return paramsName;
	}

	/**
	 * @param paramsName the paramsName to set
	 */
	public void setParamsName(String[] paramsName) {
		this.paramsName = paramsName;
	}

	/**
	 * @param filterMap the filterMap to set
	 */
	public void setFilters(ParamFilterModel[] filters) {
		this.filters = filters;
	}

	public ParamFilterModel[] getFilters() {
		return this.filters;
	}

	/**
	 * @return the translateMap
	 */
	public HashMap<String, SqlTranslate> getTranslateMap() {
		return translateMap;
	}

	/**
	 * @param translateMap the translateMap to set
	 */
	public void setTranslateMap(HashMap<String, SqlTranslate> translateMap) {
		this.translateMap = translateMap;
	}

	/**
	 * @return the linkModel
	 */
	public LinkModel getLinkModel() {
		return linkModel;
	}

	/**
	 * @param linkModel the linkModel to set
	 */
	public void setLinkModel(LinkModel linkModel) {
		this.linkModel = linkModel;
	}

	/**
	 * @return the resultProcessor
	 */
	public List getResultProcessor() {
		return resultProcessor;
	}

	/**
	 * @param resultProcessor the resultProcessor to set
	 */
	public void setResultProcessor(List resultProcessor) {
		this.resultProcessor = resultProcessor;
	}

	/**
	 * @return the dataSourceShardingStragety
	 */
	public String getDataSourceShardingStragety() {
		return dataSourceShardingStragety;
	}

	/**
	 * @param dataSourceShardingStragety the dataSourceShardingStragety to set
	 */
	public void setDataSourceShardingStragety(String dataSourceShardingStragety) {
		this.dataSourceShardingStragety = dataSourceShardingStragety;
	}

	/**
	 * @return the dataSourceShardingParams
	 */
	public String[] getDataSourceShardingParams() {
		return dataSourceShardingParams;
	}

	/**
	 * @param dataSourceShardingParams the dataSourceShardingParams to set
	 */
	public void setDataSourceShardingParams(String[] dataSourceShardingParams) {
		this.dataSourceShardingParams = dataSourceShardingParams;
	}

	/**
	 * @return the tablesShardings
	 */
	public List<QueryShardingModel> getTablesShardings() {
		return tablesShardings;
	}

	/**
	 * @param tablesShardings the tablesShardings to set
	 */
	public void setTablesShardings(List<QueryShardingModel> tablesShardings) {
		this.tablesShardings = tablesShardings;
	}

	/**
	 * @return the hasWith
	 */
	public boolean isHasWith() {
		return hasWith;
	}

	/**
	 * @param hasWith the hasWith to set
	 */
	public void setHasWith(boolean hasWith) {
		this.hasWith = hasWith;
	}

	/**
	 * @return the fastSql
	 */
	public String getFastSql(String dialect) {
		return getDialectSql("fastSql", fastSql, dialect);
	}

	/**
	 * @param fastSql the fastSql to set
	 */
	public void setFastSql(String fastSql) {
		this.fastSql = fastSql;
	}

	/**
	 * @return the fastWithSql
	 */
	public String getFastWithSql(String dialect) {
		return getDialectSql("fastWithSql", fastWithSql, dialect);
	}

	/**
	 * @param fastWithSql the fastWithSql to set
	 */
	public void setFastWithSql(String fastWithSql) {
		this.fastWithSql = fastWithSql;
	}

	/**
	 * @return the fastPreSql
	 */
	public String getFastPreSql(String dialect) {
		return getDialectSql("fastPreSql", fastPreSql, dialect);
	}

	/**
	 * @param fastPreSql the fastPreSql to set
	 */
	public void setFastPreSql(String fastPreSql) {
		this.fastPreSql = fastPreSql;
	}

	/**
	 * @return the fastTailSql
	 */
	public String getFastTailSql(String dialect) {
		return getDialectSql("fastTailSql", fastTailSql, dialect);
	}

	/**
	 * @param fastTailSql the fastTailSql to set
	 */
	public void setFastTailSql(String fastTailSql) {
		this.fastTailSql = fastTailSql;
	}

	/**
	 * @todo 判定sql是否以:name形式传递参数还是直接=?模式
	 * @return
	 */
	public boolean isNamedParam() {
		if (this.paramsName != null && this.paramsName.length > 0)
			return true;
		return false;
	}

	/**
	 * @return the fastWithIndex
	 */
	public int getFastWithIndex() {
		return fastWithIndex;
	}

	/**
	 * @param fastWithIndex the fastWithIndex to set
	 */
	public void setFastWithIndex(int fastWithIndex) {
		this.fastWithIndex = fastWithIndex;
	}

	/**
	 * @return the countSql
	 */
	public String getCountSql(String dialect) {
		return getDialectSql("countSql", countSql, dialect);
	}

	/**
	 * @param countSql the countSql to set
	 */
	public void setCountSql(String countSql) {
		this.countSql = countSql;
	}

	/**
	 * @return the sqlType
	 */
	public SqlType getSqlType() {
		return sqlType;
	}

	/**
	 * @param sqlType the sqlType to set
	 */
	public void setSqlType(SqlType sqlType) {
		this.sqlType = sqlType;
	}

	/**
	 * @param dataSourceShardingParamsAlias the dataSourceShardingParamsAlias to set
	 */
	public void setDataSourceShardingParamsAlias(String[] dataSourceShardingParamsAlias) {
		this.dataSourceShardingParamsAlias = dataSourceShardingParamsAlias;
	}

	/**
	 * @return the dataSourceShardingParamsAlias
	 */
	public String[] getDataSourceShardingParamsAlias() {
		return dataSourceShardingParamsAlias;
	}

	/**
	 * @return the tableShardingParams
	 */
	public String[] getTableShardingParams() {
		return tableShardingParams;
	}

	/**
	 * @param tableShardingParams the tableShardingParams to set
	 */
	public void setTableShardingParams(String[] tableShardingParams) {
		this.tableShardingParams = tableShardingParams;
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

	public String getDataSourceShardingStrategyValue() {
		return dataSourceShardingStrategyValue;
	}

	public void setDataSourceShardingStrategyValue(String dataSourceShardingStrategyValue) {
		this.dataSourceShardingStrategyValue = dataSourceShardingStrategyValue;
	}

	/**
	 * @return the pageOptimize
	 */
	public boolean isPageOptimize() {
		return pageOptimize;
	}

	/**
	 * @param pageOptimize the pageOptimize to set
	 */
	public void setPageOptimize(boolean pageOptimize) {
		this.pageOptimize = pageOptimize;
	}

	/**
	 * @return the pageAliveMax
	 */
	public int getPageAliveMax() {
		return pageAliveMax;
	}

	/**
	 * @param pageAliveMax the pageAliveMax to set
	 */
	public void setPageAliveMax(int pageAliveMax) {
		// 最大不超过5000
		if (pageAliveMax > 5000) {
			this.pageAliveMax = 5000;
		}
		// 最小20
		else if (pageAliveMax < 20) {
			this.pageAliveMax = 20;
		} else {
			this.pageAliveMax = pageAliveMax;
		}
	}

	/**
	 * @return the pageAliveSeconds
	 */
	public int getPageAliveSeconds() {
		return pageAliveSeconds;
	}

	/**
	 * @param pageAliveSeconds the pageAliveSeconds to set
	 */
	public void setPageAliveSeconds(int pageAliveSeconds) {
		// 最小保持30秒
		if (pageAliveSeconds < 30) {
			this.pageAliveSeconds = 30;
		}
		// 不超过24小时
		else if (pageAliveSeconds > 3600 * 24) {
			this.pageAliveSeconds = 1800;
		} else {
			this.pageAliveSeconds = pageAliveSeconds;
		}
	}

	/**
	 * @return the secureMasks
	 */
	public SecureMask[] getSecureMasks() {
		return secureMasks;
	}

	/**
	 * @param secureMasks the secureMasks to set
	 */
	public void setSecureMasks(SecureMask[] secureMasks) {
		this.secureMasks = secureMasks;
	}

	/**
	 * @return the noSqlConfigModel
	 */
	public NoSqlConfigModel getNoSqlConfigModel() {
		return noSqlConfigModel;
	}

	/**
	 * @param noSqlConfigModel the noSqlConfigModel to set
	 */
	public void setNoSqlConfigModel(NoSqlConfigModel noSqlConfigModel) {
		this.noSqlConfigModel = noSqlConfigModel;
	}

	/**
	 * @return the showSql
	 */
	public boolean isShowSql() {
		return showSql;
	}

	/**
	 * @param showSql the showSql to set
	 */
	public void setShowSql(boolean showSql) {
		this.showSql = showSql;
	}

	/**
	 * @return the formatModels
	 */
	public FormatModel[] getFormatModels() {
		return formatModels;
	}

	/**
	 * @param formatModels the formatModels to set
	 */
	public void setFormatModels(FormatModel[] formatModels) {
		this.formatModels = formatModels;
	}

	public SqlToyConfig clone() {
		try {
			return (SqlToyConfig) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void addCacheArgParam(String name) {
		String param = name.toLowerCase();
		if (!this.cacheArgNames.contains(param)) {
			this.cacheArgNames.add(param);
		}
	}

	public List<String> getCacheArgNames() {
		return this.cacheArgNames;
	}

	public String[] getFullParamNames() {
		if (cacheArgNames == null || cacheArgNames.isEmpty()) {
			return this.paramsName;
		}
		List<String> tmp = new ArrayList<String>();
		if (this.paramsName != null && this.paramsName.length > 0) {
			for (String item : this.paramsName) {
				if (!tmp.contains(item.toLowerCase())) {
					tmp.add(item.toLowerCase());
				}
			}
		}
		for (String item : this.cacheArgNames) {
			if (!tmp.contains(item.toLowerCase())) {
				tmp.add(item.toLowerCase());
			}
		}
		return tmp.toArray(new String[tmp.size()]);
	}

	/**
	 * @TODO 根据方言生成不同的sql语句
	 * @param type
	 * @param sqlContent
	 * @param dialect
	 * @return
	 */
	private String getDialectSql(String type, String sqlContent, String dialect) {
		if (StringUtil.isBlank(sqlContent))
			return sqlContent;
		if (dialect == null || dialect.equals(Dialect.UNDEFINE) || dialect.equals(this.dialect))
			return sqlContent;
		String key = dialect.concat(".").concat(type);
		if (!dialectSqlMap.contains(key)) {
			String dialectSql = FunctionUtils.getDialectSql(sqlContent, dialect);
			// 保留字处理
			dialectSql = ReservedWordsUtil.convertSql(dialectSql, DataSourceUtils.getDBType(dialect));
			dialectSqlMap.put(key, dialectSql);
		}
		return dialectSqlMap.get(key);
	}

	/**
	 * @todo 获取sqlId或sql内容
	 * @return
	 */
	public String getIdOrSql() {
		if (StringUtil.isBlank(this.id))
			return this.sql;
		return this.id;
	}

	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	public void clearDialectSql() {
		this.dialectSqlMap.clear();
	}

}
