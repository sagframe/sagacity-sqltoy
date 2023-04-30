package org.sagacity.sqltoy.utils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
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
 * @modify 2020-09-04 支持VO<->VO,DTO<->DTO,VO<->DTO 的互转
 * @modify 2022-10-19 支持对象的多级父类属性的映射
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
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
	 * @param deepIndex        避免循环递归，默认不能超过3层
	 * @param ignoreProperties
	 * @return
	 * @throws RuntimeException
	 */
	public static <T extends Serializable> T map(SqlToyContext sqlToyContext, Serializable source, Class<T> resultType,
			int deepIndex, String... ignoreProperties) throws RuntimeException {
		if (source == null || (resultType == null || resultType.equals(Object.class))) {
			throw new IllegalArgumentException("source 和 resultType 不能为null,且resultType不能为Object.class!");
		}
		// 转成List做统一处理
		List<Serializable> sourceList = new ArrayList<Serializable>();
		sourceList.add(source);
		List<T> result = mapList(sqlToyContext, sourceList, resultType, deepIndex, ignoreProperties);
		return result.get(0);
	}

	/**
	 * @TODO 实现POJO和VO对象集合之间的相互转换和赋值
	 * @param <T>
	 * @param sqlToyContext
	 * @param sourceList
	 * @param resultType
	 * @param deepIndex        避免循环递归，默认不能超过3层
	 * @param ignoreProperties
	 * @return
	 * @throws RuntimeException
	 */
	public static <T extends Serializable> List<T> mapList(SqlToyContext sqlToyContext, List<Serializable> sourceList,
			Class<T> resultType, int deepIndex, String... ignoreProperties) throws RuntimeException {
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
		// 两个类没有匹配一致的方法
		if (getMethods == null || setMethods == null) {
			return null;
		}
		// 判断get方法和set方法是否都是null，都是null无需进行后续操作
		boolean getAllNull = true;
		boolean setAllNull = true;
		for (int i = 0; i < getMethods.length; i++) {
			if (getMethods[i] != null) {
				getAllNull = false;
			}
			if (setMethods[i] != null) {
				setAllNull = false;
			}
		}
		if (getAllNull || setAllNull) {
			return null;
		}
		if (ignoreProperties != null && ignoreProperties.length > 0) {
			List<Method> getRealMethods = new ArrayList<Method>();
			List<Method> setRealMethods = new ArrayList<Method>();
			String methodName;
			String ignorePropLow;
			Class paramType;
			boolean skip;
			List<String> props = new ArrayList<String>();
			for (String ignoreProp : ignoreProperties) {
				props.add(ignoreProp.toLowerCase());
			}
			// 以set方法为映射主体
			for (int i = 0; i < setMethods.length; i++) {
				if (setMethods[i] != null) {
					methodName = setMethods[i].getName().toLowerCase();
					paramType = setMethods[i].getParameterTypes()[0];
					skip = false;
					for (int j = 0; j < props.size(); j++) {
						ignorePropLow = props.get(j);
						if (methodName.equals("set".concat(ignorePropLow))
								|| (ignorePropLow.startsWith("is") && paramType.equals(boolean.class)
										&& methodName.equals("set".concat(ignorePropLow.substring(2))))) {
							skip = true;
							props.remove(j);
							j--;
							break;
						}
					}
					if (!skip) {
						getRealMethods.add(getMethods[i]);
						setRealMethods.add(setMethods[i]);
					}
				}
			}
			if (setRealMethods.size() == 0) {
				logger.warn("最终映射对应的属性数量为零,请检查ignoreProperties是否正确,过滤了全部匹配属性!");
				return null;
			}
			getMethods = new Method[setRealMethods.size()];
			setMethods = new Method[setRealMethods.size()];
			getRealMethods.toArray(getMethods);
			setRealMethods.toArray(setMethods);
		}
		try {
			List dataSets = invokeGetValues(sourceList, getMethods);
			return reflectListToBean(sqlToyContext, dataSets, resultType, setMethods, deepIndex);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("map/mapList操作失败:" + e.getMessage());
		}
	}

	/**
	 * @TODO 通过get方法获取对象的值放入List中
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
			result = PO2PO(sqlToyContext, sourceClass, resultType);
		} else {
			result = PO2DTO(sqlToyContext, pojoClass, dtoClass);
		}
		dtoEntityMappCache.put(key, result);
		return result;
	}

	/**
	 * @TODO POJO 跟POJO 或 DTO 到DTO 之间的映射复制
	 * @param sqlToyContext
	 * @param dtoClass
	 * @param pojoClass
	 * @return
	 */
	private static DTOEntityMapModel PO2PO(SqlToyContext sqlToyContext, Class dtoClass, Class pojoClass) {
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
				if (pojoPropsMap.containsKey(fieldName.toLowerCase()) && !dtoProps.contains(fieldName)) {
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
		// 没有匹配的属性
		if (dtoProps.isEmpty()) {
			return result;
		}
		result.dtoGetMethods = BeanUtil.matchGetMethods(dtoClass, result.dtoProps);
		result.dtoSetMethods = BeanUtil.matchSetMethods(dtoClass, result.dtoProps);
		result.pojoGetMethods = BeanUtil.matchGetMethods(pojoClass, result.pojoProps);
		result.pojoSetMethods = BeanUtil.matchSetMethods(pojoClass, result.pojoProps);
		return result;
	}

	/**
	 * @TODO POJO 跟DTO 之间的映射复制
	 * @param sqlToyContext
	 * @param pojoClass
	 * @param dtoClass
	 * @return
	 */
	private static DTOEntityMapModel PO2DTO(SqlToyContext sqlToyContext, Class pojoClass, Class dtoClass) {
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
				if (pojoPropsMap.containsKey(aliasName.toLowerCase()) && !dtoProps.contains(fieldName)) {
					dtoProps.add(fieldName);
					pojoProps.add(pojoPropsMap.get(aliasName.toLowerCase()));
				}
			}
			parentClass = parentClass.getSuperclass();
		}

		DTOEntityMapModel result = new DTOEntityMapModel();
		// 模型赋值
		result.dtoClassName = dtoClass.getName();
		result.dtoProps = (String[]) dtoProps.toArray(new String[dtoProps.size()]);
		result.pojoClassName = pojoClass.getName();
		result.pojoProps = (String[]) pojoProps.toArray(new String[pojoProps.size()]);
		// 没有匹配的属性
		if (dtoProps.isEmpty()) {
			return result;
		}
		result.dtoGetMethods = BeanUtil.matchGetMethods(dtoClass, result.dtoProps);
		result.dtoSetMethods = BeanUtil.matchSetMethods(dtoClass, result.dtoProps);
		result.pojoGetMethods = BeanUtil.matchGetMethods(pojoClass, result.pojoProps);
		result.pojoSetMethods = BeanUtil.matchSetMethods(pojoClass, result.pojoProps);
		return result;
	}

	// 提取映射对应的methods
	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param sqlToyContext
	 * @param datas
	 * @param voClass
	 * @param realMethods
	 * @param deepIndex
	 * @return
	 * @throws Exception
	 */
	private static List reflectListToBean(SqlToyContext sqlToyContext, List datas, Class voClass, Method[] realMethods,
			int deepIndex) throws Exception {
		List result = new ArrayList();
		int indexSize = realMethods.length;
		String[] methodTypes = new String[indexSize];
		int[] methodTypeValues = new int[indexSize];
		Class[] methodGenTypes = new Class[indexSize];
		Boolean[] isList = new Boolean[indexSize];
		Class methodType;
		// 自动适配属性的数据类型
		for (int i = 0; i < indexSize; i++) {
			isList[i] = Boolean.FALSE;
			if (null != realMethods[i]) {
				methodTypes[i] = realMethods[i].getParameterTypes()[0].getTypeName();
				methodTypeValues[i] = DataType.getType(methodTypes[i]);
				// 泛型
				if (realMethods[i].getGenericParameterTypes()[0] instanceof ParameterizedType) {
					methodType = (Class) ((ParameterizedType) realMethods[i].getGenericParameterTypes()[0])
							.getActualTypeArguments()[0];
					// if(!methodType.isPrimitive())
					methodGenTypes[i] = methodType;
					if (realMethods[i].getParameterTypes()[0].equals(List.class)) {
						isList[i] = Boolean.TRUE;
					}
				}
			}
		}
		int size;
		Object bean;
		List row;
		List cellList;
		Object cellData = null;
		Object convertData = null;
		for (int i = 0; i < datas.size(); i++) {
			row = (List) datas.get(i);
			if (row != null) {
				bean = voClass.getDeclaredConstructor().newInstance();
				size = row.size();
				for (int j = 0; j < size; j++) {
					cellData = row.get(j);
					if (cellData != null && realMethods[j] != null) {
						// 2023/4/22 待处理：需要考虑对象中嵌套子对象和List<DTO> 这种形式
						// List<DTO>
						if ((cellData instanceof List) && methodGenTypes[j] != null && isList[j]) {
							cellList = (List) cellData;
							if (!cellList.isEmpty()) {
								// 类型一致直接赋值
								if (cellList.get(0).getClass().equals(methodGenTypes[j])) {
									realMethods[j].invoke(bean, cellList);
								} else if (deepIndex < 3) {
									// 类型映射
									List subItems = mapList(sqlToyContext, (List) cellData, methodGenTypes[j],
											deepIndex + 1);
									if (subItems != null && !subItems.isEmpty()) {
										realMethods[j].invoke(bean, subItems);
									}
								}
							}
						} else if (cellData.getClass().getTypeName().equals(methodTypes[j])) {
							realMethods[j].invoke(bean, cellData);
						} else {
							convertData = BeanUtil.convertType(cellData, methodTypeValues[j], methodTypes[j]);
							// 常规类型转换成功,直接赋值
							if (convertData != cellData) {
								realMethods[j].invoke(bean, convertData);
							} // 属性都是自定义对象类型 DTO -->DTO
							else if (deepIndex < 3 && methodGenTypes[j] != null && cellData instanceof Serializable) {
								convertData = map(sqlToyContext, (Serializable) cellData, methodGenTypes[j],
										deepIndex + 1);
								if (convertData != null) {
									realMethods[j].invoke(bean, convertData);
								}
							}
						}
					}
				}
				result.add(bean);
			}
		}
		return result;
	}

}
