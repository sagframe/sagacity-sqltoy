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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description sqltoy 字段加解密接口RSA默认实现
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-05
 */
public class FieldsRSASecureProvider implements FieldsSecureProvider {
	private final static Logger logger = LoggerFactory.getLogger(FieldsRSASecureProvider.class);

	/**
	 * 字符集
	 */
	private volatile String CHARSET = "UTF-8";

	/**
	 * 私钥
	 */
	private volatile RSAPrivateKey privateKey;

	/**
	 * 公钥
	 */
	private volatile RSAPublicKey publicKey;

	private final static String ALGORITHM_RSA = "RSA";

	@Override
	public void initialize(String charset, String privateKeyStr, String publicKeyStr) throws Exception {
		this.CHARSET = StringUtil.isBlank(charset) ? "UTF-8" : charset;
		if (StringUtil.isBlank(privateKeyStr) || StringUtil.isBlank(publicKeyStr)) {
			throw new IllegalArgumentException("请正确维护RSA的私钥和公钥!spring.sqltoy.securePrivateKey 和 securePublicKey");
		}
		KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
		privateKey = (RSAPrivateKey) keyFactory.generatePrivate(getPrivateKeySpec(privateKeyStr));
		publicKey = (RSAPublicKey) keyFactory.generatePublic(getPublicKeySpec(publicKeyStr));
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
			if (StringUtil.isBlank(contents)) {
				throw new Exception("publicKey文件内容读取失败,请检查配置文件是否编译到classes目录下!");
			}
			// FileUtil读取时增加了\r\n,这里去除
			keyBytes = Base64.getDecoder().decode(contents.trim().replaceAll("\r|\n", ""));
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
			if (StringUtil.isBlank(contents)) {
				throw new Exception("privateKey文件内容读取失败,请检查配置文件是否编译到classes目录下!");
			}
			// FileUtil读取时增加了\r\n,这里去除
			keyBytes = Base64.getDecoder().decode(contents.trim().replaceAll("\r|\n", ""));
		} else {
			keyBytes = Base64.getDecoder().decode(keyStr.trim());
		}
		return new PKCS8EncodedKeySpec(keyBytes);
	}

	@Override
	public String encrypt(String contents) {
		try {
			// Cipher非线程安全,并发查询下共享实例doFinal会产生错乱密文,必须每次调用独立创建
			Cipher cipher = Cipher.getInstance(ALGORITHM_RSA);
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
			byte[] result = cipher.doFinal(contents.getBytes(CHARSET));
			return Base64.getEncoder().encodeToString(result);
		} catch (Exception e) {
			// 明文内容属于安全字段,只记录长度不可记录内容本身
			logger.error("RSA字段加密失败(明文长度:{}),原因:{}", (contents == null) ? -1 : contents.length(), e.getMessage(), e);
		}
		return "";
	}

	@Override
	public String decrypt(String secureContents) {
		try {
			Cipher cipher = Cipher.getInstance(ALGORITHM_RSA);
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			byte[] result = cipher.doFinal(Base64.getDecoder().decode(secureContents));
			return new String(result, CHARSET);
		} catch (Exception e) {
			logger.error("RSA字段解密失败(密文长度:{}),原因:{}", (secureContents == null) ? -1 : secureContents.length(),
					e.getMessage(), e);
		}
		return "";
	}

}
