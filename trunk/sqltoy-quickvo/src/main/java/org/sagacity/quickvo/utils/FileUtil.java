/**
 * @Copyright 2008 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.quickvo.utils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * @project sagacity-quickvo
 * @description 文件处理工具类
 * @author zhongxuchen $<a href="mailto:zhongxuchen@gmail.com">联系作者</a>$
 * @version $id:FileUtil.java,Revision:v1.0,Date:2008-11-7 下午01:53:21 $
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class FileUtil {

	/**
	 * @todo 将文件读到字符串中
	 * @param file
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	public static String readAsString(File file, String charset) throws IOException {
		byte[] fileBytes = readAsByteArray(file);
		if (StringUtil.isBlank(charset))
			return new String(fileBytes);
		return new String(fileBytes, charset);
	}

	/**
	 * @todo 读取文件到二进制数组中
	 * @param file
	 * @return
	 */
	public static byte[] readAsByteArray(File file) {
		FileInputStream in = null;
		byte[] ret = null;
		try {
			in = new FileInputStream(file);
			ret = getBytes(in);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeQuietly(in);
		}
		return ret;
	}

	public static byte[] getBytes(InputStream is) throws Exception {
		byte[] data = null;
		// 避免空流
		if (is.available() == 0)
			return new byte[] {};
		Collection chunks = new ArrayList();
		byte[] buffer = new byte[1024 * 1000];
		int read = -1;
		int size = 0;
		while ((read = is.read(buffer)) != -1) {
			if (read > 0) {
				byte[] chunk = new byte[read];
				System.arraycopy(buffer, 0, chunk, 0, read);
				chunks.add(chunk);
				size += chunk.length;
			}
		}
		if (size > 0) {
			ByteArrayOutputStream bos = null;
			try {
				bos = new ByteArrayOutputStream(size);
				for (Iterator itr = chunks.iterator(); itr.hasNext();) {
					byte[] chunk = (byte[]) itr.next();
					bos.write(chunk);
				}
				data = bos.toByteArray();
			} finally {
				closeQuietly(bos);
			}
		}
		return data;
	}

	/**
	 * @todo <b>将字符串存为文件</b>
	 * @author zhongxuchen
	 * @date 2011-3-10 上午10:44:05
	 * @param content
	 * @param fileName
	 * @param charset
	 * @throws Exception
	 */
	public static void putStringToFile(String content, String fileName, String charset) throws Exception {
		FileOutputStream fos = null;
		OutputStreamWriter osw = null;
		BufferedWriter writer = null;
		try {
			File writeFile = new File(fileName);
			createFolder(writeFile.getParent());
			fos = new FileOutputStream(writeFile);
			if (charset != null) {
				osw = new OutputStreamWriter(fos, charset);
			} else {
				osw = new OutputStreamWriter(fos);
			}
			writer = new BufferedWriter(osw);
			writer.write(content);
			writer.flush();
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			closeQuietly(writer, osw, fos);
		}
	}

	/**
	 * @todo 递归将指定文件夹下面的文件（直到最底层文件夹）放入数组中
	 * @param parentFile
	 * @param fileList
	 * @param filter
	 */
	public static void getPathFiles(File parentFile, List fileList, String[] filters) {
		// 文件为空或不存在退出处理
		if (parentFile == null || !parentFile.exists())
			return;
		if (parentFile.isDirectory()) {
			File[] files = parentFile.listFiles();
			for (int loop = 0; loop < files.length; loop++) {
				if (!files[loop].isDirectory()) {
					matchFilters(fileList, files[loop], filters);
				} else {
					getPathFiles(files[loop], fileList, filters);
				}
			}
		} else {
			matchFilters(fileList, parentFile, filters);
		}
	}

	/**
	 * @todo 获取指定路径下符合条件的文件
	 * @param baseDir
	 * @param filters
	 * @return
	 */
	public static List getPathFiles(Object baseDir, String[] filters) {
		if (baseDir == null)
			return null;
		List fileList = new ArrayList();
		File file;
		if (baseDir instanceof String) {
			file = getFile((String) baseDir);
		} else {
			file = (File) baseDir;
		}
		getPathFiles(file, fileList, filters);
		return fileList;
	}

	/**
	 * @todo 判断是否跟路径
	 * @param path
	 * @return
	 */
	public static boolean isRootPath(String path) {
		// linux操作系统
		if (System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") == -1) {
			if (path.indexOf("/") == 0 || path.indexOf(File.separator) == 0) {
				return true;
			}
		} else {
			if (StringUtil.matches(path, "^[a-zA-Z]+:\\w*")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @todo 匹配文件
	 * @param fileList
	 * @param file
	 * @param filters
	 */
	private static void matchFilters(List fileList, File file, String[] filters) {
		if (filters == null || filters.length == 0)
			fileList.add(file);
		else {
			for (int i = 0; i < filters.length; i++) {
				if (StringUtil.matches(file.getName(), filters[i])) {
					fileList.add(file);
					break;
				}
			}
		}
	}

	/**
	 * @todo 新建目录
	 * @param folderPath
	 *            目录
	 * @return 返回目录创建后的路径
	 */
	public static void createFolder(String folderPath) {
		try {
			File tmpFile = new File(folderPath);
			if (!tmpFile.exists()) {
				tmpFile.mkdirs();
			}
		} catch (Exception e) {
			System.err.println("创建目录操作出错");
			e.printStackTrace();
		}
	}

	/**
	 * @todo 文件路径拼接,自动在路径中间处理文件分割符
	 * @param topPath
	 * @param lowPath
	 * @return
	 */
	public static String linkPath(String topPath, String lowPath) {
		if (lowPath != null && isRootPath(lowPath))
			return lowPath;
		String firstPath = "";
		String secondPath = "";
		if (StringUtil.isNotBlank(topPath))
			firstPath = topPath;
		if (StringUtil.isNotBlank(lowPath))
			secondPath = lowPath;
		if (firstPath.concat(secondPath).trim().equals(""))
			return "";
		String separator = File.separator;

		if (!firstPath.equals("")) {
			if (firstPath.substring(firstPath.length() - 1).equals("/")
					|| firstPath.substring(firstPath.length() - 1).equals("\\")) {
				firstPath = firstPath.substring(0, firstPath.length() - 1) + separator;
			} else {
				firstPath += separator;
			}
		} else {
			firstPath += separator;
		}
		if (!secondPath.equals("")
				&& (secondPath.substring(0, 1).equals("/") || secondPath.substring(0, 1).equals("\\"))) {
			secondPath = secondPath.substring(1);
		}
		return firstPath.concat(secondPath);
	}

	/**
	 * @todo 文件路径格式成本系统对应的文件格式，unix和window的文件路径区别
	 * @param path
	 * @return
	 */
	public static String formatPath(String path) {
		path = StringUtil.replaceAllStr(path, "\\\\", File.separator);
		path = StringUtil.replaceAllStr(path, "\\", File.separator);
		path = StringUtil.replaceAllStr(path, "/", File.separator);
		return path;
	}

	/**
	 * @todo 根据文件名称获取具体文件
	 * @param fileName
	 * @return
	 */
	public static File getFile(String fileName) {
		if (fileName == null)
			return null;
		File result = null;
		if (fileName.trim().toLowerCase().startsWith("classpath:")) {
			String realPath = fileName.trim().substring(10).trim();
			if (realPath.charAt(0) == '/') {
				realPath = realPath.substring(1);
			}
			URL url = Thread.currentThread().getContextClassLoader().getResource(realPath);
			if (url != null && url.getProtocol().equals("file"))
				try {
					result = new File(url.toURI());
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
		} else {
			result = new File(fileName);
		}
		return result;
	}

	/**
	 * @todo <b>跳转路径</b>
	 * @author zhongxuchen
	 * @date 2011-8-18 下午04:38:57
	 * @param basePath
	 * @param skipFile
	 * @return
	 */
	public static String skipPath(String basePath, String skipFile) {
		String realFile = FileUtil.formatPath(skipFile).trim();
		if (realFile.indexOf("." + File.separator) == 0) {
			realFile = realFile.substring(2);
		}
		String pattern = ".." + File.separator;
		int index = realFile.indexOf(pattern);
		File tmpFile = new File(basePath);
		String lastFile = (index != 0) ? tmpFile.getPath() : null;
		while (index == 0) {
			lastFile = tmpFile.getParent();
			tmpFile = tmpFile.getParentFile();
			realFile = realFile.substring(3);
			index = realFile.indexOf(pattern);
		}
		return linkPath(lastFile, realFile);
	}

	/**
	 * @todo 获取Resource
	 * @param reasource
	 * @return
	 */
	public static InputStream getResourceAsStream(String reasource) {
		String realRes = (reasource.charAt(0) == '/') ? reasource.substring(1) : reasource;
		InputStream result = Thread.currentThread().getContextClassLoader().getResourceAsStream(realRes);
		if (result == null) {
			try {
				Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(realRes);
				if (urls != null) {
					URL url;
					while (urls.hasMoreElements()) {
						url = urls.nextElement();
						result = new FileInputStream(url.getFile());
						if (result != null) {
							break;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * 关闭一个或多个流对象
	 * 
	 * @param closeables
	 *            可关闭的流对象列表
	 * @throws IOException
	 */
	public static void close(Closeable... closeables) throws IOException {
		if (closeables != null) {
			for (Closeable closeable : closeables) {
				if (closeable != null) {
					closeable.close();
				}
			}
		}
	}

	/**
	 * 关闭一个或多个流对象
	 * 
	 * @param closeables
	 *            可关闭的流对象列表
	 */
	public static void closeQuietly(Closeable... closeables) {
		try {
			close(closeables);
		} catch (IOException e) {
			// do nothing
		}
	}
}
