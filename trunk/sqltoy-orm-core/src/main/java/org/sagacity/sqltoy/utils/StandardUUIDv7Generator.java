package org.sagacity.sqltoy.utils;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 符合 RFC 9562 标准的 UUID v7 生成器（无时间戳回拨风险） 特性：
 * <p>
 * <li>1. 严格遵循 UUID v7 结构规范</li>
 * <li>2. 线程安全，支持超高并发场景</li>
 * <li>3. 基于单调递增时间戳，彻底解决时间戳回拨问题</li>
 * <li>4. 缓存复用对象，降低 GC 压力</li>
 * <li>5. 支持默认时间（当前系统时间）和自定义时间</li>
 * <li>6. 内置版本验证、时间戳提取等工具方法
 * <li>7. 毫秒内序列去重，保障唯一性</li>
 * </p>
 */
public final class StandardUUIDv7Generator {
	// ==================== 常量定义（规范化，避免魔法值）====================
	// UUID 版本常量（v7）
	private static final int UUID_VERSION_7 = 7;
	// 版本位掩码（mostSignificantBits 的第 49-52 位）
	private static final long VERSION_BIT_MASK = 0x000000000000F000L;
	// UUID v7 版本位标识（0111）
	private static final long VERSION_7_BIT_FLAG = (long) UUID_VERSION_7 << 12;
	// 变体位掩码（leastSignificantBits 的第 1-2 位）
	private static final long VARIANT_BIT_MASK = 0xC000000000000000L;
	// RFC 4122 变体位标识（10xx）
	private static final long RFC_4122_VARIANT_FLAG = 0x8000000000000000L;
	// 随机数高位掩码（12 位）
	private static final long RANDOM_HIGH_12_BIT_MASK = 0x0FFF;
	// 随机数低位掩码（62 位）
	private static final long RANDOM_LOW_62_BIT_MASK = 0x3FFFFFFFFFFFFFFFL;
	// 时间戳位数（48 位，符合 RFC 9562 标准）
	private static final int TIMESTAMP_BITS = 48;
	private static final long TIMESTAMP_MASK = (1L << TIMESTAMP_BITS) - 1;
	// 序列位数（20 位，支持每毫秒 1048576 个 UUID，降低重复风险）
	private static final int SEQUENCE_BITS = 20;
	private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

	// ==================== 全局单例与缓存 ====================
	// 安全随机数生成器（全局单例，保证安全性和性能）
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	// 8 字节缓冲区（缓存复用，减少对象创建）
	private static final ByteBuffer RANDOM_BYTE_BUFFER = ByteBuffer.allocate(8);
	// 单调递增时间戳（保证永不倒退，解决时间戳回拨问题）
	private static final AtomicLong MONOTONIC_TIMESTAMP = new AtomicLong(0);
	// 毫秒内序列计数器（原子类保证线程安全，用于去重）
	private static final AtomicLong SEQUENCE_COUNTER = new AtomicLong(0);

	// ==================== 私有构造器（禁止实例化）====================
	private StandardUUIDv7Generator() {
		throw new UnsupportedOperationException("该类为工具类，禁止实例化");
	}

	// ==================== 核心生成方法 ====================
	/**
	 * 生成默认时间（当前系统时间）的 UUID v7 实例
	 * 
	 * @return 符合 RFC 9562 标准的 UUID v7
	 */
	public static UUID generate() {
		return generate(Instant.now());
	}

	/**
	 * 生成指定时间的 UUID v7 实例（支持数据迁移、时间回溯等场景）
	 * 
	 * @param instant 指定时间（不可为 null）
	 * @return 符合 RFC 9562 标准的 UUID v7
	 * @throws NullPointerException 当 instant 为 null 时抛出
	 */
	public static UUID generate(Instant instant) {
		// 1. 参数校验
		if (instant == null) {
			throw new NullPointerException("指定的时间实例（instant）不能为 null");
		}

		// 2. 获取单调递增时间戳（永不倒退，彻底解决时间戳回拨问题）
		long currentTimestamp = getMonotonicTimestamp(instant);

		// 3. 处理毫秒内序列（解决高并发同一毫秒重复问题）
		long sequence = getSequence(currentTimestamp);

		// 4. 生成 64 位安全随机数（缓存复用 ByteBuffer）
		long randomValue = generateRandom64Bits();

		// 5. 融合序列与随机数（提升唯一性，序列替换随机数低 20 位）
		long mergedRandom = (randomValue & ~SEQUENCE_MASK) | sequence;

		// 6. 构造 mostSignificantBits（高 64 位）
		// 结构：48 位时间戳 + 4 位版本号 + 12 位随机数
		long mostSignificantBits = (currentTimestamp << 16) // 时间戳左移 16 位，留出版本号和 12 位随机数
				| ((mergedRandom >>> 48) & RANDOM_HIGH_12_BIT_MASK); // 提取随机数高 12 位
		// 清空版本位并设置 v7 版本
		mostSignificantBits &= ~VERSION_BIT_MASK;
		mostSignificantBits |= VERSION_7_BIT_FLAG;

		// 7. 构造 leastSignificantBits（低 64 位）
		// 结构：2 位变体位 + 62 位随机数（含序列）
		long leastSignificantBits = mergedRandom & RANDOM_LOW_62_BIT_MASK; // 清空前 2 位（变体位）
		// 清空变体位并设置 RFC 4122 变体
		leastSignificantBits &= ~VARIANT_BIT_MASK;
		leastSignificantBits |= RFC_4122_VARIANT_FLAG;

		// 8. 返回 UUID v7 实例
		return new UUID(mostSignificantBits, leastSignificantBits);
	}

