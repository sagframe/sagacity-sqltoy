package org.sagacity.sqltoy.plugins.nosql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.utils.XMLUtil;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.alibaba.fastjson2.JSONObject;

/**
 * jwt token认证单元测试:登录响应解析(token提取\有效期识别\点路径\异常提示)、
 * token头值组装(静态token\缺省前缀\自定义前缀\配置缺失校验)、
 * token-secure-key自签名(结构\claims\签名可验证\缓存复用\算法可选)及xml属性绑定
 */
public class JwtTokenManagerTest {

	@Test
	public void parseTokenResponseDefaultKeyAndExpires() {
		JwtTokenManager.TokenEntry entry = JwtTokenManager.parseTokenResponse(
				"{\"access_token\":\"token-1\",\"expires_in\":3600}", null, 1800);
		assertEquals("token-1", entry.token);
		long expect = System.currentTimeMillis() + 3600 * 1000L;
		// 解析耗时容忍1秒误差
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "expires_in应作为有效期");
	}

	@Test
	public void parseTokenResponseDotPathWithCustomPath() {
		// code/message包裹结构,token藏在data.token,有效期字段与token同级
		JwtTokenManager.TokenEntry entry = JwtTokenManager
				.parseTokenResponse("{\"code\":200,\"data\":{\"token\":\"token-2\",\"expires\":7200}}", "data.token",
						1800);
		assertEquals("token-2", entry.token);
		long expect = System.currentTimeMillis() + 7200 * 1000L;
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "data同级expires应作为有效期");
	}

	@Test
	public void parseTokenResponseExpireFromStringAndAbnormal() {
		// 数字字符串有效期合法
		JwtTokenManager.TokenEntry entry = JwtTokenManager
				.parseTokenResponse("{\"access_token\":\"t\",\"expires_in\":\"600\"}", null, 1800);
		long expect = System.currentTimeMillis() + 600 * 1000L;
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "数字字符串有效期应被识别");
		// 超过30天上限的异常值回落到缺省有效期
		entry = JwtTokenManager.parseTokenResponse("{\"access_token\":\"t\",\"expires_in\":999999999}", null, 1800);
		expect = System.currentTimeMillis() + 1800 * 1000L;
		assertTrue(Math.abs(entry.expireAtMillis - expect) < 1000, "异常超大有效期应回落缺省值");
	}

	@Test
	public void parseTokenResponseMissingTokenThrows() {
		assertThrows(IllegalArgumentException.class, () -> JwtTokenManager
				.parseTokenResponse("{\"code\":500,\"message\":\"login failed\"}", null, 1800));
		assertThrows(Exception.class,
				() -> JwtTokenManager.parseTokenResponse("{\"data\":{\"foo\":1}}", "data.token", 1800));
	}

	@Test
	public void resolveTokenValueWithStaticToken() throws Exception {
		// 静态token:缺省Bearer前缀
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").authorization("abc-token");
		assertTrue(authConfig.isJwt());
		assertEquals("Bearer abc-token", JwtTokenManager.resolveTokenValue(null, authConfig));
		// 自定义前缀
		authConfig = new HttpAuthConfig().authType("JWT").tokenPrefix("Token").authorization("abc-token");
		assertEquals("Token abc-token", JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void resolveCustomTokenHeaderAndNonePrefix() throws Exception {
		// 自定义携带token的请求头名称:缺省Authorization
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").authorization("abc-token");
		assertEquals("Authorization", JwtTokenManager.resolveTokenHeader(authConfig));
		// token-prefix=none:头值仅放token本身(适配自定义头+裸token的网关)
		authConfig = new HttpAuthConfig().authType("jwt").tokenPrefix("none").authorization("abc-token")
				.tokenHeader("Sag-Auth-Token");
		assertEquals("Sag-Auth-Token", JwtTokenManager.resolveTokenHeader(authConfig));
		assertEquals("abc-token", JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void resolveTokenValueMissingTokenSourceThrows() {
		// jwt模式token-url、token-secure-key、authorization均未配置,给出明确错误
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("u");
		assertThrows(IllegalArgumentException.class, () -> JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void selfSignWithSecureKeyVerifiable() throws Exception {
		// 自签名模式:本地生成HS256 jwt,无需登录接口
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("sqltoy").tokenExpire(600)
				.tokenSecureKey("my-secret");
		String token = JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length());
		// jwt三段式:header.payload.signature
		String[] parts = token.split("\\.");
		assertEquals(3, parts.length, "自签token应为三段式结构");
		// header
		String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
		assertEquals("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", header);
		// payload claims:sub=username,exp-iat=有效期
		JSONObject claims = JSONObject
				.parseObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
		assertEquals("sqltoy", claims.getString("sub"));
		assertEquals(600L, claims.getLong("exp") - claims.getLong("iat"));
		// 签名可验证:以同一密钥重算HMAC-SHA256应与签名段一致(服务端验签等价)
		String expectSign = recomputeHmac("HmacSHA256", "my-secret", parts[0] + "." + parts[1]);
		assertEquals(expectSign, parts[2], "自签token签名应可通过同密钥验签");
		// 有效期内缓存复用:两次获取返回同一token
		assertEquals(token, JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length()),
				"有效期内应复用缓存的token");
	}

	private static String recomputeHmac(String macAlgorithm, String secret, String signingInput) throws Exception {
		Mac mac = Mac.getInstance(macAlgorithm);
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm));
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void unsupportedSignAlgorithmThrows() {
		// 未支持的算法给出明确错误并列出支持清单
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").tokenSecureKey("k").signAlgorithm("ES256");
		assertThrows(IllegalArgumentException.class, () -> JwtTokenManager.resolveTokenValue(null, authConfig));
	}

	@Test
	public void selfSignWithSelectableHsAlgorithm() throws Exception {
		// HS384:头部alg声明正确,签名可通过同算法独立重算验签
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("sqltoy").tokenExpire(600)
				.tokenSecureKey("my-secret-384").signAlgorithm("hs384");
		String token = JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length());
		String[] parts = token.split("\\.");
		assertEquals(3, parts.length);
		assertEquals("{\"alg\":\"HS384\",\"typ\":\"JWT\"}",
				new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
		assertEquals(recomputeHmac("HmacSHA384", "my-secret-384", parts[0] + "." + parts[1]), parts[2],
				"HS384签名应可通过同密钥独立重算验证");
	}

	@Test
	public void selfSignWithRs256VerifiedByPublicKey() throws Exception {
		// 运行期生成RSA密钥对:私钥(PEM格式)参与自签,公钥独立验签(等价服务端验签)
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(2048);
		KeyPair keyPair = keyGen.generateKeyPair();
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				.concat(Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded()))
				.concat("\n-----END PRIVATE KEY-----");
		HttpAuthConfig authConfig = new HttpAuthConfig().authType("jwt").username("sqltoy").tokenExpire(600)
				.tokenSecureKey(pem).signAlgorithm("RS256");
		String token = JwtTokenManager.resolveTokenValue(null, authConfig).substring("Bearer ".length());
		String[] parts = token.split("\\.");
		assertEquals("{\"alg\":\"RS256\",\"typ\":\"JWT\"}",
				new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
		Signature verifier = Signature.getInstance("SHA256withRSA");
		verifier.initVerify(keyPair.getPublic());
		verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
		assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])), "RS256签名应可通过公钥验签");
		// 裸base64密钥体(无PEM头尾)同样可解析
		assertTrue(JwtTokenManager.parsePkcs8PrivateKey(
				Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())) != null);
	}

	@Test
	public void xmlAttributesBindToCheckerConfig() throws Exception {
		// 验证xsd新增属性经XMLUtil.setAttributes(kebab-case转驼峰)正确绑定到检测器模型
		String xml = "<rest-checker url=\"http://a/b\" auth-type=\"jwt\" token-url=\"http://a/login\""
				+ " token-path=\"Sag-Auth-Token\" token-prefix=\"Token\" token-expire=\"600\" token-secure-key=\"sk-123\""
				+ " token-header=\"Sag-Auth-Token\" sign-algorithm=\"RS256\" authorization=\"static-token\"/>";
		Element elt = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new InputSource(new StringReader(xml))).getDocumentElement();
		CheckerConfigModel model = new CheckerConfigModel();
		XMLUtil.setAttributes(elt, model);
		assertEquals("http://a/b", model.getUrl());
		assertEquals("jwt", model.getAuthType());
		assertEquals("http://a/login", model.getTokenUrl());
		assertEquals("Sag-Auth-Token", model.getTokenPath());
		assertEquals("Token", model.getTokenPrefix());
		assertEquals(600, model.getTokenExpire());
		assertEquals("sk-123", model.getTokenSecureKey());
		assertEquals("Sag-Auth-Token", model.getTokenHeader());
		assertEquals("RS256", model.getSignAlgorithm());
		assertEquals("static-token", model.getAuthorization());
		HttpAuthConfig authConfig = new HttpAuthConfig().authType(model.getAuthType()).username(model.getUsername())
				.password(model.getPassword()).tokenUrl(model.getTokenUrl()).tokenPath(model.getTokenPath())
				.tokenPrefix(model.getTokenPrefix()).tokenHeader(model.getTokenHeader())
				.tokenExpire(model.getTokenExpire()).tokenSecureKey(model.getTokenSecureKey())
				.signAlgorithm(model.getSignAlgorithm()).authorization(model.getAuthorization());
		assertTrue(authConfig.isJwt());
		assertEquals("RS256", JwtTokenManager.resolveSignAlgorithm(authConfig));
	}
}
