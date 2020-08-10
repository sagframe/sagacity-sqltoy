/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.SqlToyFieldAlias;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.model.DTOEntityMapModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供针对sqltoy的DTO到POJO、POJO到DTO的映射工具
 * @author zhongxuchen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:MapperUtils.java,Revision:v1.0,Date:2020-8-8
 * @modify data:2020-8-8 初始创建
 */
public class MapperUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(MapperUtils.class);

	/**
	 * 利用缓存来提升匹配效率
	 */
	private static Map<String, DTOEntityMapModel> dtoEntityMappCache = new HashMap<String, DTOEntityMapModel>();

	/**
	 * @param <T>
	 * @param entity
	 * @param resultType
	 * @return
	 */
	public static <T extends Serializable> T map(SqlToyContext sqlToyContext, Serializable source, Class<T> resultType)
			throws Exception {
		if (source == null || resultType == null) {
			return null;
		}
		if (Modifier.isAbstract(resultType.getModifiers()) || Modifier.isInterface(resultType.getModifiers())) {
			throw new IllegalArgumentException("resultType:" + resultType.getName() + " 是抽象类或接口,非法参数!");
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
	public static <T extends Serializable> List<T> mapList(SqlToyContext sqlToyContext, List<Serializable> sourceList,
			Class<T> resultType) throws Exception {
		if (sourceList == null || sourceList.isEmpty() || resultType == null) {
			return null;
		}
		if (Modifier.isAbstract(resultType.getModifiers()) || Modifier.isInterface(resultType.getModifiers())) {
			throw new IllegalArgumentException("resultType:" + resultType.getName() + " 是抽象类或接口,非法参数!");
		}
		DTOEntityMapModel mapModel = getDTOEntityMap(sqlToyContext, sourceList.iterator().next().getClass(),
				resultType);
		Method[] getMethods;
		Method[] setMethods;
		// pojo-->dto
		if (mapModel.dtoClassName.equals(resultType.getName())) {
			getMethods = mapModel.pojoGetMethods;
			setMethods = mapModel.dtoSetMethods;
		} // dto ---> pojo
		else {
			getMethods = mapModel.dtoGetMethods;
			setMethods = mapModel.pojoSetMethods;
		}
		List dataSets = invokeGetValues(sourceList, getMethods);
		return reflectListToBean(dataSets, resultType, setMethods);
	}

	private static List invokeGetValues(List sourceList, Method[] getMethods) throws Exception {
		List result = new ArrayList();
		Object row;
		for (int i = 0; i < sourceList.size(); i++) {
			row = sourceList.get(i);
			List rowData = new ArrayList();
			for (Method method : getMethods) {
				if (method == null) {
					rowData.add(null);
				} else {
					rowData.add(method.invoke(row));
				}
			}
			result.add(rowData);
		}
		return result;
	}

	/**
	 * @TODO 组织构造dto和pojo的映射模型
	 * @param sqlToyContext
	 * @param sourceClass
	 * @param resultType
	 * @return
	 */
	private static DTOEntityMapModel getDTOEntityMap(SqlToyContext sqlToyContext, Class sourceClass, Class resultType) {
		String sourceKey = sourceClass.getName();
		String resultKey = resultType.getName();
		String key;
		Class dtoClass = null;
		Class pojoClass = null;
		if (sqlToyContext.isEntity(sourceClass)) {
			dtoClass = resultType;
			pojoClass = sourceClass;
			key = "POJO=".concat(sourceKey).concat(";DTO=").concat(resultKey);
		} else {
			key = "POJO=".concat(resultKey).concat(";DTO=").concat(sourceKey);
			dtoClass = sourceClass;
			pojoClass = resultType;
		}
		if (pojoClass == null) {
			throw new IllegalArgumentException("请检查参数,sqltoy pojo 必须要有@SqlToyEntity标注!");
		}

		if (!dtoEntityMappCache.containsKey(key)) {
			DTOEntityMapModel result = new DTOEntityMapModel();
			// pojo
			EntityMeta entityMeta = sqlToyContext.getEntityMeta(pojoClass);
			HashMap<String, String> pojoPropsMap = new HashMap<String, String>();
			for (String field : entityMeta.getFieldsArray()) {
				pojoPropsMap.put(field.toLowerCase(), field);
			}
			// dto
			SqlToyFieldAlias alias;
			List<String> dtoProps = new ArrayList<String>();
			List<String> pojoProps = new ArrayList<String>();
			String fieldName;
			String aliasName;
			for (Field field : dtoClass.getDeclaredFields()) {
				fieldName = field.getName();
				aliasName = fieldName;
				alias = field.getAnnotation(SqlToyFieldAlias.class);
				if (alias != null) {
					aliasName = alias.value();
				}
				if (pojoPropsMap.containsKey(aliasName.toLowerCase())) {
					dtoProps.add(fieldName);
					pojoProps.add(pojoPropsMap.get(aliasName.toLowerCase()));
				}
			}

			if (dtoProps.isEmpty()) {
				throw new IllegalArgumentException(
						"dto:" + dtoClass.getName() + " mapping pojo:" + pojoClass.getName() + " 没有属性名称是匹配的，请检查!");
			}
			result.dtoClassName = dtoClass.getName();
			result.dtoProps = (String[]) dtoProps.toArray(new String[dtoProps.size()]);
			result.pojoClassName = pojoClass.getName();
			result.pojoProps = (String[]) pojoProps.toArray(new String[pojoProps.size()]);

			result.dtoGetMethods = BeanUtil.matchGetMethods(dtoClass, result.dtoProps);
			result.dtoSetMethods = BeanUtil.matchSetMethods(dtoClass, result.dtoProps);
			result.pojoGetMethods = BeanUtil.matchGetMethods(pojoClass, result.pojoProps);
			result.pojoSetMethods = BeanUtil.matchSetMethods(pojoClass, result.pojoProps);
			dtoEntityMappCache.put(key, result);
		}
		return dtoEntityMappCache.get(key);
	}

	// 提取映射对应的methods
	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param datas
	 * @param voClass
	 * @param realMethods
	 * @return
	 * @throws Exception
	 */
	private static List reflectListToBean(List datas, Class voClass, Method[] realMethods) throws Exception {
		List result = new ArrayList();
		int indexSize = realMethods.length;
		String[] methodTypesLow = new String[indexSize];
		String[] methodTypes = new String[indexSize];
		// 自动适配属性的数据类型
		for (int i = 0; i < indexSize; i++) {
			if (null != realMethods[i]) {
				methodTypes[i] = realMethods[i].getParameterTypes()[0].getName().toLowerCase();
				methodTypesLow[i] = methodTypes[i].toLowerCase();
			}
		}
		int size;
		Object bean;
		List row;
		Object cellData = null;
		for (int i = 0; i < datas.size(); i++) {
			row = (List) datas.get(i);
			if (row != null) {
				bean = voClass.getDeclaredConstructor().newInstance();
				size = row.size();
				for (int j = 0; j < size; j++) {
					cellData = row.get(j);
					if (cellData != null && realMethods[j] != null) {
						if (cellData.getClass().getName().equals(methodTypes[j])) {
							realMethods[j].invoke(bean, cellData);
						} else {
							realMethods[j].invoke(bean, BeanUtil.convertType(cellData, methodTypesLow[j]));
						}
					}
				}
				result.add(bean);
			}
		}
		return result;
	}

}
