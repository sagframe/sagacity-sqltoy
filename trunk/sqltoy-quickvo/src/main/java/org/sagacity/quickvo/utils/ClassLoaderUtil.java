/**
 * 
 */
package org.sagacity.quickvo.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @project sagacity-quickvo
 * @description 类加载器工具包，提供jar、class等动态加载功能
 * @author zhongxuchen $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:ClassLoaderUtil.java,Revision:v1.0,Date:2008-12-14 下午07:57:11 $
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ClassLoaderUtil {
	private static Method addURL;
	static {
		try {
			addURL = URLClassLoader.class.getDeclaredMethod("addURL", new Class[] { URL.class });
		} catch (Exception e) {
			// won't happen,but remain it throw new RootException(e);
		}
		addURL.setAccessible(true);
	}

	/**
	 * @todo 增加jar或zip文件到指定的UrlClassLoader中
	 * @param urlClassLoader
	 * @param dirOrJars
	 */
	public static void addClassPath(URLClassLoader urlClassLoader, File[] dirOrJars) {
		try {
			URL[] urls = convertFile2URL(dirOrJars);
			invoke(urlClassLoader, urls);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static URL[] convertFile2URL(File[] dirOrJars) throws MalformedURLException {
		if (dirOrJars == null || dirOrJars.length < 1)
			return null;
		URL[] urls = new URL[dirOrJars.length];
		for (int i = 0; i < urls.length; i++) {
			urls[i] = dirOrJars[i].toURI().toURL();
		}
		return urls;
	}

	private static void invoke(URLClassLoader urlClassLoader, URL[] urls) throws Exception {
		for (int i = 0; i < urls.length; i++)
			addURL.invoke(urlClassLoader, urls[i]);
	}

	/**
	 * @todo 加载jar文件
	 * @param jarFiles
	 */
	public static void loadJarFiles(List jarFiles) {
		List pureJarFiles = new ArrayList();
		File tmpFile;
		for (int i = 0; i < jarFiles.size(); i++) {
			tmpFile = (File) jarFiles.get(i);
			if (tmpFile.exists() && !tmpFile.isDirectory()) {
				pureJarFiles.add(jarFiles.get(i));
			}
		}

		// 加载驱动类
		if (!pureJarFiles.isEmpty()) {
			File[] pureFiles = new File[pureJarFiles.size()];
			for (int i = 0; i < pureFiles.length; i++) {
				pureFiles[i] = (File) pureJarFiles.get(i);
			}
			addClassPath((URLClassLoader) ClassLoader.getSystemClassLoader(), pureFiles);
		}
	}
}
