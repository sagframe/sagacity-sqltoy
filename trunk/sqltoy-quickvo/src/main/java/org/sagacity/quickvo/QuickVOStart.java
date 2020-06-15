/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.quickvo;

import static java.lang.System.out;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import org.sagacity.quickvo.config.XMLConfigLoader;
import org.sagacity.quickvo.engine.template.TemplateGenerator;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.utils.ClassLoaderUtil;
import org.sagacity.quickvo.utils.FileUtil;
import org.sagacity.quickvo.utils.LoggerUtil;

/**
 * @project sagacity-tools
 * @description 快速从数据库生成VO以及VO<-->PO 映射的mapping工具
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:QuickVOStart.java,Revision:v2.0,Date:Apr 15, 2009 4:10:13 PM $
 */
public class QuickVOStart {
	/**
	 * 数据库驱动文件路径
	 */
	private String DB_DRIVER_FILE = "libs/";

	private static Logger logger;

	private ConfigModel configModel;

	/**
	 * 初始化，解析配置文件
	 */
	public void init() {
		try {
			logger = LoggerUtil.getLogger();
			out.println("=========     welcome use sagacity-quickvo-4.12.11     ==========");
			out.println("======      使用java -cp jarPath mainClass args模式启动                =======");
			configModel = XMLConfigLoader.parse();
		} catch (Exception e) {
			e.printStackTrace();
			logger.info("加载系统参数或解析任务xml文件出错!" + e.getMessage());
		}
	}

	/**
	 * 开始生成文件
	 * 
	 * @throws Exception
	 */
	public void doStart() {
		if (configModel == null) {
			return;
		}
		try {
			int javaVersion = Integer.parseInt(System.getProperty("java.version").split("\\.")[0]);
			// jdk9 之后加载类的方式不一样
			if (javaVersion >= 9) {
				logger.info("请使用java -cp jarPath mainClass args 模式启动!");
			} else {
				// 加载位于driver目录下的jdbc驱动程序类库
				logger.info("Begin load jdbc driver jar path from ./libs!");
				List jars = FileUtil.getPathFiles(new File(QuickVOConstants.BASE_LOCATE, DB_DRIVER_FILE),
						new String[] { "[\\w\\-\\.]+\\.jar$" });
				ClassLoaderUtil.loadJarFiles(jars);
			}
			TaskController.setConfigModel(configModel);
			// 创建vo和vof
			TaskController.create();
			TemplateGenerator.destory();
			logger.info("成功完成vo以及vo<-->po映射类的生成!");
		} catch (ClassNotFoundException connectionException) {
			logger.info("数据库驱动加载失败!请将数据库驱动jar文件放到当前目录drivers目录下!" + connectionException.getMessage());
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
	}

	/**
	 * 主调度控制
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		QuickVOStart quickStart = new QuickVOStart();
		if (args != null && args.length > 0) {
			QuickVOConstants.QUICK_CONFIG_FILE = args[0];
		}
		String baseDir;
		if (args != null && args.length > 1) {
			baseDir = args[1];
		} else {
			baseDir = System.getProperty("user.dir");
		}
		QuickVOConstants.BASE_LOCATE = baseDir;
		// 测试使用(真实场景不起作用)
		if (args == null || args.length == 0) {
			QuickVOConstants.BASE_LOCATE = "D:/workspace/personal/sqltoy/sqltoy-postgresql/tools/quickvo";
			// QuickVOConstants.BASE_LOCATE =
			// "D:/workspace/personal/sagacity2.0/sqltoy-orm/tools/quickvo";
			QuickVOConstants.QUICK_CONFIG_FILE = "quickvo.xml";
		}
		// 做配置文件解析、数据库检测
		quickStart.init();

		// 开始根据数据库产生VO文件
		quickStart.doStart();
	}
}
