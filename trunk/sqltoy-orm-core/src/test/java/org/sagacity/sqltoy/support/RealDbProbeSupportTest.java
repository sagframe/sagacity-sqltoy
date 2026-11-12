package org.sagacity.sqltoy.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * 鍥炲綊娴嬭瘯:鐪熷簱鎺㈤拡鏀拺鐨勫垽瀹氶€昏緫(涓嶄緷璧栦换浣曞鍣?
 *
 * <p>
 * 鐩爣:鏃犲鍣ㄧ幆澧冧笅鐪熷簱鐢ㄤ緥"璺宠繃鑰岄潪鎶ラ敊";鍚屾椂涓嶅緱鎶婄湡瀹濻QL澶辫触璇垽涓?鐜涓嶅彲杈?鑰屾帺鐩栭棶棰樸€? */
public class RealDbProbeSupportTest {

	@Test
	public void missingProbeReturnsNull() {
		assertNull(RealDbProbeSupport.load("no-such-db-prefix"));
	}

	@Test
	public void unreachableUrlDetected() {
		// 鏈洃鍚鍙?蹇€熷垽瀹氫笉鍙揪(閬垮厤鏃犲鍣ㄦ椂闀挎椂闂寸瓑寰呰繛鎺ヨ秴鏃?
		// Port must stay below the Windows ephemeral range (49152-65535): a high port like 59999
		// can be picked as the SOURCE port of an outgoing connect, causing a TCP self-connect that
		// makes isReachable() wrongly return true (flaky). 15999 is unlistened and outside that range.
		assertFalse(RealDbProbeSupport.isReachable("jdbc:postgresql://localhost:15999/postgres"));
		// oracle thin 褰㈡€佸悓鏍疯鑳借В鏋?host:port
		assertFalse(RealDbProbeSupport.isReachable("jdbc:oracle:thin:@//localhost:15999/FREEPDB1"));
		assertFalse(RealDbProbeSupport.isReachable("jdbc:db2://localhost:15999/DB"));
		assertFalse(RealDbProbeSupport.isReachable(null));
	}

	@Test
	public void assumeFullMatrixSkipsByDefaultAndFailsInStrictMode() {
		String[][] probes = { { "pg", "org.postgresql.Driver", "jdbc:postgresql://localhost:15998/x" } };
		assertThrows(TestAbortedException.class, () -> RealDbProbeSupport.assumeFullMatrix(probes));
		// strict 妯″紡涓嬬己搴撳繀椤诲け璐?渚夸簬CI寮哄埗瀹屾暣鐭╅樀
		System.setProperty("sqltoy.realdb.strict", "true");
		try {
			assertThrows(AssertionError.class, () -> RealDbProbeSupport.assumeFullMatrix(probes));
		} finally {
			System.clearProperty("sqltoy.realdb.strict");
		}
	}

}
