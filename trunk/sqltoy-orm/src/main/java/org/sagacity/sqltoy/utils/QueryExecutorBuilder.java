package org.sagacity.sqltoy.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.KeyAndIndex;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.DataAuthFilterConfig;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 对QueryExecutor参数进行初始化，避免之前在其内部包含过多逻辑，导致维护和理解困难
 * @author zhongxuchen
 * @version v1.0,Date:2021年10月11日
 * @modify 2023-06-11 {兼容查询:names("xxx").values(new Object[]{}),单个参数名传递的值是数组场景}
 */
public class QueryExecutorBuilder {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(QueryExecutorBuilder.class);

	public static void initQueryExecutor(SqlToyContext sqlToyContext, QueryExecutorExtend extend,
			SqlToyConfig sqlToyConfig, boolean wrapNamedArgs) {
		initQueryExecutor(sqlToyContext, extend, sqlToyConfig, wrapNamedArgs, true);
	}

	/**
	 * @TODO 统一对QueryExecutor中的设置进行处理，整理最终使用的参数和参数值，便于QueryExecutor直接获取
	 * @param sqlToyContext
	 * @param extend
	 * @param sqlToyConfig
	 * @param wrapNamedArgs  分页场景需要额外将?模式传参转换成:named模式
	 * @param filterAuthData
	 */
	public static void initQueryExecutor(SqlToyContext sqlToyContext, QueryExecutorExtend extend,
			SqlToyConfig sqlToyConfig, boolean wrapNamedArgs, boolean filterAuthData) {
		// 在分页场景下，sql以?模式传参，因分页后续要构造startIndex和endIndex参数，需将?模式全部转成:paramName模式
		if (wrapParamNames(extend, sqlToyConfig, wrapNamedArgs)) {
			return;
		}
		// 构造实际参数名称，包含sql中的参数名、cacheArgs的参数名、分库、分表的参数名称
		String[] fullParamNames = wrapFullParamNames(sqlToyConfig.getFullParamNames(), extend, sqlToyConfig);
		if (fullParamNames == null || fullParamNames.length == 0) {
			return;
		}
		int paramsNameSize = (extend.paramsName == null || extend.paramsName.length == 0) ? -1
				: extend.paramsName.length;
		int paramsValueSize = (extend.paramsValue == null || extend.paramsValue.length == 0) ? -1
				: extend.paramsValue.length;
		Object[] fullParamValues;
		// 对象传参统一将传参模式为:paramNames和paramValues
		if (extend.entity != null) {
			fullParamValues = BeanUtil.reflectBeanToAry(extend.entity, fullParamNames);
		} // 通过setValues()传递了单个非基础类型的数据对象，而未设置setNames表示vo对象传参
		else if (paramsNameSize == -1 && paramsValueSize == 1 && extend.paramsValue[0] != null
				&& !BeanUtil.isBaseDataType(extend.paramsValue[0].getClass())) {
			fullParamValues = BeanUtil.reflectBeanToAry(extend.paramsValue[0], fullParamNames);
		} else {
			// update 2023-06-11,兼容查询:names("xxx").values(new Object[]{}),单个参数名，传递的值是数组特殊场景
			// 校验条件参数合法性(排除参数名称长度为1，数据长度>1)
			if (paramsNameSize != paramsValueSize && !(paramsNameSize == 1 && paramsValueSize > 1)) {
				throw new IllegalArgumentException(
						"参数名称数组长度:" + paramsNameSize + " 和参数值数组长度:" + paramsValueSize + "不一致,请检查!");
			}
			fullParamValues = new Object[fullParamNames.length];
			String[] paramNames = extend.paramsName;
			KeyAndIndex keyAndIndex;
			String paramLow;
			// 将传递的paramValues填充到扩展后数组的对应位置
			if (paramNames != null && paramNames.length > 0) {
				Object[] paramValues;
				// 参数数量为1，数据长度大于1，将数据归并成一个单一数组值
				if (paramsNameSize == 1 && paramsValueSize > 1) {
					paramValues = new Object[] { extend.paramsValue };
				} else {
					paramValues = extend.paramsValue;
				}
				Map<String, Integer> paramIndexMap = new HashMap<String, Integer>();
				for (int i = 0; i < paramNames.length; i++) {
					paramIndexMap.put(paramNames[i].toLowerCase(), i);
				}
				for (int i = 0; i < fullParamNames.length; i++) {
					paramLow = fullParamNames[i].toLowerCase();
					if (paramIndexMap.containsKey(paramLow)) {
						fullParamValues[i] = paramValues[paramIndexMap.get(paramLow)];
					} else {
						keyAndIndex = BeanUtil.getKeyAndIndex(paramLow);
						if (keyAndIndex != null && paramIndexMap.containsKey(keyAndIndex.getKey())) {
							fullParamValues[i] = BeanUtil.getArrayIndexValue(
									paramValues[paramIndexMap.get(keyAndIndex.getKey())], keyAndIndex.getIndex());
						}
					}
				}
			}
		}
		// 统一数据权限传参
		if (filterAuthData) {
			dataAuthFilter(sqlToyContext.getUnifyFieldsHandler(), sqlToyConfig, fullParamNames, fullParamValues);
		}
		// 回写QueryExecutor中的参数值
		extend.paramsName = fullParamNames;
		extend.paramsValue = fullParamValues;
		IgnoreKeyCaseMap<String, Object> keyValueMap = new IgnoreKeyCaseMap<String, Object>();
		for (int i = 0; i < fullParamNames.length; i++) {
			if (fullParamNames[i] != null) {
				keyValueMap.put(fullParamNames[i], fullParamValues[i]);
			}
		}
		// 分表参数名称和对应值
		extend.tableShardingParams = getTableShardingParams(extend, sqlToyConfig);
		extend.tableShardingValues = wrapParamsValue(keyValueMap, extend.tableShardingParams);
		// 分库参数名称和对应值
		extend.dbShardingParams = getDbShardingParams(extend, sqlToyConfig);
		extend.dbShardingValues = wrapParamsValue(keyValueMap, extend.dbShardingParams);
	}