	/**
	 * 生成 UUID v7 字符串（标准 8-4-4-4-12 格式）
	 * 
	 * @return UUID v7 字符串
	 */
	public static String generateString() {
		return generate().toString();
	}

	/**
	 * 生成指定时间的 UUID v7 字符串
	 * 
	 * @param instant 指定时间
	 * @return UUID v7 字符串
	 * @throws NullPointerException 当 instant 为 null 时抛出
	 */
	public static String generateString(Instant instant) {
		return generate(instant).toString();
	}

	// ==================== 工具方法 ====================
	/**
	 * 验证 UUID 是否为 v7 版本
	 * 
	 * @param uuid 待验证的 UUID
	 * @return true：是 UUID v7；false：不是
	 * @throws NullPointerException 当 uuid 为 null 时抛出
	 */
	public static boolean isUUIDv7(UUID uuid) {
		if (uuid == null) {
			throw new NullPointerException("待验证的 UUID 不能为 null");
		}
		// 提取版本号（mostSignificantBits 右移 12 位后取低 4 位）
		int version = (int) ((uuid.getMostSignificantBits() >> 12) & 0x000F);
		return version == UUID_VERSION_7;
	}

	/**
	 * 从 UUID v7 中提取单调递增时间戳（注意：非原始系统时间，保证单调）
	 * 
	 * @param uuid UUID v7 实例
	 * @return 单调递增毫秒级时间戳
	 * @throws NullPointerException     当 uuid 为 null 时抛出
	 * @throws IllegalArgumentException 当 uuid 不是 v7 版本时抛出
	 */
	public static long extractTimestamp(UUID uuid) {
		if (uuid == null) {
			throw new NullPointerException("待提取时间戳的 UUID 不能为 null");
		}
		if (!isUUIDv7(uuid)) {
			throw new IllegalArgumentException("传入的 UUID 不是 v7 版本，无法提取时间戳");
		}
		// 提取高 48 位时间戳（无符号右移 16 位）
		return uuid.getMostSignificantBits() >>> 16;
	}

	public static long extractTimestamp(String uuid) {
		if (uuid == null) {
			throw new NullPointerException("待提取时间戳的 UUID 不能为 null");
		}
		if (uuid.contains("-") && uuid.length() == 36) {
			return extractTimestamp(UUID.fromString(uuid));
		} else if (uuid.length() == 32) {
			return extractTimestamp(UUID.fromString(String.format("%s-%s-%s-%s-%s", uuid.substring(0, 8),
					uuid.substring(8, 12), uuid.substring(12, 16), uuid.substring(16, 20), uuid.substring(20))));
		}
		throw new IllegalArgumentException("传入的 UUID字符串长度不是无-符合的32位以及带-符合的36位!");
	}

	// ==================== 内部辅助方法 ====================
	/**
	 * 获取单调递增的毫秒时间戳（永不倒退，彻底解决时间戳回拨问题）
	 * 
	 * @param instant 原始时间实例
	 * @return 单调递增时间戳（48 位）
	 */
	private static long getMonotonicTimestamp(Instant instant) {
		long currentSysTs = instant.toEpochMilli() & TIMESTAMP_MASK; // 截取 48 位系统时间
		long lastMonotonicTs = MONOTONIC_TIMESTAMP.get();

		// 循环保证原子性，确保时间戳永不倒退
		while (true) {
			// 系统时间大于上次单调时间戳，更新并使用系统时间
			if (currentSysTs > lastMonotonicTs) {
				if (MONOTONIC_TIMESTAMP.compareAndSet(lastMonotonicTs, currentSysTs)) {
					return currentSysTs;
				}
				// CAS 失败，重新获取上次单调时间戳重试
				lastMonotonicTs = MONOTONIC_TIMESTAMP.get();
			}
			// 系统时间小于等于上次单调时间戳，使用上次单调时间戳 + 1
			else {
				long nextMonotonicTs = lastMonotonicTs + 1;
				// 确保不超出 48 位范围（理论上 48 位时间戳可使用到 2109 年，无需担心溢出）
				nextMonotonicTs &= TIMESTAMP_MASK;
				if (MONOTONIC_TIMESTAMP.compareAndSet(lastMonotonicTs, nextMonotonicTs)) {
					return nextMonotonicTs;
				}
				// CAS 失败，重新获取上次单调时间戳重试
				lastMonotonicTs = MONOTONIC_TIMESTAMP.get();
			}
		}
	}

	/**
	 * 生成 64 位安全随机数
	 * 
	 * @return 64 位随机数
	 */
	private static long generateRandom64Bits() {
		synchronized (RANDOM_BYTE_BUFFER) { // 保证缓冲区线程安全
			SECURE_RANDOM.nextBytes(RANDOM_BYTE_BUFFER.array());
			RANDOM_BYTE_BUFFER.rewind();
			return RANDOM_BYTE_BUFFER.getLong();
		}
	}

	/**
	 * 获取毫秒内序列值（线程安全）
	 * 
	 * @param currentTimestamp 当前单调时间戳
	 * @return 20 位序列值
	 */
	private static long getSequence(long currentTimestamp) {
		// 此处无需处理时间戳回拨（已由单调时间戳保证），仅需维护毫秒内序列
		return SEQUENCE_COUNTER.incrementAndGet() & SEQUENCE_MASK;
	}

}