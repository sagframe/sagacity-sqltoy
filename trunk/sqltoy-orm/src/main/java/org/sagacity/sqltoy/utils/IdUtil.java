package org.sagacity.sqltoy.utils;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

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

	private IdUtil() {

	}

	/**
	 * 封装JDK自带的UUID, 通过Random数字生成,中间有-分割
	 */
	public static String getUUID() {
		return UUID.randomUUID().toString().replaceAll("-", "");
	}

	/**
	 * @todo 获取22位有序安全ID,格式:13位当前毫秒+6位纳秒+3位主机ID 目前情况下任何一次提取纳秒时间都不会一样
	 * @param workerId
	 * @return
	 */
	public static BigDecimal getShortNanoTimeId(String workerId) {
		// 13位当前时间(毫秒)
		String nowTime = StringUtil.addLeftZero2Len("" + System.currentTimeMillis(), 13);
		// 6位纳秒间隔
		String nanoTime = "" + System.nanoTime();
		nanoTime = nanoTime.substring(nanoTime.length() - 6);
		// 3位主机ID
		String serverId = (workerId == null) ? secureServerId : workerId;
		String id = nowTime.concat(nanoTime).concat(serverId);
		return new BigDecimal(id);
	}

	/**
	 * @todo 获取26位有序安全ID,格式:15位:yyMMddHHmmssSSS+后6位纳秒+2位(线程Id+随机数)+3位主机ID
	 * @param workerId
	 * @return
	 */
	public static BigDecimal getNanoTimeId(String workerId) {
		// 当前时间(毫秒)
		String nowTime = DateUtil.formatDate(new Date(), "yyMMddHHmmssSSS");
		// 后6位纳秒间隔
		String nanoTime = "" + System.nanoTime();
		nanoTime = nanoTime.substring(nanoTime.length() - 6);
		// 1位随机数(防范性措施)
		String randomNum = Long.toString(Math.abs(new SecureRandom().nextLong()) % 10);
		// 线程ID(实际线程ID+1位随机数)
		String threadId = Thread.currentThread().getId() + randomNum;
		// 保留2位
		threadId = threadId.substring(threadId.length() - 2);
		// 3位主机ID,根据IP提取,默认提取IPv4的后3位
		String serverId = (workerId == null) ? secureServerId : workerId;
		return new BigDecimal(nowTime.concat(nanoTime).concat(threadId).concat(serverId));
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
			ipLastNumStr = ipLastNumStr.replaceAll("\\.", "").replaceAll("\\:", "");
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
}
