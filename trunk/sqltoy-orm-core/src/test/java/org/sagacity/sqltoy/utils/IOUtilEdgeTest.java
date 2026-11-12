package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * IOUtil 边缘场景测试
 */
public class IOUtilEdgeTest {

	/** 非序列化对象 */
	static class NotSerializable {
	}

	static class Bean implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		String name;
		int age;
	}

	@Test
	public void strToInputStreamNullAndEmpty() throws Exception {
		assertNull(IOUtil.strToInputStream(null, "UTF-8"));
		InputStream empty = IOUtil.strToInputStream("", "UTF-8");
		assertEquals(0, empty.available());
		InputStream is = IOUtil.strToInputStream("abc", null);
		assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), IOUtil.getBytes(is));
		// 指定GBK编码
		InputStream gbk = IOUtil.strToInputStream("中文", "GBK");
		assertArrayEquals("中文".getBytes("GBK"), IOUtil.getBytes(gbk));
		// 空白charset默认UTF-8
		InputStream blankCs = IOUtil.strToInputStream("abc", "  ");
		assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), IOUtil.getBytes(blankCs));
	}

	@Test
	public void strToInputStreamIllegalCharset() {
		// 非法charset抛RuntimeException(charset名语法错误)
		assertThrows(Exception.class, () -> IOUtil.strToInputStream("abc", "ILLEGAL\\CHARSET/NAME"));
	}

	@Test
	public void objectBytesRoundTrip() {
		Bean bean = new Bean();
		bean.name = "sqltoy";
		bean.age = 10;
		byte[] bytes = IOUtil.objectToBytes(bean);
		assertNotNull(bytes);
		Object back = IOUtil.bytesToObject(bytes);
		assertTrue(back instanceof Bean);
		assertEquals("sqltoy", ((Bean) back).name);
		assertNull(IOUtil.objectToBytes(null));
		assertNull(IOUtil.bytesToObject(null));
		assertNull(IOUtil.bytesToObject(new byte[0]));
	}

	@Test
	public void objectToBytesNotSerializable() {
		// 非序列化对象返回null不抛异常
		assertNull(IOUtil.objectToBytes(new NotSerializable()));
	}

	@Test
	public void bytesToObjectCorrupted() {
		// 损坏的序列化数据返回null不抛异常
		assertNull(IOUtil.bytesToObject("corrupted data".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void streamToObject() {
		Bean bean = new Bean();
		bean.name = "x";
		byte[] bytes = IOUtil.objectToBytes(bean);
		Object back = IOUtil.streamToObject(new ByteArrayInputStream(bytes));
		assertEquals("x", ((Bean) back).name);
		assertNull(IOUtil.streamToObject(null));
		assertNull(IOUtil.streamToObject(new ByteArrayInputStream(new byte[0])));
	}

	@Test
	public void getBytesEdge() throws Exception {
		assertNull(IOUtil.getBytes(null));
		// 空流返回空数组
		assertEquals(0, IOUtil.getBytes(new ByteArrayInputStream(new byte[0])).length);
		// 大于单次缓冲区8KB的数据完整性
		byte[] big = new byte[1024 * 8 * 3 + 17];
		for (int i = 0; i < big.length; i++) {
			big[i] = (byte) (i % 127);
		}
		assertArrayEquals(big, IOUtil.getBytes(new ByteArrayInputStream(big)));
	}

	@Test
	public void inputStreamToStr() {
		assertNull(IOUtil.inputStreamToStr(null, "UTF-8"));
		// 多行文本的换行统一为系统行分隔符
		String sep = System.lineSeparator();
		String result = IOUtil.inputStreamToStr(
				new ByteArrayInputStream("a\r\nb\nc".getBytes(StandardCharsets.UTF_8)), "UTF-8");
		assertEquals("a" + sep + "b" + sep + "c", result);
		// 空流返回空字符串
		assertEquals("", IOUtil.inputStreamToStr(new ByteArrayInputStream(new byte[0]), null));
		// 非法编码名容错返回null(charset解析已纳入try块)
		assertNull(IOUtil.inputStreamToStr(new ByteArrayInputStream("a".getBytes()), "ILLEGAL\\NAME"));
	}

	@Test
	public void getByteBuffer() throws Exception {
		assertNull(IOUtil.getByteBuffer(null));
		Bean bean = new Bean();
		bean.name = "buf";
		ByteBuffer buffer = IOUtil.getByteBuffer(bean);
		assertNotNull(buffer);
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		Object back = IOUtil.bytesToObject(bytes);
		assertEquals("buf", ((Bean) back).name);
	}

	@Test
	public void closeNullAndEmpty() throws Exception {
		// null数组、数组含null元素均安全
		IOUtil.close((Closeable[]) null);
		IOUtil.close((Closeable) null);
		IOUtil.closeQuietly((Closeable[]) null);
	}

	@Test
	public void closeAllClosedEvenWithException() {
		// 第一个流关闭异常,后续流仍然被关闭,异常被合并抛出
		final boolean[] closed = { false, false };
		Closeable throwFirst = () -> {
			throw new IOException("first");
		};
		Closeable normalSecond = () -> closed[1] = true;
		IOException ex = assertThrows(IOException.class, () -> IOUtil.close(throwFirst, normalSecond));
		assertEquals("first", ex.getMessage());
		assertTrue(closed[1], "第二个流必须被关闭");
		// 多个异常被suppressed
		Closeable throwSecond = () -> {
			throw new IOException("second");
		};
		IOException ex2 = assertThrows(IOException.class, () -> IOUtil.close(throwFirst, throwSecond));
		assertEquals("first", ex2.getMessage());
		assertEquals(1, ex2.getSuppressed().length);
	}

	@Test
	public void closeQuietlySwallow() {
		IOUtil.closeQuietly(() -> {
			throw new IOException("ignored");
		});
	}

	@Test
	public void sameInstancePassthrough() {
		// getFileInputStream对InputStream类型直接原样返回
		InputStream is = new ByteArrayInputStream("x".getBytes());
		assertSame(is, FileUtil.getFileInputStream(is));
	}
}
