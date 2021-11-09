/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.Dialect;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sqltoy-orm
 * @description 单个sql被解析后的模型
 * @author zhongxuchen
 * @version v1.0,Date:2014年12月9日
 * @modify Date:2020-8-2 1、修改secureMasks、formatModels类型为List并实例化空集合
 *         2、translateMap也实例化,便于后续处理 3、resultProcessor 也实例化非空集合
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
	private List<ParamFilterModel> filters = new ArrayList<ParamFilterModel>();

	/**
	 * 翻译器
	 */
	private HashMap<String, Translate> translateMap = new HashMap<String, Translate>();

	/**
	 * 安全脱敏配置
	 */
	private List<SecureMask> secureMasks = new ArrayList<SecureMask>();

	/**
	 * 格式化定义
	 */
	private List<FormatModel> formatModels = new ArrayList<FormatModel>();

	/**
	 * sql特定使用的dataSource(一般在项目跨多个数据库且是查询语句时使用)
	 */
	private String dataSource;

	/**
	 * 分库策略
	 */
	private ShardingStrategyConfig dataSourceSharding;

	/**
	 * 分表策略
	 */
	private List<ShardingStrategyConfig> tableShardings = new ArrayList<ShardingStrategyConfig>();

	/**
	 * 分表对应参数合集
	 */
	private String[] tableShardingParams = null;

	/**
	 * 对列进行拼接方式定义
	 */
	private LinkModel linkModel = null;

	/**
	 * sqlId,对应xml定义的sql唯一标志
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
	 * 缓存条件参数名称(包含aliasName),解析过程已经增加
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
	 * @fast场景下外围是否已经包含了()
	 */
	private boolean ignoreBracket = false;

	/**
	 * 解密字段
	 */
	private IgnoreCaseSet decryptColumns;

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
	 * 分页优化器
	 */
	private PageOptimize pageOptimize;

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
	private List resultProcessor = new ArrayList();

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
	public void addFilters(List<ParamFilterModel> paramFilters) {
		if (paramFilters != null && !paramFilters.isEmpty()) {
			this.filters.addAll(paramFilters);
		}
	}

	public void addFilter(ParamFilterModel paramFilter) {
		if (paramFilter != null) {
			this.filters.add(paramFilter);
		}
	}

	public List<ParamFilterModel> getFilters() {
		return this.filters;
	}

	/**
	 * @return the translateMap
	 */
	public HashMap<String, Translate> getTranslateMap() {
		return translateMap;
	}

	/**
	 * @param translateMap the translateMap to set
	 */
	public void setTranslateMap(HashMap<String, Translate> translateMap) {
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
		if (this.paramsName != null && this.paramsName.length > 0) {
			return true;
		}
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
	 * @return the secureMasks
	 */
	public List<SecureMask> getSecureMasks() {
		return secureMasks;
	}

	/**
	 * @param secureMasks the secureMasks to set
	 */
	public void setSecureMasks(List<SecureMask> secureMasks) {
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
	public List<FormatModel> getFormatModels() {
		return formatModels;
	}

	/**
	 * @param formatModels the formatModels to set
	 */
	public void setFormatModels(List<FormatModel> formatModels) {
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

	/**
	 * @TODO 已经包含aliasName,解析过程已经增加
	 * @param name
	 */
	public void addCacheArgParam(String name) {
		String nameLow = name.toLowerCase();
		boolean exists = false;
		for (String argName : cacheArgNames) {
			if (argName.toLowerCase().equals(nameLow)) {
				exists = true;
				break;
			}
		}
		if (!exists) {
			this.cacheArgNames.add(name);
		}
	}

	public List<String> getCacheArgNames() {
		return this.cacheArgNames;
	}

	public String[] getFullParamNames() {
		if (cacheArgNames == null || cacheArgNames.isEmpty()) {
			return this.paramsName;
		}
		Set<String> keys = new HashSet<String>();
		List<String> params = new ArrayList<String>();
		String key;
		if (this.paramsName != null && this.paramsName.length > 0) {
			for (String item : this.paramsName) {
				key = item.toLowerCase();
				if (!keys.contains(key)) {
					keys.add(key);
					params.add(item);
				}
			}
		}
		// 增加cacheArgs中存在的参数名称
		for (String item : this.cacheArgNames) {
			key = item.toLowerCase();
			if (!keys.contains(key)) {
				keys.add(key);
				params.add(item);
			}
		}
		return params.toArray(new String[params.size()]);
	}

	/**
	 * @TODO 根据方言生成不同的sql语句
	 * @param type
	 * @param sqlContent
	 * @param dialect
	 * @return
	 */
	private String getDialectSql(String type, String sqlContent, String dialect) {
		if (StringUtil.isBlank(sqlContent)) {
			return sqlContent;
		}
		if (dialect == null || dialect.equals(Dialect.UNDEFINE) || dialect.equals(this.dialect)) {
			return sqlContent;
		}
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
		if (StringUtil.isBlank(this.id)) {
			return this.sql;
		}
		return this.id;
	}

	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	public void clearDialectSql() {
		this.dialectSqlMap.clear();
	}

	/**
	 * @return the pageOptimize
	 */
	public PageOptimize getPageOptimize() {
		return pageOptimize;
	}

	/**
	 * @param pageOptimize the pageOptimize to set
	 */
	public void setPageOptimize(PageOptimize pageOptimize) {
		this.pageOptimize = pageOptimize;
	}

	public ShardingStrategyConfig getDataSourceSharding() {
		return dataSourceSharding;
	}

	public void setDataSourceSharding(ShardingStrategyConfig dataSourceSharding) {
		this.dataSourceSharding = dataSourceSharding;
	}

	public List<ShardingStrategyConfig> getTableShardings() {
		return tableShardings;
	}

	public void setTableShardings(List<ShardingStrategyConfig> tableShardings) {
		this.tableShardings = tableShardings;
	}

	public String[] getTableShardingParams() {
		return tableShardingParams;
	}

	public void setTableShardingParams(String[] tableShardingParams) {
		this.tableShardingParams = tableShardingParams;
	}

	/**
	 * @return the ignoreBracket
	 */
	public boolean isIgnoreBracket() {
		return ignoreBracket;
	}

	/**
	 * @param ignoreBracket the ignoreBracket to set
	 */
	public void setIgnoreBracket(boolean ignoreBracket) {
		this.ignoreBracket = ignoreBracket;
	}

	public IgnoreCaseSet getDecryptColumns() {
		return decryptColumns;
	}

	public void setDecryptColumns(IgnoreCaseSet decryptColumns) {
		this.decryptColumns = decryptColumns;
	}
}
