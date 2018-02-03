/**
 * 
 */
package org.sagacity.quickvo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.dom4j.Element;
import org.sagacity.quickvo.utils.FileUtil;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * @project sagacity-quickvo
 * @description quickVO涉及的常量定义
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:QuickVOConstants.java,Revision:v1.0,Date:Apr 18, 2009 4:54:22 PM
 */
@SuppressWarnings({ "rawtypes" })
public class QuickVOConstants implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8594672495773042796L;

	/**
	 * java vo freemarker模板
	 */
	public static String voTempate = "org/sagacity/quickvo/quickvo.ftl";

	/**
	 * java vo 抽象类freemarker模板
	 */
	public static String voAbstractTempate = "org/sagacity/quickvo/abstract-quickvo.ftl";

	/**
	 * 
	 */
	public static String daoTempate = "org/sagacity/quickvo/dao.ftl";

	/**
	 * vo中构造函数模板，用于当数据库表发生改变后修改vo中的构造函数
	 */
	public static String voConstructorTemplate = "org/sagacity/quickvo/quickvo-constructor.ftl";

	/**
	 * 
	 */
	public static String minStruct = "org/sagacity/quickvo/quickvo-minstruct.ftl";
	/**
	 * 
	 */
	public static String pkStruct = "org/sagacity/quickvo/quickvo-pkstruct.ftl";

	/**
	 * 
	 */
	public static String maxStruct = "org/sagacity/quickvo/quickvo-maxstruct.ftl";

	/**
	 * 
	 */
	public static String constructor = "org/sagacity/quickvo/quickvo-constructor.ftl";

	/**
	 * 主键默认生成策略
	 */
	public static String PK_DEFAULT_GENERATOR = "org.sagacity.sqltoy.plugin.id.DefaultIdGenerator";

	/**
	 * uuid主键策略
	 */
	public static String PK_UUID_GENERATOR = "org.sagacity.sqltoy.plugin.id.UUIDGenerator";

	/**
	 * twitter的雪花id算法
	 */
	public static String PK_SNOWFLAKE_GENERATOR = "org.sagacity.sqltoy.plugin.id.SnowflakeIdGenerator";

	/**
	 * 26位纳秒时序ID产生策略
	 */
	public static String PK_NANOTIME_ID_GENERATOR = "org.sagacity.sqltoy.plugin.id.NanoTimeIdGenerator";

	public static String constructorBegin = "/*---begin-constructor-area---don't-update-this-area--*/";
	public static String constructorEnd = "/*---end-constructor-area---don't-update-this-area--*/";
	public static String pkStructRegs = "\\/[\\*]{1,2}\\s*pk\\s+constructor\\s*\\*\\/";

	public static String minStructRegs = "\\/[\\*]{1,2}\\s*minimal\\s+constructor\\s*\\*\\/";
	public static String maxStructRegs = "\\/[\\*]{1,2}\\s*full\\s+constructor\\s*\\*\\/";
	/**
	 * 运行时默认路径
	 */
	public static String BASE_LOCATE;

	public static String QUICK_CONFIG_FILE = "quickvo.xml";

	private static final String GLOBA_IDENTITY_NAME = "globa.identity";

	private static final String GLOBA_IDENTITY = "##{globa.identity}";

	/**
	 * 默认数据类型匹配关系定义
	 */
	public static final String[][] jdbcTypMapping = {
			// jdbc.type java.type importType precision(数据长度) scale(小数位)
			{ "REAL", "Float", "" }, { "TINYINT", "Short", "" }, { "SHORT", "Short", "" }, { "SMALLINT", "Short", "" },
			{ "BIGINT", "Long", "" }, { "INT", "Integer", "" }, { "INTEGER", "Integer", "" },
			{ "SERIAL", "Integer", "" }, { "FLOAT", "Float", "" }, { "DOUBLE", "Double", "" },
			{ "NUMBER", "BigDecimal", "java.math.BigDecimal" }, { "NUMERIC", "BigDecimal", "java.math.BigDecimal" },
			{ "DECIMAL", "BigDecimal", "java.math.BigDecimal" }, { "TIMESTAMP", "Timestamp", "java.sql.Timestamp" },
			{ "BIGDECIMAL", "BigDecimal", "java.math.BigDecimal" }, { "DATE", "Date", "java.util.Date" },
			{ "DATETIME", "Timestamp", "java.util.Date" }, { "TIME", "Date", "java.util.Date" },
			{ "VARCHAR", "String", "" }, { "VARCHAR2", "String", "" }, { "LONG VARCHAR", "String", "" },
			{ "STRING", "String", "" }, { "CHAR", "String", "" }, { "CHARACTER", "String", "" },
			{ "BIT", "Boolean", "" }, { "BOOLEAN", "Boolean", "" }, { "Clob", "Clob", "java.sql.Clob" },
			{ "CLOB", "CLOB", "oracle.sql.CLOB", "10" }, { "BLOB", "BLOB", "oracle.sql.BLOB", "10" },
			{ "Blob", "Blob", "java.sql.Blob" }, { "TEXT", "String", "" }, { "LONGTEXT", "String", "" },
			{ "IMAGE", "byte[]", "" }, { "VARBINARY", "Serializable", "java.io.Serializable" } };

	/**
	 * 原始类型
	 */
	public static final String[][] prototype = { { "int", "1" }, { "short", "1" }, { "long", "1" }, { "float", "1" },
			{ "double", "1" }, { "char", "2" }, { "byte", "2" }, { "boolean", "2" } };

	/**
	 * 全局常量map
	 */
	private static HashMap<String, String> constantMap = new HashMap<String, String>();

	public static int getMaxScale() {
		String maxScale = getKeyValue("max.scale.length");
		if (maxScale == null)
			return -1;
		else
			return Integer.parseInt(maxScale);
	}

	/**
	 * 
	 * @todo 加载xml中的参数
	 * @param paramElts
	 * @throws Exception
	 */
	public static void loadProperties(List paramElts) throws Exception {
		String guid = System.getProperty(GLOBA_IDENTITY_NAME);
		if (guid == null)
			guid = "";
		// 加载任务配置文件中的参数
		if (paramElts != null && !paramElts.isEmpty()) {
			Element elt;
			for (int i = 0; i < paramElts.size(); i++) {
				elt = (Element) paramElts.get(i);
				if (elt.attribute("name") != null) {
					if (elt.attribute("value") != null)
						constantMap.put(elt.attributeValue("name"), replaceConstants(
								StringUtil.replaceAllStr(elt.attributeValue("value"), GLOBA_IDENTITY, guid)));
					else
						constantMap.put(elt.attributeValue("name"),
								replaceConstants(StringUtil.replaceAllStr(elt.getText(), GLOBA_IDENTITY, guid)));
				} else if (elt.attribute("file") != null) {
					loadPropertyFile(replaceConstants(
							StringUtil.replaceAllStr(elt.attributeValue("file"), GLOBA_IDENTITY, guid)));
				}
			}
		}
	}

	/**
	 * @todo 替换常量参数
	 * @param target
	 * @return
	 */
	public static String replaceConstants(String target) {
		if (constantMap == null || constantMap.size() < 1 || target == null)
			return target;
		String result = target;
		if (StringUtil.matches(result, "\\$\\{[\\w|\\.]+\\}")) {
			Iterator iter = constantMap.entrySet().iterator();
			Map.Entry entry;
			while (iter.hasNext()) {
				entry = (Map.Entry) iter.next();
				result = StringUtil.replaceAllStr(result, "${" + entry.getKey() + "}", (String) entry.getValue());
			}
		}
		return result;
	}

	/**
	 * @todo 加载properties文件
	 * @param propertyFile
	 * @throws IOException
	 */
	private static void loadPropertyFile(String propertyFile) throws Exception {
		if (StringUtil.isNotNullAndBlank(propertyFile)) {
			File propFile;
			// 判断根路径
			if (FileUtil.isRootPath(propertyFile))
				propFile = new File(propertyFile);
			else
				propFile = new File(FileUtil.skipPath(QuickVOConstants.BASE_LOCATE, propertyFile));
			if (!propFile.exists()) {
				throw new Exception("参数文件:" + propertyFile + "不存在,请确认!");
			}
			Properties props = new Properties();
			props.load(new FileInputStream(propFile));
			Enumeration en = props.propertyNames();
			String key;
			while (en.hasMoreElements()) {
				key = (String) en.nextElement();
				constantMap.put(key, props.getProperty(key));
			}
		}
	}

	/**
	 * @todo 获取常量信息
	 * @param key
	 * @return
	 */
	public static String getPropertyValue(String key) {
		if (StringUtil.isNullOrBlank(key))
			return key;
		if (StringUtil.matches(key.trim(), "^\\$\\{[\\w|\\.]+\\}$"))
			return (String) getKeyValue(key.substring(key.indexOf("${") + 2, key.lastIndexOf("}")));
		else {
			if (getKeyValue(key) != null)
				return getKeyValue(key);
			return key;
		}
	}

	/**
	 * @todo 获取常量信息
	 * @param key
	 * @return
	 */
	public static String getKeyValue(String key) {
		if (StringUtil.isNullOrBlank(key))
			return key;
		String value = (String) constantMap.get(key);
		if (null == value)
			value = System.getProperty(key);
		return value;
	}
}