	/**
	 * @TODO 统一数据权限条件参数:1、前端没有传则自动填充；2、前端传值，对所传值进行是否超出授权数据范围校验,没有越权则以前端传值为准
	 * @param unifyFieldsHandler
	 * @param sqlToyConfig
	 * @param fullParamNames
	 * @param fullParamValues
	 */
	private static void dataAuthFilter(IUnifyFieldsHandler unifyFieldsHandler, SqlToyConfig sqlToyConfig,
			String[] fullParamNames, Object[] fullParamValues) {
		IgnoreKeyCaseMap<String, DataAuthFilterConfig> authFilterMap = (unifyFieldsHandler == null) ? null
				: unifyFieldsHandler.dataAuthFilters();
		if (authFilterMap == null || authFilterMap.isEmpty()) {
			return;
		}
		String paramName;
		DataAuthFilterConfig dataAuthFilter;
		for (int i = 0; i < fullParamNames.length; i++) {
			paramName = fullParamNames[i];
			if (authFilterMap.containsKey(paramName)) {
				dataAuthFilter = authFilterMap.get(paramName);
				// 实际传参值为空(或等于全选标记值)，权限过滤配置了限制范围，则将实际权限数据值填充到条件参数中
				if (StringUtil.isBlank(fullParamValues[i])
						|| equalChoiceAllValue(fullParamValues[i], dataAuthFilter.getChoiceAllValue())) {
					// 实现统一传参
					if (dataAuthFilter.getValues() != null) {
						fullParamValues[i] = dataAuthFilter.getValues();
						logger.debug("sqlId={} 参数:{} 前端未传值，由平台统一带入授权值!", sqlToyConfig.getId(), paramName);
					}
				} // 数据权限指定了值，则进行值越权校验，超出范围抛出异常
				else if (dataAuthFilter.getValues() != null && dataAuthFilter.isForcelimit()) {
					// 允许访问的值
					Object[] dataAuthed;
					if (dataAuthFilter.getValues().getClass().isArray()) {
						dataAuthed = (Object[]) dataAuthFilter.getValues();
					} else if (dataAuthFilter.getValues() instanceof Collection) {
						dataAuthed = ((Collection) dataAuthFilter.getValues()).toArray();
					} else {
						dataAuthed = new Object[] { dataAuthFilter.getValues() };
					}
					Set<Object> authSet = new HashSet<Object>();
					for (Object item : dataAuthed) {
						if (item != null) {
							if (dataAuthFilter.isIgnoreType()) {
								authSet.add(item.toString());
							} else {
								authSet.add(item);
							}
						}
					}
					// 参数直接传递的值
					Object[] pointValues;
					if (fullParamValues[i].getClass().isArray()) {
						pointValues = (Object[]) fullParamValues[i];
					} else if (fullParamValues[i] instanceof Collection) {
						pointValues = ((Collection) fullParamValues[i]).toArray();
					} else {
						pointValues = new Object[] { fullParamValues[i] };
					}
					// 校验实际传递的权限值是否在授权范围内
					for (Object paramValue : pointValues) {
						if (paramValue != null && !authSet
								.contains(dataAuthFilter.isIgnoreType() ? paramValue.toString() : paramValue)) {
							throw new DataAccessException("参数:[" + paramName + "]参数对应的值:[" + paramValue
									+ "] 超出授权范围(数据来源参见spring.sqltoy.unifyFieldsHandler配置的实现),请检查!");
						}
					}
				}
			}
		}
	}

