package org.sagacity.sqltoy.plugins.nosql;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSONObject;

/**
 * @project sagacity-sqltoy
 * @description jwt token认证管理器:负责rest请求token的获取、缓存、过期主动刷新与401失效重试,
 *              支持三种token来源:token-url登录获取(动态token,表单提交username/password)、
 *              token-secure-key本地自签(HS256\HS384\HS512\RS256\RS384\RS512可选算法,无需登录接口)、
 *              authorization直接配置(静态token)
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-02
 */
public class JwtTokenManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(JwtTokenManager.class);

	/**
	 * token临近过期的提前刷新时间(毫秒),规避token临界有效被服务端拒绝的场景
	 */
	private final static long REFRESH_AHEAD_MILLIS = 60 * 1000L;

	/**
	 * 登录响应json中token的缺省字段路径
	 */
	private final static String DEFAULT_TOKEN_PATH = "access_token";

	/**
	 * Authorization请求头缺省前缀
	 */
	private final static String DEFAULT_TOKEN_PREFIX = "Bearer";

	/**
	 * 携带token的http请求头缺省名称
	 */
	private final static String DEFAULT_TOKEN_HEADER = "Authorization";

	/**
	 * token缺省有效期(秒)
	 */
	private final static int DEFAULT_EXPIRE_SECONDS = 1800;

	/**
	 * token有效期上限(秒,30天),规避响应中异常大数值导致token长期不刷新
	 */
	private final static long MAX_EXPIRE_SECONDS = 30 * 24 * 3600L;

	/**
	 * 自签名支持的算法:HS256\HS384\HS512(HMAC对称,token-secure-key为共享密钥)与
	 * RS256\RS384\RS512(RSA非对称,token-secure-key为PKCS#8格式私钥)
	 */
	private final static String[] SUPPORTED_ALGORITHMS = { "HS256", "HS384", "HS512", "RS256", "RS384", "RS512" };

	/**
	 * 缺省签名算法
	 */
	private final static String DEFAULT_SIGN_ALGORITHM = "HS256";

	/**
	 * 响应中表示token有效期的常用字段(单位秒),按顺序取首个存在且合法的值
	 */
	private final static String[] EXPIRE_KEYS = { "expires_in", "expire_in", "expires", "expire" };

	/**
	 * token缓存:key=tokenUrl|username(静态token不走缓存)
	 */
	private final static ConcurrentHashMap<String, TokenEntry> TOKEN_CACHE = new ConcurrentHashMap<String, TokenEntry>();

	private JwtTokenManager() {
	}

	/**
	 * 解析token头值:三种token来源按token-url(登录获取)>token-secure-key(本地自签)>
	 * authorization(静态token)优先;动态获取与自签均优先取缓存,缓存缺失或进入提前刷新窗口时
	 * 重新获取/重新生成(同步锁避免多检测线程并发重复处理);头值形态由token-prefix决定
	 * (默认Bearer,none时仅token本身),头名称由token-header决定(参见resolveTokenHeader)
	 *
	 * @param sqltoyContext
	 * @param authConfig    认证配置
	 * @return 形如:Bearer xxx-token 的token头值
	 * @throws Exception
	 */
	public static String resolveTokenValue(SqlToyContext sqltoyContext, HttpAuthConfig authConfig)
			throws Exception {
		String tokenPrefix = resolveTokenPrefix(authConfig);
		// 三种token来源均未配置,给出明确错误
		if (StringUtil.isBlank(authConfig.getTokenUrl()) && StringUtil.isBlank(authConfig.getTokenSecureKey())
				&& StringUtil.isBlank(authConfig.getAuthorization())) {
			throw new IllegalArgumentException(
					"jwt auth mode must config one of token-url, token-secure-key, authorization, please check the cache translate rest config!");
		}
		// 静态token模式:authorization直接配置,无需缓存与刷新
		if (StringUtil.isBlank(authConfig.getTokenUrl()) && StringUtil.isBlank(authConfig.getTokenSecureKey())) {
			return wrapTokenValue(tokenPrefix, authConfig.getAuthorization().trim());
		}
		String cacheKey = cacheKey(authConfig);
		TokenEntry entry = TOKEN_CACHE.get(cacheKey);
		if (entry == null || entry.expireAtMillis - REFRESH_AHEAD_MILLIS <= System.currentTimeMillis()) {
			synchronized (JwtTokenManager.class) {
				entry = TOKEN_CACHE.get(cacheKey);
				// 双重检查:仅当仍缺失或临近过期时重新获取
				if (entry == null || entry.expireAtMillis - REFRESH_AHEAD_MILLIS <= System.currentTimeMillis()) {
					// token-url登录获取优先,其次token-secure-key本地自签
					entry = StringUtil.isNotBlank(authConfig.getTokenUrl()) ? fetchToken(sqltoyContext, authConfig)
							: createTokenBySecureKey(authConfig);
					TOKEN_CACHE.put(cacheKey, entry);
				}
			}
		}
		return wrapTokenValue(tokenPrefix, entry.token);
	}

	/**
	 * 解析携带token的http请求头名称:token-header缺省Authorization(如Sag-Auth-Token)
	 * 
	 * @param authConfig 认证配置
	 * @return
	 */
	public static String resolveTokenHeader(HttpAuthConfig authConfig) {
		return StringUtil.isBlank(authConfig.getTokenHeader()) ? DEFAULT_TOKEN_HEADER
				: authConfig.getTokenHeader().trim();
	}

	/**
	 * 解析token头值前缀:token-prefix缺省Bearer,配置为none时头值仅放token本身
	 * 
	 * @param authConfig 认证配置
	 * @return 空串表示无前缀
	 */
	private static String resolveTokenPrefix(HttpAuthConfig authConfig) {
		String tokenPrefix = StringUtil.isBlank(authConfig.getTokenPrefix()) ? DEFAULT_TOKEN_PREFIX
				: authConfig.getTokenPrefix().trim();
		return "none".equalsIgnoreCase(tokenPrefix) ? "" : tokenPrefix;
	}

	/**
	 * 组装token头值:无前缀时仅token本身,否则为{prefix} {token}
	 * 
	 * @param tokenPrefix token前缀(空串表示无)
	 * @param token       token值
	 * @return
	 */
	private static String wrapTokenValue(String tokenPrefix, String token) {
		return tokenPrefix.isEmpty() ? token : tokenPrefix.concat(" ").concat(token);
	}

	/**
	 * 强制失效缓存的token(服务端返回401时调用,触发下次请求重新登录获取)
	 * 
	 * @param authConfig 认证配置
	 */
	public static void invalidate(HttpAuthConfig authConfig) {
		if (authConfig == null || StringUtil.isBlank(authConfig.getTokenUrl())) {
			return;
		}
		TOKEN_CACHE.remove(cacheKey(authConfig));
	}

	/**
	 * 调用登录地址获取token:以表单方式提交username/password,响应json按tokenKey路径提取,
	 * 有效期优先取响应中的expires_in等字段(单位秒),否则采用配置或缺省值
	 * 
	 * @param sqltoyContext
	 * @param authConfig    认证配置
	 * @return
	 * @throws Exception
	 */
	private static TokenEntry fetchToken(SqlToyContext sqltoyContext, HttpAuthConfig authConfig) throws Exception {
		String username = authConfig.getUsername();
		String password = authConfig.getPassword();
		// 登录请求本身不做认证(避免token递归获取),用户名密码作为表单参数提交
		String json = HttpClientUtils.doPost(sqltoyContext, authConfig.getTokenUrl().trim(),
				HttpAuthConfig.basic(null, null), new String[] { "username", "password" },
				new String[] { username, password });
		if (StringUtil.isBlank(json)) {
			throw new IllegalArgumentException("jwt token fetch failed, the response of token-url:"
					.concat(authConfig.getTokenUrl()).concat(" is blank, please check the config!"));
		}
		int defaultExpire = authConfig.getTokenExpire() > 0 ? authConfig.getTokenExpire() : DEFAULT_EXPIRE_SECONDS;
		TokenEntry entry = parseTokenResponse(json, authConfig.getTokenPath(), defaultExpire);
		if (sqltoyContext != null && sqltoyContext.isDebug()) {
			logger.debug("jwt token fetched from url:{}, expire seconds:{}", authConfig.getTokenUrl(),
					(entry.expireAtMillis - System.currentTimeMillis()) / 1000);
		}
		return entry;
	}

	/**
	 * 解析登录响应:按tokenPath点路径(如data.token)提取token,并从token同级对象中识别有效期字段
	 *
	 * @param json                 登录响应json字符串
	 * @param tokenPath            token字段路径,空白时按access_token
	 * @param defaultExpireSeconds 缺省有效期(秒)
	 * @return
	 */
	static TokenEntry parseTokenResponse(String json, String tokenPath, int defaultExpireSeconds) {
		String realKey = StringUtil.isBlank(tokenPath) ? DEFAULT_TOKEN_PATH : tokenPath.trim();
		JSONObject parent = JSONObject.parseObject(json);
		Object token = parent;
		// 点路径逐级下探(如data.token),parent记录token的直接容器用于识别有效期字段
		String[] paths = realKey.split("\\.");
		for (int i = 0; i < paths.length; i++) {
			if (!(token instanceof JSONObject)) {
				token = null;
				break;
			}
			parent = (JSONObject) token;
			token = parent.get(paths[i].trim());
		}
		if (token == null || StringUtil.isBlank(token.toString())) {
			throw new IllegalArgumentException(
					"jwt token parse failed, cannot extract token by key:[".concat(realKey)
							.concat("] from token-url response, please check token-path config! response:")
							.concat(json.length() > 200 ? json.substring(0, 200) : json));
		}
		// 有效期:响应优先(单位秒),限定在(0,30天]内规避异常值
		long expireSeconds = defaultExpireSeconds;
		Object expireObj = null;
		for (String key : EXPIRE_KEYS) {
			if (parent.containsKey(key)) {
				expireObj = parent.get(key);
				break;
			}
		}
		if (expireObj != null) {
			try {
				long value = Long.parseLong(StringUtil.trim(expireObj.toString()));
				if (value > 0 && value <= MAX_EXPIRE_SECONDS) {
					expireSeconds = value;
				}
			} catch (NumberFormatException ignore) {
				// 非数值形态(如true/描述文本)保持缺省有效期
			}
		}
		return new TokenEntry(token.toString().trim(), System.currentTimeMillis() + expireSeconds * 1000L);
	}

	/**
	 * 基于配置密钥本地生成签名jwt token(自签名模式,无需登录接口,服务端以同一密钥或公钥验签),
	 * claims包含:sub(配置的username)、iat、exp(按token有效期),签名算法由sign-algorithm选择
	 * 
	 * @param authConfig 认证配置
	 * @return
	 */
	private static TokenEntry createTokenBySecureKey(HttpAuthConfig authConfig) {
		long now = System.currentTimeMillis();
		int expireSeconds = authConfig.getTokenExpire() > 0 ? authConfig.getTokenExpire() : DEFAULT_EXPIRE_SECONDS;
		String algorithm = resolveSignAlgorithm(authConfig);
		JSONObject claims = new JSONObject();
		if (StringUtil.isNotBlank(authConfig.getUsername())) {
			claims.put("sub", authConfig.getUsername().trim());
		}
		claims.put("iat", now / 1000);
		claims.put("exp", (now + expireSeconds * 1000L) / 1000);
		String token = signToken(algorithm, claims.toJSONString(), authConfig.getTokenSecureKey().trim());
		return new TokenEntry(token, now + expireSeconds * 1000L);
	}

	/**
	 * 解析签名算法:缺省HS256,大小写不敏感,未支持的算法给出明确错误
	 * 
	 * @param authConfig 认证配置
	 * @return HS256\HS384\HS512\RS256\RS384\RS512
	 */
	static String resolveSignAlgorithm(HttpAuthConfig authConfig) {
		String algorithm = StringUtil.isBlank(authConfig.getSignAlgorithm()) ? DEFAULT_SIGN_ALGORITHM
				: authConfig.getSignAlgorithm().trim().toUpperCase(Locale.ROOT);
		for (String supported : SUPPORTED_ALGORITHMS) {
			if (supported.equals(algorithm)) {
				return algorithm;
			}
		}
		throw new IllegalArgumentException("jwt sign algorithm:[".concat(algorithm)
				.concat("] is not supported, supported algorithms are: HS256, HS384, HS512, RS256, RS384, RS512!"));
	}

	/**
	 * 按指定算法生成签名的jwt token:base64url(header).base64url(payload).base64url(签名);
	 * HS256\HS384\HS512为HMAC对称签名(token-secure-key为共享密钥),
	 * RS256\RS384\RS512为RSA非对称签名(token-secure-key为PKCS#8格式私钥,服务端以公钥验签)
	 * 
	 * @param algorithm  签名算法
	 * @param payloadJson jwt载体json(claims)
	 * @param secureKey   共享密钥或PKCS#8私钥(PEM文本或裸base64密钥体)
	 * @return
	 */
	static String signToken(String algorithm, String payloadJson, String secureKey) {
		try {
			String headerJson = "{\"alg\":\"".concat(algorithm).concat("\",\"typ\":\"JWT\"}");
			String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8)).concat(".")
					.concat(base64Url(payloadJson.getBytes(StandardCharsets.UTF_8)));
			byte[] signature;
			if (algorithm.startsWith("HS")) {
				// HS256\HS384\HS512 对应 JCA 算法名 HmacSHA{256,384,512}
				String macAlgorithm = "HmacSHA".concat(algorithm.substring(2));
				Mac mac = Mac.getInstance(macAlgorithm);
				mac.init(new SecretKeySpec(secureKey.getBytes(StandardCharsets.UTF_8), macAlgorithm));
				signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
			} else {
				// RS256\RS384\RS512 对应 SHA{256,384,512}withRSA
				Signature signatureInstance = Signature
						.getInstance("SHA".concat(algorithm.substring(2, 5)).concat("withRSA"));
				signatureInstance.initSign(parsePkcs8PrivateKey(secureKey));
				signatureInstance.update(signingInput.getBytes(StandardCharsets.UTF_8));
				signature = signatureInstance.sign();
			}
			return signingInput.concat(".").concat(base64Url(signature));
		} catch (Exception e) {
			throw new IllegalArgumentException("jwt token sign failed with algorithm:".concat(algorithm)
					.concat(" and token-secure-key, error message:").concat(e.getMessage()));
		}
	}

	/**
	 * 解析PKCS#8格式RSA私钥:支持PEM文本(-----BEGIN PRIVATE KEY-----)或剥离头尾后的裸base64密钥体
	 * 
	 * @param keyText 私钥文本
	 * @return
	 * @throws Exception
	 */
	static PrivateKey parsePkcs8PrivateKey(String keyText) throws Exception {
		String base64Body = keyText.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
		return KeyFactory.getInstance("RSA")
				.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64Body)));
	}

	/**
	 * token缓存key:token-url模式为tokenUrl|username;自签名模式为self|密钥摘要|username(缓存key不落原始密钥)
	 * 
	 * @param authConfig
	 * @return
	 */
	private static String cacheKey(HttpAuthConfig authConfig) {
		String username = StringUtil.isBlank(authConfig.getUsername()) ? "" : authConfig.getUsername().trim();
		if (StringUtil.isBlank(authConfig.getTokenUrl())) {
			return "self|".concat(sha256Hex(authConfig.getTokenSecureKey().trim())).concat("|").concat(username);
		}
		return authConfig.getTokenUrl().trim().concat("|").concat(username);
	}

	/**
	 * sha256摘要的十六进制表示(JDK必备算法,缺失时退化为hashCode)
	 * 
	 * @param value
	 * @return
	 */
	private static String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (int i = 0; i < digest.length; i++) {
				hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
				hex.append(Character.forDigit(digest[i] & 0xF, 16));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			return String.valueOf(value.hashCode());
		}
	}

	/**
	 * base64url编码(无填充,jwt标准形态)
	 * 
	 * @param data
	 * @return
	 */
	private static String base64Url(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	/**
	 * token缓存条目
	 */
	static class TokenEntry {
		final String token;
		final long expireAtMillis;

		TokenEntry(String token, long expireAtMillis) {
			this.token = token;
			this.expireAtMillis = expireAtMillis;
		}
	}
}
