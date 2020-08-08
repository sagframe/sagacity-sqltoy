/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.sagacity.sqltoy.SqlToyContext;

/**
 * @project sagacity-sqltoy
 * @description 提供针对sqltoy的DTO到POJO、POJO到DTO的映射工具
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:MapperUtils.java,Revision:v1.0,Date:2020-8-8
 * @modify data:2020-8-8 初始创建
 */
public class MapperUtils {
	/**
	 * @param <T>
	 * @param entity
	 * @param resultType
	 * @return
	 */
	public static <T extends Serializable> T map(SqlToyContext sqlToyContext, Serializable source,
			Class<T> resultType) {
		if (source == null || resultType == null) {
			return null;
		}
		// 转成List做统一处理
		List<Serializable> sourceList = new ArrayList<Serializable>();
		sourceList.add(source);
		List<T> result = mapList(sqlToyContext, sourceList, resultType);
		return result.get(0);
	}

	/**
	 * @param <T>
	 * @param entity
	 * @param resultType
	 * @return
	 */
	public static <T extends Serializable> List<T> mapList(SqlToyContext sqlToyContext,
			Collection<Serializable> sourceList, Class<T> resultType) {
		if (sourceList == null || sourceList.isEmpty() || resultType == null) {
			return null;
		}
		return null;
	}

}