	/**
	 * @TODO 构造sql实际使用到的全部参数名称,包括:cache-args(参数名-->别名，sql中用别名导致原参数名未被包含)、分库分表对应的参数名称
	 * @param paramNames
	 * @param queryExecutorExtend
	 * @param sqlToyConfig
	 * @return
	 */
	private static String[] wrapFullParamNames(String[] paramNames, QueryExecutorExtend queryExecutorExtend,
			SqlToyConfig sqlToyConfig) {
		Set<String> keys = new HashSet<String>();
		List<String> params = new ArrayList<String>();
		String key;
		// sql中自带的参数
		if (paramNames != null && paramNames.length > 0) {
			for (String item : paramNames) {
				key = item.toLowerCase();
				if (!keys.contains(key)) {
					keys.add(key);
					params.add(item);
				}
			}
		}
		// 分表参数(以QueryExecutor中指定的优先)
		List<ShardingStrategyConfig> tableShardings = queryExecutorExtend.tableShardings;
		// 未指定则以sql中指定的分表策略为准
		if (tableShardings == null || tableShardings.isEmpty()) {
			tableShardings = sqlToyConfig.getTableShardings();
		}
		if (tableShardings != null && tableShardings.size() > 0) {
			for (ShardingStrategyConfig shardingStrategy : tableShardings) {
				if (shardingStrategy.getFields() != null) {
					for (String item : shardingStrategy.getFields()) {
						key = item.toLowerCase();
						if (!keys.contains(key)) {
							keys.add(key);
							params.add(item);
						}
					}
				}
			}
		}
		// 分库参数
		ShardingStrategyConfig dbSharding = queryExecutorExtend.dbSharding;
		if (dbSharding == null) {
			dbSharding = sqlToyConfig.getDataSourceSharding();
		}
		if (dbSharding != null && dbSharding.getFields() != null) {
			for (String item : dbSharding.getFields()) {
				key = item.toLowerCase();
				if (!keys.contains(key)) {
					keys.add(key);
					params.add(item);
				}
			}
		}
		// 兼容参数属性只出现在cache-arg 中
		if (queryExecutorExtend.paramFilters != null && !queryExecutorExtend.paramFilters.isEmpty()) {
			for (ParamsFilter filter : queryExecutorExtend.paramFilters) {
				if ("cache-arg".equals(filter.getType())) {
					key = filter.getParams()[0].toLowerCase();
					if (!keys.contains(key)) {
						keys.add(key);
						params.add(key);
					}
					if (filter.getAsName() != null) {
						key = filter.getAsName().toLowerCase();
						if (!keys.contains(key)) {
							keys.add(key);
							params.add(key);
						}
					}
				}
			}
		}
		if (params.isEmpty()) {
			return null;
		}
		return params.toArray(new String[params.size()]);
	}

