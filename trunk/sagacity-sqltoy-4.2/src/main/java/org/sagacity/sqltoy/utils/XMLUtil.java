/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.sagacity.sqltoy.callback.XMLCallbackHandler;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * @project sagacity-core
 * @description xml处理的工具类,提供xml对应schema validator等功能
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:XMLUtil.java,Revision:v1.0,Date:2009-4-27 上午11:57:58
 */
public class XMLUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LogManager.getLogger(XMLUtil.class);

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
			logger.error("文件IO读取失败!" + ioe.getMessage());
			ioe.printStackTrace();
		} catch (SAXException ex) {
			logger.error("xml验证不合法:" + ex.getMessage());
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
			logger.error("文件IO读取失败!" + ioe.getMessage());
			ioe.printStackTrace();
		} catch (SAXException ex) {
			logger.error("xml验证不合法:" + ex.getMessage());
			ex.printStackTrace();
		}
		return false;
	}

	/**
	 * 
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
			logger.error("文件IO读取失败!" + ioe.getMessage());
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
	 * @todo 根据qName 获取节点对象
	 * @param xmlFile
	 * @param qName
	 * @return
	 * @throws Exception
	 */
	public static Object getXPathElement(File xmlFile, String qName) throws Exception {
		SAXReader saxReader = new SAXReader();
		InputStream is = new FileInputStream(xmlFile);
		org.dom4j.Document doc = saxReader.read(is);
		return doc.getRootElement().selectObject(qName);
	}

	/**
	 * @todo 获取节点集合
	 * @param xmlFile
	 * @param qName
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("rawtypes")
	public static List getXPathElements(File xmlFile, String qName) throws Exception {
		SAXReader saxReader = new SAXReader();
		InputStream is = new FileInputStream(xmlFile);
		org.dom4j.Document doc = saxReader.read(is);
		return doc.getRootElement().elements(qName);
	}

	/**
	 * @todo 编辑xml文件
	 * @param xmlFile
	 * @param charset
	 * @param isValidator
	 * @param handler
	 * @return
	 * @throws Exception
	 */
	public static boolean updateXML(File xmlFile, String charset, boolean isValidator, XMLCallbackHandler handler)
			throws Exception {
		if (handler == null || xmlFile == null || !xmlFile.exists())
			return false;
		InputStream is = null;
		FileOutputStream fos = null;
		try {
			SAXReader saxReader = new SAXReader();
			if (!isValidator)
				saxReader.setFeature(NO_VALIDATOR_FEATURE, false);
			if (charset != null)
				saxReader.setEncoding(charset);
			is = new FileInputStream(xmlFile);
			if (null != is) {
				org.dom4j.Document doc = saxReader.read(is);
				if (null != doc) {
					handler.process(doc, doc.getRootElement());
					OutputFormat format = OutputFormat.createPrettyPrint();
					if (charset != null)
						format.setEncoding(charset);
					fos = new FileOutputStream(xmlFile);
					XMLWriter output = new XMLWriter(fos, format);
					output.write(doc);
				}
				is.close();
			}
		} catch (Exception e) {
			logger.error("修改XML文件:{}错误:{}!", xmlFile, e.getMessage());
			throw e;
		} finally {
			if (fos != null)
				fos.close();
			if (is != null)
				is.close();
		}
		return true;
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
		InputStreamReader ir = null;
		try {
			SAXReader saxReader = new SAXReader();
			if (!isValidator)
				saxReader.setFeature(NO_VALIDATOR_FEATURE, false);
			if (StringUtil.isNotBlank(charset))
				saxReader.setEncoding(charset);
			if (charset != null)
				ir = new InputStreamReader(CommonUtils.getFileInputStream(xmlFile), charset);
			else
				ir = new InputStreamReader(CommonUtils.getFileInputStream(xmlFile));
			if (ir != null) {
				org.dom4j.Document doc = saxReader.read(ir);
				if (null != doc)
					return handler.process(doc, doc.getRootElement());
			}
		} catch (Exception e) {
			logger.error("解析文件:{}错误:{}!", xmlFile, e.getMessage());
			throw e;
		} finally {
			if (ir != null)
				ir.close();
			if (fileIS != null)
				fileIS.close();
		}
		return null;
	}

	private static HashMap<String, String> asMap(String... keyValues) {
		HashMap<String, String> result = new HashMap<String, String>();
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
	 * @param mapped
	 */
	public static void setAttributes(Element elt, Serializable entity, String... keyValues) {
		if (elt == null)
			return;
		HashMap<String, String> realMap = asMap(keyValues);
		List<Attribute> attrs = elt.attributes();
		String name;
		String value;
		String[] properties = new String[attrs.size() + 1];
		String[] values = new String[attrs.size() + 1];
		int index = 0;
		for (Attribute attr : attrs) {
			name = attr.getName();
			value = attr.getValue();
			// 对照属性
			if (realMap.containsKey(name))
				properties[index] = realMap.get(name);
			else if (realMap.containsKey(name.toLowerCase()))
				properties[index] = realMap.get(name.toLowerCase());
			else if (realMap.containsKey(name.toUpperCase()))
				properties[index] = realMap.get(name.toUpperCase());
			else
				properties[index] = StringUtil.toHumpStr(name,false);
			values[index] = value;
			index++;
		}
		// 将元素body中的值作为元素自身
		name = elt.getName();
		if (realMap.containsKey(name))
			properties[index] = realMap.get(name);
		else if (realMap.containsKey(name.toLowerCase()))
			properties[index] = realMap.get(name.toLowerCase());
		else if (realMap.containsKey(name.toUpperCase()))
			properties[index] = realMap.get(name.toUpperCase());
		else
			properties[index] = StringUtil.toHumpStr(name,false);
		values[index] = elt.getText();

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
							String[] args = values[i].split(",");
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
	public static void setAttributes(Element elt, Serializable entity) {
		setAttributes(elt, entity, null);
	}
}
