/**
 * 
 */
package org.sagacity.sqltoy.model.inner;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.sagacity.sqltoy.utils.ParamFilterUtils;

/**
 * @project sqltoy-orm
 * @description 针对QueryExecutor构造一个存放参数的内部类，避免QueryExecutor使用时带出大量的get方法
 * @author zhongxuchen
 * @version v1.0,Date:2020-8-1
 */
public class QueryExecutorExtend implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5753363607896705740L;

	/**
	 * 实体对象
	 */
	public Serializable entity;

	/**
	 * sql中参数名称
	 */
	public String[] paramsName;

	/**
	 * sql中参数名称对应的值
	 */
	public Object[] paramsValue;

	/**
	 * sql语句或sqlId
	 */
	public String sql;

	/**
	 * jdbc 查询时默认加载到内存中的记录数量 -1表示不设置，采用数据库默认的值
	 */
	public int fetchSize = -1;

	/**
	 * jdbc查询最大返回记录数量
	 */
	public int maxRows = -1;

	/**
	 * 结果集反调处理(已经极少极少使用,可以废弃)
	 */
	@Deprecated
	public RowCallbackHandler rowCallbackHandler;
	
	/**
	 * 查询属性值反射处理
	 */
	@Deprecated
	public ReflectPropsHandler reflectPropsHandler;

	/**
	 * 查询结果类型
	 */
	public Type resultType;

	/**
	 * 结果为map时标题是否变成驼峰模式
	 */
	public boolean humpMapLabel = true;

	/**
	 * 特定数据库连接资源
	 */
	public DataSource dataSource;

	/**
	 * 是否已经提取过value值
	 */
	public boolean extracted = false;

	/**
	 * 动态增加缓存翻译配置
	 */
	public HashMap<String, Translate> translates = new HashMap<String, Translate>();

	/**
	 * 动态设置filters
	 */
	public List<ParamsFilter> paramFilters = new ArrayList<ParamsFilter>();

	/**
	 * 对字段进行安全脱敏
	 */
	public LinkedHashMap<String, SecureMask> secureMask = new LinkedHashMap<String, SecureMask>();

	/**
	 * 列格式模型
	 */
	public LinkedHashMap<String, FormatModel> colsFormat = new LinkedHashMap<String, FormatModel>();

	// 行转列、列转行等
	public List calculators = new ArrayList();

	/**
	 * 分页优化模型
	 */
	public PageOptimize pageOptimize;

	/**
	 * 空白字符转为null，默认为true
	 */
	public boolean blankToNull = true;

	/**
	 * 锁表
	 */
	public LockMode lockMode = null;
	
	/**
	 * 是否构造过条件参数名称
	 */
	public boolean wrappedParamNames = false;

	/**
	 * 自定义countSql
	 */
	public String countSql;

	public String[] tableShardingParams;

	public String[] dbShardingParams;

	public Object[] tableShardingValues;

	public Object[] dbShardingValues;

	// 分库策略配置
	public ShardingStrategyConfig dbSharding;

	// 分表策略配置
	public List<ShardingStrategyConfig> tableShardings = new ArrayList<ShardingStrategyConfig>();

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getParamsName(SqlToyConfig sqlToyConfig) {
		return paramsName;
	}

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getTableShardingParamsName(SqlToyConfig sqlToyConfig) {
		return tableShardingParams;
	}

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getDataSourceShardingParamsName(SqlToyConfig sqlToyConfig) {
		return dbShardingParams;
	}

	/**
	 * 为什么不在QueryExecutorBuilder中直接初始化,因为sqltoy中有一个特殊场景:catalog-sql即一个查询过程中会执行2个不同sql
	 * 
	 * @todo 获取sql中参数对应的值
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getParamsValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig) throws Exception {
		// 整合sql中定义的filters和代码中扩展的filters
		List<ParamFilterModel> filters = ParamFilterUtils.combineFilters(sqlToyConfig.getFilters(), paramFilters);
		// 调用sql配置的filter对最终参与查询的值进行处理，设置相应值为null实现部分条件sql不参与执行
		return ParamFilterUtils.filterValue(sqlToyContext, paramsName, paramsValue, filters);
	}

	/**
	 * @todo 获取分表时传递给分表策略的参数值
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getTableShardingParamsValue(SqlToyConfig sqlToyConfig) throws Exception {
		return tableShardingValues;
	}

	/**
	 * @todo 获取分库时传递给分库策略的参数值(策略会根据值通过逻辑返回具体的库)
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getDataSourceShardingParamsValue(SqlToyConfig sqlToyConfig) throws Exception {
		return dbShardingValues;
	}
}