	/**
	 * @TODO 获取分表的参数名称
	 * @param queryExecutorExtend
	 * @param sqlToyConfig
	 * @return
	 */
	private static String[] getTableShardingParams(QueryExecutorExtend queryExecutorExtend, SqlToyConfig sqlToyConfig) {
		Set<String> keys = new HashSet<String>();
		List<String> params = new ArrayList<String>();
		String key;
		// 分表参数
		List<ShardingStrategyConfig> tableShardings = queryExecutorExtend.tableShardings;
		if (tableShardings == null || tableShardings.isEmpty()) {
			tableShardings = sqlToyConfig.getTableShardings();
		}
		if (tableShardings != null && tableShardings.size() > 0) {
			for (ShardingStrategyConfig shardingStrategy : tableShardings) {
				if (shardingStrategy.getFields() != null) {
					for (String item : shardingStrategy.getFields()) {
						key = item.toLowerCase();
						if (!keys.contains(key)) {
							keys.add(key);
							params.add(item);
						}
					}
				}
			}
		}
		if (params.isEmpty()) {
			return null;
		}
		return params.toArray(new String[params.size()]);
	}

	/**
	 * @TODO 获取分库的参数名称
	 * @param queryExecutorExtend
	 * @param sqlToyConfig
	 * @return
	 */
	private static String[] getDbShardingParams(QueryExecutorExtend queryExecutorExtend, SqlToyConfig sqlToyConfig) {
		Set<String> keys = new HashSet<String>();
		List<String> params = new ArrayList<String>();
		String key;
		// 分库参数
		ShardingStrategyConfig dbSharding = queryExecutorExtend.dbSharding;
		if (dbSharding == null) {
			dbSharding = sqlToyConfig.getDataSourceSharding();
		}
		if (dbSharding != null && dbSharding.getFields() != null) {
			for (String item : dbSharding.getFields()) {
				key = item.toLowerCase();
				if (!keys.contains(key)) {
					keys.add(key);
					params.add(item);
				}
			}
		}
		if (params.isEmpty()) {
			return null;
		}
		return params.toArray(new String[params.size()]);
	}

	/**
	 * @TODO 根据参数名称提取对应的值以数组返回(分库分表的参数名对应的值)
	 * @param keyValues
	 * @param wrapParaNames
	 * @return
	 */
	private static Object[] wrapParamsValue(IgnoreKeyCaseMap<String, Object> keyValues, String[] wrapParaNames) {
		if (wrapParaNames == null || wrapParaNames.length == 0) {
			return null;
		}
		Object[] result = new Object[wrapParaNames.length];
		for (int i = 0; i < wrapParaNames.length; i++) {
			result[i] = keyValues.get(wrapParaNames[i]);
		}
		return result;
	}

	/**
	 * @TODO 判断是否等于全选标记值
	 * @param paramValue
	 * @param choiceAllValue
	 * @return
	 */
	private static boolean equalChoiceAllValue(Object paramValue, Object choiceAllValue) {
		if (choiceAllValue == null || paramValue == null) {
			return false;
		}
		Object[] pointValues;
		if (paramValue.getClass().isArray()) {
			pointValues = (Object[]) paramValue;
		} else if (paramValue instanceof Collection) {
			pointValues = ((Collection) paramValue).toArray();
		} else {
			pointValues = new Object[] { paramValue };
		}
		if (pointValues.length == 1 && (choiceAllValue.equals(pointValues[0])
				|| choiceAllValue.toString().equals(pointValues[0].toString()))) {
			return true;
		}
		return false;
	}

