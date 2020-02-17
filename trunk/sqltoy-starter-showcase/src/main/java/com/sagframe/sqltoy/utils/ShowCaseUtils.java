/**
 * 
 */
package com.sagframe.sqltoy.utils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-boot-showcase
 * @description 输入输出IO工具类
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:ShowCaseUtils.java,Revision:v1.0,Date:2008-12-14 下午07:53:54 $
 */
@SuppressWarnings("rawtypes")
public class ShowCaseUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ShowCaseUtils.class);

	/**
	 * @TODO 加载文件
	 * @param file
	 * @param encoding
	 * @return
	 */
	public static String loadFile(Object file, String encoding) {
		return inputStream2String(getFileInputStream(file), StringUtil.isBlank(encoding) ? "UTF-8" : encoding);
	}

	/**
	 * @TODO 转换InputStream为String
	 * @param is
	 * @param encoding
	 * @return
	 */
	public static String inputStream2String(InputStream is, String encoding) {
		StringBuilder buffer = new StringBuilder();
		BufferedReader in = null;
		try {
			if (StringUtil.isNotBlank(encoding))
				in = new BufferedReader(new InputStreamReader(is, encoding));
			else
				in = new BufferedReader(new InputStreamReader(is));
			String line = "";
			while ((line = in.readLine()) != null) {
				buffer.append(line);
				buffer.append("\r\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
		} finally {
			closeQuietly(in);
		}
		return buffer.toString();
	}

	/**
	 * @TODO 将inputStream转换成byte数组
	 * @param is
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static byte[] getBytes(InputStream is) {
		byte[] data = null;
		try {
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
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}

	/**
	 * @TODO 关闭一个或多个流对象
	 * @param closeables 可关闭的流对象列表
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
	 * @TODO 关闭一个或多个流对象
	 * @param closeables 可关闭的流对象列表
	 */
	public static void closeQuietly(Closeable... closeables) {
		try {
			close(closeables);
		} catch (IOException e) {
			// do nothing
		}
	}

	/**
	 * @TODO 获得指定路径的文件
	 * @param file 文件路径like:classpath:xxx.xml或xxx.xml
	 * @return
	 */
	public static InputStream getFileInputStream(Object file) {
		if (file == null)
			return null;
		try {
			if (file instanceof File)
				return new FileInputStream((File) file);
			else {
				// 文件路径
				if (new File((String) file).exists())
					return new FileInputStream((String) file);
				else {
					String realFile = (String) file;
					if (StringUtil.indexOfIgnoreCase(realFile.trim(), "classpath:") == 0)
						realFile = realFile.trim().substring(10).trim();
					if (realFile.charAt(0) == '/')
						realFile = realFile.substring(1);
					return Thread.currentThread().getContextClassLoader().getResourceAsStream(realFile);
				}
			}
		} catch (FileNotFoundException fn) {
			fn.printStackTrace();
		}
		return null;
	}

	public static int getRandomNum(int max) {
		return getRandomNum(0, max);
	}

	public static int getRandomNum(int start, int end) {
		long value = Math.abs(new SecureRandom().nextLong()) % (end - start);
		return Long.valueOf(value + start).intValue();
	}

	/**
	 * @TODO 按照概率获取对应概率的数据索引，如：A：概率80%，B：10%，C：6%，D：4%，将出现概率放入数组， 按随机规则返回对应概率的索引
	 * @param probabilities
	 * @return
	 */
	public static int getProbabilityIndex(int[] probabilities) {
		int total = 0;
		for (int probabilitiy : probabilities)
			total = total + probabilitiy;
		int randomData = (int) (Math.random() * total) + 1;
		int base = 0;
		for (int i = 0; i < probabilities.length; i++) {
			if (randomData > base && randomData <= base + probabilities[i])
				return i;
			base = base + probabilities[i];
		}
		return 0;
	}
}
