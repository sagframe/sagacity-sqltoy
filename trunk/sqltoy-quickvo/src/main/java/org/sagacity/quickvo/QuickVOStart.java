/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.quickvo;

import static java.lang.System.out;

import java.util.logging.Logger;

import org.sagacity.quickvo.config.XMLConfigLoader;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.utils.LoggerUtil;
import org.sagacity.quickvo.utils.FreemarkerUtil;

/**
 * @project sagacity-quickvo
 * @description 快速从数据库生成VO以及VO<-->PO 映射的mapping工具
 * @author chenrenfei <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:QuickVOStart.java,Revision:v2.0,Date:2009-04-15
 */
public class QuickVOStart {
	private static Logger logger = LoggerUtil.getLogger();

	/**
	 * 开始生成文件
	 * 
	 * @throws Exception
	 */
	public void doStart() {
		try {
			out.println("=========     welcome use sagacity-quickvo-4.13.8     ==========");
			out.println("======      使用java -cp jarPath mainClass args模式启动                =======");
			// 解析配置文件
			ConfigModel configModel = XMLConfigLoader.parse();
			TaskController.setConfigModel(configModel);
			// 创建vo和vof
			TaskController.create();
			FreemarkerUtil.destory();
			logger.info("成功完成vo以及vo<-->po映射类的生成!");
		} catch (ClassNotFoundException connectionException) {
			logger.info("数据库驱动加载失败!请将数据库驱动jar文件放到当前目录libs目录下!" + connectionException.getMessage());
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
			Constants.QUICK_CONFIG_FILE = args[0];
		}
		String baseDir;
		if (args != null && args.length > 1) {
			baseDir = args[1];
		} else {
			baseDir = System.getProperty("user.dir");
		}
		Constants.BASE_LOCATE = baseDir;
		// 代码调试时使用(真实场景不起作用,注意pom中要增加对应数据库驱动才可以运行)
		if (args == null || args.length == 0) {
			Constants.BASE_LOCATE = "D:/workspace/personal/sqltoy/sqltoy-postgresql/tools/quickvo";
			Constants.QUICK_CONFIG_FILE = "quickvo.xml";
		}
		// 开始根据数据库产生VO文件
		quickStart.doStart();
	}
}
