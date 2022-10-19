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
import org.sagacity.sqltoy.config.model.DTOEntityMapModel;
import org.sagacity.sqltoy.config.model.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供针对sqltoy的DTO到POJO、POJO到DTO的映射工具
 * @author zhongxuchen
 * @version v1.0,Date:2020-8-8
 * @modify data:2020-09-04 支持VO<->VO,DTO<->DTO,VO<->DTO 的互转
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

	private MapperUtils() {
	}

	/**
	 * @TODO 实现POJO和VO单个对象之间的相互转换和赋值
	 * @param <T>
	 * @param sqlToyContext
	 * @param source
	 * @param resultType
	 * @return
	 * @throws Exception
	 */
	public static <T extends Serializable> T map(SqlToyContext sqlToyContext, Serializable source, Class<T> resultType)
			throws Exception {
		if (source == null || (resultType == null || resultType.equals(Object.class))) {
			throw new IllegalArgumentException("source 和 resultType 不能为null,且resultType不能为Object.class!");
		}
		// 转成List做统一处理
		List<Serializable> sourceList = new ArrayList<Serializable>();
		sourceList.add(source);
		List<T> result = mapList(sqlToyContext, sourceList, resultType);
		return result.get(0);
	}

	/**
	 * @TODO 实现POJO和VO对象集合之间的相互转换和赋值
	 * @param <T>
	 * @param sqlToyContext
	 * @param sourceList
	 * @param resultType
	 * @return
	 * @throws Exception
	 */
	public static <T extends Serializable> List<T> mapList(SqlToyContext sqlToyContext, List<Serializable> sourceList,
			Class<T> resultType) throws Exception {
		if (sourceList == null || (resultType == null || resultType.equals(Object.class))) {
			throw new IllegalArgumentException("sourceList 和 resultType 不能为null,且resultType不能为Object.class!");
		}
		if (sourceList.isEmpty()) {
			return new ArrayList<T>();
		}
		// resultType不能是接口和抽象类
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

	/**
	 * @TODO 通过get方法回去对象的值放入List中
	 * @param sourceList
	 * @param getMethods
	 * @return
	 * @throws Exception
	 */
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
	 * @TODO 组织构造dto和pojo的映射模型放入缓存，并通过get和set方法调用完成复制过程(比BeanUtils.copyProperties效率高)
	 * @param sqlToyContext
	 * @param sourceClass
	 * @param resultType
	 * @return
	 */
	private static DTOEntityMapModel getDTOEntityMap(SqlToyContext sqlToyContext, Class sourceClass, Class resultType) {
		String sourceKey = sourceClass.getName();
		String resultKey = resultType.getName();
		String key = "POJO=".concat(sourceKey).concat(";DTO=").concat(resultKey);
		// 通过缓存获取
		if (dtoEntityMappCache.containsKey(key)) {
			return dtoEntityMappCache.get(key);
		}
		key = "POJO=".concat(resultKey).concat(";DTO=").concat(sourceKey);
		if (dtoEntityMappCache.containsKey(key)) {
			return dtoEntityMappCache.get(key);
		}
		int style = 0;
		Class dtoClass = null;
		Class pojoClass = null;
		// 是否是POJO
		if (sqlToyContext.isEntity(sourceClass)) {
			dtoClass = resultType;
			pojoClass = sourceClass;
			key = "POJO=".concat(sourceKey).concat(";DTO=").concat(resultKey);
			style++;
		}
		if (sqlToyContext.isEntity(resultType)) {
			dtoClass = sourceClass;
			pojoClass = resultType;
			key = "POJO=".concat(resultKey).concat(";DTO=").concat(sourceKey);
			style++;
		}
		DTOEntityMapModel result = null;
		// 全是POJO或全是DTO
		if (style == 2 || style == 0) {
			key = "POJO=".concat(sourceKey).concat(";DTO=").concat(resultKey);
			result = PO2PO(sourceClass, resultType);
		} else {
			result = PO2DTO(pojoClass, dtoClass);
		}
		dtoEntityMappCache.put(key, result);
		return result;
	}

	/**
	 * @TODO POJO 跟POJO 或 DTO 到DTO 之间的映射复制
	 * @param dtoClass
	 * @param pojoClass
	 * @return
	 */
	private static DTOEntityMapModel PO2PO(Class dtoClass, Class pojoClass) {
		DTOEntityMapModel result = new DTOEntityMapModel();
		String fieldName;
		HashMap<String, String> pojoPropsMap = new HashMap<String, String>();
		// pojo 以及父类
		Class parentClass = pojoClass;
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
				fieldName = field.getName();
				pojoPropsMap.put(fieldName.toLowerCase(), fieldName);
			}
			parentClass = parentClass.getSuperclass();
		}

		// dto
		List<String> dtoProps = new ArrayList<String>();
		List<String> pojoProps = new ArrayList<String>();
		// dto以及其所有父类
		parentClass = dtoClass;
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
				fieldName = field.getName();
				if (pojoPropsMap.containsKey(fieldName.toLowerCase())) {
					dtoProps.add(fieldName);
					pojoProps.add(pojoPropsMap.get(fieldName.toLowerCase()));
				}
			}
			parentClass = parentClass.getSuperclass();
		}
		// 模型赋值
		result.dtoClassName = dtoClass.getName();
		result.dtoProps = (String[]) dtoProps.toArray(new String[dtoProps.size()]);
		result.pojoClassName = pojoClass.getName();
		result.pojoProps = (String[]) pojoProps.toArray(new String[pojoProps.size()]);

		result.dtoGetMethods = BeanUtil.matchGetMethods(dtoClass, result.dtoProps);
		result.dtoSetMethods = BeanUtil.matchSetMethods(dtoClass, result.dtoProps);
		result.pojoGetMethods = BeanUtil.matchGetMethods(pojoClass, result.pojoProps);
		result.pojoSetMethods = BeanUtil.matchSetMethods(pojoClass, result.pojoProps);
		return result;
	}

	/**
	 * @TODO POJO 跟DTO 之间的映射复制
	 * @param pojoClass
	 * @param dtoClass
	 * @return
	 */
	private static DTOEntityMapModel PO2DTO(Class pojoClass, Class dtoClass) {
		String fieldName;
		String aliasName;
		SqlToyFieldAlias alias;
		HashMap<String, String> pojoPropsMap = new HashMap<String, String>();
		// pojo 以及父类
		Class parentClass = pojoClass;
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
				fieldName = field.getName();
				pojoPropsMap.put(fieldName.toLowerCase(), fieldName);
			}
			parentClass = parentClass.getSuperclass();
		}

		// dto
		List<String> dtoProps = new ArrayList<String>();
		List<String> pojoProps = new ArrayList<String>();
		// dto 和dto父类
		parentClass = dtoClass;
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
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
			parentClass = parentClass.getSuperclass();
		}

		// 没有匹配的属性
		if (dtoProps.isEmpty()) {
			throw new IllegalArgumentException(
					"dto:" + dtoClass.getName() + " mapping pojo:" + pojoClass.getName() + " 没有属性名称是匹配的，请检查!");
		}
		DTOEntityMapModel result = new DTOEntityMapModel();
		// 模型赋值
		result.dtoClassName = dtoClass.getName();
		result.dtoProps = (String[]) dtoProps.toArray(new String[dtoProps.size()]);
		result.pojoClassName = pojoClass.getName();
		result.pojoProps = (String[]) pojoProps.toArray(new String[pojoProps.size()]);

		result.dtoGetMethods = BeanUtil.matchGetMethods(dtoClass, result.dtoProps);
		result.dtoSetMethods = BeanUtil.matchSetMethods(dtoClass, result.dtoProps);
		result.pojoGetMethods = BeanUtil.matchGetMethods(pojoClass, result.pojoProps);
		result.pojoSetMethods = BeanUtil.matchSetMethods(pojoClass, result.pojoProps);
		return result;
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
		String[] methodTypes = new String[indexSize];
		int[] methodTypeValues = new int[indexSize];
		// 自动适配属性的数据类型
		for (int i = 0; i < indexSize; i++) {
			if (null != realMethods[i]) {
				methodTypes[i] = realMethods[i].getParameterTypes()[0].getTypeName();
				methodTypeValues[i] = DataType.getType(methodTypes[i]);
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
						if (cellData.getClass().getTypeName().equals(methodTypes[j])) {
							realMethods[j].invoke(bean, cellData);
						} else {
							realMethods[j].invoke(bean,
									BeanUtil.convertType(cellData, methodTypeValues[j], methodTypes[j]));
						}
					}
				}
				result.add(bean);
			}
		}
		return result;
	}

}
