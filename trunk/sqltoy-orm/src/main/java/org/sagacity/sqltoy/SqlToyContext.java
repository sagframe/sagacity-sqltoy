package org.sagacity.sqltoy;

import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.SqlScriptLoader;
import org.sagacity.sqltoy.config.model.ElasticEndpoint;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.integration.AppContext;
import org.sagacity.sqltoy.integration.ConnectionFactory;
import org.sagacity.sqltoy.integration.impl.SpringConnectionFactory;
import org.sagacity.sqltoy.model.OverTimeSql;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.plugins.FilterHandler;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.sagacity.sqltoy.plugins.OverTimeSqlHandler;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.sagacity.sqltoy.plugins.datasource.DataSourceSelector;
import org.sagacity.sqltoy.plugins.datasource.impl.DefaultDataSourceSelector;
import org.sagacity.sqltoy.plugins.function.FunctionUtils;
import org.sagacity.sqltoy.plugins.overtime.DefaultOverTimeHandler;
import org.sagacity.sqltoy.plugins.secure.DesensitizeProvider;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;
import org.sagacity.sqltoy.plugins.secure.impl.DesensitizeDefaultProvider;
import org.sagacity.sqltoy.plugins.secure.impl.FieldsRSASecureProvider;
import org.sagacity.sqltoy.plugins.sharding.ShardingStrategy;
import org.sagacity.sqltoy.translate.TranslateManager;
import org.sagacity.sqltoy.translate.cache.TranslateCacheManager;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.DataSourceUtils.Dialect;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//------------------了解 sqltoy的关键优势: -------------------------------------------------------------------------------------------*/
//1、最简最直观的sql编写方式(不仅仅是查询语句)，采用条件参数前置处理规整法，让sql语句部分跟客户端保持高度一致
//2、sql中支持注释(规避了对hint特性的影响,知道hint吗?搜oracle hint)，和动态更新加载，便于开发和后期维护整个过程的管理
//3、支持缓存翻译和反向缓存条件检索(通过缓存将名称匹配成精确的key)，实现sql简化和性能大幅提升
//4、支持快速分页和分页优化功能，实现分页最高级别的优化，同时还考虑到了cte多个with as情况下的优化支持
//5、支持并行查询
//6、根本杜绝sql注入问题
//7、支持行列转换、分组汇总求平均、同比环比计算，在于用算法解决复杂sql，同时也解决了sql跨数据库问题
//8、支持保留字自动适配
//9、支持跨数据库函数自适配,从而非常有利于一套代码适应多种数据库便于产品化,比如oracle的nvl，当sql在mysql环境执行时自动替换为ifnull
//10、支持分库分表
//11、提供了在新增和修改操作过程中公共字段插入和修改，如:租户、创建人、创建时间、修改时间等
//12、提供了统一数据权限传参和数据越权校验
//13、提供了取top、取random记录、树形表结构构造和递归查询支持、updateSaveFetch/updateFetch单次交互完成修改和查询等实用的功能
//14、sqltoy的update、save、saveAll、load 等crud操作规避了jpa的缺陷,参见update(entity,String...forceUpdateProps)和updateFetch、updateSaveFetch
//15、提供了极为人性化的条件处理:排它性条件、日期条件加减和提取月末月初处理等
//16、提供了查询结果日期、数字格式化、安全脱敏处理，让复杂的事情变得简单，大幅简化sql和结果的二次处理工作
//------------------------------------------------------------------------------------------------------------------------------------*/
/**
 * @project sagacity-sqltoy
 * @description sqltoy 工具的上下文容器，提供对应的sql获取以及相关参数设置
 * @author zhongxuchen
 * @version v1.0,Date:2009-12-11
 * @modify {Date:2018-1-5,增加对redis缓存翻译的支持}
 * @modify {Date:2019-09-15,将跨数据库函数FunctionConverts统一提取到FunctionUtils中,实现不同数据库函数替换后的语句放入缓存,避免每次执行函数替换}
 * @modify {Date:2020-05-29,调整mongo的注入方式,剔除之前MongoDbFactory模式,直接使用MongoTemplate}
 * @modify {Date:2022-04-23,pageOverToFirst默认值改为false}
 * @modify {Date:2022-06-11,支持多个缓存翻译定义文件}
 * @modify {Date:2022-06-14,剔除pageOverToFirst 属性}
 */
