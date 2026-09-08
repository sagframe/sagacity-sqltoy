package org.sagacity.sqltoy.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
 * @modify Date:2026-01-16 开放dynamicCacheFetch功能
 * @modify Date:2026-01-22 完善批量查询，批量翻译功能
 */
public class TranslateUtils {

	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateUtils.class);

	// 跟link-sign不能存在冲突，定义较为特殊的符号
	private final static String UN_MATCHED_SIGN = "^";
	private final static int UN_MATCHED_SIGN_LENGTH = UN_MATCHED_SIGN.length();

	/**
	 * @date 2018-5-26 优化缓存翻译，提供keyCode1,keyCode2,keyCode3 形式的多代码翻译 统一对key进行缓存翻译
	 * @param translateExtend    翻译配置扩展信息(缓存名、取值下标、分隔符、未匹配模板等)
	 * @param dynamicCacheFetch  动态缓存数据抓取接口，翻译配置为动态缓存时逐key实时查询
	 * @param dynamicCacheHolder 用于判断是否暂停逐行翻译，并记录未匹配的key
	 * @param cacheData          缓存数据，key为缓存key、value为缓存行数据
	 * @param fieldValue         待翻译的字段值，支持splitRegex分隔的多值形式
	 * @return 翻译后的值，未匹配时按uncached模板或原key返回
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
						if (translateExtend.uncached != null) {
							fieldValue = UN_MATCHED_SIGN + fieldStr;
						} else {
							fieldValue = fieldStr;
						}
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
								logger.warn(
										"translate cache:{},cacheType:{}, the key:{} has no corresponding value set!",
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
						logger.warn("translate cache:{},cacheType:{}, the key:{} has no corresponding value set!",
								translateExtend.cache, translateExtend.cacheType, fieldValue);
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
						if (translateExtend.uncached != null) {
							result.append(UN_MATCHED_SIGN).append(key);
						} else {
							result.append(key);
						}
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
								logger.warn(
										"translate cache:{},cacheType:{}, the key:{} has no corresponding value set!",
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
						logger.warn("translate cache:{},cacheType:{}, the key:{} has no corresponding value set!",
								translateExtend.cache, translateExtend.cacheType, key);
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
	 * 对翻译器的条件逻辑进行计算，判断是否需要执行缓存翻译
	 * 
	 * @param sourceValue   参与比较的字段实际值，null按"null"字符串处理
	 * @param compareType   比较类型，支持eq(等于)、neq(不等于)、in(包含)、out(不包含)
	 * @param compareValues 配置的比较值数组，统一转小写后与字段值比较
	 * @return 满足比较条件返回true表示需要执行翻译，不满足或类型无法识别返回false
	 */
	public static boolean judgeTranslate(Object sourceValue, String compareType, String[] compareValues) {
		// compareValues长度不做校验,解析设置时已经校验必须有值
		// 比较值与数据值统一转小写,否则配置大写比较值将永远无法匹配
		String sourceStr = (sourceValue == null) ? "null" : sourceValue.toString().toLowerCase(java.util.Locale.ROOT);
		if (compareType.equals("eq")) {
			return compareValues[0].toLowerCase(java.util.Locale.ROOT).equals(sourceStr);
		} else if (compareType.equals("neq")) {
			return !compareValues[0].toLowerCase(java.util.Locale.ROOT).equals(sourceStr);
		} else if (compareType.equals("in")) {
			for (String compareStr : compareValues) {
				if (compareStr.toLowerCase(java.util.Locale.ROOT).equals(sourceStr)) {
					return true;
				}
			}
			return false;
		} else if (compareType.equals("out")) {
			for (String compareStr : compareValues) {
				if (compareStr.toLowerCase(java.util.Locale.ROOT).equals(sourceStr)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * 针对List<DTO>进行翻译
	 * 
	 * @param translateManager   翻译管理器，用于回写动态查询到的缓存数据
	 * @param batchDynamicCache  批量动态缓存信息(待翻译字段及对应Translate配置)
	 * @param dynamicCacheHolder 记录逐行翻译时未匹配key的持有器
	 * @param dynamicCacheFetch  动态缓存数据批量抓取接口
	 * @param items              待翻译的DTO对象集合，直接在原对象上回写翻译结果
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
						// 读翻译字段当前值(第一遍翻译的输出),而非原始key列,避免将已翻译成功的结果覆盖回原始key
						keyValue = BeanUtil.getProperty(rowBean, fieldName);
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
	 * 对resultSet 查询结果集合进行批量查询key获取缓存数据并进行逐行二次翻译
	 * 
	 * @param translateManager   翻译管理器，用于回写动态查询到的缓存数据
	 * @param batchDynamicCache  批量动态缓存信息(待翻译列及对应Translate配置)
	 * @param dynamicCacheHolder 记录逐行翻译时未匹配key的持有器
	 * @param dynamicCacheFetch  动态缓存数据批量抓取接口
	 * @param labelIndexMap      查询结果列名(小写)与列下标的对应关系
	 * @param items              待翻译的二维List数据集合，直接在原行数据上回写翻译结果
	 * @param hasAliasName       针对mongo存在别名场景(保留参数兼容既有调用,二次翻译统一回读翻译列自身)
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
		int compareValueIndex = -1;
		List rowList;
		Object cellValue;
		Object translateResult;
		String realCacheType;
		for (Map.Entry<String, Translate> entry : batchDynamicCache.getTranslates().entrySet()) {
			columnLow = entry.getKey().toLowerCase(Locale.ROOT);
			translate = entry.getValue();
			extend = translate.getExtend();
			// 翻译列不在查询结果列中时给出指向配置的明确错误,而非拆箱NPE
			Integer columnIndex = labelIndexMap.get(columnLow);
			if (columnIndex == null) {
				throw new IllegalArgumentException("the dynamic translate column [" + columnLow
						+ "] is not in the query result columns, please check the translate columns config and the select columns of the sql!");
			}
			colIndex = columnIndex;
			// compareColumn初始化时已经小写
			if (extend.hasLogic) {
				Integer compareIndex = labelIndexMap.get(extend.compareColumn);
				if (compareIndex == null) {
					throw new IllegalArgumentException("the where compare column [" + extend.compareColumn
							+ "] of the dynamic translate column [" + columnLow
							+ "] is not in the query result columns, please check the translate where config!");
				}
				compareValueIndex = compareIndex;
			} else {
				compareValueIndex = -1;
			}
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
						// 读翻译列当前值(第一遍翻译的输出),而非原始key列(mongo别名场景),避免将已翻译成功的结果覆盖回原始key
						cellValue = rowList.get(colIndex);
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
	 * 面向批量查询的缓存翻译
	 * 
	 * @param notMatchedKeyCacheData 批量动态查询得到的缓存数据，key为缓存key、value为缓存行数据
	 * @param translateExtend        翻译配置扩展信息(取值下标、分隔符、未匹配模板、条件逻辑等)
	 * @param rowList                当前行数据(isBean为false时用于读取条件比较列的值)
	 * @param dto                    当前行DTO对象(isBean为true时用于读取条件比较属性的值)
	 * @param isBean                 true表示数据行为DTO对象，false表示为List行
	 * @param compareValueIndex      条件比较列的下标，无条件逻辑时传-1
	 * @param translateValue         待翻译的字段值(可能已带未匹配标记^前缀)
	 * @return 翻译后的值；存在条件逻辑且不满足翻译条件时返回null
	 */
	private static Object translateKey(Map<String, Object[]> notMatchedKeyCacheData, TranslateExtend translateExtend,
			List rowList, Object dto, boolean isBean, int compareValueIndex, Object translateValue) {
		boolean doTranslate = true;
		boolean hasUnmatchTemplate = translateExtend.uncached != null;
		Object compareValue = null;
		if (translateExtend.hasLogic) {
			compareValue = isBean ? BeanUtil.getProperty(dto, translateExtend.compareColumn)
					: rowList.get(compareValueIndex);
			doTranslate = judgeTranslate(compareValue, translateExtend.compareType, translateExtend.compareValues);
		}
		if (doTranslate) {
			Object[] cacheValues;
			String keyStr = translateValue.toString();
			boolean hasUnMatchSign = false;
			// 单值翻译
			if (translateExtend.splitRegex == null) {
				if (hasUnmatchTemplate && keyStr.startsWith(UN_MATCHED_SIGN)) {
					keyStr = keyStr.substring(UN_MATCHED_SIGN_LENGTH);
					hasUnMatchSign = true;
				}
				// 根据key获取缓存值
				cacheValues = notMatchedKeyCacheData.get(keyStr);
				if (cacheValues != null) {
					return cacheValues[translateExtend.index];
				} else if (hasUnMatchSign) {
					return translateExtend.uncached.replace("${value}", keyStr);
				}
				return keyStr;
			}
			// 将字符串用分隔符切分开进行逐个翻译(这里使用translateExtend.linkSign,因为逐行翻译时已经用linkSign拼接)
			String linkSign = translateExtend.linkSign;
			String[] keys = StringUtil.splitByIndex(keyStr, linkSign, true);
			StringBuilder result = new StringBuilder();
			int index = 0;
			for (String key : keys) {
				hasUnMatchSign = false;
				if (index > 0) {
					result.append(linkSign);
				}
				if (hasUnmatchTemplate && key.startsWith(UN_MATCHED_SIGN)) {
					key = key.substring(UN_MATCHED_SIGN_LENGTH);
					hasUnMatchSign = true;
				}
				cacheValues = notMatchedKeyCacheData.get(key);
				// 没有匹配到(之前已经匹配过，已经是翻译后的结果)
				if (cacheValues == null) {
					if (hasUnMatchSign) {
						result.append(translateExtend.uncached.replace("${value}", key));
					} else {
						result.append(key);
					}
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
	 * 判断cacheType是否是动态的${tenant_id}多租户场景下,当前租户的表达式
	 * 
	 * @param sqlToyContext sqltoy上下文，通过其中的统一字段处理器获取当前租户id
	 * @param cacheType     缓存类型，支持${usertenantid}/${currentusertenantid}/${tenantid}动态占位符或普通字符串
	 * @return 实际的缓存类型值，动态占位符解析为当前租户id，普通值原样返回，cacheType为null返回null
	 */
	public static String getRealCacheType(SqlToyContext sqlToyContext, String cacheType) {
		String realCacheType = null;
		if (cacheType != null) {
			// ${user_tenant_id}形式传递租户id
			if (cacheType.startsWith("${") && cacheType.endsWith("}")) {
				String lowCacheType = cacheType.substring(2, cacheType.length() - 1).replace("_", "").trim()
						.toLowerCase(Locale.ROOT);
				if (lowCacheType.equals("usertenantid") || lowCacheType.equals("currentusertenantid")
						|| lowCacheType.equals("tenantid")) {
					if (sqlToyContext.getUnifyFieldsHandler() == null) {
						throw new DataAccessException("the translate passes tenant info via [" + cacheType
								+ "], you must implement IUnifyFieldsHandler.getUserTenantId() or IUnifyFieldsHandler.authTenants(null,null) to get the current user's tenant id!");
					}
					realCacheType = sqlToyContext.getUnifyFieldsHandler().getUserTenantId();
					if (realCacheType == null) {
						String[] authedTenantIds = sqlToyContext.getUnifyFieldsHandler().authTenants(null, null);
						// 只支持单一租户
						if (authedTenantIds != null && authedTenantIds.length > 0) {
							realCacheType = authedTenantIds[0];
						} else {
							throw new DataAccessException("the translate passes tenant info via [" + cacheType
									+ "], you must implement IUnifyFieldsHandler.getUserTenantId() or IUnifyFieldsHandler.authTenants(null,null) to get the current user's tenant id!");
						}
					}
				} else {
					throw new DataAccessException(
							"the translate cache-type only supports the three dynamic placeholders ${usertenantid}/"
									+ "${currentusertenantid}/${tenantid}, [" + cacheType
									+ "] is not supported, please check the translate config!");
				}
			} else {
				realCacheType = cacheType;
			}
		}
		return realCacheType;
	}

	/**
	 * 取字段的最外层是动态取数据的翻译
	 * 
	 * @param sqlToyContext              sqltoy上下文，用于解析多租户动态缓存类型
	 * @param fieldTranslateCacheHolders 字段与翻译配置持有器的映射
	 * @param linkColumns                存在link关联查询的列名，这些列仍采取逐行翻译不参与批量提取
	 * @return 封装了批量动态翻译列及其真实缓存名的BatchDynamicCache对象
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