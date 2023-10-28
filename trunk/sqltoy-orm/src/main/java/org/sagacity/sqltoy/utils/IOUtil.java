package org.sagacity.sqltoy.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

/**
 * @project sagacity-sqltoy
 * @description 输入输出IO工具类
 * @author zhongxuchen
 * @version v1.0,Date:2008-12-14
 */
public class IOUtil {

	private IOUtil() {
	}

	/**
	 * @TODO 转换String为InputStream
	 * @param str
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	public static InputStream strToInputStream(String str, String charset) throws Exception {
		if (charset != null) {
			return new ByteArrayInputStream(str.getBytes(charset));
		}
		return new ByteArrayInputStream(str.getBytes());
	}

	/**
	 * @todo 将对象转换成字节数组
	 * @param obj
	 * @return
	 */
	public static byte[] objectToBytes(Object obj) {
		if (obj == null) {
			return null;
		}
		ByteArrayOutputStream out = null;
		ObjectOutputStream outputStream = null;
		try {
			out = new ByteArrayOutputStream();
			outputStream = new ObjectOutputStream(out);
			outputStream.writeObject(obj);
			outputStream.flush();
			return out.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			closeQuietly(outputStream, out);
		}
	}

	/**
	 * @todo 字节数组转换成对象
	 * @param objBytes
	 * @return
	 */
	public static Object bytesToObject(byte[] objBytes) {
		if (objBytes == null || objBytes.length == 0) {
			return null;
		}
		Object obj = null;
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new ByteArrayInputStream(objBytes));
			obj = in.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeQuietly(in);
		}
		return obj;
	}

	/**
	 * @todo 字节数组转换成对象,一般用于对象序列化
	 * @param is
	 * @return
	 */
	public static Object streamToObject(InputStream is) {
		if (is == null) {
			return null;
		}
		Object obj = null;
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(is);
			obj = in.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeQuietly(in);
		}
		return obj;
	}

	/**
	 * @todo 将inputStream转换成byte数组
	 * @param is
	 * @return
	 * @throws Exception
	 */
	public static byte[] getBytes(InputStream is) throws Exception {
		if (is == null) {
			return null;
		}
		// 无需关闭
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len;
		while ((len = is.read(buffer)) != -1) {
			outputStream.write(buffer, 0, len);
		}
		return outputStream.toByteArray();
	}

	public static ByteBuffer getByteBuffer(Object obj) throws IOException {
		if (obj == null) {
			return null;
		}
		ByteArrayOutputStream bOut = null;
		ObjectOutputStream out = null;
		try {
			bOut = new ByteArrayOutputStream();
			out = new ObjectOutputStream(bOut);
			out.writeObject(obj);
			out.flush();
			return ByteBuffer.wrap(bOut.toByteArray());
		} catch (IOException ie) {
			throw ie;
		} finally {
			closeQuietly(out, bOut);
		}
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
}
