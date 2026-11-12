package org.sagacity.sqltoy.support;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;

/**
 * @description 真库探针支撑:统一"探针配置读取/连接/可达性探测/不可达判定",让真库用例在无容器环境下
 *              <b>整类跳过</b>而不是报错,有容器时照常全量执行
 *
 * <p>
 * 探针配置约定(不入库):{@code target/<db>-probe.properties},键为 url/username/password。
 * 本地起容器后由 {@code docker/probe-properties/} 模板复制生成,详见 {@code docker/README.md}。
 *
 * <p>
 * 用法:
 * <ul>
 * <li>单库用例:{@link #load(String)} 读探针 + {@link #isReachable(String)} 判定可达,不可达即 abort
 * (clickhouse-jdbc 等懒连接驱动必须用 TCP 探测:getConnection 阶段不会报错,执行SQL时才抛连接异常)</li>
 * <li>全库矩阵用例:{@link #assumeFullMatrix(String[][])} 在 @BeforeAll 探测所有库,缺库即整类跳过
 * (避免把"缺库"误报成用例失败);CI 需要"缺库即失败"时加 -Dsqltoy.realdb.strict=true</li>
 * </ul>
 */
public final class RealDbProbeSupport {

	/** 探针配置目录(与各用例既有约定一致) */
	public static final String PROBE_DIR = "target";

	/** 单个端口的TCP探测超时:足够覆盖本机容器,又不会让"无容器"场景等待过久 */
	private static final int TCP_TIMEOUT_MS = 800;

	/**
	 * 从JDBC URL中提取 host:port(兼容 jdbc:xxx://h:p/... 与 oracle thin @//h:p/service);
	 * update 2026-11-12 兼容IPv6字面量([::1]:port,kingbase8驱动仅IPv6可达时verify工程用此形态):
	 * 方括号内的::含:不会被普通host字符类匹配,须单独分组
	 */
	private static final Pattern HOST_PORT_PATTERN = Pattern
			.compile("(?:@//|://)(?:\\[([^\\]]+)\\]|([^/:;?]+)):(\\d+)");

	private RealDbProbeSupport() {
	}

	/**
	 * @param dbPrefix 探针前缀(如 mysql/pg/db2/oracle/clickhouse/sqlserver)
	 * @return 探针配置;文件不存在返回null
	 */
	public static Properties load(String dbPrefix) {
		File cfg = new File(PROBE_DIR + File.separator + dbPrefix + "-probe.properties");
		if (!cfg.exists()) {
			return null;
		}
		Properties properties = new Properties();
		try (InputStream in = new FileInputStream(cfg)) {
			properties.load(in);
		} catch (Exception e) {
			return null;
		}
		return properties;
	}

	/**
	 * @param jdbcUrl JDBC连接串
	 * @return 目标 host:port 是否可建立TCP连接
	 */
	public static boolean isReachable(String jdbcUrl) {
		if (jdbcUrl == null) {
			return false;
		}
		Matcher matcher = HOST_PORT_PATTERN.matcher(jdbcUrl);
		if (!matcher.find()) {
			return false;
		}
		// update 2026-11-12 兼容IPv6:group(1)=[::1]方括号形态,group(2)=普通host,group(3)=port
		String host = (matcher.group(1) != null) ? matcher.group(1) : matcher.group(2);
		int port = Integer.parseInt(matcher.group(3));
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), TCP_TIMEOUT_MS);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * @description 全库矩阵用例的守卫:这类用例断言"所有库全绿",缺库时继续跑只会把缺库误报成失败,
	 *              因此除非矩阵完整(或显式要求strict)否则整类跳过
	 *
	 * <p>
	 * 单库/多库冒烟不适用本方法:它们用 {@link #isReachable(String)} 逐库判定并跳过,缺哪个跳过哪个;
	 * 本方法只给"必须全绿"的矩阵sweep用。需CI强制完整矩阵时加 {@code -Dsqltoy.realdb.strict=true}。
	 *
	 * @param probes 探针数组,每项至少含[0]=名称、[2]=JDBC url
	 */
	public static void assumeFullMatrix(String[][] probes) {
		List<String> unreachable = new ArrayList<String>();
		for (String[] probe : probes) {
			if (probe == null || probe.length < 3) {
				continue;
			}
			if (!isReachable(probe[2])) {
				unreachable.add(probe[0]);
			}
		}
		if (unreachable.isEmpty()) {
			return;
		}
		if (Boolean.parseBoolean(System.getProperty("sqltoy.realdb.strict", "false"))) {
			throw new AssertionError("real DB matrix incomplete (sqltoy.realdb.strict=true): " + unreachable);
		}
		Assumptions.abort("real DB matrix incomplete, skipped; unreachable=" + unreachable
				+ " (start the missing containers via docker/docker-compose.yml, or run with "
				+ "-Dsqltoy.realdb.strict=true to fail instead of skip)");
	}
}