public class SqlToyContext {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(SqlToyContext.class);

	/**
	 * sqlToy 配置解析插件
	 */
	private SqlScriptLoader scriptLoader = new SqlScriptLoader();

	/**
	 * 实体对象管理器，加载实体bean
	 */
	private EntityManager entityManager = new EntityManager();

	/**
	 * sqltoy的翻译器插件(可以通过其完成对缓存的管理扩展)
	 */
	private TranslateManager translateManager = new TranslateManager();

	/**
	 * 延时检测时长(避免应用一启动即进行检测,包含:缓存变更检测、sql文件变更检测)
	 */
	private int delayCheckSeconds = 30;

	/**
	 * 默认查询数据库端提取记录量
	 */
	private int fetchSize = -1;

	/**
	 * 统一公共字段赋值处理; 如修改时,为修改人和修改时间进行统一赋值; 创建时:为创建人、创建时间、修改人、修改时间进行统一赋值
	 */
	private IUnifyFieldsHandler unifyFieldsHandler;

	/**
	 * 具体缓存实现(默认ehcache,可以根据自己喜好来自行扩展实现,sqltoy习惯将有争议的提供默认实现但用户可自行选择)
	 */
	private TranslateCacheManager translateCacheManager;

	/**
	 * 自定义参数过滤处理器(防范性预留)
	 */
	private FilterHandler customFilterHandler;

	/**
	 * 执行超时sql自定义处理器
	 */
	private OverTimeSqlHandler overTimeSqlHandler = new DefaultOverTimeHandler();

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
	 * 缓存类型，默认ehcache(可选:caffeine)
	 */
	private String cacheType = "ehcache";

	/**
	 * 默认数据源名称，一般无需设置
	 */
	private String defaultDataSourceName;

	/**
	 * @return the translateManager
	 */
	public TranslateManager getTranslateManager() {
		return translateManager;
	}

	/**
	 * 批处理记录数量,默认为200
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
	 * 超时打印sql(毫秒,默认30秒)
	 */
	private int printSqlTimeoutMillis = 30000;

	/**
	 * 获取MetaData的列标题处理策略：default:不做处理;upper:转大写;lower
	 */
	private String columnLabelUpperOrLower = "default";

	/**
	 * 数据库类型
	 */
	private String dialect;

	/*----------------snowflake参数,如不设置框架自动以本机IP来获取----  */
	/**
	 * snowflake 集群节点id<31
	 */
	private Integer workerId;

	/**
	 * 数据中心id<31
	 */
	private Integer dataCenterId;
	/*----------------snowflake 参数---- ---------------------------- */

	/**
	 * 服务器id(3位数字)，用于22位和26位主键生成，不设置会自动根据本机IP生成
	 */
	private Integer serverId;

	/**
	 * 默认数据库连接池
	 */
	private DataSource defaultDataSource;

	/**
	 * sql脚本检测间隔时长(debug模式下默认3秒,非debug默认15秒)
	 */
	private Integer scriptCheckIntervalSeconds;

	/**
	 * 提供自定义类型处理器,一般针对json等类型
	 */
	private TypeHandler typeHandler;

	/**
	 * dataSource选择器，提供给开发者扩展窗口
	 */
	private DataSourceSelector dataSourceSelector = new DefaultDataSourceSelector();

	/**
	 * 提供数据源获得connection的扩展(默认spring的实现)
	 */
	private ConnectionFactory connectionFactory;

	/**
	 * 定义TranslateCacheManager 对应实现类
	 */
	private String translateCaffeineManagerClass = "org.sagacity.sqltoy.translate.cache.impl.TranslateCaffeineManager";

	/**
	 * 定义mongo查询的实现类,默认基于spring实现，其他框架修改成对应实现类
	 */
	private String mongoQueryClass = "org.sagacity.sqltoy.integration.impl.SpringMongoQuery";

	/**
	 * 分布式id产生器实现类
	 */
	private String distributeIdGeneratorClass = "org.sagacity.sqltoy.integration.impl.SpringRedisIdGenerator";

