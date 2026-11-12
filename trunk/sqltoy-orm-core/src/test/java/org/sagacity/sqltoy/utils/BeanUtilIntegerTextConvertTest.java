package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.DataType;

/**
 * 回归测试:整型目标类型(Long/Integer/BigInteger/原生int、long)的文本转换口径统一
 *
 * <p>
 * 历史上四个分支各写各的:原生long走Double中转(&gt;2^53静默失真,但容忍科学计数与前导点)、
 * 其余三个走split(".")[0](&gt;2^53精确,却把"1.9E10"截成1、"1e3"/".5"/首尾空白抛异常);
 * 后来改成BigDecimal.xValue()的版本又引入越界静默取低64位("9999999999999999999999"-&gt;垃圾值)。
 * 现统一由BeanUtil.toIntegerText先归一再交给各类型精确解析,四条口径必须一致且越界必须响亮报错。
 */
public class BeanUtilIntegerTextConvertTest {

	private static Object convert(Object value, int typeValue, String typeName) throws Exception {
		return BeanUtil.convertType(null, value, Types.OTHER, typeValue, typeName, null);
	}

	private static Object toLong(Object value) throws Exception {
		return convert(value, DataType.primitiveLongType, "long");
	}

	@Test
	public void longKeepsPrecisionBeyond2Pow53() throws Exception {
		// 雪花ID量级:经Double中转为1234567890123456768,必须精确
		assertEquals(Long.valueOf(1234567890123456789L), toLong("1234567890123456789"));
		assertEquals(Long.valueOf(Long.MAX_VALUE), toLong("9223372036854775807"));
		// 越界:必须响亮报错,不能像BigDecimal.longValue()那样静默取低64位
		assertThrows(NumberFormatException.class, () -> toLong("9999999999999999999999"));
	}

	@Test
	public void longAcceptsScientificAndLooseForms() throws Exception {
		assertEquals(Long.valueOf(19000000000L), toLong("1.9E10"));
		assertEquals(Long.valueOf(1000L), toLong("1e3"));
		assertEquals(Long.valueOf(0L), toLong(".5"));
		assertEquals(Long.valueOf(0L), toLong("-.5"));
		assertEquals(Long.valueOf(12L), toLong(" 12 "));
		assertEquals(Long.valueOf(1L), toLong("1."));
		// 小数截断(向零)语义保持:原Double.longValue()即此口径
		assertEquals(Long.valueOf(1L), toLong("1.5"));
		assertEquals(Long.valueOf(-1L), toLong("-1.5"));
	}

	@Test
	public void longFromFloatingValuesNoLongerCollapses() throws Exception {
		// Double.toString对>=1e7/<1e-3走科学计数:split口径下1.0E7→1、0.0001→1
		assertEquals(Long.valueOf(10000000L), toLong(Double.valueOf(10000000.0d)));
		assertEquals(Long.valueOf(12345678L), toLong(Double.valueOf(12345678.0d)));
		assertEquals(Long.valueOf(0L), toLong(Double.valueOf(0.0001d)));
	}

	@Test
	public void groupingSeparatorStillStripped() throws Exception {
		// 千分位由convertBoolean剥离,归一层不能破坏该能力
		assertEquals(Long.valueOf(1888L), toLong("1,888.9"));
	}

	@Test
	public void hugeExponentRejectedInsteadOfHang() throws Exception {
		// update 2026-11-10 巨大指数展开防御:1e999999999的setScale/toPlainString会展开10^N位整数
		// 导致挂死/OOM,守卫须在展开前响亮抛NumberFormatException(合法科学计数不受影响,见下方对照)
		assertThrows(NumberFormatException.class, () -> toLong("1e999999999"));
		assertThrows(NumberFormatException.class, () -> toLong("1E2147483647"));
		assertThrows(NumberFormatException.class, () -> toLong("-1e100000000"));
		// 对照:常规大展开(1e100=101位)仍正常走目标类型parse响亮(超long范围)而非守卫误伤
		assertThrows(NumberFormatException.class, () -> toLong("1e100"));
		// 对照:上限内的展开值精确转换(1e15 → 1000000000000000)
		assertEquals(Long.valueOf(1000000000000000L), toLong("1e15"));
		// 微小指数(负向)不展开,截断为0
		assertEquals(Long.valueOf(0L), toLong("1e-999999999"));
	}

	@Test
	public void wrapLongAndPrimitiveIntUseSamePolicy() throws Exception {
		assertEquals(Long.valueOf(19000000000L), convert("1.9E10", DataType.wrapLongType, "java.lang.Long"));
		// 数据库默认值0.00的既有兼容(不能因精度而抛错)
		assertEquals(Long.valueOf(0L), convert("0.00", DataType.wrapLongType, "java.lang.Long"));
		assertEquals(Integer.valueOf(1), convert("1.5", DataType.primitiveIntType, "int"));
		// 原Double中转越界会静默饱和成Integer.MAX_VALUE,现必须报错
		assertThrows(NumberFormatException.class, () -> convert("99999999999", DataType.primitiveIntType, "int"));
		assertThrows(NumberFormatException.class, () -> convert("1.9E10", DataType.primitiveIntType, "int"));
	}

	@Test
	public void integerAlignment() throws Exception {
		assertEquals(Integer.valueOf(1), convert("1.5", DataType.wrapIntegerType, "java.lang.Integer"));
		assertEquals(Integer.valueOf(12), convert(" 12 ", DataType.wrapIntegerType, "java.lang.Integer"));
		assertEquals(Integer.valueOf(0), convert(".5", DataType.wrapIntegerType, "java.lang.Integer"));
		// 超int范围一律报错:纯整数由parse报,科学计数展开后同样报(旧split口径"1.9E10"静默得1)
		assertThrows(NumberFormatException.class,
				() -> convert("99999999999", DataType.wrapIntegerType, "java.lang.Integer"));
		assertThrows(NumberFormatException.class,
				() -> convert("1.9E10", DataType.wrapIntegerType, "java.lang.Integer"));
	}

	@Test
	public void bigIntegerAlignment() throws Exception {
		assertEquals(new BigInteger("123456789012345678901234567890"),
				convert("123456789012345678901234567890", DataType.wrapBigIntegerType, "java.math.BigInteger"));
		assertEquals(new BigInteger("19000000000"),
				convert("1.9E10", DataType.wrapBigIntegerType, "java.math.BigInteger"));
		assertEquals(BigInteger.ZERO, convert(".5", DataType.wrapBigIntegerType, "java.math.BigInteger"));
		assertEquals(BigInteger.valueOf(-1L),
				convert("-1.5", DataType.wrapBigIntegerType, "java.math.BigInteger"));
	}

	@Test
	public void bigDecimalToleratesSurroundingBlank() throws Exception {
		// 与整型分支的trim口径对齐:原" 12.5 "直接抛NumberFormatException
		assertEquals(new BigDecimal("12.5"),
				convert(" 12.5 ", DataType.wrapBigDecimalType, "java.math.BigDecimal"));
	}
}
