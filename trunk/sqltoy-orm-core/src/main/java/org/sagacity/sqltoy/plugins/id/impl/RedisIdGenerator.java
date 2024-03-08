/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.impl;

import java.util.Date;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.integration.DistributeIdGenerator;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 基于redis的集中式主键生成策略
 * @author zhongxuchen
 * @version v1.0,Date:2018年1月30日
 * @modify Date:2019-1-24 {key命名策略改为SQLTOY_GL_ID:tableName:xxx 便于redis检索}
 */
public class RedisIdGenerator implements IdGenerator {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(RedisIdGenerator.class);

	/**
	 * 分布式id生成器
	 */
	private DistributeIdGenerator distributeIdGenerator;

	@Override
	public void initialize(SqlToyContext sqlToyContext) throws Exception {
		if (distributeIdGenerator == null) {
			distributeIdGenerator = (DistributeIdGenerator) Class.forName(sqlToyContext.getDistributeIdGeneratorClass())
					.newInstance();
			distributeIdGenerator.initialize(sqlToyContext.getAppContext());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugins.id.IdGenerator#getId(java.lang.String,
	 * java.lang.String, java.lang.Object[], int)
	 */
	@Override
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
			Date bizDate, String idJavaType, int length, int sequencSize) {
		String key = (signature == null ? "" : signature);
		// 主键生成依赖业务的相关字段值
		IgnoreKeyCaseMap<String, Object> keyValueMap = new IgnoreKeyCaseMap<String, Object>();
		if (relatedColumns != null && relatedColumns.length > 0) {
			for (int i = 0; i < relatedColumns.length; i++) {
				if (null == relatedColValue[i]) {
					throw new RuntimeException(
							"table=" + tableName + " 生成业务主键失败,关联字段:" + relatedColumns[i] + " 对应的值为null!");
				}
				keyValueMap.put(relatedColumns[i], relatedColValue[i]);
			}
		}
		// 替换signature中的@df() 和@case()等宏表达式
		String realKey = MacroUtils.replaceMacros(key, keyValueMap);
		// 没有宏
		if (realKey.equals(key)) {
			// 长度够放下6位日期 或没有设置长度且流水长度小于6,则默认增加一个6位日期作为前置
			if ((length <= 0 && sequencSize < 6) || (length - realKey.length() > 6)) {
				Date realBizDate = (bizDate == null ? new Date() : bizDate);
				realKey = realKey.concat(DateUtil.formatDate(realBizDate, "yyMMdd"));
			}
		}
		// 参数替换
		if (!keyValueMap.isEmpty()) {
			realKey = MacroUtils.replaceParams(realKey, keyValueMap);
		}
		// 结合redis计数取末尾几位顺序数
		Long result;
		// update 2019-1-24 key命名策略改为SQLTOY_GL_ID:tableName:xxx 便于redis检索
		if (tableName != null) {
			result = distributeIdGenerator
					.generateId("".equals(realKey) ? tableName : tableName.concat(":").concat(realKey), 1, null);
		} else {
			result = distributeIdGenerator.generateId(realKey, 1, null);
		}
		return realKey.concat(
				StringUtil.addLeftZero2Len("" + result, (sequencSize > 0) ? sequencSize : length - realKey.length()));
	}
}
