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

import org.sagacity.sqltoy.config.annotation.SqlToyFieldAlias;
import org.sagacity.sqltoy.config.model.DTOEntityMapModel;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.model.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供针对sqltoy的DTO到POJO、POJO到DTO的映射工具
 * @author zhongxuchen
 * @version v1.0,Date:2020-8-8
 * @modify 2020-09-04 支持VO<->VO,DTO<->DTO,VO<->DTO 的互转
 * @modify 2022-10-19 支持对象的多级父类属性的映射
 * @modify 2023-05-01 支持多级子对象映射，代码全面改造完全工具类化，无需再依赖SqlToyContext
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
	private static Map<String, DTOEntityMapModel> dtoEntityMapperCache = new HashMap<String, DTOEntityMapModel>();

	// 递归最大层级
	private static int MAX_RECURSION = 3;

	private MapperUtils() {
	}

	public static <T extends Serializable> T map(Serializable source, Class<T> resultType, String... ignoreProperties)
			throws RuntimeException {
		if (source == null || (resultType == null || BeanUtil.isBaseDataType(resultType))) {
			throw new IllegalArgumentException("source 和 resultType 不能为null,且resultType不能为基本类型!");
		}
		return map(source, resultType, 0, ignoreProperties);
	}

	public static <T extends Serializable> List<T> mapList(List sourceList, Class<T> resultType,
			String... ignoreProperties) throws RuntimeException {
		if (sourceList == null || (resultType == null || BeanUtil.isBaseDataType(resultType))) {
			throw new IllegalArgumentException("sourceList 和 resultType 不能为null,且resultType不能为基本类型!");
		}
		// resultType不能是接口和抽象类
		if (Modifier.isAbstract(resultType.getModifiers()) || Modifier.isInterface(resultType.getModifiers())) {
			throw new IllegalArgumentException("resultType:" + resultType.getName() + " 是抽象类或接口,非法参数!");
		}
		if (sourceList.isEmpty()) {
			return new ArrayList<T>();
		}
		return mapList(sourceList, resultType, 0, ignoreProperties);
	}

	public static <T extends Serializable> Page<T> map(Page sourcePage, Class<T> resultType,
			String... ignoreProperties) {
		if (sourcePage == null || resultType == null || BeanUtil.isBaseDataType(resultType)) {
			throw new IllegalArgumentException("sourcePage 和 resultType 不能为null,且resultType不能为基本类型!");
		}
		Page result = new Page();
		result.setPageNo(sourcePage.getPageNo());
		result.setPageSize(sourcePage.getPageSize());
		result.setRecordCount(sourcePage.getRecordCount());
		result.setSkipQueryCount(sourcePage.getSkipQueryCount());
		if (sourcePage.getRows().isEmpty()) {
			return result;
		}
		result.setRows(mapList(sourcePage.getRows(), resultType, ignoreProperties));
		return result;
	}

	/**
	 * @TODO 实现POJO和VO单个对象之间的相互转换和赋值
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @param recursionLevel   避免循环递归，默认不能超过3层
	 * @param ignoreProperties
	 * @return
	 * @throws RuntimeException
	 */
	private static <T extends Serializable> T map(Serializable source, Class<T> resultType, int recursionLevel,
			String... ignoreProperties) throws RuntimeException {
		// 转成List做统一处理
		List<Serializable> sourceList = new ArrayList<Serializable>();
		sourceList.add(source);
		List<T> result = mapList(sourceList, resultType, recursionLevel, ignoreProperties);
		if (result == null || result.isEmpty()) {
			return null;
		}
		return result.get(0);
	}

	/**
	 * @TODO 实现POJO和VO对象集合之间的相互转换和赋值
	 * @param <T>
	 * @param sqlToyContext
	 * @param sourceList
	 * @param resultType
	 * @param recursionLevel   避免循环递归，默认不能超过3层
	 * @param ignoreProperties
	 * @return
	 * @throws RuntimeException
	 */
	private static <T extends Serializable> List<T> mapList(List sourceList, Class<T> resultType, int recursionLevel,
			String... ignoreProperties) throws RuntimeException {
		Class sourceClass = sourceList.iterator().next().getClass();
		DTOEntityMapModel mapModel = getDTOEntityMap(sourceClass, resultType);
		if (mapModel == null || mapModel.fromGetMethods == null || mapModel.targetSetMethods == null) {
			return null;
		}
		Method[] getMethods = mapModel.fromGetMethods;
		Method[] setMethods = mapModel.targetSetMethods;
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
		// get方法或set方法都为null,表示是一些类似serialVersionUID类的公共属性，直接返回null
		if (getAllNull || setAllNull) {
			return null;
		}
		// 不做映射处理的属性，针对targetClass
		if (ignoreProperties != null && ignoreProperties.length > 0) {
			List<Method> getRealMethods = new ArrayList<Method>();
			List<Method> setRealMethods = new ArrayList<Method>();
			String methodName;
			String ignorePropLow;
			Class paramType;
			boolean skip;
			List<String> ignoreProps = new ArrayList<String>();
			for (String ignoreProp : ignoreProperties) {
				ignoreProps.add(ignoreProp.toLowerCase());
			}
			// 以set方法为映射主体
			for (int i = 0; i < setMethods.length; i++) {
				if (setMethods[i] != null) {
					methodName = setMethods[i].getName().toLowerCase();
					paramType = setMethods[i].getParameterTypes()[0];
					skip = false;
					for (int j = 0; j < ignoreProps.size(); j++) {
						ignorePropLow = ignoreProps.get(j);
						if (methodName.equals("set".concat(ignorePropLow))
								|| (ignorePropLow.startsWith("is") && paramType.equals(boolean.class)
										&& methodName.equals("set".concat(ignorePropLow.substring(2))))) {
							skip = true;
							ignoreProps.remove(j);
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
			return reflectListToBean(dataSets, resultType, setMethods, recursionLevel);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("map/mapList,类型:[" + sourceClass.getName() + "-->" + resultType.getName()
					+ "]映射操作失败:" + e.getMessage());
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
		for (int i = 0, n = sourceList.size(); i < n; i++) {
			row = sourceList.get(i);
			if (row != null) {
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
		}
		return result;
	}

	/**
	 * @TODO 组织构造dto和pojo的映射模型放入缓存，并通过get和set方法调用完成复制过程(比BeanUtils.copyProperties效率高)
	 * @param sourceClass
	 * @param resultType
	 * @return
	 */
	private static DTOEntityMapModel getDTOEntityMap(Class sourceClass, Class resultType) {
		String sourceKey = sourceClass.getName();
		String resultKey = resultType.getName();
		String key = "fromClass=".concat(sourceKey).concat(";toClass=").concat(resultKey);
		// 通过缓存获取
		if (dtoEntityMapperCache.containsKey(key)) {
			return dtoEntityMapperCache.get(key);
		}
		DTOEntityMapModel result = sourceMapTarget(sourceClass, resultType);
		dtoEntityMapperCache.put(key, result);
		return result;
	}

	/**
	 * @TODO 解析2个类之间属性名称相同的方法，建立getXXX 和 setXXX 的映射关系
	 * @param fromClass
	 * @param targetClass
	 * @return
	 */
	private static DTOEntityMapModel sourceMapTarget(Class fromClass, Class targetClass) {
		// 不支持基本类型
		if (BeanUtil.isBaseDataType(fromClass) || BeanUtil.isBaseDataType(targetClass)) {
			return null;
		}
		DTOEntityMapModel result = new DTOEntityMapModel();
		String fieldName;
		HashMap<String, String> targetPropsMap = new HashMap<String, String>();
		// targetClass类型属性
		Class parentClass = targetClass;
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
				fieldName = field.getName();
				targetPropsMap.put(fieldName.toLowerCase(), fieldName);
			}
			parentClass = parentClass.getSuperclass();
		}
		boolean checkAlias = fromClass.equals(targetClass) ? false : true;
		// fromClass
		List<String> fromClassProps = new ArrayList<String>();
		List<String> targetProps = new ArrayList<String>();
		// dto以及其所有父类
		parentClass = fromClass;
		SqlToyFieldAlias alias;
		String aliasName;
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
				fieldName = field.getName();
				aliasName = fieldName;
				if (checkAlias) {
					alias = field.getAnnotation(SqlToyFieldAlias.class);
					if (alias != null) {
						aliasName = alias.value();
					}
				}
				if (!fromClassProps.contains(fieldName)) {
					if (targetPropsMap.containsKey(fieldName.toLowerCase())) {
						fromClassProps.add(fieldName);
						targetProps.add(targetPropsMap.get(fieldName.toLowerCase()));
					} else if (targetPropsMap.containsKey(aliasName.toLowerCase())) {
						fromClassProps.add(fieldName);
						targetProps.add(targetPropsMap.get(fieldName.toLowerCase()));
					}
				}
			}
			parentClass = parentClass.getSuperclass();
		}
		// 模型赋值
		result.fromClassName = fromClass.getName();
		result.fromProps = (String[]) fromClassProps.toArray(new String[fromClassProps.size()]);
		result.targetClassName = targetClass.getName();
		result.targetProps = (String[]) targetProps.toArray(new String[targetProps.size()]);
		// 没有匹配的属性
		if (fromClassProps.isEmpty()) {
			return result;
		}
		result.fromGetMethods = BeanUtil.matchGetMethods(fromClass, result.fromProps);
		result.targetSetMethods = BeanUtil.matchSetMethods(targetClass, result.targetProps);
		return result;
	}

	// 提取映射对应的methods
	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param dataSet
	 * @param voClass
	 * @param realMethods
	 * @param recursionLevel 递归层级
	 * @return
	 * @throws Exception
	 */
	private static List reflectListToBean(List dataSet, Class voClass, Method[] realMethods, int recursionLevel)
			throws Exception {
		List result = new ArrayList();
		int indexSize = realMethods.length;
		String[] methodTypes = new String[indexSize];
		int[] methodTypeValues = new int[indexSize];
		Class[] methodGenTypes = new Class[indexSize];
		Boolean[] isList = new Boolean[indexSize];
		// 判断List<T> 场景泛型是否是基本类型
		Boolean[] notListBaseType = new Boolean[indexSize];
		Class methodType;
		// 自动适配属性的数据类型
		for (int i = 0; i < indexSize; i++) {
			isList[i] = Boolean.FALSE;
			notListBaseType[i] = Boolean.TRUE;
			if (null != realMethods[i]) {
				methodType = realMethods[i].getParameterTypes()[0];
				methodTypes[i] = methodType.getTypeName();
				methodTypeValues[i] = DataType.getType(methodTypes[i]);
				// 非普通类型、非枚举、非Map(DTO)
				if ((methodTypeValues[i] == DataType.objectType || methodTypeValues[i] == DataType.listType
						|| methodTypeValues[i] == DataType.setType) && !methodType.isEnum()
						&& !Map.class.isAssignableFrom(methodType)) {
					methodGenTypes[i] = realMethods[i].getParameterTypes()[0];
				}
				// 泛型
				if (realMethods[i].getGenericParameterTypes()[0] instanceof ParameterizedType) {
					methodType = (Class) ((ParameterizedType) realMethods[i].getGenericParameterTypes()[0])
							.getActualTypeArguments()[0];
					methodGenTypes[i] = methodType;
					// 非基本类型、非List、非Map、非枚举、非数组
					if (!BeanUtil.isBaseDataType(methodType) && !List.class.equals(methodType)
							&& !ArrayList.class.equals(methodType) && !methodType.isEnum()
							&& !Map.class.isAssignableFrom(methodType) && !methodType.isArray()) {
						notListBaseType[i] = Boolean.FALSE;
					}
					if (realMethods[i].getParameterTypes()[0].equals(List.class)
							|| realMethods[i].getParameterTypes()[0].equals(ArrayList.class)) {
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
		for (int i = 0, end = dataSet.size(); i < end; i++) {
			row = (List) dataSet.get(i);
			if (row != null) {
				bean = voClass.getDeclaredConstructor().newInstance();
				size = row.size();
				for (int j = 0; j < size; j++) {
					cellData = row.get(j);
					if (cellData != null && realMethods[j] != null) {
						// 基本类型
						if (methodTypeValues[j] != DataType.objectType && methodTypeValues[j] != DataType.listType
								&& methodTypeValues[j] != DataType.setType) {
							realMethods[j].invoke(bean,
									BeanUtil.convertType(cellData, methodTypeValues[j], methodTypes[j]));
						} // List<DTO>
						else if (methodGenTypes[j] != null && (cellData instanceof List) && isList[j]) {
							cellList = (List) cellData;
							if (!cellList.isEmpty()) {
								if (!notListBaseType[j] && recursionLevel < MAX_RECURSION) {
									// 类型映射
									List subItems = mapList((List) cellData, methodGenTypes[j], recursionLevel + 1);
									if (subItems != null && !subItems.isEmpty()) {
										realMethods[j].invoke(bean, subItems);
									}
								}
								// 基本类型或特殊类型
								else if (cellList.get(0) != null
										&& cellList.get(0).getClass().equals(methodGenTypes[j])) {
									realMethods[j].invoke(bean, cellList);
								}
							}
						} // DTO->DTO 对象之间转换
						else if (recursionLevel < MAX_RECURSION && methodGenTypes[j] != null
								&& (cellData instanceof Serializable)) {
							convertData = map((Serializable) cellData, methodGenTypes[j], recursionLevel + 1);
							if (convertData != null) {
								realMethods[j].invoke(bean, convertData);
							}
						} // 类型相同直接赋值
						else if (cellData.getClass().getTypeName().equals(methodTypes[j])) {
							realMethods[j].invoke(bean, cellData);
						}
					}
				}
				result.add(bean);
			}
		}
		return result;
	}

}