	/**
	 * @TODO 组织在分页查询时，sql中以?模式传参，统一成:named 模式，便于后面插入分页开始行截止行参数，并使用pst预编译功能
	 * @param queryExecutorExtend
	 * @param sqlToyConfig
	 * @param wrapNamedArgs
	 * @return
	 */
	private static boolean wrapParamNames(QueryExecutorExtend queryExecutorExtend, SqlToyConfig sqlToyConfig,
			boolean wrapNamedArgs) {
		// :named 模式传参
		if (sqlToyConfig.isNamedParam()
				|| (queryExecutorExtend.paramsName != null && queryExecutorExtend.paramsName.length > 0)) {
			return false;
		}
		// ?参数个数
		int argCount = StringUtil.matchCnt(SqlConfigParseUtils.clearDblQuestMark(sqlToyConfig.getSql()),
				SqlConfigParseUtils.ARG_REGEX);
		// 存在?传参
		if (argCount > 0) {
			// 验证传参数量合法性
			int valuesSize = (queryExecutorExtend.paramsValue == null) ? 0 : queryExecutorExtend.paramsValue.length;
			// update 2022-7-18 增强单? 且为in (?) 模式传参兼容性处理
			if (argCount == 1 && valuesSize > 1
					&& StringUtil.matches(sqlToyConfig.getSql(), SqlConfigParseUtils.IN_PATTERN)) {
				queryExecutorExtend.paramsValue = new Object[] { queryExecutorExtend.paramsValue };
				valuesSize = 1;
			}

			// 分页需要将?转参数名称模式
			if (wrapNamedArgs) {
				// 只在分页场景下校验
				if (argCount != valuesSize) {
					throw new IllegalArgumentException("参数值数量:" + valuesSize + " 跟sql中的?条件数量" + argCount
							+ "不匹配,请检查,如是json或sql中存在?特殊字符但无实际条件参数场景，可通过虚构一个条件参数如where #[1=:flag]解决!");
				}
				String[] paramsName = new String[argCount];
				for (int i = 0; i < argCount; i++) {
					paramsName[i] = SqlToyConstants.DEFAULT_PARAM_NAME + (i + 1);
				}
				queryExecutorExtend.paramsName = paramsName;
				queryExecutorExtend.wrappedParamNames = true;
			}
			return true;
		} // 无:name且无?，且参数是空Map，将参数值设置为null(2023-10-07)
		else if (argCount == 0) {
			// null instanceof Map 是false
			if (queryExecutorExtend.paramsValue != null && queryExecutorExtend.paramsValue.length == 1
					&& ((queryExecutorExtend.paramsValue[0] instanceof Map)
							&& ((Map) queryExecutorExtend.paramsValue[0]).isEmpty())) {
				queryExecutorExtend.paramsValue = null;
			}
		}
		return false;
	}

	/**
	 * @TODO 將QueryExecutor中的条件参数构造成单一对象返回(map或entity)
	 * @param queryExecutor
	 * @return
	 */
	public static Object getParamValues(QueryExecutor queryExecutor) {
		Object result = null;
		if (queryExecutor != null) {
			QueryExecutorExtend queryExecutorExtend = queryExecutor.getInnerModel();
			if (queryExecutorExtend.entity != null) {
				result = queryExecutorExtend.entity;
			} else {
				String[] paramsName = queryExecutorExtend.paramsName;
				Object[] paramsValue = queryExecutorExtend.paramsValue;
				if (paramsName != null && paramsValue != null && paramsName.length > 0
						&& paramsName.length == paramsValue.length) {
					IgnoreKeyCaseMap map = new IgnoreKeyCaseMap();
					for (int i = 0; i < paramsName.length; i++) {
						map.put(paramsName[i], paramsValue[i]);
					}
					result = map;
				} else if ((paramsName == null || paramsName.length == 0) && paramsValue != null
						&& paramsValue.length == 1 && paramsValue[0] != null) {
					if (paramsValue[0] instanceof IgnoreKeyCaseMap) {
						result = paramsValue[0];
					} else if (paramsValue[0] instanceof Map) {
						result = new IgnoreKeyCaseMap((Map) paramsValue[0]);
					} else {
						result = paramsValue[0];
					}
				}
			}
		}
		return result;
	}
}