	/**
	 * 获取bean的上下文容器
	 */
	private AppContext appContext;

	/**
	 * 对sqltoy-default.properties 值的修改(一般情况下不会涉及)
	 */
	private Map<String, String> dialectConfig;

	/**
	 * 数据库保留字,用逗号分隔
	 */
	private String reservedWords;

	// 如果是文件存储则使用classpath:开头
	/**
	 * 安全私钥
	 */
	private String securePrivateKey;

	/**
	 * 安全公钥
	 */
	private String securePublicKey;

	/**
	 * 字段加解密实现类，sqltoy提供了RSA的默认实现
	 */
	private FieldsSecureProvider fieldsSecureProvider;

	/**
	 * 当发现有重复sqlId时是否抛出异常，终止程序执行
	 */
	private boolean breakWhenSqlRepeat = true;

	/**
	 * 编码格式
	 */
	private String encoding = "UTF-8";

	/**
	 * 脱敏处理器
	 */
	private DesensitizeProvider desensitizeProvider;

	/**
	 * @todo 初始化
	 * @throws Exception
	 */
	public void initialize() throws Exception {
		logger.debug("start init sqltoy ..............................");
		// 加载sqltoy的各类参数,如db2是否要增加with
		// ur等,详见org/sagacity/sqltoy/sqltoy-default.properties
		SqlToyConstants.loadProperties(dialectConfig);
		// 默认使用基于spring的连接管理
		if (connectionFactory == null) {
			connectionFactory = new SpringConnectionFactory();
		}
		// 初始化默认dataSource
		initDefaultDataSource();
		// 设置workerId和dataCenterId,为使用snowflake主键ID产生算法服务
		SqlToyConstants.setWorkerAndDataCenterId(workerId, dataCenterId, serverId);

		// 初始化脚本加载器
		scriptLoader.initialize(this.debug, delayCheckSeconds, scriptCheckIntervalSeconds, breakWhenSqlRepeat);

		// 初始化翻译器,update 2021-1-23 增加caffeine缓存支持
		if (translateCacheManager == null && "caffeine".equalsIgnoreCase(this.cacheType)) {
			translateManager.initialize(this,
					(TranslateCacheManager) Class.forName(translateCaffeineManagerClass).newInstance(),
					delayCheckSeconds);
		} else {
			translateManager.initialize(this, translateCacheManager, delayCheckSeconds);
		}
		// 初始化实体对象管理器(此功能已经无实际意义,已经改为即用即加载而非提前加载)
		entityManager.initialize(this);
		// 设置保留字
		ReservedWordsUtil.put(reservedWords);
		// 设置默认fetchSize
		SqlToyConstants.FETCH_SIZE = this.fetchSize;
		// 初始化sql执行统计的基本参数
		SqlExecuteStat.setDebug(this.debug);
		SqlExecuteStat.setOverTimeSqlHandler(overTimeSqlHandler);
		SqlExecuteStat.setPrintSqlTimeoutMillis(this.printSqlTimeoutMillis);
		// 字段加解密实现类初始化
		if (null != fieldsSecureProvider) {
			fieldsSecureProvider.initialize(this.encoding, securePrivateKey, securePublicKey);
		} else {
			if (StringUtil.isNotBlank(securePrivateKey) && StringUtil.isNotBlank(securePublicKey)) {
				if (fieldsSecureProvider == null) {
					fieldsSecureProvider = new FieldsRSASecureProvider();
				}
				fieldsSecureProvider.initialize(this.encoding, securePrivateKey, securePublicKey);
			}
		}
		// 默认的脱敏处理器
		if (desensitizeProvider == null) {
			desensitizeProvider = new DesensitizeDefaultProvider();
		}
		logger.debug("sqltoy init complete!");
	}

