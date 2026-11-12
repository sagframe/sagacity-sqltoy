package org.sagacity.sqltoy.dialect.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.callback.DecryptHandler;
import org.sagacity.sqltoy.callback.ReflectPropsHandler;
import org.sagacity.sqltoy.config.model.FieldSecureConfig;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.SecureType;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;

/**
 * 回归测试：安全字段空白值在写入(加密)与读取(解密)两侧判据统一,均使用StringUtil.isBlank。
 * 修复前写入侧为!"".equals(contents)只挡空串,纯空格会被加密落库;读取侧却按trim判空不解密,
 * 两侧对"空白"的处理漂移
 */
public class SecureFieldBlankHandlingTest {

	private static final class RecordingProvider implements FieldsSecureProvider {
		final AtomicInteger encryptCount = new AtomicInteger();
		final AtomicInteger decryptCount = new AtomicInteger();

		@Override
		public void initialize(String charset, String privateKey, String publicKey) {
		}

		@Override
		public String encrypt(String contents) {
			encryptCount.incrementAndGet();
			return "ENC[" + contents + "]";
		}

		@Override
		public String decrypt(String secureContents) {
			decryptCount.incrementAndGet();
			return "DEC[" + secureContents + "]";
		}
	}

	/** 走写入侧真实入口:getSecureReflectHandler返回的handler */
	private static Object encryptColumn(Object value, RecordingProvider provider) {
		List<FieldSecureConfig> secureFields = new ArrayList<FieldSecureConfig>();
		secureFields.add(new FieldSecureConfig("address", SecureType.ENCRYPT, null, null, 0, 0, 0));
		ReflectPropsHandler handler = DialectUtils.getSecureReflectHandler(null, provider, null, secureFields);
		Object[] rowData = new Object[] { value };
		HashMap<String, Integer> indexMap = new HashMap<String, Integer>();
		indexMap.put("address", 0);
		handler.setRowData(rowData);
		handler.setPropertyIndexMap(indexMap);
		handler.process();
		return rowData[0];
	}

	/** 走读取侧真实入口:DecryptHandler */
	private static Object decryptColumn(Object value, RecordingProvider provider) {
		IgnoreCaseSet columns = new IgnoreCaseSet();
		columns.add("address");
		return new DecryptHandler(provider, columns).decrypt("address", value);
	}

	@Test
	public void blankValueIsNotEncrypted() {
		RecordingProvider provider = new RecordingProvider();
		// 纯空格/tab等空白:保持原值落库,不加密
		assertEquals("   ", encryptColumn("   ", provider));
		assertEquals("\t", encryptColumn("\t", provider));
		// 空串与null
		assertEquals("", encryptColumn("", provider));
		assertNull(encryptColumn(null, provider));
		assertEquals(0, provider.encryptCount.get(), "空白值不得进入加密");
	}

	@Test
	public void normalValueIsStillEncrypted() {
		RecordingProvider provider = new RecordingProvider();
		assertEquals("ENC[13800138000]", encryptColumn("13800138000", provider));
		assertEquals(1, provider.encryptCount.get());
	}

	@Test
	public void blankValueIsNotDecrypted() {
		RecordingProvider provider = new RecordingProvider();
		// 与写入侧同一判据:空白原值返回,不进入解密(解密失败已改为抛异常,空白必须被挡在门外)
		assertEquals("   ", decryptColumn("   ", provider));
		assertEquals("", decryptColumn("", provider));
		assertNull(decryptColumn(null, provider));
		assertEquals(0, provider.decryptCount.get(), "空白值不得进入解密");
	}

	@Test
	public void normalCipherTextIsStillDecrypted() {
		RecordingProvider provider = new RecordingProvider();
		assertEquals("DEC[QUJD]", decryptColumn("QUJD", provider));
		assertEquals(1, provider.decryptCount.get());
	}
}
