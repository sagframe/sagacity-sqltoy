package org.sagacity.sqltoy.utils;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.sagacity.sqltoy.callback.XMLCallbackHandler;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @project sagacity-sqltoy
 * @description xml处理的工具类,提供xml对应schema validator等功能
 * @author zhongxuchen
 * @version v1.0,Date:2009-4-27
 */
public class XMLUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(XMLUtil.class);

	// xml 忽视验证的特性
	private final static String NO_VALIDATOR_FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

	private XMLUtil() {
	}

	/**
	 * @todo 读取xml文件
	 * @param xmlFile
	 * @param charset
	 * @param isValidator
	 * @param handler
	 * @throws Exception
	 */
	public static Object readXML(Object xmlFile, String charset, boolean isValidator, XMLCallbackHandler handler)
			throws Exception {
		if (StringUtil.isBlank(xmlFile)) {
			return null;
		}
		InputStream fileIS = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			if (!isValidator) {
				factory.setFeature(NO_VALIDATOR_FEATURE, false);
			}
			DocumentBuilder builder = factory.newDocumentBuilder();
			fileIS = FileUtil.getFileInputStream(xmlFile);
			if (fileIS != null) {
				Document doc = builder.parse(fileIS);
				if (null != doc) {
					return handler.process(doc, doc.getDocumentElement());
				}
			}
		} catch (Exception e) {
			logger.error("解析文件:{}错误:{}!", xmlFile, e.getMessage());
			throw e;
		} finally {
			if (fileIS != null) {
				fileIS.close();
			}
		}
		return null;
	}

	/**
	 * @TODO 将数组2位一组转成map
	 * @param keyValues
	 * @return
	 */
	private static IgnoreKeyCaseMap<String, String> asMap(String... keyValues) {
		IgnoreKeyCaseMap<String, String> result = new IgnoreKeyCaseMap<String, String>();
		if (keyValues == null || keyValues.length == 0) {
			return result;
		}
		for (int i = 0; i < keyValues.length - 1; i = i + 2) {
			result.put(keyValues[i], keyValues[i + 1]);
		}
		return result;
	}

	/**
	 * @todo 解析xml元素的属性映射到java对象属性
	 * @param elt
	 * @param entity
	 * @param aliasProps 属性映射,长度必须是偶数,如:a对应到a1,{a,a1,b,b1}
	 * @throws Exception
	 */
	public static void setAttributes(Element elt, Serializable entity, String... aliasProps) throws Exception {
		if (elt == null) {
			return;
		}
		IgnoreKeyCaseMap<String, String> realMap = asMap(aliasProps);
		NamedNodeMap attrs = elt.getAttributes();
		String name;
		String value;
		String[] properties = new String[attrs.getLength() + 1];
		String[] values = new String[attrs.getLength() + 1];
		int index = 0;
		Node attr;
		for (int i = 0; i < attrs.getLength(); i++) {
			attr = attrs.item(i);
			name = attr.getNodeName();
			value = attr.getNodeValue();
			// 对照属性
			if (realMap.containsKey(name)) {
				properties[index] = realMap.get(name);
			} else {
				properties[index] = StringUtil.toHumpStr(name, false);
			}
			values[index] = value;
			index++;
		}
		// 将元素body中的值作为元素自身
		name = elt.getNodeName();
		if (realMap.containsKey(name)) {
			properties[index] = realMap.get(name);
		} else {
			properties[index] = StringUtil.toHumpStr(name, false);
		}
		// 最后一个
		values[index] = StringUtil.trim(elt.getTextContent());
		Method[] realMethods = BeanUtil.matchSetMethods(entity.getClass(), properties);
		Method method;
		String[] args;
		Class argType;
		String className;
		for (int i = 0; i < properties.length; i++) {
			// 属性值为空白的排除掉
			if (StringUtil.isNotBlank(values[i])) {
				method = realMethods[i];
				if (method != null) {
					try {
						argType = method.getParameterTypes()[0];
						className = argType.getTypeName().toLowerCase();
						className = className.substring(className.lastIndexOf(".") + 1);
						if (argType.isArray()) {
							// 替换全角为半角
							args = values[i].replaceAll("\\，", ",").split("\\,");
							className = className.substring(0, className.indexOf("["));
							if ("int".equals(className)) {
								int[] arrayData = new int[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Integer.parseInt(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if ("long".equals(className)) {
								long[] arrayData = new long[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Long.parseLong(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if ("float".equals(className)) {
								float[] arrayData = new float[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Float.parseFloat(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if ("double".equals(className)) {
								double[] arrayData = new double[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Double.parseDouble(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if ("short".equals(className)) {
								short[] arrayData = new short[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Short.parseShort(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if ("boolean".equals(className)) {
								boolean[] arrayData = new boolean[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Boolean.parseBoolean(args[j]);
								}
								method.invoke(entity, arrayData);
							} else {
								Object valueAry = CollectionUtil.toArray(args, className);
								method.invoke(entity, valueAry);
							}
						} else if (BeanUtil.isBaseDataType(argType)) {
							method.invoke(entity, convertType(values[i], className));
						}
					} catch (Exception e) {
						e.printStackTrace();
						throw e;
					}
				}
			}
		}
	}

	/**
	 * @TODO 对xml处理过程中的简单类型转换(2022-10-18
	 *       从BeanUtil中剥离出来,便于BeanUtil进行getTypeName()针对性优化)
	 * @param value
	 * @param lowCaseTypeName 类型名称小写
	 * @return
	 */
	public static Object convertType(String value, String lowCaseTypeName) {
		// value值的类型跟目标类型一致，直接返回
		if ("java.lang.string".equals(lowCaseTypeName) || "string".equals(lowCaseTypeName)) {
			return value;
		}
		boolean isBlank = (value != null) && "".equals(value.trim());
		if (value == null || isBlank) {
			if ("int".equals(lowCaseTypeName) || "long".equals(lowCaseTypeName) || "double".equals(lowCaseTypeName)
					|| "float".equals(lowCaseTypeName) || "short".equals(lowCaseTypeName)) {
				return 0;
			}
			if ("boolean".equals(lowCaseTypeName)) {
				return false;
			}
			if ("char".equals(lowCaseTypeName) && isBlank) {
				return " ".charAt(0);
			}
			return null;
		}

		// 第二优先
		if ("java.math.bigdecimal".equals(lowCaseTypeName) || "decimal".equals(lowCaseTypeName)
				|| "bigdecimal".equals(lowCaseTypeName)) {
			return new BigDecimal(BeanUtil.convertBoolean(value));
		}
		// 第三优先
		if ("java.time.localdatetime".equals(lowCaseTypeName) || "localdatetime".equals(lowCaseTypeName)) {
			return DateUtil.asLocalDateTime(DateUtil.convertDateObject(value));
		}
		// 第四
		if ("java.time.localdate".equals(lowCaseTypeName) || "localdate".equals(lowCaseTypeName)) {
			return DateUtil.asLocalDate(DateUtil.convertDateObject(value));
		}
		// 第五
		if ("java.lang.integer".equals(lowCaseTypeName) || "integer".equals(lowCaseTypeName)) {
			return Integer.valueOf(BeanUtil.convertBoolean(value).split("\\.")[0]);
		}
		// 第六
		if ("java.sql.timestamp".equals(lowCaseTypeName) || "timestamp".equals(lowCaseTypeName)) {
			return new Timestamp(DateUtil.parseString(value).getTime());
		}
		if ("java.lang.double".equals(lowCaseTypeName)) {
			return Double.valueOf(value);
		}
		if ("java.util.date".equals(lowCaseTypeName) || "date".equals(lowCaseTypeName)) {
			return DateUtil.parseString(value);
		}
		if ("java.lang.long".equals(lowCaseTypeName)) {
			// 考虑数据库中存在默认值为0.00 的问题，导致new Long() 报错
			return Long.valueOf(BeanUtil.convertBoolean(value).split("\\.")[0]);
		}
		if ("int".equals(lowCaseTypeName)) {
			return Double.valueOf(BeanUtil.convertBoolean(value)).intValue();
		}
		if ("java.time.localtime".equals(lowCaseTypeName) || "localtime".equals(lowCaseTypeName)) {
			return DateUtil.asLocalTime(DateUtil.convertDateObject(value));
		}
		// add 2020-4-9
		if ("java.math.biginteger".equals(lowCaseTypeName) || "biginteger".equals(lowCaseTypeName)) {
			return new BigInteger(BeanUtil.convertBoolean(value).split("\\.")[0]);
		}
		if ("long".equals(lowCaseTypeName)) {
			return Double.valueOf(BeanUtil.convertBoolean(value)).longValue();
		}
		if ("double".equals(lowCaseTypeName)) {
			return Double.valueOf(value).doubleValue();
		}
		// 字符串转 boolean 型
		if ("boolean".equals(lowCaseTypeName)) {
			if ("true".equals(value.toLowerCase()) || "1".equals(value)) {
				return true;
			}
			return false;
		}
		// 字符串转 boolean 型
		if ("java.lang.boolean".equals(lowCaseTypeName)) {
			if ("true".equals(value.toLowerCase()) || "1".equals(value)) {
				return Boolean.TRUE;
			}
			return Boolean.FALSE;
		}
		if ("java.lang.short".equals(lowCaseTypeName)) {
			return Short.valueOf(Double.valueOf(BeanUtil.convertBoolean(value)).shortValue());
		}
		if ("short".equals(lowCaseTypeName)) {
			return Double.valueOf(BeanUtil.convertBoolean(value)).shortValue();
		}
		if ("java.lang.float".equals(lowCaseTypeName)) {
			return Float.valueOf(value);
		}
		if ("float".equals(lowCaseTypeName)) {
			return Float.valueOf(value).floatValue();
		}
		if ("java.sql.date".equals(lowCaseTypeName)) {
			return new java.sql.Date(DateUtil.parseString(value).getTime());
		}
		if ("char".equals(lowCaseTypeName)) {
			return value.charAt(0);
		}
		if ("java.sql.time".equals(lowCaseTypeName) || "time".equals(lowCaseTypeName)) {
			return DateUtil.parseString(value);
		}
		return value;
	}
}
