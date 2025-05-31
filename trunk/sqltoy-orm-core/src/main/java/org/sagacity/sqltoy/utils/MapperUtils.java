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
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.config.annotation.SqlToyFieldAlias;
import org.sagacity.sqltoy.config.model.DTOEntityMapModel;
import org.sagacity.sqltoy.config.model.DataType;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.PropsMapperConfig;
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
 * @modify 2024-03-15 支持DTO<-->POJO 双向映射中@SqlToyFieldAlias，由gleam反馈
 * @modify 2024-05-10 进一步强化对象间映射，增加指定属性映射fieldsMap参数
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

	private static ConcurrentHashMap<String, HashMap<String, String>> classHasAliasMap = new ConcurrentHashMap<>();

	// 递归最大层级
	private static int MAX_RECURSION = 3;

	private MapperUtils() {
	}

	/**
	 * @TODO DTO<-->POJO 双向映射
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @param ignoreProperties 忽略的属性
	 * @return
	 * @throws RuntimeException
	 */
	@Deprecated
	public static <T extends Serializable> T map(Serializable source, Class<T> resultType, String... ignoreProperties)
			throws RuntimeException {
		return map(source, resultType, new PropsMapperConfig(ignoreProperties).isIgnore(true));
	}

	/**
	 * @TODO DTO<-->POJO 双向映射
	 * @param <T>
	 * @param source
	 * @param resultType
	 * @param propsMapperConfig 映射属性配置
	 * @return
	 * @throws RuntimeException
	 */
	public static <T extends Serializable> T map(Serializable source, Class<T> resultType,
			PropsMapperConfig propsMapperConfig) throws RuntimeException {
		if (source == null) {
			return null;
		}
		if (resultType == null || BeanUtil.isBaseDataType(resultType)) {
			throw new IllegalArgumentException("resultType 不能为null,且resultType不能为基本类型!");
		}
		return map(source, resultType, 0, propsMapperConfig);
	}

	@Deprecated
	public static <T extends Serializable> List<T> mapList(List sourceList, Class<T> resultType,
			String... ignoreProperties) throws RuntimeException {
		return mapList(sourceList, resultType, new PropsMapperConfig(ignoreProperties).isIgnore(true));
	}

	/**
	 * @TODO List<DTO> <--> List<POJO> 互相映射
	 * @param <T>
	 * @param sourceList
	 * @param resultType
	 * @param propsMapperConfig
	 * @return
	 * @throws RuntimeException
	 */
	public static <T extends Serializable> List<T> mapList(List sourceList, Class<T> resultType,
			PropsMapperConfig propsMapperConfig) throws RuntimeException {
		if (sourceList == null) {
			return null;
		}
		if (resultType == null || BeanUtil.isBaseDataType(resultType)) {
			throw new IllegalArgumentException("resultType 不能为null,且resultType不能为基本类型!");
		}
		// resultType不能是接口和抽象类
		if (Modifier.isAbstract(resultType.getModifiers()) || Modifier.isInterface(resultType.getModifiers())) {
			throw new IllegalArgumentException("resultType:" + resultType.getName() + " 是抽象类或接口,非法参数!");
		}
		if (sourceList.isEmpty()) {
			return new ArrayList<T>();
		}
		return mapList(sourceList, resultType, 0, propsMapperConfig);
	}

	/**
	 * @TODO 分页映射
	 * @param <T>
	 * @param sourcePage
	 * @param resultType
	 * @param ignoreProperties 忽略的属性
	 * @return
	 */
	@Deprecated
	public static <T extends Serializable> Page<T> map(Page sourcePage, Class<T> resultType,
			String... ignoreProperties) {
		return map(sourcePage, resultType, new PropsMapperConfig(ignoreProperties).isIgnore(true));
	}

	/**
	 * @TODO 分页映射
	 * @param <T>
	 * @param sourcePage
	 * @param resultType
	 * @param propsMapperConfig
	 * @return
	 */
	public static <T extends Serializable> Page<T> map(Page sourcePage, Class<T> resultType,
			PropsMapperConfig propsMapperConfig) {
		if (sourcePage == null) {
			return null;
		}
		if (resultType == null || BeanUtil.isBaseDataType(resultType)) {
			throw new IllegalArgumentException("resultType 不能为null,且resultType不能为基本类型!");
		}
		Page result = new Page();
		result.setPageNo(sourcePage.getPageNo());
		result.setPageSize(sourcePage.getPageSize());
		result.setRecordCount(sourcePage.getRecordCount());
		result.setSkipQueryCount(sourcePage.getSkipQueryCount());
		if (sourcePage.getRows() == null || sourcePage.getRows().isEmpty()) {
			return result;
		}
		result.setRows(mapList(sourcePage.getRows(), resultType, propsMapperConfig));
		return result;
	}

	/**
	 * @TODO 两个实体对象属性值复制
	 * @param source
	 * @param target
	 * @param ignoreProperties 忽略的不参与复制的属性
	 * @throws RuntimeException
	 */
	@Deprecated
	public static void copyProperties(Serializable source, Serializable target, String... ignoreProperties)
			throws RuntimeException {
		copyProperties(source, target, new PropsMapperConfig(ignoreProperties).isIgnore(true));
	}

	/**
	 * @TODO 两个实体对象属性值复制
	 * @param source
	 * @param target
	 * @param propsMapperConfig new
	 *                          PropsMapperConfig(String...properties).isIgnore(true|false)
	 *                          默认为false
	 * @throws RuntimeException
	 */
	public static void copyProperties(Serializable source, Serializable target, PropsMapperConfig propsMapperConfig)
			throws RuntimeException {
		if (source == null || target == null) {
			return;
		}
		if (BeanUtil.isBaseDataType(source.getClass()) || BeanUtil.isBaseDataType(target.getClass())) {
			throw new IllegalArgumentException("copyProperties<DTO>不支持基本类型对象的属性值映射!");
		}
		List<Serializable> sourceList = new ArrayList<Serializable>();
		sourceList.add(source);
		List<Serializable> targetList = new ArrayList<Serializable>();
		targetList.add(target);
		copyProperties(sourceList, targetList, propsMapperConfig);
	}

	/**
	 * @TODO List集合属性映射
	 * @param sourceList
	 * @param targetList
	 * @param copyPropsConfig
	 * @throws RuntimeException
	 */
	public static void copyProperties(List sourceList, List targetList, PropsMapperConfig propsMapperConfig)
			throws RuntimeException {
		if (sourceList == null || sourceList.isEmpty()) {
			throw new RuntimeException("copyProperties<List>操作sourceList为空对象");
		}
		if (targetList == null || targetList.isEmpty()) {
			throw new RuntimeException("copyProperties<List>操作targetList为空对象");
		}
		if (sourceList.size() != targetList.size()) {
			throw new RuntimeException("copyProperties<List>操作sourceList和targetList集合数据记录:" + sourceList.size() + "!="
					+ targetList.size() + "不相同!");
		}
		Class sourceClass = sourceList.get(0).getClass();
		Class targetClass = targetList.get(0).getClass();
		if (BeanUtil.isBaseDataType(sourceClass) || BeanUtil.isBaseDataType(targetClass)) {
			throw new IllegalArgumentException("copyProperties<List>不支持基本类型对象的属性值映射!");
		}
		PropsMapperConfig propConfig = (propsMapperConfig == null) ? new PropsMapperConfig() : propsMapperConfig;
		Method[] getMethods;
		Method[] setMethods;
		// 属性参数未定义即表示全部属性匹配
		if (propConfig.isIgnore() || propConfig.getProperties() == null) {
			Object[] getSetMethods = matchGetSetMethods(sourceClass, targetClass, propConfig.getFieldsMap(),
					propConfig.getProperties());
			if (getSetMethods == null) {
				return;
			}
			getMethods = (Method[]) getSetMethods[0];
			setMethods = (Method[]) getSetMethods[1];
		} else {
			// 指定属性映射
			getMethods = BeanUtil.matchGetMethods(sourceClass, propConfig.getProperties());
			HashMap<String, String> aliasMap = new HashMap<>();
			aliasMap.putAll(getClassAliasMap(sourceClass, true));
			aliasMap.putAll(getClassAliasMap(targetClass, false));
			aliasMap.putAll(propConfig.getFieldsMap());
			setMethods = BeanUtil.matchSetMethods(targetClass, getMappedProps(propConfig.getProperties(), aliasMap));
		}
		if (getMethods.length < 1 || setMethods.length < 1) {
			return;
		}
		try {
			List dataSets = invokeGetValues(sourceList, getMethods);
			listToList(dataSets, targetList, setMethods, propsMapperConfig.getSkipNull());
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("copyProperties<List>类型:[" + sourceClass.getName() + "-->"
					+ targetClass.getName() + "]映射操作失败:" + e.getMessage());
		}
	}

	@Deprecated
	public static void copyProperties(List sourceList, List targetList, String... ignoreProperties)
			throws RuntimeException {
		copyProperties(sourceList, targetList, new PropsMapperConfig(ignoreProperties).isIgnore(true));
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
	public static <T extends Serializable> T map(Serializable source, Class<T> resultType, int recursionLevel,
			PropsMapperConfig propsMapperConfig) throws RuntimeException {
		// 转成List做统一处理
		List<Serializable> sourceList = new ArrayList<Serializable>();
		sourceList.add(source);
		List<T> result = mapList(sourceList, resultType, recursionLevel, propsMapperConfig);
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
	 * @param targetClass
	 * @param recursionLevel    避免循环递归，默认不能超过3层
	 * @param propsMapperConfig 设置映射属性的模型
	 * @return
	 * @throws RuntimeException
	 */
	public static <T extends Serializable> List<T> mapList(List sourceList, Class<T> targetClass, int recursionLevel,
			PropsMapperConfig propsMapperConfig) throws RuntimeException {
		Class sourceClass = sourceList.iterator().next().getClass();
		Method[] getMethods;
		Method[] setMethods;
		PropsMapperConfig propConfig = (propsMapperConfig == null) ? new PropsMapperConfig() : propsMapperConfig;
		// 属性参数未定义即表示全部属性匹配
		if (propConfig.isIgnore() || propConfig.getProperties() == null) {
			Object[] getSetMethods = matchGetSetMethods(sourceClass, targetClass, propConfig.getFieldsMap(),
					propConfig.getProperties());
			if (getSetMethods == null) {
				return null;
			}
			getMethods = (Method[]) getSetMethods[0];
			setMethods = (Method[]) getSetMethods[1];
		} else {
			// 指定属性映射
			getMethods = BeanUtil.matchGetMethods(sourceClass, propConfig.getProperties());
			HashMap<String, String> aliasMap = new HashMap<>();
			aliasMap.putAll(getClassAliasMap(sourceClass, true));
			aliasMap.putAll(getClassAliasMap(targetClass, false));
			aliasMap.putAll(propConfig.getFieldsMap());
			setMethods = BeanUtil.matchSetMethods(targetClass, getMappedProps(propConfig.getProperties(), aliasMap));
		}
		if (getMethods.length < 1 || setMethods.length < 1) {
			return null;
		}
		try {
			List dataSets = invokeGetValues(sourceList, getMethods);
			return reflectListToBean(dataSets, targetClass, setMethods, recursionLevel);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("map/mapList,类型:[" + sourceClass.getName() + "-->" + targetClass.getName()
					+ "]映射操作失败:" + e.getMessage());
		}
	}

	/**
	 * @TODO 通过映射关系，提取最终的映射属性名称
	 * @param sourceProps
	 * @param sourceTargetMap
	 * @return
	 */
	private static String[] getMappedProps(String[] sourceProps, Map<String, String> sourceTargetMap) {
		if (sourceTargetMap == null || sourceTargetMap.isEmpty()) {
			return sourceProps;
		}
		IgnoreKeyCaseMap<String, String> ignoreKeyCaseMap = new IgnoreKeyCaseMap<>(sourceTargetMap);
		int aryLength = sourceProps.length;
		String[] resultProps = new String[aryLength];
		String key;
		for (int i = 0; i < aryLength; i++) {
			key = sourceProps[i];
			if (ignoreKeyCaseMap.containsKey(key)) {
				resultProps[i] = ignoreKeyCaseMap.get(key);
			} else {
				resultProps[i] = key;
			}
		}
		return resultProps;
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
	 * @param fieldsNameMap
	 * @return
	 */
	private static DTOEntityMapModel getDTOEntityMap(Class sourceClass, Class resultType,
			Map<String, String> fieldsNameMap) {
		String sourceKey = sourceClass.getName();
		String resultKey = resultType.getName();
		String mapKey = (fieldsNameMap == null || fieldsNameMap.isEmpty()) ? "" : fieldsNameMap.toString();
		String key = "fromClass=".concat(sourceKey).concat(";toClass=").concat(resultKey).concat(";mapKey=")
				.concat(mapKey);
		// 通过缓存获取
		if (dtoEntityMapperCache.containsKey(key)) {
			return dtoEntityMapperCache.get(key);
		}
		DTOEntityMapModel result = sourceMapTarget(sourceClass, resultType, fieldsNameMap);
		dtoEntityMapperCache.put(key, result);
		return result;
	}

	/**
	 * @TODO 解析2个类之间属性名称相同的方法，建立getXXX 和 setXXX 的映射关系
	 * @param fromClass
	 * @param targetClass
	 * @return
	 */
	private static DTOEntityMapModel sourceMapTarget(Class fromClass, Class targetClass,
			Map<String, String> fieldsNameMap) {
		// 不支持基本类型
		if (BeanUtil.isBaseDataType(fromClass) || BeanUtil.isBaseDataType(targetClass)) {
			return null;
		}
		// 是否要匹配别名(两个不同对象之间，属性名称不一致，通过注解提供别名模式进行映射)
		boolean checkAlias = fromClass.equals(targetClass) ? false : true;
		DTOEntityMapModel result = new DTOEntityMapModel();
		String fieldName;
		SqlToyFieldAlias alias;
		IgnoreKeyCaseMap<String, String> targetPropsMap = new IgnoreKeyCaseMap<String, String>();
		// targetClass类型属性
		Class parentClass = targetClass;
		IgnoreKeyCaseMap<String, String> aliasMap = new IgnoreKeyCaseMap<String, String>();
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
				fieldName = field.getName();
				// 不同对象，检查是否有别名
				if (checkAlias) {
					alias = field.getAnnotation(SqlToyFieldAlias.class);
					if (alias != null) {
						aliasMap.put(alias.value(), fieldName);
					}
				}
				targetPropsMap.put(fieldName, fieldName);
			}
			parentClass = parentClass.getSuperclass();
		}
		// 将指定的属性名称映射最后放入其中
		if (fieldsNameMap != null && !fieldsNameMap.isEmpty()) {
			aliasMap.putAll(fieldsNameMap);
		}
		// fromClass
		List<String> fromClassProps = new ArrayList<String>();
		List<String> targetProps = new ArrayList<String>();
		// dto以及其所有父类
		parentClass = fromClass;
		String aliasName;
		while (!parentClass.equals(Object.class)) {
			for (Field field : parentClass.getDeclaredFields()) {
				fieldName = field.getName();
				aliasName = fieldName;
				// 不同对象，检查是否有别名
				if (checkAlias) {
					alias = field.getAnnotation(SqlToyFieldAlias.class);
					if (alias != null) {
						aliasName = alias.value();
					}
				}
				if (!fromClassProps.contains(fieldName)) {
					if (targetPropsMap.containsKey(fieldName)) {
						fromClassProps.add(fieldName);
						targetProps.add(targetPropsMap.get(fieldName));
					} else if (targetPropsMap.containsKey(aliasName)) {
						fromClassProps.add(fieldName);
						targetProps.add(targetPropsMap.get(aliasName));
					} else if (aliasMap.containsKey(fieldName)) {
						fromClassProps.add(fieldName);
						targetProps.add(aliasMap.get(fieldName));
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
				methodTypeValues[i] = DataType.getType(methodType);
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
		PropsMapperConfig emptyConfig = new PropsMapperConfig();
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
									List subItems = mapList((List) cellData, methodGenTypes[j], recursionLevel + 1,
											emptyConfig);
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
							convertData = map((Serializable) cellData, methodGenTypes[j], recursionLevel + 1,
									emptyConfig);
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

	/**
	 * @TODO List和List属性映射，只支持基本类型和类型相同的属性映射
	 * @param dataSet
	 * @param targetList
	 * @param realMethods
	 * @param skipNull
	 * @throws Exception
	 */
	private static void listToList(List dataSet, List targetList, Method[] realMethods, boolean skipNull)
			throws Exception {
		int indexSize = realMethods.length;
		String[] methodTypes = new String[indexSize];
		int[] methodTypeValues = new int[indexSize];
		Class methodType;
		// 自动适配属性的数据类型
		for (int i = 0; i < indexSize; i++) {
			if (null != realMethods[i]) {
				methodType = realMethods[i].getParameterTypes()[0];
				methodTypes[i] = methodType.getTypeName();
				methodTypeValues[i] = DataType.getType(methodType);
			}
		}
		int size;
		Object bean;
		List row;
		Object cellData = null;
		for (int i = 0, end = dataSet.size(); i < end; i++) {
			row = (List) dataSet.get(i);
			if (row != null) {
				bean = targetList.get(i);
				size = row.size();
				for (int j = 0; j < size; j++) {
					cellData = row.get(j);
					if (realMethods[j] != null) {
						if (cellData != null) {
							// 基本类型
							if (methodTypeValues[j] != DataType.objectType && methodTypeValues[j] != DataType.listType
									&& methodTypeValues[j] != DataType.setType) {
								realMethods[j].invoke(bean,
										BeanUtil.convertType(cellData, methodTypeValues[j], methodTypes[j]));
							} // 类型相同直接赋值
							else if (cellData.getClass().getTypeName().equals(methodTypes[j])) {
								realMethods[j].invoke(bean, cellData);
							}
						} else if (!skipNull) {
							realMethods[j].invoke(bean,
									BeanUtil.convertType(cellData, methodTypeValues[j], methodTypes[j]));
						}
					}
				}
			}
		}
	}

	/**
	 * @TODO 两个类属性匹配，获取对应的getMehtod和setMethod
	 * @param sourceClass
	 * @param targetClass
	 * @param fieldsNameMap
	 * @param ignoreProperties
	 * @return
	 */
	private static Object[] matchGetSetMethods(Class sourceClass, Class targetClass, Map<String, String> fieldsNameMap,
			String... ignoreProperties) {
		DTOEntityMapModel mapModel = getDTOEntityMap(sourceClass, targetClass, fieldsNameMap);
		if (mapModel == null || mapModel.fromGetMethods == null || mapModel.targetSetMethods == null) {
			return null;
		}
		Method[] getMethods = mapModel.fromGetMethods;
		Method[] setMethods = mapModel.targetSetMethods;
		// 判断get方法和set方法是否都是null，都是null无需进行后续操作
		boolean getAllNull = true;
		boolean setAllNull = true;
		for (int i = 0, n = getMethods.length; i < n; i++) {
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
			for (int i = 0, n = setMethods.length; i < n; i++) {
				if (setMethods[i] != null) {
					methodName = setMethods[i].getName().toLowerCase();
					paramType = setMethods[i].getParameterTypes()[0];
					skip = false;
					for (int j = 0; j < ignoreProps.size(); j++) {
						ignorePropLow = ignoreProps.get(j);
						// boolean 类型去除is
						if (methodName.equals("set".concat(ignorePropLow))
								|| (ignorePropLow.startsWith("is") && paramType.equals(boolean.class)
										&& methodName.equals("set".concat(ignorePropLow.substring(2))))) {
							skip = true;
							// 移除匹配上的属性，避免下次继续匹配
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
		Object[] result = new Object[2];
		result[0] = getMethods;
		result[1] = setMethods;
		return result;
	}

	/**
	 * @TODO 提取对象中@SqlToyFieldAlias 属性跟其他对象映射关系
	 * @param targetClass
	 * @param doFrom
	 * @return
	 */
	private static HashMap<String, String> getClassAliasMap(Class targetClass, boolean doFrom) {
		String typeName = targetClass.getTypeName();
		String direction = doFrom ? "from" : "target";
		String mapKey = "class=" + typeName + ";direct=" + direction;
		HashMap<String, String> aliasMap = classHasAliasMap.get(mapKey);
		if (aliasMap == null) {
			aliasMap = new HashMap<String, String>();
			Class parentClass = targetClass;
			SqlToyFieldAlias alias;
			while (!parentClass.equals(Object.class)) {
				for (Field field : parentClass.getDeclaredFields()) {
					// 不同对象，检查是否有别名
					alias = field.getAnnotation(SqlToyFieldAlias.class);
					if (alias != null) {
						if (doFrom) {
							aliasMap.put(field.getName(), alias.value());
						} else {
							aliasMap.put(alias.value(), field.getName());
						}
					}
				}
				parentClass = parentClass.getSuperclass();
			}
			classHasAliasMap.put(mapKey, aliasMap);
		}
		return aliasMap;
	}
}