	/**
	 * @todo 获取service并调用其指定方法获取报表数据
	 * @param beanName
	 * @param method
	 * @param args
	 * @return
	 */
	public Object getServiceData(String beanName, String method, Object[] args) {
		if (StringUtil.isBlank(beanName) || StringUtil.isBlank(method)) {
			return null;
		}
		try {
			Object beanDefine = null;
			if (appContext.containsBean(beanName)) {
				beanDefine = appContext.getBean(beanName);
			} else if (beanName.indexOf(".") > 0) {
				beanDefine = appContext.getBean(Class.forName(beanName));
			} else {
				return null;
			}
			return BeanUtil.invokeMethod(beanDefine, method, args);
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
		try {
			if (beanName instanceof String) {
				return appContext.getBean(beanName.toString());
			}
			return appContext.getBean((Class) beanName);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("从springContext中获取Bean:{} 错误!{}", e.getMessage());
		}
		return null;
	}

	/**
	 * @todo 获取数据源
	 * @param dataSourceName
	 * @return
	 */
	public DataSource getDataSourceBean(String dataSourceName) {
		if (StringUtil.isBlank(dataSourceName)) {
			return null;
		}
		// 优先使用扩展来实现
		if (dataSourceSelector != null) {
			return dataSourceSelector.getDataSourceBean(appContext, dataSourceName);
		} else if (appContext.containsBean(dataSourceName)) {
			return (DataSource) appContext.getBean(dataSourceName);
		}
		return null;
	}

	/**
	 * @TODO 保留一个获取查询的sql(针对报表平台)
	 * @param sqlKey
	 * @return
	 */
	public SqlToyConfig getSqlToyConfig(String sqlKey) {
		return getSqlToyConfig(sqlKey, SqlType.search, (getDialect() == null) ? "" : getDialect());
	}

	/**
	 * @todo 获取sql对应的配置模型(请阅读scriptLoader,硬code的sql对应模型也利用了内存来存放非每次都动态构造对象)
	 * @param sqlKey
	 * @param sqlType
	 * @param dialect
	 * @return
	 */
	public SqlToyConfig getSqlToyConfig(String sqlKey, SqlType sqlType, String dialect) {
		if (StringUtil.isBlank(sqlKey)) {
			throw new IllegalArgumentException("sql or sqlId is null!");
		}
		return scriptLoader.getSqlConfig(sqlKey, sqlType, dialect);
	}

	public SqlToyConfig getSqlToyConfig(QueryExecutor queryExecutor, SqlType sqlType, String dialect) {
		String sqlKey = queryExecutor.getInnerModel().sql;
		if (StringUtil.isBlank(sqlKey)) {
			throw new IllegalArgumentException("sql or sqlId is null!");
		}
		// 查询语句补全select * from table,避免一些sql直接从from 开始
		if (SqlType.search.equals(sqlType)) {
			if (queryExecutor.getInnerModel().resultType != null) {
				sqlKey = SqlUtil.completionSql(this, (Class) queryExecutor.getInnerModel().resultType, sqlKey);
			} // update 2021-12-7 sql 类似 from table where xxxx 形式，补全select *
			else if (!SqlConfigParseUtils.isNamedQuery(sqlKey)
					&& StringUtil.matches(sqlKey.toLowerCase().trim(), "^from\\W")) {
				sqlKey = "select * ".concat(sqlKey);
			}
		}
		SqlToyConfig result = scriptLoader.getSqlConfig(sqlKey, sqlType, dialect);
		// 剔除空白转null的默认设置
		if (!queryExecutor.getInnerModel().blankToNull && StringUtil.isBlank(result.getId())) {
			if (result.getFilters().size() == 1) {
				result.getFilters().remove(0);
			}
		}
		return result;
	}

	/**
	 * @return the scriptLoader
	 */
	public SqlScriptLoader getScriptLoader() {
		return scriptLoader;
	}

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
	 * @param serverId the serverId to set
	 */
	public void setServerId(Integer serverId) {
		this.serverId = serverId;
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
		if (batchSize > 0) {
			this.batchSize = batchSize;
		}
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
		}
		ShardingStrategy shardingStrategy = (ShardingStrategy) appContext.getBean(strategyName);
		if (shardingStrategy != null) {
			shardingStrategys.put(strategyName, shardingStrategy);
		}
		return shardingStrategy;
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
	 * @TODO 判断是否是实体bean
	 * @param entityClass
	 * @return
	 */
	public boolean isEntity(Class<?> entityClass) {
		return entityManager.isEntity(this, entityClass);
	}

	/**
	 * <p>
	 * <li>1、第一步调用解析，注意是单个sqlId的片段</li>
	 * <li>2、根据业务情况，调整id,sqlToyConfig.setId(),注意:这步并非必要,当报表平台时,报表里面多个sql,每个id在本报表范围内唯一，当很多个报表时会冲突，所以需要整合rptId+sqlId</li>
	 * <li>3、putSqlToyConfig(SqlToyConfig sqlToyConfig) 放入交由sqltoy统一管理</li>
	 * </p>
	 * 
	 * @todo 提供可以动态增加解析sql片段配置的接口,完成SqltoyConfig模型的构造(用于第三方平台集成，如报表平台等)，
	 * @param sqlSegment
	 * @return
	 * @throws Exception
	 */
	public synchronized SqlToyConfig parseSqlSegment(Object sqlSegment) throws Exception {
		return scriptLoader.parseSqlSagment(sqlSegment);
	}

	/**
	 * @todo 将构造好的SqlToyConfig放入交给sqltoy统一托管(在托管前可以对id进行重新组合确保id的唯一性,比如报表平台，将rptId+sqlId组合成一个全局唯一的id)
	 * @param sqlToyConfig
	 * @throws Exception
	 */
	public synchronized void putSqlToyConfig(SqlToyConfig sqlToyConfig) throws Exception {
		scriptLoader.putSqlToyConfig(sqlToyConfig);
	}

	/**
	 * @TODO 开放sql文件动态交由开发者挂载
	 * @param sqlFile
	 * @throws Exception
	 */
	public synchronized void parseSqlFile(Object sqlFile) throws Exception {
		scriptLoader.parseSqlFile(sqlFile);
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
		if (StringUtil.isBlank(dialect)) {
			return;
		}
		// 规范数据库方言命名(避免方言和版本一起定义)
		String tmp = dialect.toLowerCase();
		if (tmp.startsWith(Dialect.MYSQL)) {
			this.dialect = Dialect.MYSQL;
		} else if (tmp.startsWith(Dialect.ORACLE)) {
			this.dialect = Dialect.ORACLE;
		} else if (tmp.startsWith(Dialect.POSTGRESQL)) {
			this.dialect = Dialect.POSTGRESQL;
		} else if (tmp.startsWith(Dialect.GREENPLUM)) {
			this.dialect = Dialect.POSTGRESQL;
		} else if (tmp.startsWith(Dialect.DB2)) {
			this.dialect = Dialect.DB2;
		} else if (tmp.startsWith(Dialect.SQLSERVER)) {
			this.dialect = Dialect.SQLSERVER;
		} else if (tmp.startsWith(Dialect.SQLITE)) {
			this.dialect = Dialect.SQLITE;
		} else if (tmp.startsWith(Dialect.GAUSSDB)) {
			this.dialect = Dialect.GAUSSDB;
		} else if (tmp.startsWith(Dialect.MARIADB)) {
			this.dialect = Dialect.MARIADB;
		} else if (tmp.startsWith(Dialect.CLICKHOUSE)) {
			this.dialect = Dialect.CLICKHOUSE;
		} else if (tmp.startsWith(Dialect.OCEANBASE)) {
			this.dialect = Dialect.OCEANBASE;
		} else if (tmp.startsWith(Dialect.DM)) {
			this.dialect = Dialect.DM;
		} else if (tmp.startsWith(Dialect.TIDB)) {
			this.dialect = Dialect.TIDB;
		} else if (tmp.startsWith(Dialect.KINGBASE)) {
			this.dialect = Dialect.KINGBASE;
		} else if (tmp.startsWith(Dialect.IMPALA) || tmp.contains("kudu")) {
			this.dialect = Dialect.IMPALA;
		} else if (tmp.startsWith(Dialect.TDENGINE)) {
			this.dialect = Dialect.TDENGINE;
		} else if (tmp.startsWith(Dialect.ES)) {
			this.dialect = Dialect.ES;
		} else {
			this.dialect = dialect;
		}
		scriptLoader.setDialect(this.dialect);
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

	public void setDialectConfig(Map<String, String> dialectConfig) {
		this.dialectConfig = dialectConfig;
	}

	public void setSqlResourcesDir(String sqlResourcesDir) {
		scriptLoader.setSqlResourcesDir(sqlResourcesDir);
	}

	public void setSqlResources(List<String> sqlResources) {
		scriptLoader.setSqlResources(sqlResources);
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
		scriptLoader.setEncoding(encoding);
	}

	/**
	 * functionConverts=close表示关闭
	 * 
	 * @param functionConverts the functionConverts to set
	 */
	public void setFunctionConverts(Object functionConverts) {
		if (functionConverts == null) {
			return;
		}
		if (functionConverts instanceof List) {
			FunctionUtils.setFunctionConverts((List<String>) functionConverts);
		} else if (functionConverts instanceof String) {
			String converts = (String) functionConverts;
			if (StringUtil.isBlank(converts) || "default".equals(converts) || "defaults".equals(converts)) {
				FunctionUtils.setFunctionConverts(null);
			} else if (!"close".equalsIgnoreCase(converts)) {
				FunctionUtils.setFunctionConverts(Arrays.asList(converts.split("\\,")));
			}
		}
	}

	/**
	 * @param translateConfig the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		translateManager.setTranslateConfig(translateConfig);
	}

	/**
	 * @param uncachedKeyResult the nocacheKeyResult to set
	 */
	public void setUncachedKeyResult(String uncachedKeyResult) {
		SqlToyConstants.setUncachedKeyResult(uncachedKeyResult);
	}

	public AppContext getAppContext() {
		return appContext;
	}

	public void setAppContext(AppContext appContext) {
		this.appContext = appContext;
	}

	public void initDefaultDataSource() {
		if (StringUtil.isNotBlank(defaultDataSourceName)) {
			this.defaultDataSource = getDataSourceBean(defaultDataSourceName);
		}
	}

	public DataSource getDefaultDataSource() {
		return defaultDataSource;
	}

	/**
	 * @param elasticEndpointList the elasticConfigs to set
	 */
	public void setElasticEndpoints(List<ElasticEndpoint> elasticEndpointList) {
		if (elasticEndpointList == null || elasticEndpointList.isEmpty()) {
			return;
		}
		// 第一个作为默认值
		if (StringUtil.isBlank(defaultElastic)) {
			defaultElastic = elasticEndpointList.get(0).getId();
		}
		for (ElasticEndpoint config : elasticEndpointList) {
			// 初始化restClient
			config.initRestClient();
			elasticEndpoints.put(config.getId().toLowerCase(), config);
		}
	}

	public ElasticEndpoint getElasticEndpoint(String id) {
		ElasticEndpoint result = elasticEndpoints.get(StringUtil.isBlank(id) ? defaultElastic : id.toLowerCase());
		// 取不到,则可能sql中自定义url地址,自行构建模型，按指定的url进行查询
		if (result == null) {
			return new ElasticEndpoint(id);
		}
		return result;
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

	/**
	 * @param scriptCheckIntervalSeconds the scriptCheckIntervalSeconds to set
	 */
	public void setScriptCheckIntervalSeconds(int scriptCheckIntervalSeconds) {
		this.scriptCheckIntervalSeconds = scriptCheckIntervalSeconds;
	}

	public void setDelayCheckSeconds(int delayCheckSeconds) {
		this.delayCheckSeconds = delayCheckSeconds;
	}

	public void setTranslateCacheManager(TranslateCacheManager translateCacheManager) {
		this.translateCacheManager = translateCacheManager;
	}

	public String getDefaultElastic() {
		return defaultElastic;
	}

	public void setDefaultElastic(String defaultElastic) {
		this.defaultElastic = defaultElastic;
	}

	/**
	 * @param reservedWords the reservedWords to set
	 */
	public void setReservedWords(String reservedWords) {
		this.reservedWords = reservedWords;
	}

	/**
	 * @return the typeHandler
	 */
	public TypeHandler getTypeHandler() {
		return typeHandler;
	}

	/**
	 * @param typeHandler the typeHandler to set
	 */
	public void setTypeHandler(TypeHandler typeHandler) {
		this.typeHandler = typeHandler;
	}

	/**
	 * @param cacheType the cacheType to set
	 */
	public void setCacheType(String cacheType) {
		this.cacheType = cacheType;
	}

	public void destroy() {
		try {
			scriptLoader.destroy();
			translateManager.destroy();
		} catch (Exception e) {

		}
	}

	/**
	 * @return the dataSourceSelector
	 */
	public DataSourceSelector getDataSourceSelector() {
		return dataSourceSelector;
	}

	/**
	 * @param dataSourceSelector the dataSourceSelector to set
	 */
	public void setDataSourceSelector(DataSourceSelector dataSourceSelector) {
		this.dataSourceSelector = dataSourceSelector;
	}

	/**
	 * @return the fetchSize
	 */
	public int getFetchSize() {
		return fetchSize;
	}

	/**
	 * @param fetchSize the fetchSize to set
	 */
	public void setFetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
	}

	public void setDefaultDataSource(DataSource defaultDataSource) {
		this.defaultDataSource = defaultDataSource;
	}

	public void setDefaultDataSourceName(String defaultDataSourceName) {
		this.defaultDataSourceName = defaultDataSourceName;
	}

	public void setConnectionFactory(ConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	public Connection getConnection(DataSource datasource) {
		return connectionFactory.getConnection(datasource);
	}

	public void releaseConnection(Connection conn, DataSource dataSource) {
		connectionFactory.releaseConnection(conn, dataSource);
	}

	public void setBreakWhenSqlRepeat(boolean breakWhenSqlRepeat) {
		this.breakWhenSqlRepeat = breakWhenSqlRepeat;
	}

	public void setSecurePrivateKey(String securePrivateKey) {
		this.securePrivateKey = securePrivateKey;
	}

	public void setSecurePublicKey(String securePublicKey) {
		this.securePublicKey = securePublicKey;
	}

	public void setFieldsSecureProvider(FieldsSecureProvider fieldsSecureProvider) {
		this.fieldsSecureProvider = fieldsSecureProvider;
	}

	public FieldsSecureProvider getFieldsSecureProvider() {
		return fieldsSecureProvider;
	}

	public DesensitizeProvider getDesensitizeProvider() {
		return desensitizeProvider;
	}

	public void setDesensitizeProvider(DesensitizeProvider desensitizeProvider) {
		this.desensitizeProvider = desensitizeProvider;
	}

	public FilterHandler getCustomFilterHandler() {
		return customFilterHandler;
	}

	public void setCustomFilterHandler(FilterHandler customFilterHandler) {
		this.customFilterHandler = customFilterHandler;
	}

	public void setTranslateCaffeineManagerClass(String translateCaffeineManagerClass) {
		this.translateCaffeineManagerClass = translateCaffeineManagerClass;
	}

	public String getDistributeIdGeneratorClass() {
		return distributeIdGeneratorClass;
	}

	public void setDistributeIdGeneratorClass(String distributeIdGeneratorClass) {
		this.distributeIdGeneratorClass = distributeIdGeneratorClass;
	}

	public String getMongoQueryClass() {
		return mongoQueryClass;
	}

	public void setMongoQueryClass(String mongoQueryClass) {
		this.mongoQueryClass = mongoQueryClass;
	}

	public OverTimeSqlHandler getOverTimeSqlHandler() {
		return overTimeSqlHandler;
	}

	public void setOverTimeSqlHandler(OverTimeSqlHandler overTimeSqlHandler) {
		this.overTimeSqlHandler = overTimeSqlHandler;
	}

	/**
	 * @TODO 获取执行最慢的sql
	 * @param size     提取记录数量
	 * @param hasSqlId 是否是xml中定义含id的sql(另外一种就是代码中直接写的sql)
	 * @return
	 */
	public List<OverTimeSql> getSlowestSql(int size, boolean hasSqlId) {
		return overTimeSqlHandler.getSlowest(size, hasSqlId);
	}

	public String getColumnLabelUpperOrLower() {
		return columnLabelUpperOrLower;
	}

	public void setColumnLabelUpperOrLower(String columnLabelUpperOrLower) {
		this.columnLabelUpperOrLower = columnLabelUpperOrLower;
	}

}
