/**
 * 
 */
package org.sagacity.quickvo.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.sagacity.quickvo.QuickVOConstants;
import org.sagacity.quickvo.model.BusinessIdConfig;
import org.sagacity.quickvo.model.CascadeModel;
import org.sagacity.quickvo.model.ColumnTypeMapping;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.model.PrimaryKeyStrategy;
import org.sagacity.quickvo.model.QuickModel;
import org.sagacity.quickvo.utils.DBHelper;
import org.sagacity.quickvo.utils.FileUtil;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * @project sagacity-quickvo
 * @description 解析xml配置文件
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:XMLConfigLoader.java,Revision:v1.0,Date:2010-7-21 下午02:08:26 $
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class XMLConfigLoader {
	/**
	 * 定义全局日志
	 */
	private final static Logger logger = LogManager.getLogger(XMLConfigLoader.class);

	/**
	 * @todo 解析配置文件
	 * @return
	 * @throws Exception
	 */
	public static ConfigModel parse() throws Exception {
		File xmlFile = new File(FileUtil.linkPath(QuickVOConstants.BASE_LOCATE, QuickVOConstants.QUICK_CONFIG_FILE));
		// 文件不存在则忽视相对路径进行重试
		if (!xmlFile.exists()) {
			xmlFile = new File(QuickVOConstants.QUICK_CONFIG_FILE);
			if (!xmlFile.exists()) {
				logger.error("相对路径:{},配置文件:{}不存在,请正确配置!", QuickVOConstants.BASE_LOCATE,
						QuickVOConstants.QUICK_CONFIG_FILE);
				throw new Exception("配置文件不存在,请正确配置!");
			}
		}
		ConfigModel configModel = new ConfigModel();
		SAXReader saxReader = new SAXReader();
		saxReader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		Document doc = saxReader.read(xmlFile);
		Element root = doc.getRootElement();
		// 加载常量信息
		QuickVOConstants.loadProperties(root.elements("property"));

		// 任务设置
		Element tasks = root.element("tasks");

		// 输出路径
		if (tasks.attribute("dist") != null)
			configModel.setTargetDir(QuickVOConstants.replaceConstants(tasks.attributeValue("dist")));
		else
			configModel.setTargetDir(QuickVOConstants.BASE_LOCATE);
		// 判断指定的目标路径是否是根路径
		if (!FileUtil.isRootPath(configModel.getTargetDir())) {
			configModel.setTargetDir(FileUtil.skipPath(QuickVOConstants.BASE_LOCATE, configModel.getTargetDir()));
		}

		// 设置抽象类路径
		if (tasks.attribute("abstractPath") != null)
			configModel.setAbstractPath(QuickVOConstants.replaceConstants(tasks.attributeValue("abstractPath")));

		// 设置编码格式
		if (tasks.attribute("encoding") != null)
			configModel.setEncoding(QuickVOConstants.replaceConstants(tasks.attributeValue("encoding")));

		List quickVOs = tasks.elements("task");
		if (quickVOs == null || quickVOs.isEmpty())
			return null;
		// 解析task 任务配置信息
		List quickModels = new ArrayList();
		Element quickvo;
		Element vo;
		Element dao;
		boolean active = true;
		for (int i = 0; i < quickVOs.size(); i++) {
			quickvo = (Element) quickVOs.get(i);
			active = true;
			if (quickvo.attribute("active") != null)
				active = Boolean.parseBoolean(quickvo.attributeValue("active"));
			// 生效的任务
			if (active) {
				QuickModel quickModel = new QuickModel();
				if (quickvo.attribute("author") != null)
					quickModel.setAuthor(quickvo.attributeValue("author"));
				if (quickvo.attribute("datasource") != null)
					quickModel.setDataSource(quickvo.attributeValue("datasource"));
				else if (quickvo.attribute("dataSource") != null)
					quickModel.setDataSource(quickvo.attributeValue("dataSource"));

				vo = quickvo.element("vo");
				dao = quickvo.element("dao");
				if (quickvo.attribute("include") != null) {
					// *表示全部,等同于没有include配置
					if (!quickvo.attributeValue("include").equals("*")) {
						quickModel
								.setIncludeTables(QuickVOConstants.replaceConstants(quickvo.attributeValue("include")));
					}
				}
				// 排除的表
				if (quickvo.attribute("exclude") != null) {
					quickModel.setExcludeTables(QuickVOConstants.replaceConstants(quickvo.attributeValue("exclude")));
				}
				// 实体对象包可以统一用参数定义
				if (quickvo.attribute("package") == null)
					quickModel.setEntityPackage(QuickVOConstants.getPropertyValue("entity.package"));
				else
					quickModel.setEntityPackage(QuickVOConstants.replaceConstants(quickvo.attributeValue("package")));
				quickModel.setVoPackage(QuickVOConstants.replaceConstants(vo.attributeValue("package")));
				if (vo.attribute("substr") != null)
					quickModel.setVoSubstr(QuickVOConstants.replaceConstants(vo.attributeValue("substr")));
				quickModel.setVoName(QuickVOConstants.replaceConstants(vo.attributeValue("name")));
				if (dao != null && (dao.attribute("active") == null || dao.attributeValue("active").equals("true"))) {
					quickModel.setDaoPackage(QuickVOConstants.replaceConstants(dao.attributeValue("package")));
					quickModel.setDaoName(QuickVOConstants.replaceConstants(dao.attributeValue("name")));
					if (dao.attribute("include") != null)
						quickModel.setDaoInclude(QuickVOConstants.replaceConstants(dao.attributeValue("include")));
					if (dao.attribute("exclude") != null)
						quickModel.setDaoExclude(QuickVOConstants.replaceConstants(dao.attributeValue("exclude")));
				}
				quickModels.add(quickModel);
			}
		}
		if (quickModels.isEmpty()) {
			logger.info("没有激活的任务可以执行!");
			return null;
		}
		configModel.setTasks(quickModels);
		// 主键设置
		if (root.element("primary-key") != null) {
			List tables = root.element("primary-key").elements("table");
			Element table;
			String name;
			String sequence;
			// 默认为赋值模式
			String strategy;
			String generator;
			for (int i = 0; i < tables.size(); i++) {
				table = (Element) tables.get(i);
				name = table.attributeValue("name");
				sequence = null;
				strategy = "assign";
				generator = null;
				if (table.attribute("strategy") != null)
					strategy = table.attributeValue("strategy");
				if (table.attribute("sequence") != null) {
					sequence = table.attributeValue("sequence");
					strategy = "sequence";
				}

				// 生成器模式优先
				if (table.attribute("generator") != null) {
					generator = table.attributeValue("generator");
					strategy = "generator";
					sequence = null;
					if (generator.equalsIgnoreCase("default"))
						generator = QuickVOConstants.PK_DEFAULT_GENERATOR;
				}

				PrimaryKeyStrategy primaryKeyStrategy = new PrimaryKeyStrategy(name, strategy, sequence, generator);
				// force没有必要
				if (table.attribute("force") != null)
					primaryKeyStrategy.setForce(Boolean.parseBoolean(table.attributeValue("force")));
				configModel.addPkGeneratorStrategy(primaryKeyStrategy);
			}
		}
		// 自定义数据匹配类型
		if (root.element("type-mapping") != null) {
			List jdbcTypes = root.element("type-mapping").elements("sql-type");
			if (jdbcTypes != null && !jdbcTypes.isEmpty()) {
				List typeMapping = new ArrayList();
				Element type;
				String javaType;
				String[] precision;
				String[] scale;
				for (int i = 0; i < jdbcTypes.size(); i++) {
					type = (Element) jdbcTypes.get(i);
					ColumnTypeMapping colTypeMapping = new ColumnTypeMapping();
					// 兼容老版本
					colTypeMapping.putNativeTypes(type.attributeValue("native-types").split("\\,"));

					if (type.attribute("jdbc-type") != null)
						colTypeMapping.setJdbcType(type.attributeValue("jdbc-type"));

					if (type.attribute("precision") != null) {
						if (StringUtil.isNotNullAndBlank(type.attributeValue("precision"))) {
							precision = type.attributeValue("precision").split("\\..");
							if (precision.length >= 2) {
								colTypeMapping.setPrecisionMin(Integer.valueOf(precision[0].trim()));
								colTypeMapping.setPrecisionMax(Integer.valueOf(precision[1].trim()));
							} else {
								colTypeMapping.setPrecisionMin(Integer.valueOf(precision[0].trim()));
								colTypeMapping.setPrecisionMax(Integer.valueOf(precision[0].trim()));
							}
						}
					}

					// 小数位
					if (type.attribute("scale") != null) {
						if (StringUtil.isNotNullAndBlank(type.attributeValue("scale"))) {
							scale = type.attributeValue("scale").split("\\..");
							if (scale.length >= 2) {
								colTypeMapping.setScaleMin(Integer.valueOf(scale[0].trim()));
								colTypeMapping.setScaleMax(Integer.valueOf(scale[1].trim()));
							} else {
								colTypeMapping.setScaleMin(Integer.valueOf(scale[0].trim()));
								colTypeMapping.setScaleMax(Integer.valueOf(scale[0].trim()));
							}
						}
					}
					// 对应的java类型
					javaType = type.attributeValue("java-type");
					colTypeMapping.setJavaType(javaType);
					if (javaType.indexOf(".") != -1)
						colTypeMapping.setResultType(javaType.substring(javaType.lastIndexOf(".") + 1));
					else
						colTypeMapping.setResultType(javaType);
					typeMapping.add(colTypeMapping);
				}
				configModel.setTypeMapping(typeMapping);
			}
		}
		// 级联操作设置
		if (root.elements("cascade") != null) {
			List<Element> mainCascades = root.elements("cascade");
			String mainTable;
			Element mainCasade;
			for (int m = 0; m < mainCascades.size(); m++) {
				mainCasade = mainCascades.get(m);
				mainTable = (mainCasade.attribute("main-table") == null) ? ("quickvo_temp_maintable" + m)
						: mainCasade.attributeValue("main-table");
				List cascades = mainCasade.elements("table");
				Element cascadeElt;
				List cascadeModelList = new ArrayList<CascadeModel>();
				for (int i = 0; i < cascades.size(); i++) {
					cascadeElt = (Element) cascades.get(i);
					CascadeModel cascade = new CascadeModel();
					cascade.setTableName(cascadeElt.attributeValue("name"));
					if (cascadeElt.attribute("delete") != null)
						cascade.setDelete(Boolean.parseBoolean(cascadeElt.attributeValue("delete")));
					// lazy load 的特定sql，可以自行定义,如:enabled=1附加条件
					if (cascadeElt.attribute("load") != null)
						cascade.setLoad(cascadeElt.attributeValue("load"));

					// 新的配置模式
					if (cascadeElt.attribute("update-cascade") != null)
						cascade.setUpdateSql(cascadeElt.attributeValue("update-cascade"));
					cascadeModelList.add(cascade);
				}
				configModel.addCascadeConfig(mainTable, cascadeModelList);
			}
		}

		// 数据库
		DBHelper.loadDatasource(root.elements("datasource"));

		// 表业务主键配置
		if (root.element("business-primary-key") != null) {
			List<Element> tables = root.element("business-primary-key").elements("table");
			Element tableElt;
			for (int m = 0; m < tables.size(); m++) {
				tableElt = tables.get(m);
				BusinessIdConfig bizIdConfig = new BusinessIdConfig();
				bizIdConfig.setTableName(tableElt.attributeValue("name"));
				bizIdConfig.setColumn(tableElt.attributeValue("column"));
				bizIdConfig.setGenerator(tableElt.attributeValue("generator"));
				if (tableElt.attribute("related-column") != null) {
					String relatedColumn = tableElt.attributeValue("related-column");
					// 统一分割符
					bizIdConfig.setRelatedColumn(relatedColumn);
				}
				if (tableElt.attribute("signature") != null)
					bizIdConfig.setSignature(tableElt.attributeValue("signature"));
				if (tableElt.attribute("length") != null)
					bizIdConfig.setLength(Integer.parseInt(tableElt.attributeValue("length")));
				configModel.addBusinessId(bizIdConfig);
			}
		}
		return configModel;
	}
}
