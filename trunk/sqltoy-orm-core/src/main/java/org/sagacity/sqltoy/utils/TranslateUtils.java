package org.sagacity.sqltoy.utils;

import java.util.HashMap;

import org.sagacity.sqltoy.model.inner.TranslateExtend;
import org.sagacity.sqltoy.translate.DynamicCacheFetch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 对结果翻译提供一个工具类
 * @author zhongxuchen
 * @version v1.0,Date:2024-12-28
 */
public class TranslateUtils {

	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateUtils.class);

	/**
	 * @date 2018-5-26 优化缓存翻译，提供keyCode1,keyCode2,keyCode3 形式的多代码翻译
	 * @todo 统一对key进行缓存翻译
	 * @param extend
	 * @param dynamicCacheFetch 预留功能,暂时不开放,需再思考一下必要性
	 * @param translateKeyMap
	 * @param fieldValue
	 * @return
	 */
	public static Object translateKey(TranslateExtend extend, DynamicCacheFetch dynamicCacheFetch,
			HashMap<String, Object[]> translateKeyMap, Object fieldValue) {
		String fieldStr = fieldValue.toString();
		// 单值翻译
		if (extend.splitRegex == null) {
			if (extend.keyTemplate != null) {
				// keyTemplate已经提前做了规整,将${key},${},${0} 统一成了{}
				fieldStr = extend.keyTemplate.replace("{}", fieldStr);
			}
			// 根据key获取缓存值
			Object[] cacheValues = translateKeyMap.get(fieldStr);
			// 执行动态缓存获取(暂时不开放)
//			if (cacheValues == null && extend.dynamicCache && dynamicCacheFetch != null) {
//				cacheValues = dynamicCacheFetch.getCache(extend.cache, extend.cacheSid, extend.cacheProperties,
//						fieldStr);
//				if (cacheValues != null) {
//					translateKeyMap.put(fieldStr, cacheValues);
//				}
//			}
			// 未匹配到
			if (cacheValues == null || cacheValues.length == 0) {
				// 定义未匹配模板则不输出日志
				if (extend.uncached != null) {
					fieldValue = extend.uncached.replace("${value}", fieldStr);
				} else {
					fieldValue = fieldValue.toString();
					logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!", extend.cache,
							extend.cacheType, fieldValue);
				}
			} else {
				fieldValue = cacheValues[extend.index];
			}
			return fieldValue;
		}
		// 将字符串用分隔符切分开进行逐个翻译
		String[] keys = StringUtil.splitRegex(fieldStr, extend.splitRegex, true);
		String linkSign = extend.linkSign;
		StringBuilder result = new StringBuilder();
		int index = 0;
		Object[] cacheValues;
		for (String key : keys) {
			if (index > 0) {
				result.append(linkSign);
			}
			cacheValues = translateKeyMap.get(key);
			// 暂时不开放
//			if (cacheValues == null && extend.dynamicCache && dynamicCacheFetch != null) {
//				cacheValues = dynamicCacheFetch.getCache(extend.cache, extend.cacheSid, extend.cacheProperties,
//						fieldStr);
//				if (cacheValues != null) {
//					translateKeyMap.put(fieldStr, cacheValues);
//				}
//			}
			if (cacheValues == null || cacheValues.length == 0) {
				// 定义未匹配模板则不输出日志
				if (extend.uncached != null) {
					result.append(extend.uncached.replace("${value}", key));
				} else {
					result.append(key);
					logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!", extend.cache,
							extend.cacheType, key);
				}
			} else {
				result.append(cacheValues[extend.index]);
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
		//compareValues长度不做校验,解析设置时已经校验必须有值
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
}
