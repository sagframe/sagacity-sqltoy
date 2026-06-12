package org.sagacity.sqltoy.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 输入输出IO工具类
 * @author zhongxuchen
 * @version v1.0,Date:2008-12-14
 */
public class IOUtil {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(IOUtil.class);

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
		if (str == null) {
			return null;
		}
		Charset cs = StringUtil.isNotBlank(charset) ? Charset.forName(charset) : StandardCharsets.UTF_8;
		return new ByteArrayInputStream(str.getBytes(cs));
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
		// 预分配缓冲区，减少扩容
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
				ObjectOutputStream oos = new ObjectOutputStream(bos)) {
			oos.writeObject(obj);
			// 主动刷出缓冲区
			oos.flush();
			return bos.toByteArray();
		} catch (Exception e) {
			logger.error("对象序列化失败: {}", e.getMessage(), e);
			return null;
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
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(objBytes))) {
			return in.readObject();
		} catch (Exception e) {
			logger.error("对象反序列化失败: {}", e.getMessage(), e);
			return null;
		}
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
		try (ObjectInputStream in = new ObjectInputStream(is)) {
			return in.readObject();
		} catch (Exception e) {
			logger.error("对象反序列化失败: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * @todo 将inputStream转换成byte数组
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static byte[] getBytes(InputStream is) throws IOException {
		if (is == null) {
			return null;
		}
		try (InputStream stream = is) {
			// 8KB 缓冲区
			byte[] buffer = new byte[1024 * 8];
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int len;
			while ((len = stream.read(buffer)) != -1) {
				bos.write(buffer, 0, len);
			}
			return bos.toByteArray();
		}
	}

	/**
	 * @todo 将inputStream转换成字符串
	 * @param is
	 * @param encoding
	 * @return
	 */
	public static String inputStreamToStr(InputStream is, String encoding) {
		if (is == null) {
			return null;
		}
		Charset charset = StringUtil.isNotBlank(encoding) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
		final String lineSep = System.lineSeparator();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(is, charset))) {
			StringBuilder buffer = new StringBuilder();
			String line;
			boolean firstLine = true;
			while ((line = in.readLine()) != null) {
				if (!firstLine) {
					buffer.append(lineSep);
				}
				buffer.append(line);
				firstLine = false;
			}
			return buffer.toString();
		} catch (Exception e) {
			logger.error("读取InputStream失败: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * @todo 将对象转换成ByteBuffer
	 * @param obj
	 * @return
	 * @throws IOException
	 */
	public static ByteBuffer getByteBuffer(Object obj) throws IOException {
		if (obj == null) {
			return null;
		}
		try (ByteArrayOutputStream bOut = new ByteArrayOutputStream(1024);
				ObjectOutputStream out = new ObjectOutputStream(bOut)) {
			out.writeObject(obj);
			out.flush();
			// 直接使用内部数组，不再二次拷贝（慎用：BAOS 内部数组会随操作变化）
			// 稳妥写法：wrap(toByteArray())，追求极致性能用下面注释行
			// return ByteBuffer.wrap(bOut.buf, 0, bOut.count);
			return ByteBuffer.wrap(bOut.toByteArray());
		}
	}

	/**
	 * @TODO 关闭一个或多个流对象
	 * @param closeables 可关闭的流对象列表
	 * @throws IOException
	 */
	public static void close(Closeable... closeables) throws IOException {
		if (closeables != null) {
			IOException firstException = null;
			for (Closeable closeable : closeables) {
				if (closeable != null) {
					try {
						closeable.close();
					} catch (IOException e) {
						if (firstException == null) {
							firstException = e;
						} else {
							firstException.addSuppressed(e);
						}
					}
				}
			}
			if (firstException != null) {
				throw firstException;
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
