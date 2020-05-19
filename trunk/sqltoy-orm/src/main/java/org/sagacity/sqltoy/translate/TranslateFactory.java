/**
 * 
 */
package org.sagacity.sqltoy.translate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.HttpClientUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * @project sagacity-sqltoy4.2
 * @description 缓存刷新检测接口定义
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:TranslateFactory.java,Revision:v1.0,Date:2018年3月8日
 */
public class TranslateFactory {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateFactory.class);

	/**
	 * @todo 执行检测,返回缓存相关数据最后修改时间,便于比较是否发生变化
	 * @param sqlToyContext
	 * @param config
	 * @param preCheckTime
	 * @return
	 */
	public static List<CacheCheckResult> doCheck(final SqlToyContext sqlToyContext, final CheckerConfigModel config,
			Timestamp preCheckTime) {
		List result = null;
		try {
			if (config.getType().equals("sql")) {
				result = doSqlCheck(sqlToyContext, config, preCheckTime);
			} else if (config.getType().equals("service")) {
				result = doServiceCheck(sqlToyContext, config, preCheckTime);
			} else if (config.getType().equals("rest")) {
				result = doRestCheck(sqlToyContext, config, preCheckTime);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("执行缓存变更检测发生错误,错误信息:{}", e.getMessage());
		}

		// 增量更新模式
		if (config.isIncrement()) {
			return wrapIncrementCheckResult(result, config);
		}
		// 清空模式
		return wrapClearCheckResult(result, config);
	}

	/**
	 * @todo 执行sql检测
	 * @param sqlToyContext
	 * @param config
	 * @param preCheckTime
	 * @return
	 * @throws Exception
	 */
	private static List doSqlCheck(final SqlToyContext sqlToyContext, final CheckerConfigModel config,
			Timestamp preCheckTime) throws Exception {
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(config.getSql(), SqlType.search);
		String dataSourceName = config.getDataSource();
		if (dataSourceName == null) {
			dataSourceName = sqlToyConfig.getDataSource();
		}
		return DialectFactory.getInstance()
				.findByQuery(sqlToyContext,
						new QueryExecutor(config.getSql(), sqlToyConfig.getParamsName(),
								new Object[] { new Date(preCheckTime.getTime()) }),
						sqlToyConfig, null, StringUtil.isBlank(dataSourceName) ? sqlToyContext.getDefaultDataSource()
								: sqlToyContext.getDataSource(dataSourceName))
				.getRows();
	}

	/**
	 * @todo 执行基于service调用的检测
	 * @param sqlToyContext
	 * @param config
	 * @param preCheckTime
	 * @return
	 * @throws Exception
	 */
	private static List doServiceCheck(final SqlToyContext sqlToyContext, final CheckerConfigModel config,
			Timestamp preCheckTime) throws Exception {
		return (List) sqlToyContext.getServiceData(config.getService(), config.getMethod(),
				new Object[] { preCheckTime });
	}

	/**
	 * @todo 执行基于rest请求模式的缓存更新检测
	 * @param sqlToyContext
	 * @param config
	 * @param preCheckTime
	 * @return
	 * @throws Exception
	 */
	private static List doRestCheck(final SqlToyContext sqlToyContext, final CheckerConfigModel config,
			Timestamp preCheckTime) throws Exception {
		String jsonStr = HttpClientUtils.doPost(sqlToyContext, config.getUrl(), config.getUsername(),
				config.getPassword(), "lastUpdateTime", DateUtil.formatDate(preCheckTime, "yyyy-MM-dd HH:mm:ss.SSS"));
		List result = null;
		if (jsonStr != null) {
			boolean fatal = false;
			try {
				result = JSON.parseArray(jsonStr, CacheCheckResult.class);
			} catch (Exception e) {
				fatal = true;
			}
			if (fatal) {
				try {
					result = JSON.parseArray(jsonStr, Object[].class);
					fatal = false;
				} catch (Exception e) {
					fatal = true;
				}
			}
			if (fatal) {
				logger.warn("rest模式检测缓存是否更新数据格式转换异常,数据格式是数组或CacheCheckResult对象类型的数组!");
			}
		}
		return result;
	}

	/**
	 * @todo 包装检测结果为统一的对象集合
	 * @param result
	 * @param config
	 * @return
	 */
	private static List<CacheCheckResult> wrapClearCheckResult(List result, CheckerConfigModel config) {
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.get(0) instanceof CacheCheckResult) {
			return result;
		}
		List<Object[]> cacheSet = null;
		if (result.get(0) instanceof Object[]) {
			cacheSet = result;
		} else if (result.get(0) instanceof List) {
			cacheSet = CollectionUtil.innerListToArray(result);
		} else if (config.getProperties() != null && config.getProperties().length > 0) {
			cacheSet = BeanUtil.reflectBeansToInnerAry(result, config.getProperties(), null, null, false, 0);
		}
		if (cacheSet != null) {
			List<CacheCheckResult> checkResult = new ArrayList<CacheCheckResult>();
			Object[] row;
			for (int i = 0; i < cacheSet.size(); i++) {
				row = (Object[]) cacheSet.get(i);
				CacheCheckResult item = new CacheCheckResult();
				item.setCacheName((String) row[0]);
				if (row.length > 1) {
					item.setCacheType((String) row[1]);
				}
				checkResult.add(item);
			}
			return checkResult;
		}
		return null;
	}

	/**
	 * @todo 包装检测结果为统一的对象集合
	 * @param result
	 * @param config
	 * @return
	 */
	private static List<CacheCheckResult> wrapIncrementCheckResult(List result, CheckerConfigModel config) {
		if (result == null || result.isEmpty()) {
			return null;
		}
		if (result.get(0) instanceof CacheCheckResult) {
			return result;
		}
		List<Object[]> cacheSet = null;
		if (result.get(0) instanceof List) {
			cacheSet = CollectionUtil.innerListToArray(result);
		} else if (result.get(0) instanceof Object[]) {
			cacheSet = result;
		} else if (config.getProperties() != null && config.getProperties().length > 0) {
			cacheSet = BeanUtil.reflectBeansToInnerAry(result, config.getProperties(), null, null, false, 0);
		}
		if (cacheSet != null) {
			String cacheName = config.getCache();
			boolean hasInsideGroup = config.isHasInsideGroup();
			List<CacheCheckResult> checkResult = new ArrayList<CacheCheckResult>();
			Object[] row;
			for (int i = 0; i < cacheSet.size(); i++) {
				row = (Object[]) cacheSet.get(i);
				CacheCheckResult item = new CacheCheckResult();
				item.setCacheName(cacheName);
				// 缓存内部存在分组(参考数据字典表中的字典分类)
				if (hasInsideGroup) {
					item.setCacheType((String) row[0]);
					Object[] cacheValue = new Object[row.length - 1];
					// 跳过第一列缓存类别
					System.arraycopy(row, 1, cacheValue, 0, row.length - 1);
					item.setItem(cacheValue);
				} else {
					item.setItem(row);
				}
				checkResult.add(item);
			}
			return checkResult;
		}
		return null;
	}

	/**
	 * @todo 重新查询获取缓存数据
	 * @param sqlToyContext
	 * @param cacheModel
	 * @param cacheType
	 * @return
	 */
	public static HashMap<String, Object[]> getCacheData(final SqlToyContext sqlToyContext,
			TranslateConfigModel cacheModel, String cacheType) {
		Object result = null;
		try {
			if (cacheModel.getType().equals("sql")) {
				result = getSqlCacheData(sqlToyContext, cacheModel, cacheType);
			} else if (cacheModel.getType().equals("service")) {
				result = getServiceCacheData(sqlToyContext, cacheModel, cacheType);
			} else if (cacheModel.getType().equals("rest")) {
				result = getRestCacheData(sqlToyContext, cacheModel, cacheType);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("获取缓存数据失败,返回结果应该是List<List> 或List<Object[]> 或 Map<String,Object[]> 类型,错误信息:{}",
					e.getMessage());
		}
		HashMap<String, Object[]> cacheData = wrapCacheResult(result, cacheModel);
		// 增加错误日志提醒
		if (cacheData == null || cacheData.isEmpty()) {
			logger.error("缓存cacheName={} 数据集为空,请检查对应的配置和查询逻辑是否正确!", cacheModel.getCache());
		}
		return cacheData;
	}

	/**
	 * @todo 通过sql查询获取缓存数据
	 * @param sqlToyContext
	 * @param cacheModel
	 * @param cacheType
	 * @return
	 * @throws Exception
	 */
	private static List getSqlCacheData(final SqlToyContext sqlToyContext, TranslateConfigModel cacheModel,
			String cacheType) throws Exception {
		final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(cacheModel.getSql(), SqlType.search);
		String dataSourceName = cacheModel.getDataSource();
		if (dataSourceName == null) {
			dataSourceName = sqlToyConfig.getDataSource();
		}
		QueryExecutor queryExecutor = null;
		if (StringUtil.isBlank(cacheType)) {
			queryExecutor = new QueryExecutor(cacheModel.getSql());
		} else {
			queryExecutor = new QueryExecutor(cacheModel.getSql(), sqlToyConfig.getParamsName(),
					new Object[] { cacheType.trim() });
		}
		return DialectFactory.getInstance()
				.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null,
						StringUtil.isBlank(dataSourceName) ? sqlToyContext.getDefaultDataSource()
								: sqlToyContext.getDataSource(dataSourceName))
				.getRows();
	}

	/**
	 * @todo 基于service bean 调用方式获取缓存数据
	 * @param sqlToyContext
	 * @param cacheModel
	 * @param cacheType
	 * @return
	 * @throws Exception
	 */
	private static Object getServiceCacheData(final SqlToyContext sqlToyContext, TranslateConfigModel cacheModel,
			String cacheType) throws Exception {
		return sqlToyContext.getServiceData(cacheModel.getService(), cacheModel.getMethod(),
				StringUtil.isBlank(cacheType) ? new Object[] {} : new Object[] { cacheType.trim() });
	}

	/**
	 * @todo 基于rest http 请求获取缓存数据
	 * @param sqlToyContext
	 * @param cacheModel
	 * @param cacheType
	 * @return
	 * @throws Exception
	 */
	private static List<Object[]> getRestCacheData(final SqlToyContext sqlToyContext, TranslateConfigModel cacheModel,
			String cacheType) throws Exception {
		String jsonStr = HttpClientUtils.doPost(sqlToyContext, cacheModel.getUrl(), cacheModel.getUsername(),
				cacheModel.getPassword(), "type", StringUtil.isBlank(cacheType) ? null : cacheType.trim());
		if (jsonStr != null) {
			if (cacheModel.getProperties() == null || cacheModel.getProperties().length == 0) {
				return JSON.parseArray(jsonStr, Object[].class);
			}
			JSONArray jsonSet = JSON.parseArray(jsonStr);
			if (jsonSet.isEmpty()) {
				return null;
			}
			List<Object[]> result = new ArrayList<Object[]>();
			int size = cacheModel.getProperties().length;
			JSONObject jsonObj;
			for (Object obj : jsonSet) {
				jsonObj = (JSONObject) obj;
				Object[] row = new Object[size];
				for (int i = 0; i < size; i++) {
					row[i] = jsonObj.get(cacheModel.getProperties()[i]);
				}
				result.add(row);
			}
			return result;
		}
		return null;
	}

	/**
	 * @todo 包装结果，转化为统一的格式
	 * @param target
	 * @param cacheModel
	 * @return
	 */
	private static HashMap<String, Object[]> wrapCacheResult(Object target, TranslateConfigModel cacheModel) {
		if (target == null) {
			return null;
		}
		if (target instanceof HashMap && ((HashMap) target).isEmpty()) {
			return null;
		}
		if (target instanceof HashMap && ((HashMap) target).values().iterator().next().getClass().isArray()) {
			return (HashMap<String, Object[]>) target;
		}
		LinkedHashMap<String, Object[]> result = new LinkedHashMap<String, Object[]>();
		if (target instanceof HashMap) {
			if (!((HashMap) target).isEmpty()) {
				if (((HashMap) target).values().iterator().next() instanceof List) {
					Iterator<Map.Entry<String, List>> iter = ((HashMap<String, List>) target).entrySet().iterator();
					while (iter.hasNext()) {
						Map.Entry<String, List> entry = iter.next();
						Object[] row = new Object[entry.getValue().size()];
						entry.getValue().toArray(row);
						result.put(entry.getKey(), row);
					}
				} else {
					Iterator<Map.Entry<String, Object>> iter = ((HashMap<String, Object>) target).entrySet().iterator();
					while (iter.hasNext()) {
						Map.Entry<String, Object> entry = iter.next();
						result.put(entry.getKey(), new Object[] { entry.getKey(), entry.getValue() });
					}
				}
			}
		} else if (target instanceof List) {
			List tempList = (List) target;
			if (!tempList.isEmpty()) {
				int cacheIndex = cacheModel.getKeyIndex();
				if (tempList.get(0) instanceof List) {
					List row;
					for (int i = 0, n = tempList.size(); i < n; i++) {
						row = (List) tempList.get(i);
						Object[] rowAry = new Object[row.size()];
						row.toArray(rowAry);
						result.put(rowAry[cacheIndex].toString(), rowAry);
					}
				} else if (tempList.get(0) instanceof Object[]) {
					Object[] row;
					for (int i = 0, n = tempList.size(); i < n; i++) {
						row = (Object[]) tempList.get(i);
						result.put(row[cacheIndex].toString(), row);
					}
				} // 对象数组，利用反射提取属性值
				else if (cacheModel.getProperties() != null && cacheModel.getProperties().length > 1) {
					List<Object[]> dataSet = BeanUtil.reflectBeansToInnerAry(tempList, cacheModel.getProperties(), null,
							null, false, 0);
					for (Object[] row : dataSet) {
						result.put(row[cacheIndex].toString(), row);
					}
				}
			}
		}
		return result;
	}
}
