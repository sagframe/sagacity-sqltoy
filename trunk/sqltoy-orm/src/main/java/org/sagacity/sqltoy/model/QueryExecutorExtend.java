/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.ParamFilterModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.ParamFilterUtils;

/**
 * @project sqltoy-orm
 * @description 针对QueryExecutor构造一个存放参数的内部类，避免QueryExecutor使用时带出大量的get方法
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:QueryExecutorExtend.java,Revision:v1.0,Date:2020-8-1
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
	 * 原生数值
	 */
	public Object[] shardingParamsValue;

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
	public ReflectPropertyHandler reflectPropertyHandler;

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

	// 分库分表策略配置
	// public ShardingConfig shardingConfig = new ShardingConfig();

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getParamsName(SqlToyConfig sqlToyConfig) {
		if (entity == null) {
			if (paramsName == null || paramsName.length == 0) {
				return sqlToyConfig.getParamsName();
			}
			return paramsName;
		}
		return sqlToyConfig.getFullParamNames();
	}

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getTableShardingParamsName(SqlToyConfig sqlToyConfig) {
		if (entity == null) {
			if (paramsName == null || paramsName.length == 0) {
				return sqlToyConfig.getParamsName();
			}
			return paramsName;
		}
		return sqlToyConfig.getTableShardingParams();
	}

	/**
	 * @param sqlToyConfig
	 * @return
	 */
	public String[] getDataSourceShardingParamsName(SqlToyConfig sqlToyConfig) {
		if (entity == null) {
			if (paramsName == null || paramsName.length == 0) {
				return sqlToyConfig.getParamsName();
			}
			return paramsName;
		}
		return sqlToyConfig.getDataSourceShardingParams();
	}

	/**
	 * @todo 获取sql中参数对应的值
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getParamsValue(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig) throws Exception {
		Object[] realValues = null;
		// 是否萃取过
		if (!extracted) {
			if (entity != null) {
				paramsValue = BeanUtil.reflectBeanToAry(entity, sqlToyConfig.getFullParamNames(), null,
						reflectPropertyHandler);
			}
			extracted = true;
		}
		if (paramsValue != null) {
			realValues = paramsValue.clone();
		}
		// 过滤加工参数值
		if (realValues != null) {
			// 整合sql中定义的filters和代码中扩展的filters
			List<ParamFilterModel> filters = ParamFilterUtils.combineFilters(sqlToyConfig.getFilters(), paramFilters);
			realValues = ParamFilterUtils.filterValue(sqlToyContext, getParamsName(sqlToyConfig), realValues, filters);
		} else {
			// update 2017-4-11,默认参数值跟参数数组长度保持一致,并置为null
			String[] names = getParamsName(sqlToyConfig);
			if (names != null && names.length > 0) {
				realValues = new Object[names.length];
			}
		}
		return realValues;
	}

	/**
	 * @todo 获取分表时传递给分表策略的参数值
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getTableShardingParamsValue(SqlToyConfig sqlToyConfig) throws Exception {
		if (entity != null) {
			return BeanUtil.reflectBeanToAry(entity, sqlToyConfig.getTableShardingParams(), null,
					reflectPropertyHandler);
		}
		return shardingParamsValue;
	}

	/**
	 * @todo 获取分库时传递给分库策略的参数值(策略会根据值通过逻辑返回具体的库)
	 * @param sqlToyConfig
	 * @return
	 * @throws Exception
	 */
	public Object[] getDataSourceShardingParamsValue(SqlToyConfig sqlToyConfig) throws Exception {
		if (entity != null) {
			return BeanUtil.reflectBeanToAry(entity, sqlToyConfig.getDataSourceShardingParams(), null,
					reflectPropertyHandler);
		}
		return shardingParamsValue;
	}

	/**
	 * @TODO 用于cache-arg模式下传参数容易漏掉alias-name 的场景
	 * @param sqlToyConfig
	 */
	public void optimizeArgs(SqlToyConfig sqlToyConfig) {
		if (sqlToyConfig.getCacheArgNames().isEmpty() || entity != null) {
			return;
		}
		// 只有这种场景下需要校正参数
		if (paramsName != null && paramsValue != null) {
			List<String> tmp = new ArrayList<String>();
			boolean exist = false;
			for (String comp : sqlToyConfig.getCacheArgNames()) {
				exist = false;
				for (String param : paramsName) {
					if (param.toLowerCase().equals(comp)) {
						exist = true;
						break;
					}
				}
				if (!exist) {
					tmp.add(comp);
				}
			}
			if (tmp.isEmpty()) {
				return;
			}
			// 补全额外的参数名称,对应的值则为null
			String[] realParams = new String[paramsName.length + tmp.size()];
			Object[] realValues = new Object[paramsValue.length + tmp.size()];
			System.arraycopy(paramsName, 0, realParams, 0, paramsName.length);
			int index = paramsName.length;
			for (String extParam : tmp) {
				realParams[index] = extParam;
				index++;
			}
			System.arraycopy(paramsValue, 0, realValues, 0, paramsValue.length);
			paramsName = realParams;
			paramsValue = realValues;
			shardingParamsValue = realValues;
		}
	}

}
