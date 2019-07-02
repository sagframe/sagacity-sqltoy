/**
 * @Copyright 2009 版权归陈仁飞，SqlToy ORM框架不允许任何形式的抄袭
 */
package org.sagacity.sqltoy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.SqlScriptLoader;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.plugin.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugin.ShardingStrategy;
import org.sagacity.sqltoy.translate.TranslateManager;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.IdUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @project sagacity-sqltoy4.0
 * @description sqltoy 工具的上下文容器，提供对应的sql获取以及相关参数设置
 * @author chenrf <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:SqlToyContext.java,Revision:v1.0,Date:2009-12-11 下午09:48:15
 * @Modification {Date:2018-1-5,增加对redis缓存翻译的支持}
 */
public class SqlToyContext implements ApplicationContextAware {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LogManager.getLogger(getClass());

	/**
	 * sqlToy 配置解析插件
	 */
	private SqlScriptLoader scriptLoader = new SqlScriptLoader();

	/**
	 * 实体对象管理器，加载实体bean
	 */
	private EntityManager entityManager = new EntityManager();

	/**
	 * 翻译器插件
	 */
	private TranslateManager translateManager = new TranslateManager();

	/**
	 * 统一公共字段赋值处理; 如修改时,为修改人和修改时间进行统一赋值; 创建时:为创建人、创建时间、修改人、修改时间进行统一赋值
	 */
	private IUnifyFieldsHandler unifyFieldsHandler;

	/**
	 * @param unifyFieldsHandler the unifyFieldsHandler to set
	 */
	public void setUnifyFieldsHandler(IUnifyFieldsHandler unifyFieldsHandler) {
		this.unifyFieldsHandler = unifyFieldsHandler;
	}

	/**
	 * @return the unifyFieldsHandler
	 */
	public IUnifyFieldsHandler getUnifyFieldsHandler() {
		return unifyFieldsHandler;
	}

	/**
	 * 数据库方言参数
	 */
	private String dialectProperties;

	/**
	 * sharding策略
	 */
	private HashMap<String, ShardingStrategy> shardingStrategys = new HashMap<String, ShardingStrategy>();

	/**
	 * es的地址配置
	 */
	private HashMap<String, ElasticEndpoint> elasticEndpoints = new HashMap<String, ElasticEndpoint>();

	/**
	 * 默认为default
	 */
	private String defaultElastic = "default";

	/**
	 * 登记sqltoy所需要访问的DataSource
	 */
	private HashMap<String, DataSource> dataSourcesMap = new HashMap<String, DataSource>();

	/**
	 * @return the translateManager
	 */
	public TranslateManager getTranslateManager() {
		return translateManager;
	}

	/**
	 * 批处理记录数量,默认为50
	 */
	private int batchSize = 200;

	/**
	 * 分页单次提取数据长度限制(默认为10万条),防止通过pageNo=-1 进行全表数据级提取 pageFetchSizeLimit=-1 表示不做限制
	 */
	private int pageFetchSizeLimit = 100000;

	/**
	 * 是否debug模式
	 */
	private boolean debug = false;

	/**
	 * debug\error
	 */
	private String printSqlStrategy = "error";

	/**
	 * 超时打印sql(毫秒,默认30秒)
	 */
	private int printSqlTimeoutMillis = 30000;

	/**
	 * 数据库类型
	 */
	private String dialect;

	/**
	 * snowflake 集群节点id<31
	 */
	private Integer workerId;

	/**
	 * 数据中心id<31
	 */
	private Integer dataCenterId;

	/**
	 * 服务器id(3位数字)
	 */
	private Integer serverId;

	/**
	 * 默认数据库连接池
	 */
	private DataSource defaultDataSource;

	/**
	 * 定义mongodb处理的Factory类在spring中的bean实例名称 用名称方式避免强依赖
	 */
	private String mongoFactoryName = "mongoDbFactory";

	/**
	 * @param workerId the workerId to set
	 */
	public void setWorkerId(Integer workerId) {
		this.workerId = workerId;
	}

	/**
	 * @param dataCenterId the dataCenterId to set
	 */
	public void setDataCenterId(Integer dataCenterId) {
		this.dataCenterId = dataCenterId;
	}

	/**
	 * spring 上下文容器
	 */
	private ApplicationContext applicationContext;

