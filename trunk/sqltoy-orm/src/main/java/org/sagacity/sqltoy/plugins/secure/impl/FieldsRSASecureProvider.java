package org.sagacity.sqltoy.plugins.secure.impl;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

import org.sagacity.sqltoy.plugins.secure.FieldsSecureProvider;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * 
 * @author zhong
 *
 */
public class FieldsRSASecureProvider implements FieldsSecureProvider {

	/**
	 * 字符集
	 */
	private String CHARSET = "UTF-8";

	/**
	 * 私钥
	 */
	private RSAPrivateKey privateKey;

	/**
	 * 公钥
	 */
	private RSAPublicKey publicKey;

	private final static String ALGORITHM_RSA = "RSA";

	/**
	 * 加密cipher
	 */
	private Cipher encryptCipher;

	/**
	 * 解密cipher
	 */
	private Cipher decryptCipher;

	@Override
	public void initialize(String charset, String privateKeyStr, String publicKeyStr) throws Exception {
		this.CHARSET = StringUtil.isBlank(charset) ? "UTF-8" : charset;
		if (StringUtil.isBlank(privateKeyStr) || StringUtil.isBlank(publicKeyStr)) {
			throw new IllegalArgumentException("请正确维护RSA的私钥和公钥!spring.sqltoy.securePrivateKey 和 securePublicKey");
		}
		KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
		privateKey = (RSAPrivateKey) keyFactory.generatePrivate(getPrivateKeySpec(privateKeyStr));
		publicKey = (RSAPublicKey) keyFactory.generatePublic(getPublicKeySpec(publicKeyStr));
		// 公钥加密
		encryptCipher = Cipher.getInstance(ALGORITHM_RSA);
		encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

		// 私钥解密
		decryptCipher = Cipher.getInstance(ALGORITHM_RSA);
		decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
	}

	/**
	 * @TODO 获得公钥Key spec
	 * @param keyStr
	 * @return
	 * @throws Exception
	 */
	private X509EncodedKeySpec getPublicKeySpec(String keyStr) throws Exception {
		byte[] keyBytes;
		if (keyStr.toLowerCase().trim().startsWith("classpath:")) {
			String contents = FileUtil.readFileAsStr(keyStr, CHARSET);
			keyBytes = Base64.getDecoder().decode(contents.trim().replaceAll("\r\n", ""));
		} else {
			keyBytes = Base64.getDecoder().decode(keyStr.trim());
		}
		return new X509EncodedKeySpec(keyBytes);
	}

	/**
	 * @TODO 获得私钥Key spec
	 * @param keyStr
	 * @return
	 * @throws Exception
	 */
	private PKCS8EncodedKeySpec getPrivateKeySpec(String keyStr) throws Exception {
		byte[] keyBytes;
		if (keyStr.toLowerCase().trim().startsWith("classpath:")) {
			String contents = FileUtil.readFileAsStr(keyStr, CHARSET);
			keyBytes = Base64.getDecoder().decode(contents.trim().replaceAll("\r\n", ""));
		} else {
			keyBytes = Base64.getDecoder().decode(keyStr.trim());
		}
		return new PKCS8EncodedKeySpec(keyBytes);
	}

	@Override
	public String encrypt(String contents) {
		try {
			byte[] result = encryptCipher.doFinal(contents.getBytes(CHARSET));
			return Base64.getEncoder().encodeToString(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	@Override
	public String decrypt(String secureContents) {
		try {
			byte[] result = decryptCipher.doFinal(Base64.getDecoder().decode(secureContents));
			return new String(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

}
