/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.Serializable;
import java.util.List;

/**
 * @project sagacity-sqltoy
 * @description 提供针对sqltoy的DTO到POJO、POJO到DTO的映射工具
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:MapperUtils.java,Revision:v1.0,Date:2020-8-8
 * @modify data:2020-8-8 初始创建
 */
public class MapperUtils {
	/**
	 * @TODO POJO to DTO
	 * @param <T>
	 * @param entity
	 * @param resultType
	 * @return
	 */
	public static <T extends Serializable> T map(Serializable entity, Class<T> resultType) {
		return null;
	}

	/**
	 * @TODO POJO to DTO
	 * @param <T>
	 * @param entity
	 * @param resultType
	 * @return
	 */
	public static <T extends Serializable> List<T> mapList(List<Serializable> entities, Class<T> resultType) {
		return null;
	}

}
