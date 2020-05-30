/**
 * @Copyright 2008 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-core
 * @description 文件处理工具类
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:FileUtil.java,Revision:v1.0,Date:2008-11-7 下午01:53:21
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class FileUtil {
	/**
	 * 定义全局日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(FileUtil.class);

	/**
	 * @todo 将文件转到OutputStream
	 * @param out
	 * @param fileName
	 */
	public static void putFileInOutStream(OutputStream out, Object fileName) {
		if (fileName == null || out == null) {
			throw new IllegalArgumentException("参数不能为空");
		}
		File outFile = null;
		if (fileName instanceof String) {
			outFile = new File((String) fileName);
		} else if (fileName instanceof File) {
			outFile = (File) fileName;
		} else {
			throw new IllegalArgumentException("fileName参数类型错误,只提供String and File两个类型!");
		}
		FileInputStream fileIn = null;
		if (outFile.exists()) {
			try {
				fileIn = new FileInputStream(outFile);
				byte[] buffer = new byte[fileIn.available()];
				int length;
				while ((length = fileIn.read(buffer)) != -1) {
					out.write(buffer, 0, length);
					// 避免死循环
					if (length == 0) {
						break;
					}
				}
				out.flush();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				IOUtil.closeQuietly(out, fileIn);
			}
		}
	}

	/**
	 * @todo 将流保存为文??
	 * @param is
	 * @param fileName
	 */
	public static void putInputStreamToFile(InputStream is, String fileName) {
		FileOutputStream fos = null;
		try {
			File writeFile = new File(fileName);
			createFolder(writeFile.getParent());
			fos = new FileOutputStream(writeFile);
			byte[] buffer = new byte[is.available()];
			int length;
			while ((length = is.read(buffer)) != -1) {
				fos.write(buffer, 0, length);
				// 避免死循环
				if (length == 0) {
					break;
				}
			}
			fos.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(fos, is);
		}
	}

	/**
	 * @todo 将文件转换为流
	 * @param fileName
	 * @return
	 * @throws Exception
	 */
	public static InputStream putFileToInputStream(String fileName) throws Exception {
		return new FileInputStream(new File(fileName));
	}

	/**
	 * @todo 将字节数组保存为文件
	 * @param bytes
	 * @param fileName
	 */
	public static void putByteArrayToFile(byte[] bytes, String fileName) {
		FileOutputStream fos = null;
		try {
			File writeFile = new File(fileName);
			createFolder(writeFile.getParent());
			fos = new FileOutputStream(writeFile);
			fos.write(bytes);
			fos.flush();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(fos);
		}
	}

	/**
	 * @todo 将文件读到字符串中
	 * @param file
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	public static String readAsString(File file, String charset) throws IOException {
		byte[] fileBytes = readAsByteArray(file);
		if (StringUtil.isBlank(charset)) {
			return new String(fileBytes);
		}
		return new String(fileBytes, charset);
	}

	/**
	 * @TODO 读取文件存为字符串
	 * @param file
	 * @param charset
	 * @return
	 */
	public static String readFileAsString(Object file, String charset) {
		return inputStreamToStr(getFileInputStream(file), charset);
	}

	/**
	 * 转换InputStream为String
	 *
	 * @param is
	 * @param encoding
	 * @return
	 */
	public static String inputStreamToStr(InputStream is, String encoding) {
		if (null == is) {
			return null;
		}
		StringBuilder buffer = new StringBuilder();
		BufferedReader in = null;
		try {
			if (StringUtil.isNotBlank(encoding)) {
				in = new BufferedReader(new InputStreamReader(is, encoding));
			} else {
				in = new BufferedReader(new InputStreamReader(is));
			}
			String line = "";
			while ((line = in.readLine()) != null) {
				buffer.append(line);
				buffer.append("\r\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} finally {
			IOUtil.closeQuietly(in);
		}
		return buffer.toString();
	}

	public static String readLineAsString(File file, String charset) {
		BufferedReader reader = null;
		StringBuilder result = new StringBuilder();
		try {
			if (StringUtil.isBlank(charset)) {
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			} else {
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
			}
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(reader);
		}
		return result.toString();
	}

	/**
	 * @todo 读取文件到二进制数组中
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static byte[] readAsByteArray(File file) {
		FileInputStream in = null;
		byte[] ret = null;
		try {
			in = new FileInputStream(file);
			ret = IOUtil.getBytes(in);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(in);
		}
		return ret;
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
			IOUtil.closeQuietly(writer, osw, fos);
		}
	}

	/**
	 * @todo 递归将指定文件夹下面的文件（直到最底层文件夹）放入数组中
	 * @param parentFile
	 * @param fileList
	 * @param filter
	 */
	public static void getPathFiles(File parentFile, List fileList, String[] filters) {
		// 文件为空或不存在,跳出处理
		if (parentFile == null || !parentFile.exists()) {
			return;
		}
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
		if (baseDir == null) {
			return null;
		}
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
			if (path.indexOf("/") == 0) {
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
	 * @todo 递归匹配文件名称获取文件
	 * @param fileList
	 * @param file
	 * @param filters
	 */
	private static void matchFilters(List fileList, File file, String[] filters) {
		if (filters == null || filters.length == 0) {
			fileList.add(file);
		} else {
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
	 * @param folderPath 目录
	 * @return 返回目录创建后的路径
	 */
	public static void createFolder(String folderPath) {
		try {
			File tmpFile = new File(folderPath);
			if (!tmpFile.exists()) {
				tmpFile.mkdirs();
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("创建目录:{}操作出错{}", folderPath, e.getMessage());
		}
	}

	/**
	 * @todo 新建文件
	 * @param filePathAndName 文本文件完整绝对路径及文件名
	 * @param fileContent     文本文件内容
	 * @return
	 */

	public static void createFile(String filePathAndName, String fileContent) {
		FileWriter resultFile = null;
		PrintWriter myFile = null;
		try {
			File myFilePath = new File(filePathAndName);
			if (!myFilePath.exists()) {
				if (!myFilePath.getParentFile().exists()) {
					myFilePath.getParentFile().mkdirs();
				}
				if (fileContent == null) {
					myFilePath.createNewFile();
				}
			}
			if (fileContent != null) {
				resultFile = new FileWriter(myFilePath);
				myFile = new PrintWriter(resultFile);
				myFile.println(fileContent);
			}
		} catch (Exception e) {
			logger.error("创建文件:{},操作出错{}", filePathAndName, e.getMessage());
		} finally {
			IOUtil.closeQuietly(myFile, resultFile);
		}
	}

	/**
	 * @todo 删除文件
	 * @param filePathAndName 文本文件完整绝对路径及文件名
	 * @return Boolean 成功删除返回true遭遇异常返回false
	 */
	public static boolean delFile(String filePathAndName) {
		boolean bea = false;
		try {
			File myDelFile = new File(filePathAndName);
			if (myDelFile.exists()) {
				myDelFile.delete();
				bea = true;
			} else {
				bea = false;
			}
		} catch (Exception e) {
			logger.error("删除文件:{},操作出错{}", filePathAndName, e.getMessage());
		}
		return bea;
	}

	/**
	 * @todo 删除文件夹
	 * @param folderPath 文件夹完整绝对路??
	 * @return
	 */
	public static void delFolder(String folderPath) {
		try {
			// 删除子文件
			delAllFile(folderPath);
			// 删除当前文件夹
			new File(folderPath).delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @todo 删除指定文件夹下??有文??
	 * @param path 文件夹完整绝对路??
	 * @return
	 * @return
	 */
	public static boolean delAllFile(String path) {
		boolean result = false;
		File file = new File(path);
		if (!file.exists()) {
			return result;
		}
		if (!file.isDirectory()) {
			return result;
		}
		String[] tempList = file.list();
		File temp = null;
		for (int i = 0; i < tempList.length; i++) {
			if (path.endsWith(File.separator)) {
				temp = new File(path + tempList[i]);
			} else {
				temp = new File(path + File.separator + tempList[i]);
			}
			if (temp.isFile()) {
				temp.delete();
			}
			if (temp.isDirectory()) {
				if (path.endsWith(File.separator)) {
					delAllFile(path + tempList[i]);// 先删除文件夹里面的文??
					delFolder(path + tempList[i]);// 再删除空文件??
				} else {
					delAllFile(path + File.separator + tempList[i]);// 先删除文件夹里面的文??
					delFolder(path + File.separator + tempList[i]);// 再删除空文件??
				}
				result = true;
			}
		}
		return result;
	}

	/**
	 * @todo <b>删除指定路径下，文件名称正则匹配的文件</b>
	 * @param path
	 * @param regex
	 * @return
	 */
	public static boolean deleteMatchedFile(Object path, String[] regex) {
		List matchedFile = getPathFiles(path, regex);
		if (matchedFile != null && !matchedFile.isEmpty()) {
			logger.debug("将删除的文件数量共计:{}个!", matchedFile.size());
			Iterator iter = matchedFile.iterator();
			while (iter.hasNext()) {
				((File) iter.next()).delete();
			}
		}
		return true;
	}

	/**
	 * @todo 复制单个文件
	 * @param oldPathFile 准备复制的文件源
	 * @param newPathFile 拷贝到新绝对路径带文件名
	 * @return
	 */
	public static boolean copyFile(String oldPathFile, String newPathFile) {
		File oldfile = new File(oldPathFile);
		return copyFile(oldfile, newPathFile);
	}

	/**
	 * @todo 复制单个文件
	 * @param oldPathFile 准备复制的文件源
	 * @param newPathFile 拷贝到新绝对路径带文件名
	 * @return
	 */
	public static boolean copyFile(File oldPathFile, String newPathFile) {
		InputStream inStream = null;
		FileOutputStream fs = null;
		try {
			newPathFile = formatPath(newPathFile);
			File newFile = new File(newPathFile);
			createFolder(newFile.getParent());
			if (oldPathFile.exists()) { // 文件存在??
				inStream = new FileInputStream(oldPathFile); // 读入原文??
				fs = new FileOutputStream(newFile);
				byte[] buffer = new byte[1024];
				// update 2012.8.24 by chenrenfei
				int byteread = 0;
				while ((byteread = inStream.read(buffer)) != -1) {
					fs.write(buffer, 0, byteread);
				}
				fs.flush();
				return true;
			}
			logger.error("文件=" + oldPathFile + "不存在!计划改名对应的文件为=" + newPathFile);
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("复制文件:" + oldPathFile + " 到目标文件:" + newPathFile + " 操作失败!");
		} finally {
			IOUtil.closeQuietly(fs, inStream);
		}
		return false;
	}

	/**
	 * @todo 复制整个文件夹的内容
	 * @param oldPath 准备拷贝的目录
	 * @param newPath 指定绝对路径的新目录
	 * @return
	 */
	public static void copyFolder(String oldPath, String newPath) {
		FileInputStream input = null;
		FileOutputStream output = null;
		try {
			createFolder(newPath);
			File a = new File(oldPath);
			String[] file = a.list();
			File temp = null;
			for (int i = 0; i < file.length; i++) {
				if (oldPath.endsWith(File.separator)) {
					temp = new File(oldPath + file[i]);
				} else {
					temp = new File(oldPath + File.separator + file[i]);
				}
				if (temp.isFile()) {
					input = new FileInputStream(temp);
					output = new FileOutputStream(newPath + File.separator + (temp.getName()).toString());
					byte[] b = new byte[1024 * 5];
					int len;
					while ((len = input.read(b)) != -1) {
						output.write(b, 0, len);
						// 避免死循环(空文件)
						if (len == 0) {
							break;
						}
					}
					output.flush();
				}
				if (temp.isDirectory()) {// 如果是子文件??
					copyFolder(oldPath + File.separator + file[i], newPath + File.separator + file[i]);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("复制整个文件夹,从文件夹:{} 到文件夹:{},操作出错{}", oldPath, newPath, e.getMessage());
		} finally {
			IOUtil.closeQuietly(output, input);
		}
	}

	/**
	 * @todo 移动文件
	 * @param oldPath
	 * @param newPath
	 * @param deleteOldFile
	 */
	public static void moveFile(String oldPath, String newPath, boolean deleteOldFile) {
		copyFile(oldPath, newPath);
		if (deleteOldFile) {
			delFile(oldPath);
		}
	}

	/**
	 * @todo 移动目录
	 * @param oldPath
	 * @param newPath
	 * @return
	 */
	public static void moveFolder(String oldPath, String newPath) {
		copyFolder(oldPath, newPath);
		delFolder(oldPath);
	}

	/**
	 * @todo 文件改名
	 * @param fileName
	 * @param distFile
	 * @return:1 修改成功,0:修改失败,-1:文件不存??
	 */
	public static int rename(Object fileName, String distFile) {
		synchronized (fileName) {
			File oldFile;
			if (fileName instanceof String) {
				oldFile = new File((String) fileName);
			} else {
				oldFile = (File) fileName;
			}
			if (oldFile.exists()) {
				try {
					oldFile.renameTo(new File(distFile));
					return 1;
				} catch (Exception e) {
					e.printStackTrace();
					return 0;
				}
			} else {
				return -1;
			}
		}
	}

	/**
	 * @todo 获取文件的摘要，一般应用于检查文件是否被修改过（如在网络传输过程中，下载后取其摘要进行对比）
	 * @param fileName
	 * @param digestType :like MD5
	 * @return
	 */
	public static String getFileMessageDigest(String fileName, String digestType) {
		String result = "";
		FileInputStream fin = null;
		DigestInputStream din = null;
		try {
			MessageDigest md = MessageDigest.getInstance(digestType);
			fin = new FileInputStream(fileName);
			if (fin.available() == 0) {
				return "";
			}
			din = new DigestInputStream(fin, md);// 构造输入流
			while ((din.read()) != -1) {
				;
			}

			byte[] re = md.digest();// 获得消息摘要
			for (int i = 0; i < re.length; i++) {
				result += Integer.toHexString((0x000000ff & re[i]) | 0xffffff00).substring(6);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(din, fin);
		}
		return result;
	}

	/**
	 * @todo 文件路径拼接,自动在路径中间处理文件分割符
	 * @param topPath
	 * @param lowPath
	 * @return
	 */
	public static String linkPath(String topPath, String lowPath) {
		if (lowPath != null && isRootPath(lowPath)) {
			return lowPath;
		}
		String firstPath = "";
		String secondPath = "";
		if (StringUtil.isNotBlank(topPath)) {
			firstPath = topPath;
		}
		if (StringUtil.isNotBlank(lowPath)) {
			secondPath = lowPath;
		}
		if (firstPath.concat(secondPath).trim().equals("")) {
			return "";
		}
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
		if (fileName == null) {
			return null;
		}
		File result = null;
		if (fileName.trim().toLowerCase().startsWith("classpath:")) {
			String realPath = fileName.trim().substring(10).trim();
			if (realPath.charAt(0) == '/') {
				realPath = realPath.substring(1);
			}
			URL url = Thread.currentThread().getContextClassLoader().getResource(realPath);
			if (url != null && url.getProtocol().equals("file")) {
				try {
					result = new File(url.toURI());
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
		} else {
			result = new File(fileName);
		}
		return result;
	}

	/**
	 * @todo 判断路径是package还是file path
	 * @param file
	 * @return
	 */
	public static boolean isPackage(String file) {
		if (file.trim().startsWith("classpath:")) {
			return true;
		}
		if (isRootPath(file)) {
			return false;
		}
		if (new File(file).exists()) {
			return false;
		}
		return true;
	}

	/**
	 * @todo 获得指定路径的文件
	 * @param file 文件路径like:classpath:xxx.xml或xxx.xml
	 * @return
	 */
	public static InputStream getFileInputStream(Object file) {
		if (file == null) {
			return null;
		}
		try {
			if (file instanceof InputStream) {
				return (InputStream) file;
			}
			if (file instanceof File) {
				return new FileInputStream((File) file);
			}

			String realFile = (String) file;
			if (StringUtil.isBlank(realFile)) {
				return null;
			}
			// 文件路径
			if (new File(realFile).exists()) {
				return new FileInputStream(realFile);
			}
			if (StringUtil.indexOfIgnoreCase(realFile.trim(), "classpath:") == 0) {
				realFile = realFile.trim().substring(10).trim();
			}
			if (realFile.charAt(0) == '/') {
				realFile = realFile.substring(1);
			}
			InputStream result = Thread.currentThread().getContextClassLoader().getResourceAsStream(realFile);
			if (result == null) {
				try {
					Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(realFile);
					URL url;
					while (urls.hasMoreElements()) {
						url = urls.nextElement();
						result = new FileInputStream(url.getFile());
						if (result != null) {
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return result;
		} catch (FileNotFoundException fn) {
			fn.printStackTrace();
		}
		return null;
	}

	/**
	 * @todo 追加文件：使用FileOutputStream，在构造FileOutputStream时，把第二个参数设为true
	 * @param fileName
	 * @param content
	 */
	public static void appendFileByStream(Object fileName, String conent) {
		BufferedWriter out = null;
		try {
			File appendFile = null;
			if (fileName instanceof String) {
				appendFile = new File((String) fileName);
			} else {
				appendFile = (File) fileName;
			}
			if (!appendFile.exists()) {
				appendFile.createNewFile();
			}
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(appendFile, true)));
			out.write(conent);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(out);
		}
	}

	/**
	 * @todo 追加文件：使用FileWriter
	 * @param fileName
	 * @param content
	 */
	public static void appendFileByWriter(Object fileName, String content) {
		FileWriter writer = null;
		try {
			// 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
			File appendFile = null;
			if (fileName instanceof String) {
				appendFile = new File((String) fileName);
			} else {
				appendFile = (File) fileName;
			}
			if (!appendFile.exists()) {
				appendFile.createNewFile();
			}
			writer = new FileWriter(appendFile, true);
			writer.write(content);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(writer);
		}
	}

	/**
	 * @todo 追加文件：使用RandomAccessFile
	 * @param fileName 文件名
	 * @param content  追加的内容
	 */
	public static void appendFileByRandomAccess(Object fileName, String content) {
		RandomAccessFile randomFile = null;
		try {
			// 打开一个随机访问文件流，按读写方式
			File appendFile = null;
			if (fileName instanceof String) {
				appendFile = new File((String) fileName);
			} else {
				appendFile = (File) fileName;
			}
			if (!appendFile.exists()) {
				appendFile.createNewFile();
			}
			randomFile = new RandomAccessFile(appendFile, "rw");
			// 文件长度，字节数
			long fileLength = randomFile.length();
			// 将写文件指针移到文件尾。
			randomFile.seek(fileLength);
			randomFile.writeBytes(content);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			IOUtil.closeQuietly(randomFile);
		}
	}

	/**
	 * @todo 处理文件路径字符串，提取其的父路径
	 * @param fileName
	 * @return
	 */
	public static String getParentPath(String fileName) {
		if (fileName.lastIndexOf("/") != -1) {
			return fileName.substring(0, fileName.lastIndexOf("/"));
		}
		if (fileName.lastIndexOf("\\") != -1) {
			return fileName.substring(0, fileName.lastIndexOf("\\"));
		}
		return null;
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
}
