package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * @version v1.0,Date:2012-04-07
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

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");

	private IdUtil() {

	}

	/**
	 * update 2025-12-24 改为uuidv7版本
	 */
	public static String getUUID() {
		return StandardUUIDv7Generator.generateString().replace("-", "");
	}

	/**
	 * 获取22位有序安全ID,格式:13位当前毫秒+6位计数值+3位主机ID 目前情况下任何一次提取纳秒时间都不会一样
	 * 
	 * @param workerId 工作节点标识(最多3位数字字符串)，null时默认取本机IP末3位数字
	 * @return 22位十进制数字形式的唯一ID
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
	 * 获取26位有序安全ID,格式:15位:yyMMddHHmmssSSS+8位计数+3位主机ID
	 * 
	 * @param identityName 一般用表名
	 * @param workerId     工作节点标识(最多3位数字字符串)，null时默认取本机IP末3位数字
	 * @return 26位十进制数字形式的唯一ID
	 */
	public static BigDecimal getNanoTimeId(String identityName, String workerId) {
		String realIdentityName = StringUtil.isBlank(identityName) ? SQLTOY_ID : identityName;
		long[] currentValue = getCurrentValue(realIdentityName, 99999999);
		LocalDateTime nowTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentValue[0]), ZoneId.systemDefault());
		// 15位
		String nowTimeStr = DATE_FORMATTER.format(nowTime);
		// 8位
		String currentId = StringUtil.addLeftZero2Len("" + (currentValue[1] % 100000000), 8);
		// 3位主机ID,根据IP提取,默认提取IPv4的后3位
		String serverId = StringUtil.addLeftZero2Len((workerId == null) ? secureServerId : workerId, 3);
		// 总计26位
		return new BigDecimal(nowTimeStr.concat(currentId).concat(serverId));
	}

	/**
	 * 改用并发map根据表名称存放当前毫秒对应的计数值
	 * 
	 * @param identityName 计数标识名称(一般为表名)，同一名称同一毫秒内递增计数
	 * @param maxValue     同一毫秒内允许的最大计数值，超出后等待进入下一毫秒重新计数
	 * @return 长度为2的数组，[0]为当前毫秒时间值，[1]为对应的计数值
	 */
	private static long[] getCurrentValue(String identityName, int maxValue) {
		long[] result = new long[2];
		tablesCurrentTimeId.compute(identityName, (k, v) -> {
			long currentTime = System.currentTimeMillis();
			// 首次获取，从1开始计数
			if (null == v) {
				v = new CurrentTimeMaxValue(currentTime, 1);
			} // 当前时间大于上次提取maxValue的时间，则从新计数
			else if (currentTime > v.getCurrentTime()) {
				v.setCurrentTime(currentTime);
				v.setValue(1);
			} // currentTime == currentValue.getCurrentTime()
				// 超出阀值，从下一个毫秒重新计数
			else if (v.getValue() >= maxValue) {
				// 确保时间推进到下一毫秒，避免计数器重置后与同毫秒内的历史值重复
				// 用busy-wait而非Thread.sleep(1)：sleep(1)实际耗时≥2ms，在compute锁内
				// 会阻塞同表名其他线程，busy-wait通常<2ms且保证时间已前进
				long newTime = System.currentTimeMillis();
				while (newTime <= v.getCurrentTime()) {
					newTime = System.currentTimeMillis();
				}
				v.setCurrentTime(newTime);
				v.setValue(1);
			} else {
				v.setValue(v.getValue() + 1);
			}
			// 必须在compute原子区域内捕获值，避免返回后被其他线程修改导致重复
			result[0] = v.getCurrentTime();
			result[1] = v.getValue();
			return v;
		});
		return result;
	}

	/**
	 * 获取debug ID,只需保障单机当天唯一，主要帮助日志分组
	 * 
	 * @return 形如"HH:mm:ss.xxxxxxx"的debug ID字符串
	 */
	public static String getDebugId() {
		// 当前时间(秒)
		String nowTime = DateUtil.formatDate(new Date(), "HH:mm:ss");
		return buildDebugId(nowTime, System.nanoTime());
	}

	/**
	 * 组装debugId,nanoTime参数化便于测试负值场景
	 * 
	 * @param nowTime  当前时刻字符串
	 * @param nanoTime 纳秒计数
	 * @return 时刻字符串加点号连接纳秒截取部分(7~9位)的debug ID
	 */
	static String buildDebugId(String nowTime, long nanoTime) {
		// nanoTime原点由JVM任选(契约允许为负,部分平台开机初期为负值),无条件剥离负号再截取
		String nanoTimeStr = Long.toString(nanoTime).replace("-", "");
		int length = nanoTimeStr.length();
		// 极端情况下nanoTime位数不足，补齐避免substring越界
		if (length < 9) {
			nanoTimeStr = StringUtil.addLeftZero2Len(nanoTimeStr, 9);
			length = nanoTimeStr.length();
		}
		if (nanoTimeStr.endsWith("00")) {
			nanoTimeStr = nanoTimeStr.substring(length - 9, length - 2);
		} else {
			nanoTimeStr = nanoTimeStr.substring(length - 7);
		}
		return nowTime.concat(".").concat(nanoTimeStr);
	}

	/**
	 * 获取本机IP地址
	 * 
	 * @param hasHostName true同时返回主机名称
	 * @param hasIPV6     true包含IPV6地址，false仅返回IPV4地址
	 * @return 主机名称和IP地址组成的列表(排除回环地址)，获取失败返回空列表
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
					if (!ip.isLoopbackAddress() && (hasIPV6 || ip.getHostAddress().indexOf(":") == -1)) {
						if (hasHostName && !result.contains(ip.getHostName())) {
							result.add(ip.getHostName());
						}
						result.add(ip.getHostAddress());
					}
				}
			}
		} catch (Exception e) {
			logger.error(
					"exception occurred on the serverId which the id generation based on ip depends on, failed to get ip info:{}",
					e.getMessage());
		}
		return result;
	}

	/**
	 * 获取本机的IP地址，并从末尾截取指定长度的数字
	 * 
	 * @param size 需要保留的位数
	 * @return IP去除分隔符后末尾指定位数的数字字符串(不足左补零)，无网络时返回末位为1的补零字符串
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
			serverIdentity = StringUtil.addLeftZero2Len(serverIdentity, size);
		}
		// 无网络无法获取ip场景下 update 2021-09-17
		if (serverIdentity == null) {
			return StringUtil.addLeftZero2Len("1", size);
		}
		return serverIdentity;
	}

	/**
	 * 产生分布式主键
	 * 
	 * @param distributeIdGenerator 分布式ID生成器实现(如基于redis)
	 * @param tableName             表名，用于构造分布式计数的key
	 * @param signature             主键前缀签名，支持@df()、@case()等宏表达式，无宏且长度充足时自动拼接yyMMdd业务日期
	 * @param keyValues             宏表达式依赖的业务字段值，相关字段值不允许为null
	 * @param bizDate               业务日期，null时取当前日期
	 * @param length                主键总长度，小于等于0时由sequenceSize决定流水位数
	 * @param sequenceSize          流水号位数，大于0时优先于length生效
	 * @return 前缀+流水号拼成的分布式主键字符串
	 */
	public static String getId(DistributeIdGenerator distributeIdGenerator, String tableName, String signature,
			Map<String, Object> keyValues, LocalDate bizDate, int length, int sequenceSize) {
		String key = (signature == null ? "" : signature);
		// 主键生成依赖业务的相关字段值
		IgnoreKeyCaseMap<String, Object> keyValueMap = new IgnoreKeyCaseMap<String, Object>();
		if (keyValues != null && !keyValues.isEmpty()) {
			keyValues.forEach((keyStr, value) -> {
				if (null == value) {
					throw new RuntimeException("failed to generate business primary key for table [" + tableName
							+ "], the related field [" + keyStr + "] value is null!");
				}
			});
			keyValueMap.putAll(keyValues);
		}
		// 替换signature中的@df() 和@case()等宏表达式
		String realKey = MacroUtils.replaceMacros(key, keyValueMap);
		// 没有宏
		if (realKey.equals(key)) {
			// 1、length<=0(默认为-1)且sequenceSize<6
			// 2、length-keySize-6(yyMMdd)>2 确保有三位流水
			if ((length <= 0 && sequenceSize < 6) || (length - realKey.length() > 8)) {
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
