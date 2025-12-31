package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.CurrentTimeMaxValue;
import org.sagacity.sqltoy.integration.DistributeIdGenerator;
import org.sagacity.sqltoy.model.IgnoreKeyCaseMap;
import org.sagacity.sqltoy.plugins.id.macro.MacroUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 封装各种生成唯一性ID算法的工具类
 * @author zhongxuchen
 * @version v1.0,Date:2012-4-7
 */
public class IdUtil {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(IdUtil.class);

	// 纳秒id的ip截取位数
	private static final int NANOTIME_IP_SUBSIZE = 3;

	/**
	 * 安全服务器ID
	 */
	private static String secureServerId = getLastIp(NANOTIME_IP_SUBSIZE);

	private static final String SQLTOY_ID = "SQLTOY_IDENTITY_8";
	private static final String SQLTOY_ID_SHORT = "SQLTOY_IDENTITY_6";

	// 根据表名存放当前毫秒对应的计数值，毫秒变化就重新计数
	private static ConcurrentHashMap<String, CurrentTimeMaxValue> tablesCurrentTimeId = new ConcurrentHashMap<String, CurrentTimeMaxValue>();

	private IdUtil() {

	}

	/**
	 * update 2025-12-24 改为uuidv7版本
	 */
	public static String getUUID() {
		// return UUID.randomUUID().toString().replace("-", "");
		return StandardUUIDv7Generator.generateString().replace("-", "");
	}

	/**
	 * @todo 获取22位有序安全ID,格式:13位当前毫秒+6位计数值+3位主机ID 目前情况下任何一次提取纳秒时间都不会一样
	 * @param workerId
	 * @return
	 */
	public static BigDecimal getShortNanoTimeId(String workerId) {
		return getShortNanoTimeId(SQLTOY_ID_SHORT, workerId);
	}

	public static BigDecimal getShortNanoTimeId(String identityName, String workerId) {
		String realIdentityName = StringUtil.isBlank(identityName) ? SQLTOY_ID_SHORT : identityName;
		long[] currentValue = getCurrentValue(realIdentityName, 999999);
		// 13位
		String nowTimeStr = StringUtil.addRightZero2Len("" + currentValue[0], 13);
		// 6位
		String currentId = StringUtil.addLeftZero2Len("" + (currentValue[1] % 1000000), 6);
		// 3位主机标识
		String serverId = StringUtil.addLeftZero2Len((workerId == null) ? secureServerId : workerId, 3);
		// 总计22位
		return new BigDecimal(nowTimeStr.concat(currentId).concat(serverId));
	}

	public static BigDecimal getNanoTimeId(String workerId) {
		return getNanoTimeId(SQLTOY_ID, workerId);
	}

	/**
	 * @todo 获取26位有序安全ID,格式:15位:yyMMddHHmmssSSS+8位计数+3位主机ID
	 * @param identityName 一般用表名
	 * @param workerId
	 * @return
	 */
	public static BigDecimal getNanoTimeId(String identityName, String workerId) {
		String realIdentityName = StringUtil.isBlank(identityName) ? SQLTOY_ID : identityName;
		long[] currentValue = getCurrentValue(realIdentityName, 99999999);
		DateFormat df = new SimpleDateFormat("yyMMddHHmmssSSS");
		// 15位
		String nowTimeStr = df.format(new Date(currentValue[0]));
		// 8位
		String currentId = StringUtil.addLeftZero2Len("" + (currentValue[1] % 100000000), 8);
		// 3位主机ID,根据IP提取,默认提取IPv4的后3位
		String serverId = StringUtil.addLeftZero2Len((workerId == null) ? secureServerId : workerId, 3);
		// 总计26位
		return new BigDecimal(nowTimeStr.concat(currentId).concat(serverId));
	}

	/**
	 * @TODO 改用并发map根据表名称存放当前毫秒对应的计数值
	 * @param identityName
	 * @param maxValue
	 * @return
	 */
	private static long[] getCurrentValue(String identityName, int maxValue) {
		synchronized (identityName.intern()) {
			long currentTime = System.currentTimeMillis();
			CurrentTimeMaxValue currentValue = tablesCurrentTimeId.get(identityName);
			// 首次获取，从1开始计数
			if (null == currentValue) {
				currentValue = new CurrentTimeMaxValue(currentTime, 1);
				tablesCurrentTimeId.put(identityName, currentValue);
			} // 当前时间大于上次提取maxValue的时间，则从新计数
			else if (currentTime > currentValue.getCurrentTime()) {
				currentValue.setCurrentTime(currentTime);
				currentValue.setValue(1);
			} // currentTime == currentValue.getCurrentTime()
				// 超出阀值，从下一个毫秒重新计数
			else if (currentValue.getValue() >= maxValue) {
				// 延时1毫秒
				try {
					Thread.sleep(1);
				} catch (Exception e) {

				}
				currentValue.setCurrentTime(System.currentTimeMillis());
				currentValue.setValue(1);
			} else {
				currentValue.setValue(currentValue.getValue() + 1);
			}
			return new long[] { currentValue.getCurrentTime(), currentValue.getValue() };
		}
	}

	/**
	 * @TODO 获取debug ID,只需保障单机当天唯一，主要帮助日志分组
	 * @return
	 */
	public static String getDebugId() {
		// 当前时间(秒)
		String nowTime = DateUtil.formatDate(new Date(), "HH:mm:ss");
		// 后7位纳秒间隔
		String nanoTime = "" + System.nanoTime();
		int length = nanoTime.length();
		if (nanoTime.endsWith("00")) {
			nanoTime = nanoTime.substring(length - 9, length - 2);
		} else {
			nanoTime = nanoTime.substring(length - 7);
		}
		return nowTime.concat(".").concat(nanoTime);
	}

