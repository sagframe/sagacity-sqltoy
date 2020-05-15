/**
 * 
 */
package org.sagacity.quickvo.engine.template;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.sagacity.quickvo.utils.LoggerUtil;
import org.sagacity.quickvo.utils.StringUtil;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * @project sagacity-quickvo
 * @description 基于freemarker的模版工具引擎，提供日常项目中模版和数据对象的结合处理
 * @author zhongxuchen $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version id:TemplateGenerator.java,Revision:v1.0,Date:2008-11-24
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TemplateGenerator {
	/**
	 * 定义全局日志
	 */
	private static Logger logger = LoggerUtil.getLogger();
	private static Configuration cfg = null;

	public static TemplateGenerator me;

	public static TemplateGenerator getInstance() {
		if (me == null)
			me = new TemplateGenerator();
		return me;
	}

	/**
	 * 编码格式，默认utf-8
	 */
	private String encoding = "UTF-8";

	/**
	 * 设置编码格式
	 * 
	 * @param encoding
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	/**
	 * 
	 * @todo 将字符串模版处理后以字符串输出
	 * @param keys
	 * @param templateData
	 * @param templateStr
	 * @return
	 */
	public String create(String[] keys, Object[] templateData, String templateStr) {
		if (keys == null || templateData == null)
			return null;
		String result = null;
		StringWriter writer = null;
		try {
			init();
			StringTemplateLoader templateLoader = new StringTemplateLoader();
			templateLoader.putTemplate("string_template", templateStr);
			cfg.setTemplateLoader(templateLoader);
			Template template = null;
			if (StringUtil.isNotBlank(this.encoding))
				template = cfg.getTemplate("string_template", this.encoding);
			else
				template = cfg.getTemplate("string_template");

			Map root = new HashMap();
			for (int i = 0; i < keys.length; i++) {
				root.put(keys[i], templateData[i]);
			}
			writer = new StringWriter();
			template.process(root, writer);
			writer.flush();
			result = writer.getBuffer().toString();
		} catch (Exception e) {
			e.printStackTrace();
			logger.info(e.getMessage());
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
			writer = null;
		}
		return result;
	}

	/**
	 * @todo <b>将模板和数据结合产生到目的文件中</b>
	 * @param keys
	 * @param templateData
	 * @param templateStr
	 * @param distFile
	 */
	public void create(String[] keys, Object[] templateData, String templateStr, String distFile) {
		if (keys == null || templateData == null)
			return;
		Writer writer = null;
		FileOutputStream out = null;
		try {
			init();
			StringTemplateLoader templateLoader = new StringTemplateLoader();
			templateLoader.putTemplate("template", templateStr);
			cfg.setTemplateLoader(templateLoader);
			Template template = null;
			if (StringUtil.isNotBlank(this.encoding))
				template = cfg.getTemplate("template", this.encoding);
			else
				template = cfg.getTemplate("template");
			Map root = new HashMap();
			for (int i = 0; i < keys.length; i++) {
				root.put(keys[i], templateData[i]);
			}

			out = new FileOutputStream(distFile);

			if (StringUtil.isNotBlank(this.encoding))
				writer = new BufferedWriter(new OutputStreamWriter(out, this.encoding));
			else
				writer = new BufferedWriter(new OutputStreamWriter(out));
			logger.info("generate file " + distFile);
			template.process(root, writer);
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
			logger.info(e.getMessage());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
			out = null;
			writer = null;
		}
	}

	/**
	 * 销毁实例
	 */
	public static void destory() {
		cfg = null;
	}

	public void init() {
		if (cfg == null) {
			cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
			if (StringUtil.isNotBlank(this.encoding))
				cfg.setDefaultEncoding(this.encoding);
		}
	}
}
