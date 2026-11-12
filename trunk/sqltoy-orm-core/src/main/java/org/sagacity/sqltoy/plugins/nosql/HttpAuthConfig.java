package org.sagacity.sqltoy.plugins.nosql;

import java.io.Serializable;

import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description rest请求认证配置:basic(用户名密码基础认证,兼容既有)与jwt(token认证)两种模式,
 *              缓存翻译rest模式与rest更新检测(rest-checker\rest-increment-checker)共用,
 *              属性与sqltoy-translate.xsd中sqlToyTranslateRestAuth属性组一一对应
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-02
 */
public class HttpAuthConfig implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * basic认证用户名(亦作为jwt自签名的sub声明)
	 */
	private String username;

	/**
	 * basic认证密码(jwt登录模式下作为token-url的表单参数提交)
	 */
	private String password;

	/**
	 * 认证模式:basic(默认)、jwt(token认证),对应auth-type属性
	 */
	private String authType;

	/**
	 * jwt模式:获取token的登录地址,以表单方式提交username/password,对应token-url属性
	 */
	private String tokenUrl;

	/**
	 * jwt模式:登录响应json中token字段路径(支持data.token点路径),默认access_token,对应token-path属性
	 */
	private String tokenPath;

	/**
	 * jwt模式:token头值前缀,默认Bearer,设为none时头值仅放token本身,对应token-prefix属性
	 */
	private String tokenPrefix;

	/**
	 * jwt模式:携带token的http请求头名称,默认Authorization,对应token-header属性
	 */
	private String tokenHeader;

	/**
	 * jwt模式:token有效期(秒),响应含expires_in时以响应为准,默认1800,对应token-expire属性
	 */
	private int tokenExpire;

	/**
	 * jwt自签名模式:签名密钥(HS系列为共享密钥、RS系列为PKCS#8私钥),对应token-secure-key属性;
	 * 与token-url、authorization三选一,同时配置时按token-url>token-secure-key>authorization优先
	 */
	private String tokenSecureKey;

	/**
	 * jwt自签名模式:签名算法(HS256\HS384\HS512\RS256\RS384\RS512,默认HS256),对应sign-algorithm属性
	 */
	private String signAlgorithm;

	/**
	 * jwt模式:直接配置的静态token(长效token场景),对应authorization属性
	 */
	private String authorization;

	/**
	 * basic基础认证(兼容既有username/password模式)
	 *
	 * @param username
	 * @param password
	 * @return
	 */
	public static HttpAuthConfig basic(String username, String password) {
		return new HttpAuthConfig().username(username).password(password);
	}

	/**
	 * 是否jwt token认证模式
	 *
	 * @return
	 */
	public boolean isJwt() {
		return "jwt".equalsIgnoreCase(StringUtil.trim(authType));
	}

	/**
	 * @param username the username to set
	 * @return
	 */
	public HttpAuthConfig username(String username) {
		this.username = username;
		return this;
	}

	/**
	 * @param password the password to set
	 * @return
	 */
	public HttpAuthConfig password(String password) {
		this.password = password;
		return this;
	}

	/**
	 * @param authType the authType to set
	 * @return
	 */
	public HttpAuthConfig authType(String authType) {
		this.authType = authType;
		return this;
	}

	/**
	 * @param tokenUrl the tokenUrl to set
	 * @return
	 */
	public HttpAuthConfig tokenUrl(String tokenUrl) {
		this.tokenUrl = tokenUrl;
		return this;
	}

	/**
	 * @param tokenPath the tokenPath to set
	 * @return
	 */
	public HttpAuthConfig tokenPath(String tokenPath) {
		this.tokenPath = tokenPath;
		return this;
	}

	/**
	 * @param tokenPrefix the tokenPrefix to set
	 * @return
	 */
	public HttpAuthConfig tokenPrefix(String tokenPrefix) {
		this.tokenPrefix = tokenPrefix;
		return this;
	}

	/**
	 * @param tokenHeader the tokenHeader to set
	 * @return
	 */
	public HttpAuthConfig tokenHeader(String tokenHeader) {
		this.tokenHeader = tokenHeader;
		return this;
	}

	/**
	 * @param tokenExpire the tokenExpire to set
	 * @return
	 */
	public HttpAuthConfig tokenExpire(int tokenExpire) {
		this.tokenExpire = tokenExpire;
		return this;
	}

	/**
	 * @param tokenSecureKey the tokenSecureKey to set
	 * @return
	 */
	public HttpAuthConfig tokenSecureKey(String tokenSecureKey) {
		this.tokenSecureKey = tokenSecureKey;
		return this;
	}

	/**
	 * @param signAlgorithm the signAlgorithm to set
	 * @return
	 */
	public HttpAuthConfig signAlgorithm(String signAlgorithm) {
		this.signAlgorithm = signAlgorithm;
		return this;
	}

	/**
	 * @param authorization the authorization to set
	 * @return
	 */
	public HttpAuthConfig authorization(String authorization) {
		this.authorization = authorization;
		return this;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @return the authType
	 */
	public String getAuthType() {
		return authType;
	}

	/**
	 * @return the tokenUrl
	 */
	public String getTokenUrl() {
		return tokenUrl;
	}

	/**
	 * @return the tokenPath
	 */
	public String getTokenPath() {
		return tokenPath;
	}

	/**
	 * @return the tokenPrefix
	 */
	public String getTokenPrefix() {
		return tokenPrefix;
	}

	/**
	 * @return the tokenHeader
	 */
	public String getTokenHeader() {
		return tokenHeader;
	}

	/**
	 * @return the tokenExpire
	 */
	public int getTokenExpire() {
		return tokenExpire;
	}

	/**
	 * @return the tokenSecureKey
	 */
	public String getTokenSecureKey() {
		return tokenSecureKey;
	}

	/**
	 * @return the signAlgorithm
	 */
	public String getSignAlgorithm() {
		return signAlgorithm;
	}

	/**
	 * @return the authorization
	 */
	public String getAuthorization() {
		return authorization;
	}
}
