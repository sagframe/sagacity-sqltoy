/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.sagacity.sqltoy.callback.XMLCallbackHandler;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @project sagacity-sqltoy
 * @description xml处理的工具类,提供xml对应schema validator等功能
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:XMLUtil.java,Revision:v1.0,Date:2009-4-27 上午11:57:58
 */
public class XMLUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(XMLUtil.class);

	// xml 忽视验证的特性
	private final static String NO_VALIDATOR_FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

	private final static String XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";

	/**
	 * @todo xml文件合法性验证
	 * @param xsdStream
	 * @param xmlStream
	 * @return
	 */
	public static boolean validate(InputStream xsdStream, InputStream xmlStream) {
		SchemaFactory factory = SchemaFactory.newInstance(XML_SCHEMA);
		try {
			Source xsdSource = new StreamSource(xsdStream);
			Schema schema = factory.newSchema(xsdSource);
			// Get a validator from the schema.
			Validator validator = schema.newValidator();

			// Parse the document you want to check.
			Source xmlSource = new StreamSource(xmlStream);
			// Check the document
			validator.validate(xmlSource);
			return true;
		} catch (IOException ioe) {
			logger.error("文件IO读取失败!{}", ioe.getMessage());
			ioe.printStackTrace();
		} catch (SAXException ex) {
			logger.error("xml验证不合法:{}", ex.getMessage());
			ex.printStackTrace();
		}
		return false;
	}

	/**
	 * @todo xml文件合法性验证
	 * @param xsdUrl
	 * @param xmlUrl
	 * @return
	 */
	public static boolean validate(URL xsdUrl, URL xmlUrl) {
		SchemaFactory factory = SchemaFactory.newInstance(XML_SCHEMA);
		try {
			Schema schema = factory.newSchema(xsdUrl);
			// Get a validator from the schema.
			Validator validator = schema.newValidator();

			// Parse the document you want to check.
			Source xmlSource = new StreamSource(new FileInputStream(xmlUrl.getFile()));
			// Check the document
			validator.validate(xmlSource);
			return true;
		} catch (IOException ioe) {
			logger.error("文件IO读取失败!{}", ioe.getMessage());
			ioe.printStackTrace();
		} catch (SAXException ex) {
			logger.error("xml验证不合法:{}", ex.getMessage());
			ex.printStackTrace();
		}
		return false;
	}

	/**
	 * @todo 验证xml文件对应的schema文件是否匹配
	 * @param xsdFile
	 * @param xmlFile
	 * @return
	 */
	public static boolean validate(String xsdFile, String xmlFile) {
		SchemaFactory factory = SchemaFactory.newInstance(XML_SCHEMA);
		File schemaLocation = new File(xsdFile);
		try {
			Schema schema = factory.newSchema(schemaLocation);
			// Get a validator from the schema.
			Validator validator = schema.newValidator();

			// Parse the document you want to check.
			Source source = new StreamSource(xmlFile);
			// Check the document
			validator.validate(source);
			return true;
		} catch (IOException ioe) {
			logger.error("文件IO读取失败!{}", ioe.getMessage());
			ioe.printStackTrace();
		} catch (SAXException ex) {
			logger.error(xmlFile + " is not valid " + ex.getMessage());
			ex.printStackTrace();
		}
		return false;
	}

	/**
	 * @todo 获取qName对应的内容
	 * @param xmlFile
	 * @param xmlQuery
	 * @param qName
	 * @return
	 * @throws Exception
	 */
	public static Object getXPathContent(File xmlFile, String xmlQuery, QName qName) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(xmlFile);
		XPathFactory pathFactory = XPathFactory.newInstance();
		XPath xpath = pathFactory.newXPath();
		XPathExpression pathExpression = xpath.compile(xmlQuery);
		return pathExpression.evaluate(doc, qName);
	}

	/**
	 * 读取xml文件
	 * 
	 * @param xmlFile
	 * @param charset
	 * @param isValidator
	 * @param handler
	 * @throws Exception
	 */
	public static Object readXML(Object xmlFile, String charset, boolean isValidator, XMLCallbackHandler handler)
			throws Exception {
		if (StringUtil.isBlank(xmlFile))
			return null;
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

	private static IgnoreKeyCaseMap<String, String> asMap(String... keyValues) {
		IgnoreKeyCaseMap<String, String> result = new IgnoreKeyCaseMap<String, String>();
		if (keyValues == null)
			return result;
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
	 */
	public static void setAttributes(Element elt, Serializable entity, String... aliasProps) throws Exception {
		if (elt == null)
			return;
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
		Class argType;
		String className;
		for (int i = 0; i < properties.length; i++) {
			// 属性值为空白的排除掉
			if (StringUtil.isNotBlank(values[i])) {
				method = realMethods[i];
				if (method != null) {
					try {
						argType = method.getParameterTypes()[0];
						className = argType.getTypeName();
						className = className.substring(className.lastIndexOf(".") + 1);
						if (argType.isArray()) {
							// 替换全角为半角
							String[] args = values[i].replaceAll("\\，", ",").split("\\,");
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
								Object valueAry = CollectionUtil.toArray(args, className.toLowerCase());
								method.invoke(entity, valueAry);
							}
						} else if (BeanUtil.isBaseDataType(argType)) {
							method.invoke(entity, BeanUtil.convertType(values[i], className.toLowerCase()));
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
	 * @todo 解析xml元素的属性映射到对象的属性中去
	 * @param elt
	 * @param entity
	 */
	public static void setAttributes(Element elt, Serializable entity) throws Exception {
		setAttributes(elt, entity, null);
	}
}
