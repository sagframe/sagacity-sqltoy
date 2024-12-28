package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description 提供对某个字段进行多次翻译的聚合组织类
 * @author zhongxuchen
 * @version v1.0,Date:2024-12-28
 */
public class FieldTranslate implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5834406408680610075L;

	/**
	 * 字段名称
	 */
	public String colName;

	/**
	 * 依据字段(VO中缓存翻译注解)
	 */
	public String keyColumn;

	/**
	 * 缓存翻译配置
	 */
	public Translate[] translates;

	public void put(Translate translate) {
		if (translates == null) {
			translates = new Translate[] { translate };
		} else {
			boolean hasCache = false;
			String cacheName;
			String cacheType;
			for (int j = 0; j < translates.length; j++) {
				cacheName = translates[j].getExtend().cache;
				cacheType = translates[j].getExtend().cacheType;
				if (cacheName.equals(translate.getExtend().cache)
						&& (cacheType == null || cacheType.equals(translate.getExtend().cacheType))) {
					translates[j] = translate;
					hasCache = true;
					break;
				}
			}
			if (!hasCache) {
				Translate[] tmpAry = new Translate[translates.length + 1];
				System.arraycopy(translates, 0, tmpAry, 0, translates.length);
				tmpAry[translates.length] = translate;
				translates = tmpAry;
			}
		}
	}

}