	/**
	 * @todo 初始化
	 * @throws Exception
	 */
	public void initialize() throws Exception {
		// 加载sqltoy的各类参数,如db2是否要增加with
		// ur等,详见org/sagacity/sqltoy/sqltoy-default.properties
		SqlToyConstants.loadProperties(dialectProperties);

		// 设置workerId和dataCenterId,为使用snowflake主键ID产生算法服务
		setWorkerAndDataCenterId();

		/**
		 * 初始化脚本加载器
		 */
		scriptLoader.initialize();

		/**
		 * 初始化实体对象管理器
		 */
		entityManager.initialize(this);

		/**
		 * 初始化翻译器
		 */
		translateManager.initialize(this);

		/**
		 * 初始化sql执行统计的基本参数
		 */
		SqlExecuteStat.setDebug(this.debug);
		SqlExecuteStat.setPrintSqlStrategy(this.printSqlStrategy);
		SqlExecuteStat.setPrintSqlTimeoutMillis(this.printSqlTimeoutMillis);
	}

	/**
	 * @todo 获取service并调用其指定方法获取报表数据
	 * @param beanName
	 * @param motheded
	 * @param args
	 * @return
	 */
	public Object getServiceData(String beanName, String motheded, Object[] args) {
		if (StringUtil.isBlank(beanName) || StringUtil.isBlank(motheded))
			return null;
		try {
			return BeanUtil.invokeMethod(applicationContext.getBean(beanName), motheded, args);
		} catch (BeansException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @todo 获取bean
	 * @param beanName
	 * @return
	 */
	public Object getBean(Object beanName) {
		if (beanName instanceof String)
			return applicationContext.getBean(beanName.toString());
		else
			return applicationContext.getBean((Class) beanName);
	}

	/**
	 * @todo 获取数据源
	 * @param dataSourceName
	 * @return
	 */
	public DataSource getDataSource(String dataSourceName) {
		if (StringUtil.isBlank(dataSourceName))
			return null;
		if (dataSourcesMap.containsKey(dataSourceName))
			return dataSourcesMap.get(dataSourceName);
		else
			return (DataSource) applicationContext.getBean(dataSourceName);
	}

	public SqlToyConfig getSqlToyConfig(String sqlKey) {
		return getSqlToyConfig(sqlKey, SqlType.search);
	}

	/**
	 * @todo 获取sql对应的配置模型
	 * @param sqlKey
	 * @param type
	 * @return
	 */
	public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType type) {
		SqlToyConfig result = scriptLoader.getSqlConfig(sqlKey);
		if (null == result) {
			// 判断是否是sqlId,非在xml中定义id的sql
			if (!SqlConfigParseUtils.isNamedQuery(sqlKey)) {
				result = SqlConfigParseUtils.parseSqlToyConfig(sqlKey,
						(null == scriptLoader) ? null : scriptLoader.getDialect(), type,
						(null == scriptLoader) ? null : scriptLoader.getFunctionConverts());
				// 设置默认空白查询条件过滤filter,便于直接传递sql语句情况下查询条件的处理
				ParamFilterModel[] filters = new ParamFilterModel[1];
				filters[0] = new ParamFilterModel("blank", new String[] { "*" });
				result.setFilters(filters);
			} else {
				// 这一步理论上不应该执行
				result = new SqlToyConfig();
				result.setSql(sqlKey);
			}
		}
		return result;
	}

	/**
	 * 设置workerId和dataCenterId,当没有通过配置文件指定workerId时通过IP来自动分配
	 */
	private void setWorkerAndDataCenterId() {
		try {
			String keyValue = SqlToyConstants.getKeyValue("sqltoy.snowflake.workerId");
			if (workerId == null && keyValue != null)
				workerId = Integer.parseInt(keyValue);
			keyValue = SqlToyConstants.getKeyValue("sqltoy.snowflake.dataCenterId");
			if (dataCenterId == null && keyValue != null)
				dataCenterId = Integer.parseInt(keyValue);
			if (workerId != null && (workerId.intValue() > 0 && workerId.intValue() < 32)) {
				SqlToyConstants.WORKER_ID = workerId.intValue();
			} else {
				String serverIdentity = IdUtil.getLastIp(2);
				int id = Integer.parseInt(serverIdentity == null ? "0" : serverIdentity);
				if (id > 31) {
					// 个位作为workerId
					SqlToyConstants.WORKER_ID = id % 10;
					// 十位数作为dataCenterId
					if (dataCenterId == null)
						SqlToyConstants.DATA_CENTER_ID = id / 10;
				} else
					SqlToyConstants.WORKER_ID = id;
			}
			if (dataCenterId != null && dataCenterId.intValue() > 0 && dataCenterId.intValue() < 32) {
				SqlToyConstants.DATA_CENTER_ID = dataCenterId.intValue();
			}
			// 22位或26位主键对应的serverId
			String serverNode = (serverId == null) ? SqlToyConstants.getKeyValue("sqltoy.server.id") : ("" + serverId);
			if (serverNode != null) {
				serverNode = StringUtil.addLeftZero2Len(serverNode, 3);
				if (serverNode.length() > 3)
					serverNode = serverNode.substring(serverNode.length() - 3);
				SqlToyConstants.SERVER_ID = serverNode;
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("设置workerId和dataCenterId发生错误:{}", e.getMessage());
		}
	}

	/**
	 * @return the scriptLoader
	 */
	public SqlScriptLoader getScriptLoader() {
		return scriptLoader;
	}

	/**
	 * @return the batchSize
	 */
	public int getBatchSize() {
		return batchSize;
	}

	/**
	 * @param batchSize the batchSize to set
	 */
	public void setBatchSize(int batchSize) {
		// 必须要大于零
		if (batchSize > 0)
			this.batchSize = batchSize;
	}

	/**
	 * @todo 返回sharding策略实例
	 * @param strategyName
	 * @return
	 */
	public ShardingStrategy getShardingStrategy(String strategyName) {
		// hashMap可以事先不赋值,直接定义spring的bean
		if (shardingStrategys.containsKey(strategyName)) {
			return shardingStrategys.get(strategyName);
		} else {
			ShardingStrategy shardingStrategy = (ShardingStrategy) applicationContext.getBean(strategyName);
			if (shardingStrategy != null) {
				shardingStrategys.put(strategyName, shardingStrategy);
			}
			return shardingStrategy;
		}
	}

	/**
	 * @param shardingStrategys the shardingStrategys to set
	 */
	public void setShardingStrategys(HashMap<String, ShardingStrategy> shardingStrategys) {
		this.shardingStrategys = shardingStrategys;
	}

	/**
	 * @return the entityManager
	 */
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public EntityMeta getEntityMeta(Class<?> entityClass) {
		return entityManager.getEntityMeta(this, entityClass);
	}

	/**
	 * @todo 提供可以动态增加解析sql片段配置的接口,并返回具体id
	 * @param sqlSegment
	 * @return
	 * @throws Exception
	 */
	public synchronized SqlToyConfig parseSqlSegment(Object sqlSegment) throws Exception {
		return scriptLoader.parseSqlSagment(sqlSegment);
	}

	/**
	 * @todo 将构造好的SqlToyConfig放入缓存
	 * @param sqlToyConfig
	 * @throws Exception
	 */
	public synchronized void putSqlToyConfig(SqlToyConfig sqlToyConfig) throws Exception {
		scriptLoader.putSqlToyConfig(sqlToyConfig);
	}

	/**
	 * @todo 判断方言是否一致，不一致执行函数替换
	 * @param sql
	 * @param dialect
	 * @return
	 */
	public String convertFunctions(String sql, String dialect) {
		if (this.dialect != null && this.dialect.equalsIgnoreCase(dialect))
			return sql;
		return SqlConfigParseUtils.convertFunctions(scriptLoader.getFunctionConverts(), dialect, sql);
	}

	/**
	 * @return the dataSourcesMap
	 */
	public HashMap<String, DataSource> getDataSourcesMap() {
		return dataSourcesMap;
	}

	/**
	 * @param dataSourcesMap the dataSourcesMap to set
	 */
	public void setDataSourcesMap(HashMap<String, DataSource> dataSourcesMap) {
		this.dataSourcesMap = dataSourcesMap;
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
		scriptLoader.setDialect(dialect);
	}

	/**
	 * @return the debug
	 */
	public boolean isDebug() {
		return debug;
	}

	/**
	 * @param debug the debug to set
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
		scriptLoader.setDebug(debug);
	}

	/**
	 * @param packagesToScan the packagesToScan to set
	 */
	public void setPackagesToScan(String[] packagesToScan) {
		entityManager.setPackagesToScan(packagesToScan);
	}

	/**
	 * @return the pageFetchSizeLimit
	 */
	public int getPageFetchSizeLimit() {
		return pageFetchSizeLimit;
	}

	/**
	 * @param pageFetchSizeLimit the pageFetchSizeLimit to set
	 */
	public void setPageFetchSizeLimit(int pageFetchSizeLimit) {
		this.pageFetchSizeLimit = pageFetchSizeLimit;
	}

	@Deprecated
	public void setPage_fetch_size_limit(int page_fetch_size_limit) {
		this.pageFetchSizeLimit = page_fetch_size_limit;
	}

	/**
	 * @param recursive the recursive to set
	 */
	public void setRecursive(boolean recursive) {
		entityManager.setRecursive(recursive);
	}

	/**
	 * @param annotatedClasses the annotatedClasses to set
	 */
	public void setAnnotatedClasses(String[] annotatedClasses) {
		entityManager.setAnnotatedClasses(annotatedClasses);
	}

	/**
	 * @param dialectProperties the dialectProperties to set
	 */
	public void setDialectProperties(String dialectProperties) {
		this.dialectProperties = dialectProperties;
	}

	public void setSqlResourcesDir(String sqlResourcesDir) {
		scriptLoader.setSqlResourcesDir(sqlResourcesDir);
	}

	public void setSqlResources(List<String> sqlResources) {
		scriptLoader.setSqlResources(sqlResources);
	}

	public void setEncoding(String encoding) {
		scriptLoader.setEncoding(encoding);
	}

	/**
	 * @param functionConverts the functionConverts to set
	 */
	public void setFunctionConverts(Object functionConverts) {
		if (functionConverts == null)
			return;
		if (functionConverts instanceof List)
			scriptLoader.setFunctionConverts((List<String>) functionConverts);
		else if (functionConverts instanceof String) {
			String converts = (String) functionConverts;
			if (StringUtil.isBlank(converts) || converts.equals("default") || converts.equals("defaults"))
				scriptLoader.setFunctionConverts(null);
			else
				scriptLoader.setFunctionConverts(Arrays.asList(converts.split(",")));
		}
	}

	/**
	 * @param translateConfig the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		translateManager.setTranslateConfig(translateConfig);
	}

	/**
	 * @param nocacheKeyResult the nocacheKeyResult to set
	 */
	public void setUncachedKeyResult(String uncachedKeyResult) {
		SqlToyConstants.setUncachedKeyResult(uncachedKeyResult);
	}

	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * @param serverId the serverId to set
	 */
	public void setServerId(Integer serverId) {
		this.serverId = serverId;
	}

	/**
	 * @return the defaultDataSource
	 */
	public DataSource getDefaultDataSource() {
		return defaultDataSource;
	}

	/**
	 * @param defaultDataSource the defaultDataSource to set
	 */
	public void setDefaultDataSource(DataSource defaultDataSource) {
		this.defaultDataSource = defaultDataSource;
	}

	/**
	 * @param mongoDbFactory the mongoDbFactory to set
	 */
	public void setMongoFactoryName(String mongoFactoryName) {
		this.mongoFactoryName = mongoFactoryName;
	}

	/**
	 * @return the mongoFactoryName
	 */
	public String getMongoFactoryName() {
		return mongoFactoryName;
	}

	/**
	 * @param elasticConfigs the elasticConfigs to set
	 */
	public void setElasticEndpoints(List<ElasticEndpoint> elasticEndpointList) {
		if (elasticEndpointList != null && !elasticEndpointList.isEmpty()) {
			// 第一个作为默认值
			defaultElastic = elasticEndpointList.get(0).getId();
			boolean esEnableSql = Boolean
					.parseBoolean(SqlToyConstants.getKeyValue("elasticsearch.enable.sql", "false"));
			for (ElasticEndpoint config : elasticEndpointList) {
				// 初始化restClient
				config.initRestClient();
				if (!config.isEnableSql())
					config.setEnableSql(esEnableSql);
				elasticEndpoints.put(config.getId().toLowerCase(), config);
			}
		}
	}

	public ElasticEndpoint getElasticEndpoint(String id) {
		ElasticEndpoint result = elasticEndpoints.get(StringUtil.isBlank(id) ? defaultElastic : id.toLowerCase());
		// 取不到,则可能sql中自定义url地址,自行构建模型，按指定的url进行查询
		if (result == null)
			return new ElasticEndpoint(id);
		else
			return result;
	}

	/**
	 * @return the printSqlStrategy
	 */
	public String getPrintSqlStrategy() {
		return printSqlStrategy;
	}

	/**
	 * @param printSqlStrategy the printSqlStrategy to set
	 */
	public void setPrintSqlStrategy(String printSqlStrategy) {
		this.printSqlStrategy = printSqlStrategy;
	}

	/**
	 * @return the printSqlTimeoutMillis
	 */
	public int getPrintSqlTimeoutMillis() {
		return printSqlTimeoutMillis;
	}

	/**
	 * @param printSqlTimeoutMillis the printSqlTimeoutMillis to set
	 */
	public void setPrintSqlTimeoutMillis(int printSqlTimeoutMillis) {
		this.printSqlTimeoutMillis = printSqlTimeoutMillis;
	}

	/**
	 * @param keywordSign the keywordSign to set
	 */
	public void setKeywordSign(String keywordSign) {
		SqlToyConstants.keywordSign = keywordSign;
	}

}
