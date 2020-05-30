/**
 * @Copyright 2008 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * @project sagacity-core
 * @description 输入输出IO工具类
 * @author chenrenfei $<a href="mailto:zhongxuchen@hotmail.com">联系作者</a>$
 * @version $id:IOUtil.java,Revision:v1.0,Date:2008-12-14 下午07:53:54 $
 */
@SuppressWarnings("rawtypes")
public class IOUtil {

	/**
	 * 转换String为InputStream
	 * 
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
	 * @param inStream
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
	 *
	 * @todo 将inputStream转换成byte数组
	 * @param is
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static byte[] getBytes(InputStream is) throws Exception {
		if (is == null) {
			return null;
		}
		// 避免空流
		if (is.available() == 0) {
			return new byte[] {};
		}
		byte[] data = null;
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
	 * 关闭一个或多个流对象
	 *
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
	 * 关闭一个或多个流对象
	 *
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
