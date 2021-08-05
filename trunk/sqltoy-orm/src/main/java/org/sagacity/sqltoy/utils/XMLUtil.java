package org.sagacity.sqltoy.utils;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Method;

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
							if (className.equals("int")) {
								int[] arrayData = new int[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Integer.parseInt(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if (className.equals("long")) {
								long[] arrayData = new long[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Long.parseLong(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if (className.equals("float")) {
								float[] arrayData = new float[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Float.parseFloat(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if (className.equals("double")) {
								double[] arrayData = new double[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Double.parseDouble(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if (className.equals("short")) {
								short[] arrayData = new short[args.length];
								for (int j = 0; j < arrayData.length; j++) {
									arrayData[j] = Short.parseShort(args[j]);
								}
								method.invoke(entity, arrayData);
							} else if (className.equals("boolean")) {
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
							method.invoke(entity, BeanUtil.convertType(values[i], className));
						}
					} catch (Exception e) {
						e.printStackTrace();
						throw e;
					}
				}
			}
		}
	}
}
