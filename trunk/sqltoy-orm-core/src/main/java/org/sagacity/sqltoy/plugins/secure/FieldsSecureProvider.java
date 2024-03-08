package org.sagacity.sqltoy.plugins.secure;

/**
 * @project sagacity-sqltoy
 * @description sqltoy 字段加解密接口定义,sqltoy提供默认基于RSA非对称实现，同时提供开发者自行扩展
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-05
 */
public interface FieldsSecureProvider {

	/**
	 * @TODO 初始化
	 * @param charset
	 * @param privateKey
	 * @param publicKey
	 */
	public void initialize(String charset, String privateKey, String publicKey) throws Exception;

	/**
	 * @TODO 加密
	 * @param contents
	 * @return
	 */
	public String encrypt(String contents);

	/**
	 * @TODO 解密
	 * @param secureContents
	 * @return
	 */
	public String decrypt(String secureContents);
}
