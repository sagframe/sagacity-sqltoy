package org.sagacity.sqltoy.plugins.secure;

/**
 * @project sagacity-sqltoy
 * @description sqltoy 字段加解密接口定义,sqltoy提供默认基于RSA非对称实现，同时提供开发者自行扩展
 * @author zhongxuchen
 * @version v1.0,Date:2021-11-05
 */
public interface FieldsSecureProvider {

	/**
	 * 初始化
	 * 
	 * @param charset
	 * @param privateKey
	 * @param publicKey
	 */
	public void initialize(String charset, String privateKey, String publicKey) throws Exception;

	/**
	 * 加密
	 * 
	 * @param contents 明文
	 * @return 密文(加密失败必须抛出运行时异常,不可返回空串——空串会被直接落库导致原文永久丢失)
	 */
	public String encrypt(String contents);

	/**
	 * 解密
	 * 
	 * @param secureContents 密文
	 * @return 明文(解密失败必须抛出运行时异常,不可返回空串——空串与"字段值本身为空"无法区分)
	 */
	public String decrypt(String secureContents);
}