	/**
	 * @todo 获取本机IP地址
	 * @param hasHostName
	 * @param hasIPV6
	 * @return
	 */
	public static List<String> getLocalAddress(boolean hasHostName, boolean hasIPV6) {
		List<String> result = new ArrayList<String>();
		try {
			Enumeration<NetworkInterface> netInterface = NetworkInterface.getNetworkInterfaces();
			NetworkInterface ni;
			InetAddress ip = null;
			Enumeration<InetAddress> netCards;
			while (netInterface.hasMoreElements()) {
				ni = (NetworkInterface) netInterface.nextElement();
				netCards = ni.getInetAddresses();
				while (netCards.hasMoreElements()) {
					ip = (InetAddress) netCards.nextElement();
					if (!ip.isLoopbackAddress() && (hasIPV6 ? true : ip.getHostAddress().indexOf(":") == -1)) {
						if (hasHostName && !result.contains(ip.getHostName())) {
							result.add(ip.getHostName());
						}
						result.add(ip.getHostAddress());
					}
				}
			}
		} catch (Exception e) {
			logger.error("根据ip产生id所依赖的serverId异常，无法获得ip信息:" + e.getMessage());
		}
		return result;
	}

	/**
	 * @todo 获取本机的IP地址，并从末尾截取指定长度的数字
	 * @param size
	 * @return
	 */
	public static String getLastIp(int size) {
		// 默认取ipv4地址
		List<String> ipaddress = getLocalAddress(false, false);
		boolean ipv6 = false;
		// 取ipv6的地址
		if (ipaddress == null || ipaddress.isEmpty()) {
			ipaddress = getLocalAddress(false, true);
			ipv6 = true;
		}
		String serverIdentity = null;
		if (ipaddress != null && !ipaddress.isEmpty()) {
			// 最后一个IP地址(一般机器可能存在多个IP地址)
			String ipLastNumStr = ipaddress.get(ipaddress.size() - 1);
			// 避免ipv6 中的%部分字符
			if (ipLastNumStr.indexOf("%") != -1) {
				ipLastNumStr = ipLastNumStr.substring(0, ipLastNumStr.indexOf("%"));
			}
			// 替换IP地址中的非数字字符
			ipLastNumStr = ipLastNumStr.replace(".", "").replace(":", "");
			// 保留4位
			if (ipLastNumStr.length() > size) {
				ipLastNumStr = ipLastNumStr.substring(ipLastNumStr.length() - size);
			}
			// ipv6 16进制
			if (ipv6) {
				serverIdentity = Integer.toString(Integer.parseInt(ipLastNumStr, 16));
			} else {
				serverIdentity = ipLastNumStr;
			}
			// 最终保留指定的位数
			if (serverIdentity.length() > size) {
				serverIdentity = serverIdentity.substring(serverIdentity.length() - size);
			}
			// 补足位数
			serverIdentity = StringUtil.addLeftZero2Len(ipLastNumStr, size);
		}
		// 无网络无法获取ip场景下 update 2021-09-17
		if (serverIdentity == null) {
			return StringUtil.addLeftZero2Len("1", size);
		}
		return serverIdentity;
	}

	/**
	 * @todo 产生分布式主键
	 * @param distributeIdGenerator
	 * @param tableName
	 * @param signature
	 * @param keyValues
	 * @param bizDate
	 * @param length
	 * @param sequenceSize
	 * @return
	 */
	public static String getId(DistributeIdGenerator distributeIdGenerator, String tableName, String signature,
			Map<String, Object> keyValues, LocalDate bizDate, int length, int sequenceSize) {
		String key = (signature == null ? "" : signature);
		// 主键生成依赖业务的相关字段值
		IgnoreKeyCaseMap<String, Object> keyValueMap = new IgnoreKeyCaseMap<String, Object>();
		if (keyValues != null && !keyValues.isEmpty()) {
			keyValues.forEach((keyStr, value) -> {
				if (null == value) {
					throw new RuntimeException("table=" + tableName + " 生成业务主键失败,关联字段:" + keyStr + " 对应的值为null!");
				}
			});
			keyValueMap.putAll(keyValues);
		}
		// 替换signature中的@df() 和@case()等宏表达式
		String realKey = MacroUtils.replaceMacros(key, keyValueMap);
		// 没有宏
		if (realKey.equals(key)) {
			// 长度够放下6位日期 或没有设置长度且流水长度小于6,则默认增加一个6位日期作为前置
			if ((length <= 0 && sequenceSize < 6) || (length - realKey.length() > 6)) {
				LocalDate realBizDate = (bizDate == null ? LocalDate.now() : bizDate);
				realKey = realKey.concat(DateUtil.formatDate(realBizDate, "yyMMdd"));
			}
		}
		// 参数替换
		if (!keyValueMap.isEmpty()) {
			realKey = MacroUtils.replaceParams(realKey, keyValueMap);
		}
		// 结合redis计数取末尾几位顺序数
		Long result;
		// update 2019-1-24 key命名策略改为SQLTOY_GL_ID:tableName:xxx 便于redis检索
		if (tableName != null) {
			result = distributeIdGenerator.generateId(
					"".equals(realKey) ? tableName : tableName.concat(":").concat(realKey), 1,
					SqlToyConstants.getDistributeIdCacheExpireDate());
		} else {
			result = distributeIdGenerator.generateId(realKey, 1, SqlToyConstants.getDistributeIdCacheExpireDate());
		}
		return realKey.concat(
				StringUtil.addLeftZero2Len("" + result, (sequenceSize > 0) ? sequenceSize : length - realKey.length()));
	}
}
