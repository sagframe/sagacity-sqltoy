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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.translate.model.CacheCheckResult;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.HttpClientUtils;
import org.sagacity.sqltoy.utils.StringUtil;

import com.alibaba.fastjson.JSON;

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
	protected final static Logger logger = LogManager.getLogger(TranslateFactory.class);

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
		return wrapCheckResult(result);
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
		if (dataSourceName == null)
			dataSourceName = sqlToyConfig.getDataSource();
		return DialectFactory.getInstance()
				.findByQuery(sqlToyContext,
						new QueryExecutor(config.getSql(), sqlToyConfig.getParamsName(),
								new Object[] { new Date(preCheckTime.getTime()) }),
						StringUtil.isBlank(dataSourceName) ? sqlToyContext.getDefaultDataSource()
								: sqlToyContext.getDataSource(dataSourceName))
				.getRows();
	}

	/**
	 * @todo 执行sql检测
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
	 * @todo 执行sql检测
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
	 * @return
	 */
	private static List<CacheCheckResult> wrapCheckResult(List result) {
		if (result == null || result.isEmpty())
			return null;
		if (result.get(0) instanceof CacheCheckResult)
			return result;
		List<CacheCheckResult> checkResult = new ArrayList<CacheCheckResult>();
		if (result.get(0) instanceof Object[]) {
			Object[] row;
			for (int i = 0; i < result.size(); i++) {
				row = (Object[]) result.get(i);
				CacheCheckResult item = new CacheCheckResult();
				item.setCacheName((String) row[0]);
				if (row.length > 1)
					item.setCacheType((String) row[1]);
				checkResult.add(item);
			}
			return checkResult;
		} else if (result.get(0) instanceof List) {
			List row;
			for (int i = 0; i < result.size(); i++) {
				row = (List) result.get(i);
				CacheCheckResult item = new CacheCheckResult();
				item.setCacheName((String) row.get(0));
				if (row.size() > 1)
					item.setCacheType((String) row.get(1));
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
		return wrapCacheResult(result, cacheModel);
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
		if (dataSourceName == null)
			dataSourceName = sqlToyConfig.getDataSource();
		QueryExecutor queryExecutor = null;
		if (StringUtil.isBlank(cacheType))
			queryExecutor = new QueryExecutor(cacheModel.getSql());
		else
			queryExecutor = new QueryExecutor(cacheModel.getSql(), sqlToyConfig.getParamsName(),
					new Object[] { cacheType.trim() });
		return DialectFactory.getInstance()
				.findByQuery(sqlToyContext, queryExecutor,
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
		if (jsonStr != null)
			return JSON.parseArray(jsonStr, Object[].class);
		return null;
	}

	/**
	 * @todo 包装结果，转化为统一的格式
	 * @param target
	 * @param cacheModel
	 * @return
	 */
	private static HashMap<String, Object[]> wrapCacheResult(Object target, TranslateConfigModel cacheModel) {
		if (target == null)
			return null;
		if (target instanceof HashMap && ((HashMap) target).isEmpty())
			return null;
		if (target instanceof HashMap && ((HashMap) target).values().iterator().next().getClass().isArray())
			return (HashMap<String, Object[]>) target;
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
				}
			}
		}
		return result;
	}
}
