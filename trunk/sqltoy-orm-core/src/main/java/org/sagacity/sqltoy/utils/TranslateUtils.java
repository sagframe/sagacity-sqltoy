package org.sagacity.sqltoy.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.sagacity.sqltoy.translate.FieldTranslateCacheHolder;
import org.sagacity.sqltoy.translate.TranslateManager;
import org.sagacity.sqltoy.translate.model.BatchDynamicCache;
import org.sagacity.sqltoy.translate.model.DynamicCacheHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 对结果翻译提供一个工具类
 * @author zhongxuchen
 * @version v1.0,Date:2024-12-28
 * @modify 2026-1-16 开放dynamicCacheFetch功能
 * @modify 2026-1-22 完善批量查询，批量翻译功能
 */
public class TranslateUtils {

	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateUtils.class);

	/**
	 * @date 2018-5-26 优化缓存翻译，提供keyCode1,keyCode2,keyCode3 形式的多代码翻译
	 * @todo 统一对key进行缓存翻译
	 * @param translateExtend
	 * @param dynamicCacheFetch
	 * @param dynamicCacheHolder 用于判断是否暂停逐行翻译，并记录未匹配的key
	 * @param cacheData
	 * @param fieldValue
	 * @return
	 */
	public static Object translateKey(TranslateExtend translateExtend, DynamicCacheFetch dynamicCacheFetch,
			DynamicCacheHolder dynamicCacheHolder, HashMap<String, Object[]> cacheData, Object fieldValue) {
		String fieldStr = fieldValue.toString();
		// 单值翻译
		if (translateExtend.splitRegex == null) {
			// ${key}_ZH_CN 用于组合匹配缓存,类似i18n场景
			if (translateExtend.keyTemplate != null) {
				// keyTemplate已经提前做了规整,将${key},${},${0} 统一成了{}
				fieldStr = translateExtend.keyTemplate.replace("{}", fieldStr);
			}
			// 根据key获取缓存值
			Object[] cacheValues = cacheData.get(fieldStr);
			if (cacheValues == null) {
				// 动态查询缓存数据
				if (translateExtend.dynamicCache) {
					String realCacheNameAndType = dynamicCacheHolder
							.getRealCacheNameAndType(translateExtend.cacheNameAndType);
					// 暂停逐条查询数据，将未匹配到的key放入Set，后续批量查询，减少查询次数
					if (dynamicCacheHolder.isPauseTranslate(realCacheNameAndType)) {
						// 未匹配的key加入到集合，后续整体查询
						dynamicCacheHolder.addNotMatchedKey(realCacheNameAndType, fieldStr);
						// 值赋予原始key，后续再批量翻译
						fieldValue = fieldStr;
					} else {
						String realCacheType = dynamicCacheHolder.getRealCacheType(translateExtend.cacheNameAndType);
						// 动态获取缓存数据并放入缓存
						cacheValues = dynamicCacheFetch.getCache(translateExtend.cache, realCacheType,
								translateExtend.cacheSid, translateExtend.cacheProperties, fieldStr);
						if (cacheValues != null) {
							// 匹配key获取name
							fieldValue = cacheValues[translateExtend.index];
							// 放入缓存
							cacheData.put(fieldStr, cacheValues);
						} else {
							// 定义未匹配模板则不输出日志
							if (translateExtend.uncached != null) {
								fieldValue = translateExtend.uncached.replace("${value}", fieldStr);
							} else {
								fieldValue = fieldStr;
								logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!",
										translateExtend.cache, realCacheType, fieldValue);
							}
						}
					}
				} else {
					// 定义未匹配模板则不输出日志
					if (translateExtend.uncached != null) {
						fieldValue = translateExtend.uncached.replace("${value}", fieldStr);
					} else {
						fieldValue = fieldValue.toString();
						logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!", translateExtend.cache,
								translateExtend.cacheType, fieldValue);
					}
				}
			} else {
				fieldValue = cacheValues[translateExtend.index];
			}
			return fieldValue;
		}
		// 将字符串用分隔符切分开进行逐个翻译
		String[] keys = StringUtil.splitRegex(fieldStr, translateExtend.splitRegex, true);
		String linkSign = translateExtend.linkSign;
		StringBuilder result = new StringBuilder();
		int index = 0;
		Object[] cacheValues;
		// 是否使用动态缓存数据抓取
		boolean useDynamicCache = translateExtend.dynamicCache;
		String realCacheType = dynamicCacheHolder.getRealCacheType(translateExtend.cacheNameAndType);
		String realCacheNameAndType = dynamicCacheHolder.getRealCacheNameAndType(translateExtend.cacheNameAndType);
		// 是否暂停执行动态缓存翻译
		boolean isPauseTranslate = dynamicCacheHolder.isPauseTranslate(realCacheNameAndType);
		for (String key : keys) {
			if (index > 0) {
				result.append(linkSign);
			}
			cacheValues = cacheData.get(key);
			if (cacheValues == null) {
				if (useDynamicCache) {
					// 是否暂停执行动态缓存翻译
					if (isPauseTranslate) {
						// 未匹配的key加入到集合，后续整体查询
						dynamicCacheHolder.addNotMatchedKey(realCacheNameAndType, key);
						result.append(key);
					} else {
						cacheValues = dynamicCacheFetch.getCache(translateExtend.cache, realCacheType,
								translateExtend.cacheSid, translateExtend.cacheProperties, key);
						if (cacheValues != null) {
							result.append(cacheValues[translateExtend.index]);
							cacheData.put(key, cacheValues);
						} else {
							// 定义未匹配模板则不输出日志
							if (translateExtend.uncached != null) {
								result.append(translateExtend.uncached.replace("${value}", key));
							} else {
								result.append(key);
								logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!",
										translateExtend.cache, realCacheType, key);
							}
						}
					}
				} else {
					// 定义未匹配模板则不输出日志
					if (translateExtend.uncached != null) {
						result.append(translateExtend.uncached.replace("${value}", key));
					} else {
						result.append(key);
						logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!", translateExtend.cache,
								translateExtend.cacheType, key);
					}
				}
			} else {
				result.append(cacheValues[translateExtend.index]);
			}
			index++;
		}
		return result.toString();
	}

	/**
	 * @TODO 对翻译器的条件逻辑进行计算，判断是否需要执行缓存翻译
	 * @param sourceValue
	 * @param compareType
	 * @param compareValues
	 * @return
	 */
	public static boolean judgeTranslate(Object sourceValue, String compareType, String[] compareValues) {
		// compareValues长度不做校验,解析设置时已经校验必须有值
		String sourceStr = (sourceValue == null) ? "null" : sourceValue.toString().toLowerCase();
		if (compareType.equals("eq")) {
			return compareValues[0].equals(sourceStr);
		} else if (compareType.equals("neq")) {
			return !compareValues[0].equals(sourceStr);
		} else if (compareType.equals("in")) {
			for (String compareStr : compareValues) {
				if (compareStr.equals(sourceStr)) {
					return true;
				}
			}
			return false;
		} else if (compareType.equals("out")) {
			for (String compareStr : compareValues) {
				if (compareStr.equals(sourceStr)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * @TODO 针对List<DTO>进行翻译
	 * @param translateManager
	 * @param batchDynamicCache
	 * @param dynamicCacheHolder
	 * @param dynamicCacheFetch
	 * @param items
	 */
	public static void translateDTOListByDynamicCache(TranslateManager translateManager,
			BatchDynamicCache batchDynamicCache, DynamicCacheHolder dynamicCacheHolder,
			DynamicCacheFetch dynamicCacheFetch, List items) {
		// 没有动态查询数据的缓存
		if (batchDynamicCache.getTranslates() == null || batchDynamicCache.getTranslates().isEmpty() || items == null
				|| items.isEmpty()) {
			return;
		}
		String fieldName;
		Translate translate;
		String realCacheNameAndType;
		String[] notMatchedKeys;
		TranslateExtend extend;
		Map<String, Map<String, Object[]>> dynamicFetchedCaches = new HashMap<>();
		Map<String, Object[]> notMatchedKeyCacheData;
		int rowCnt = items.size();
		Object rowBean;
		Object keyValue;
		Object translateResult;
		String realCacheType;
		for (Map.Entry<String, Translate> entry : batchDynamicCache.getTranslates().entrySet()) {
			fieldName = entry.getKey();
			translate = entry.getValue();
			extend = translate.getExtend();
			realCacheType = dynamicCacheHolder.getRealCacheType(extend.cacheNameAndType);
			realCacheNameAndType = dynamicCacheHolder.getRealCacheNameAndType(extend.cacheNameAndType);
			notMatchedKeys = dynamicCacheHolder.getNotMatchedKeys(realCacheNameAndType);
			// 存在未匹配的key,如果notMatchedKeys是空，表示逐行翻译时已经完成了翻译
			if (notMatchedKeys != null && notMatchedKeys.length > 0) {
				// 多列column 使用相同缓存
				if (!dynamicFetchedCaches.containsKey(realCacheNameAndType)) {
					notMatchedKeyCacheData = dynamicCacheFetch.getCache(extend.cache, realCacheType, extend.cacheSid,
							extend.cacheProperties, notMatchedKeys);
					dynamicFetchedCaches.put(realCacheNameAndType, notMatchedKeyCacheData);
					// 回写动态查询的缓存数据
					translateManager.getCacheData(extend.cache, realCacheType).putAll(notMatchedKeyCacheData);
				} else {
					notMatchedKeyCacheData = dynamicFetchedCaches.get(realCacheNameAndType);
				}
				// 循环集合，进行批量翻译
				for (int i = 0; i < rowCnt; i++) {
					rowBean = items.get(i);
					if (null != rowBean) {
						keyValue = BeanUtil.getProperty(rowBean, extend.keyColumn);
						if (null != keyValue) {
							translateResult = translateKey(notMatchedKeyCacheData, extend, null, rowBean, true, -1,
									keyValue);
							// 为null表示存在translateExtend.hasLogic逻辑判断，无需执行翻译，则不用回写
							if (null != translateResult) {
								BeanUtil.setProperty(rowBean, fieldName, translateResult);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * @TODO 对resultSet 查询结果集合进行批量查询key获取缓存数据并进行逐行二次翻译
	 * @param translateManager
	 * @param batchDynamicCache
	 * @param dynamicCacheHolder
	 * @param dynamicCacheFetch
	 * @param labelIndexMap
	 * @param items
	 * @param hasAliasName       针对mongo存在别名场景
	 */
	public static void translateArrayListByDynamicCache(TranslateManager translateManager,
			BatchDynamicCache batchDynamicCache, DynamicCacheHolder dynamicCacheHolder,
			DynamicCacheFetch dynamicCacheFetch, HashMap<String, Integer> labelIndexMap, List items,
			boolean hasAliasName) {
		// 没有动态查询数据的缓存
		if (batchDynamicCache.getTranslates() == null || batchDynamicCache.getTranslates().isEmpty() || items == null
				|| items.isEmpty()) {
			return;
		}
		String columnLow;
		Translate translate;
		String realCacheNameAndType;
		String[] notMatchedKeys;
		TranslateExtend extend;
		Map<String, Map<String, Object[]>> dynamicFetchedCaches = new HashMap<>();
		Map<String, Object[]> notMatchedKeyCacheData;
		int rowCnt = items.size();
		int colIndex;
		int colAliasIndex;
		int compareValueIndex = -1;
		List rowList;
		Object cellValue;
		Object translateResult;
		String realCacheType;
		for (Map.Entry<String, Translate> entry : batchDynamicCache.getTranslates().entrySet()) {
			columnLow = entry.getKey().toLowerCase();
			translate = entry.getValue();
			extend = translate.getExtend();
			colIndex = labelIndexMap.get(columnLow);
			colAliasIndex = colIndex;
			// mongodb 存在别名场景
			if (hasAliasName && extend.alias != null) {
				colAliasIndex = labelIndexMap.get(extend.alias.toLowerCase());
			}
			// compareColumn初始化时已经小写
			compareValueIndex = extend.hasLogic ? labelIndexMap.get(extend.compareColumn) : -1;
			realCacheType = dynamicCacheHolder.getRealCacheType(extend.cacheNameAndType);
			realCacheNameAndType = dynamicCacheHolder.getRealCacheNameAndType(extend.cacheNameAndType);
			notMatchedKeys = dynamicCacheHolder.getNotMatchedKeys(realCacheNameAndType);
			// 存在未匹配的key
			if (notMatchedKeys != null && notMatchedKeys.length > 0) {
				// 多列column 使用相同缓存
				if (!dynamicFetchedCaches.containsKey(realCacheNameAndType)) {
					notMatchedKeyCacheData = dynamicCacheFetch.getCache(extend.cache, realCacheType, extend.cacheSid,
							extend.cacheProperties, notMatchedKeys);
					dynamicFetchedCaches.put(realCacheNameAndType, notMatchedKeyCacheData);
					// 回写动态查询的缓存数据
					translateManager.getCacheData(extend.cache, realCacheType).putAll(notMatchedKeyCacheData);
				} else {
					notMatchedKeyCacheData = dynamicFetchedCaches.get(realCacheNameAndType);
				}
				// 循环集合，进行批量翻译
				for (int i = 0; i < rowCnt; i++) {
					rowList = (List) items.get(i);
					if (null != rowList) {
						cellValue = rowList.get(colAliasIndex);
						if (null != cellValue) {
							translateResult = translateKey(notMatchedKeyCacheData, extend, rowList, null, false,
									compareValueIndex, cellValue);
							// 为null表示存在translateExtend.hasLogic逻辑判断，无需执行翻译，则不用回写
							if (null != translateResult) {
								rowList.set(colIndex, translateResult);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * @TODO 面向批量查询的缓存翻译
	 * @param notMatchedKeyCacheData
	 * @param translateExtend
	 * @param rowList
	 * @param dto
	 * @param isBean
	 * @param compareValueIndex
	 * @param translateValue
	 * @return
	 */
	private static Object translateKey(Map<String, Object[]> notMatchedKeyCacheData, TranslateExtend translateExtend,
			List rowList, Object dto, boolean isBean, int compareValueIndex, Object translateValue) {
		boolean doTranslate = true;
		Object compareValue = null;
		if (translateExtend.hasLogic) {
			compareValue = isBean ? BeanUtil.getProperty(dto, translateExtend.compareColumn)
					: rowList.get(compareValueIndex);
			doTranslate = judgeTranslate(compareValue, translateExtend.compareType, translateExtend.compareValues);
		}
		if (doTranslate) {
			Object[] cacheValues;
			String keyStr = translateValue.toString();
			// 单值翻译
			if (translateExtend.splitRegex == null) {
				// 根据key获取缓存值
				cacheValues = notMatchedKeyCacheData.get(keyStr);
				if (cacheValues != null) {
					return cacheValues[translateExtend.index];
				}
				return keyStr;
			}
			// 将字符串用分隔符切分开进行逐个翻译(这里使用translateExtend.linkSign,因为逐行翻译时已经用linkSign拼接)
			String linkSign = translateExtend.linkSign;
			String[] keys = StringUtil.splitByIndex(keyStr, linkSign, true);
			StringBuilder result = new StringBuilder();
			int index = 0;
			for (String key : keys) {
				if (index > 0) {
					result.append(linkSign);
				}
				cacheValues = notMatchedKeyCacheData.get(key);
				// 没有匹配到(之前已经匹配过，已经是翻译后的结果)
				if (cacheValues == null) {
					result.append(key);
				} else {
					result.append(cacheValues[translateExtend.index]);
				}
				index++;
			}
			return result.toString();
		}
		return null;
	}

	/**
	 * @TODO 判断cacheType是否是动态的${tenant_id}多租户场景下,当前租户的表达式
	 * @param sqlToyContext
	 * @param cacheType
	 * @return
	 */
	public static String getRealCacheType(SqlToyContext sqlToyContext, String cacheType) {
		String realCacheType = null;
		if (cacheType != null) {
			// ${user_tenant_id}形式传递租户id
			if (cacheType.startsWith("${") && cacheType.endsWith("}")) {
				String lowCacheType = cacheType.substring(2, cacheType.length() - 1).replace("_", "").trim()
						.toLowerCase();
				if (lowCacheType.equals("usertenantid") || lowCacheType.equals("currentusertenantid")
						|| lowCacheType.equals("tenantid")) {
					if (sqlToyContext.getUnifyFieldsHandler() == null) {
						throw new DataAccessException("缓存翻译使用:" + cacheType
								+ "形式传递租户信息，必须要实现:IUnifyFieldsHandler.getUserTenantId()或IUnifyFieldsHandler.authTenants(null,null)来获取当前用户的租户id!");
					}
					realCacheType = sqlToyContext.getUnifyFieldsHandler().getUserTenantId();
					if (realCacheType == null) {
						String[] authedTenantIds = sqlToyContext.getUnifyFieldsHandler().authTenants(null, null);
						// 只支持单一租户
						if (authedTenantIds != null && authedTenantIds.length > 0) {
							realCacheType = authedTenantIds[0];
						} else {
							throw new DataAccessException("缓存翻译使用:" + cacheType
									+ "形式传递租户信息，必须要实现:IUnifyFieldsHandler.getUserTenantId()或IUnifyFieldsHandler.authTenants(null,null)来获取当前用户的租户id!");
						}
					}
				}
			} else {
				realCacheType = cacheType;
			}
		}
		return realCacheType;
	}

	/**
	 * @TODO 取字段的最外层是动态取数据的翻译
	 * @param sqlToyContext
	 * @param fieldTranslateCacheHolders
	 * @param linkColumns
	 * @return
	 */
	public static BatchDynamicCache getBatchTranslates(SqlToyContext sqlToyContext,
			HashMap<String, FieldTranslateCacheHolder> fieldTranslateCacheHolders, String... linkColumns) {
		BatchDynamicCache batchDynamicCache = new BatchDynamicCache();
		if (fieldTranslateCacheHolders == null || fieldTranslateCacheHolders.isEmpty()) {
			return batchDynamicCache;
		}
		Map<String, Translate> result = new HashMap<>();
		Map<String, String> cacheAndTypeForRealMap = new HashMap<>();
		Map<String, String> cacheAndTypeForRealType = new HashMap<>();
		String column;
		Translate[] translates;
		Translate tailTranslate;
		FieldTranslateCacheHolder fieldTranslateCacheHolder;
		Set<String> cacheSids = new HashSet<>();
		IgnoreCaseSet linkColumnSet = new IgnoreCaseSet();
		if (linkColumns != null && linkColumns.length > 0) {
			for (String colName : linkColumns) {
				linkColumnSet.add(colName);
			}
		}
		String realCacheNameAndType;
		String realCacheType;
		for (Map.Entry<String, FieldTranslateCacheHolder> entry : fieldTranslateCacheHolders.entrySet()) {
			column = entry.getKey();
			// 有link查询时，还是采取逐个获取key的缓存数据
			if (!linkColumnSet.contains(column)) {
				fieldTranslateCacheHolder = entry.getValue();
				translates = fieldTranslateCacheHolder.getTranslates();
				tailTranslate = translates[translates.length - 1];
				// 最外层翻译，针对一个字段可能存在多次翻译，比如有2次翻译，第一次翻译如果是动态缓存，则必须要实时查询
				if (tailTranslate.getExtend().dynamicCache) {
					result.put(column, tailTranslate);
					if (tailTranslate.getExtend().cacheType == null) {
						cacheSids.add(tailTranslate.getExtend().cache);
						cacheAndTypeForRealMap.put(tailTranslate.getExtend().cache, tailTranslate.getExtend().cache);
					} else {
						// ${tenant_id}动态替换
						realCacheType = TranslateUtils.getRealCacheType(sqlToyContext,
								tailTranslate.getExtend().cacheType);
						realCacheNameAndType = tailTranslate.getExtend().cache.concat("_").concat(realCacheType);
						cacheSids.add(realCacheNameAndType);
						cacheAndTypeForRealMap.put(tailTranslate.getExtend().cacheNameAndType, realCacheNameAndType);
						cacheAndTypeForRealType.put(tailTranslate.getExtend().cacheNameAndType, realCacheType);
					}
				}
			}
		}
		batchDynamicCache.setTranslates(result);
		batchDynamicCache.setCacheAndTypeForRealMap(cacheAndTypeForRealMap);
		batchDynamicCache.setCacheAndTypeForRealType(cacheAndTypeForRealType);
		batchDynamicCache.setDynamicCaches(cacheSids.toArray(new String[0]));
		return batchDynamicCache;
	}
}