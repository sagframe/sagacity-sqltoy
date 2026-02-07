package org.sagacity.sqltoy.utils;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author zhong
 *
 */
public class SecureUtils {
	public static final String KEY_ALGORITHM = "RSA";
	private static final String PUBLIC_KEY = "RSAPublicKey";
	private static final String PRIVATE_KEY = "RSAPrivateKey";
	private static final int KEY_SIZE = 2048;

	public static Map<String, Object> initKey() throws Exception {
		// 获得对象 KeyPairGenerator 参数 RSA 1024个字节
		KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
		keyPairGen.initialize(KEY_SIZE);
		// 通过对象 KeyPairGenerator 获取对象KeyPair
		KeyPair keyPair = keyPairGen.generateKeyPair();

		// 通过对象 KeyPair 获取RSA公私钥对象RSAPublicKey RSAPrivateKey
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		// 公私钥对象存入map中
		Map<String, Object> keyMap = new HashMap<String, Object>(2);
		keyMap.put(PUBLIC_KEY, publicKey);
		keyMap.put(PRIVATE_KEY, privateKey);
		return keyMap;
	}

	// 获得公钥
	public static String getPublicKey(Map<String, Object> keyMap) throws Exception {
		// 获得map中的公钥对象 转为key对象
		Key key = (Key) keyMap.get(PUBLIC_KEY);
		// byte[] publicKey = key.getEncoded();
		// 编码返回字符串
		return encryptBASE64(key.getEncoded());
	}

	// 获得私钥
	public static String getPrivateKey(Map<String, Object> keyMap) throws Exception {
		// 获得map中的私钥对象 转为key对象
		Key key = (Key) keyMap.get(PRIVATE_KEY);
		// byte[] privateKey = key.getEncoded();
		// 编码返回字符串
		return encryptBASE64(key.getEncoded());
	}

	// 编码返回字符串
	public static String encryptBASE64(byte[] key) throws Exception {
		return Base64.getEncoder().encodeToString(key);
	}

	public static void main(String[] args) throws Exception {
		Map map = initKey();
		String privateKey = getPrivateKey(map);
		String publicKey = getPublicKey(map);
		System.err.println("[" + privateKey + "]");
		System.err.println("[" + publicKey + "]");
	}
}
