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
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
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
	 * 查询结果类型
	 */
	public Type resultType;

	/**
	 * 结果为map时标题是否变成驼峰模式
	 */
	public Boolean humpMapLabel;

	/**
	 * 特定数据库连接资源
	 */
	public DataSource dataSource;

	/**
	 * 是否已经提取过value值
	 */
	public boolean extracted = false;

	/**
	 * 将结果封装成父子对象级联模式，one ->many 或 one-one
	 */
	public boolean hiberarchy = false;

	/**
	 * 体现层次的类型
	 */
	public Class[] hiberarchyClasses;

	/**
	 * 动态增加缓存翻译配置
	 */
	public List<Translate> translates = new ArrayList<Translate>();

	/**
	 * 动态设置filters
	 */
	public List<ParamsFilter> paramFilters = new ArrayList<ParamsFilter>();

	/**
	 * 用于对象层次结构封装指定sql中label对应类的属性
	 */
	public Map<Class, IgnoreKeyCaseMap<String, String>> fieldsMap = new HashMap<Class, IgnoreKeyCaseMap<String, String>>();

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

	/**
	 * 执行时是否输出sql 日志
	 */
	public Boolean showSql;
	/**
	 * 标记基于单表的简单操作
	 */
	public Class entityClass = null;

	/**
	 * 是否是sql片段(正常无需使用)
	 */
	public boolean sqlSegment = false;

	// 分表策略配置
	public List<ShardingStrategyConfig> tableShardings = new ArrayList<ShardingStrategyConfig>();

	/**
	 * @return
	 */
	public String[] getParamsName() {
		return paramsName;
	}

	/**
	 * @return
	 */
	public String[] getTableShardingParamsName() {
		return tableShardingParams;
	}

	/**
	 * @return
	 */
	public String[] getDataSourceShardingParamsName() {
		return dbShardingParams;
	}

	/**
	 * 为什么不在QueryExecutorBuilder中直接初始化,因为sqltoy中有一个特殊场景:catalog-sql即一个查询过程中会执行2个不同sql
	 * 
	 * @todo 获取sql中参数对应的值
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @return
	 */
	public Object[] getParamsValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig) {
		// 整合sql中定义的filters和代码中扩展的filters
		List<ParamFilterModel> filters = ParamFilterUtils.combineFilters(sqlToyConfig.getFilters(), paramFilters);
		// 调用sql配置的filter对最终参与查询的值进行处理，设置相应值为null实现部分条件sql不参与执行
		return ParamFilterUtils.filterValue(sqlToyContext, paramsName, paramsValue, filters);
	}

	/**
	 * @todo 获取分表时传递给分表策略的参数值
	 * @return
	 */
	public Object[] getTableShardingParamsValue() {
		return tableShardingValues;
	}

	/**
	 * @todo 获取分库时传递给分库策略的参数值(策略会根据值通过逻辑返回具体的库)
	 * @return
	 */
	public Object[] getDataSourceShardingParamsValue() {
		return dbShardingValues;
	}

	/**
	 * @todo 拼换某列,mysql中等同于Broup_concat\oracle 中的WMSWS,HN_CONCAT功能
	 * @return
	 */
	public LinkModel linkModel;
}
