package org.sagacity.sqltoy.plugins.secure.impl;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Cipher;

import org.sagacity.sqltoy.exception.DataAccessException;
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
			throw new IllegalArgumentException(
					"the RSA private key and public key are not correctly configured, please check spring.sqltoy.securePrivateKey and securePublicKey!");
		}
		KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
		privateKey = (RSAPrivateKey) keyFactory.generatePrivate(getPrivateKeySpec(privateKeyStr));
		publicKey = (RSAPublicKey) keyFactory.generatePublic(getPublicKeySpec(publicKeyStr));
	}

	/**
	 * 获得公钥Key spec
	 * 
	 * @param keyStr
	 * @return
	 * @throws Exception
	 */
	private X509EncodedKeySpec getPublicKeySpec(String keyStr) throws Exception {
		byte[] keyBytes;
		if (keyStr.toLowerCase(Locale.ROOT).trim().startsWith("classpath:")) {
			String contents = FileUtil.readFileAsStr(keyStr, CHARSET);
			if (StringUtil.isBlank(contents)) {
				throw new Exception(
						"Failed to read the publicKey file content, please check whether the configuration file is compiled into the classes directory!");
			}
			// FileUtil读取时增加了\r\n,这里去除
			keyBytes = Base64.getDecoder().decode(contents.trim().replaceAll("\r|\n", ""));
		} else {
			keyBytes = Base64.getDecoder().decode(keyStr.trim());
		}
		return new X509EncodedKeySpec(keyBytes);
	}

	/**
	 * 获得私钥Key spec
	 * 
	 * @param keyStr
	 * @return
	 * @throws Exception
	 */
	private PKCS8EncodedKeySpec getPrivateKeySpec(String keyStr) throws Exception {
		byte[] keyBytes;
		if (keyStr.toLowerCase(Locale.ROOT).trim().startsWith("classpath:")) {
			String contents = FileUtil.readFileAsStr(keyStr, CHARSET);
			if (StringUtil.isBlank(contents)) {
				throw new Exception(
						"Failed to read the privateKey file content, please check whether the configuration file is compiled into the classes directory!");
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
		int contentLength = (contents == null) ? -1 : contents.length();
		try {
			// Cipher非线程安全,并发查询下共享实例doFinal会产生错乱密文,必须每次调用独立创建
			Cipher cipher = Cipher.getInstance(ALGORITHM_RSA);
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
			byte[] result = cipher.doFinal(contents.getBytes(CHARSET));
			return Base64.getEncoder().encodeToString(result);
		} catch (Exception e) {
			// update 2026-9-14 加密失败必须抛异常:原形态返回""会被直接落库,原文永久丢失且调用方无感
			// (明文超过RSA密钥明文上限时必然触发,如地址/备注类长字段),保存链路须失败以阻断写入
			// 明文内容属于安全字段,异常与日志只记录长度不可记录内容本身
			logger.error("rsa field encrypt failed(plain text length:{}), reason:{}", contentLength, e.getMessage(), e);
			throw new DataAccessException(
					"rsa field encrypt failed(plain text length:{}, rsa key plain text upper limit is about {} bytes), the operation is blocked to avoid the loss of the original data!",
					contentLength, getMaxPlainTextLength(), e.getMessage());
		}
	}

	@Override
	public String decrypt(String secureContents) {
		int cipherLength = (secureContents == null) ? -1 : secureContents.length();
		try {
			Cipher cipher = Cipher.getInstance(ALGORITHM_RSA);
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			byte[] result = cipher.doFinal(Base64.getDecoder().decode(secureContents));
			return new String(result, CHARSET);
		} catch (Exception e) {
			// update 2026-9-14 解密失败必须抛异常:原形态返回""使密文损坏、密钥不匹配、未加密的历史明文
			// 与"字段值本身为空"无法区分,查询结果静默丢值;同样只记录密文长度不记录内容
			logger.error("rsa field decrypt failed(cipher text length:{}), reason:{}", cipherLength, e.getMessage(), e);
			throw new DataAccessException(
					"rsa field decrypt failed(cipher text length:{}, reason:{}), please check whether the field was encrypted with the configured key, or it still holds plain text data!",
					cipherLength, e.getMessage());
		}
	}

	/**
	 * RSA(PKCS1 padding)单次可加密的明文长度上限=密钥字节数-11,用于加密失败时给出可操作的提示
	 * 
	 * @return 明文上限字节数,公钥未初始化时返回-1
	 */
	private int getMaxPlainTextLength() {
		RSAPublicKey key = publicKey;
		return (key == null) ? -1 : key.getModulus().bitLength() / 8 - 11;
	}

}
