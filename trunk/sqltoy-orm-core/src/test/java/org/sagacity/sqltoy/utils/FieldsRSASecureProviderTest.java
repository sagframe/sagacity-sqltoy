package org.sagacity.sqltoy.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;
import org.sagacity.sqltoy.plugins.secure.impl.FieldsRSASecureProvider;

public class FieldsRSASecureProviderTest {
	private FieldsSecureProvider fieldsSecureProvider;

	@BeforeEach
	public void init() {
		fieldsSecureProvider = new FieldsRSASecureProvider();
		try {
			// String privateKey =
			// "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAM26WSLBNn7tV8e2XjfO/pp3pCXll3Kt+PNCmsbxF60ygpWy1I81vqIuGEGUStbaLkJVAYTfKFzqIQCUs23jOD2qWeYAJ4FVka4xkFbBBky7rJwnIiLjAbpXRgHncTRr1BpJ9xIIEzx+1TRf1MjBChFLC8s+ileLgO5xKLFa9OX1AgMBAAECgYEAiOWJzuC3PLr/AHxQMd7h+TPH3RfsMXmnAWi+ycdAtBW6Y5b+btWapxz5MxpUuqewxJ8ARcShfUKm91X8GBFtKBlcGcGmqFatcL/hrMrlhZueF7LmzZnMR87FcDdIqweq7xv9GfWLcgI3LLMUrs6r4SZsVgKAm8OneQDtY1XJ9HkCQQD3Z0vGHYvOMoV05iqIJ+OrfW5z2DZzUXtOEb4cK/keHAQf63xks1LbydwOwkstm4bucIvmm2rK06RWywWCYZybAkEA1OBW65lDRx4e2wLhI4XZcpgkVl9Aw6UbXMPkMwDvjWk+u9YteSvbs/eRmMNNUJbqtrGZQiy2jqrL8CIfTmIIrwJAMw2p4VQviXl7eMgWdspkfPsBU/6GHf3uiAm5RW79lW0KnNuna9BlhN1+/7ywbtTtXz7yX8AqpXhPLWnv1Rv3iQJBAMDP8eqzhxyDS69TjFiAg9QnucIBxMdwZLhBNhB8aH3NNeUsuUNnVjhLpLSZMQ4to6qWchpeJXxTdySpw3FbmkECQQCR7xaAdqLYpTNsZ7CU9xXrT4gzgi+cR7xRtc0yF+m1NErJ5riNadJ79gBw1Dn6JbuPcm4VRbPnQaTFTe/I9xPK";
			// String publicKey =
			// "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDNulkiwTZ+7VfHtl43zv6ad6Ql5ZdyrfjzQprG8RetMoKVstSPNb6iLhhBlErW2i5CVQGE3yhc6iEAlLNt4zg9qlnmACeBVZGuMZBWwQZMu6ycJyIi4wG6V0YB53E0a9QaSfcSCBM8ftU0X9TIwQoRSwvLPopXi4DucSixWvTl9QIDAQAB";
			String privateKey = "classpath:mock/rsa_private.key";
			String publicKey = "classpath:mock/rsa_public.key";
			fieldsSecureProvider.initialize(null, privateKey, publicKey);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void testEncrypt() {
		System.err.println(fieldsSecureProvider.encrypt("chenrenfei"));
		System.err.println(fieldsSecureProvider.encrypt("chenrenfei"));
		System.err.println(fieldsSecureProvider.encrypt("13918658756"));
		System.err.println(fieldsSecureProvider.encrypt("中国 江苏省 南京市 仙霞区 公道街区 高德小区429弄78号321室"));
	}

	@Test
	public void testDecrypt() {
		String secureCode = fieldsSecureProvider.encrypt("chenrenfei");
		System.err.println(fieldsSecureProvider.decrypt(secureCode));
		secureCode = fieldsSecureProvider.encrypt("中国 江苏省 南京市 仙霞区 公道街区 高德小区429弄78号321室");
		System.err.println(fieldsSecureProvider.decrypt(secureCode));
	}
}
