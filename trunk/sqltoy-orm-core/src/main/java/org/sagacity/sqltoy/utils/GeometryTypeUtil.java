package org.sagacity.sqltoy.utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description geometry空间类型处理工具,jts-core为可选依赖,框架中所有org.locationtech类的引用
 *              全部隔离在本类中,方法内部均先判断hasJts()再触碰JTS类,避免类加载失败;
 *              支持的值形式:WKT/EWKT字符串、postgis EWKB hex字符串、postgis EWKB二进制、
 *              mysql的4字节SRID前缀+WKB二进制、JTS Geometry对象
 * @author zhongxuchen
 * @date 2026-8-28
 */
public class GeometryTypeUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(GeometryTypeUtil.class);

	// JTS是否可用(静态一次性检测,jts-core为可选依赖)
	private static final boolean HAS_JTS;

	static {
		boolean found;
		try {
			Class.forName("org.locationtech.jts.geom.Geometry");
			found = true;
		} catch (ClassNotFoundException e) {
			found = false;
		}
		HAS_JTS = found;
	}

	// postgis EWKB扩展标志位(定义于liblwgeom)
	private static final int EWKB_SRID_FLAG = 0x20000000;
	private static final int EWKB_Z_FLAG = 0x80000000;
	private static final int EWKB_M_FLAG = 0x40000000;

	private GeometryTypeUtil() {
	}

	/**
	 * @todo jts-core是否在classpath中
	 * @return
	 */
	public static boolean hasJts() {
		return HAS_JTS;
	}

	/**
	 * @todo 判断数据库元数据返回的列类型名是否属于geometry空间类型(传入大写形式)
	 * @param columnTypeName
	 * @return
	 */
	public static boolean isGeometryTypeName(String columnTypeName) {
		if (columnTypeName == null) {
			return false;
		}
		switch (columnTypeName) {
		// mysql元数据对空间列可能返回具体子类型名,统一映射为GEOMETRY
		case "GEOMETRY":
		case "GEOGRAPHY":
		case "POINT":
		case "LINESTRING":
		case "POLYGON":
		case "MULTIPOINT":
		case "MULTILINESTRING":
		case "MULTIPOLYGON":
		case "GEOMETRYCOLLECTION":
			return true;
		default:
			return false;
		}
	}

	/**
	 * @todo 将JTS Geometry对象转为WKT字符串,非JTS对象或JTS不可用时返回null
	 * @param value
	 * @return
	 */
	public static String toWKT(Object value) {
		if (!HAS_JTS || !(value instanceof Geometry)) {
			return null;
		}
		return new WKTWriter(3).write((Geometry) value);
	}

	/**
	 * @todo 将数据库返回的空间值(WKT/EWKT字符串、EWKB hex、二进制WKB/EWKB、PGobject等)解析为JTS Geometry
	 * @param jdbcValue
	 * @return 解析失败返回null,交回框架按常规类型处理
	 */
	public static Object parse(Object jdbcValue) {
		if (!HAS_JTS || jdbcValue == null) {
			return null;
		}
		try {
			if (jdbcValue instanceof Geometry) {
				return jdbcValue;
			}
			if (jdbcValue instanceof byte[]) {
				return parseBytes((byte[]) jdbcValue);
			}
			return parseString(jdbcValue.toString());
		} catch (Exception e) {
			logger.debug("geometry值解析失败:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * @todo 将数据库返回的空间值统一转为WKT字符串(String目标类型场景,如PG的EWKB hex转WKT)
	 * @param jdbcValue
	 * @return 解析失败返回null
	 */
	public static String toWKTString(Object jdbcValue) {
		Object geometry = parse(jdbcValue);
		if (geometry == null) {
			return null;
		}
		return new WKTWriter(3).write((Geometry) geometry);
	}

	/**
	 * @todo 解析字符串形式:postgis EWKB hex或WKT/EWKT
	 * @param value
	 * @return
	 * @throws Exception
	 */
	private static Object parseString(String value) throws Exception {
		String str = value.trim();
		if (str.isEmpty()) {
			return null;
		}
		// postgis EWKB hex形式:以字节序标识00/01开头的纯hex串
		if (looksLikeHex(str)) {
			int[] srid = new int[1];
			byte[] wkb = normalizeEWKB(WKBReader.hexToBytes(str), srid);
			Geometry geometry = new WKBReader().read(wkb);
			geometry.setSRID(srid[0]);
			return geometry;
		}
		// EWKT形式:SRID=4326;POINT(1 2)
		int srid = 0;
		if (str.regionMatches(true, 0, "SRID=", 0, 5)) {
			int idx = str.indexOf(';');
			if (idx > 5) {
				srid = Integer.parseInt(str.substring(5, idx).trim());
				str = str.substring(idx + 1).trim();
			}
		}
		Geometry geometry = new WKTReader().read(str);
		geometry.setSRID(srid);
		return geometry;
	}

	/**
	 * @todo 解析二进制形式:mysql(4字节SRID前缀+WKB)或postgis EWKB
	 * @param bytes
	 * @return
	 * @throws Exception
	 */
	private static Object parseBytes(byte[] bytes) throws Exception {
		if (bytes.length < 5) {
			return null;
		}
		int srid = 0;
		byte[] payload;
		// 首字节不是字节序标识(0/1)而偏移4处是,判定为mysql的SRID前缀形式
		if (!isByteOrderByte(bytes[0]) && isByteOrderByte(bytes[4])) {
			// mysql的SRID为4字节整型,按前两字节是否为0区分大小端
			srid = (bytes[0] == 0 && bytes[1] == 0) ? readInt(bytes, 0, true) : readInt(bytes, 0, false);
			payload = new byte[bytes.length - 4];
			System.arraycopy(bytes, 4, payload, 0, payload.length);
		} else {
			payload = bytes;
		}
		int[] sridHolder = new int[1];
		byte[] wkb = normalizeEWKB(payload, sridHolder);
		Geometry geometry = new WKBReader().read(wkb);
		geometry.setSRID(srid != 0 ? srid : sridHolder[0]);
		return geometry;
	}

	/**
	 * @todo 将EWKB归一化为标准OGC WKB:JTS的WKBReader不识别EWKB的类型字高位标志位和SRID字段,
	 *       递归重写类型字(高位标志转OGC的1000/2000偏移),坐标数据原样拷贝,同时提取顶层SRID
	 * @param ewkb
	 * @param sridHolder 提取的SRID(仅顶层geometry携带)
	 * @return
	 * @throws Exception
	 */
	private static byte[] normalizeEWKB(byte[] ewkb, int[] sridHolder) throws Exception {
		ByteBuffer in = ByteBuffer.wrap(ewkb);
		ByteArrayOutputStream out = new ByteArrayOutputStream(ewkb.length);
		sridHolder[0] = 0;
		copyGeometry(in, out, sridHolder);
		return out.toByteArray();
	}

	/**
	 * @todo 递归拷贝单个geometry,归一化类型字并剔除SRID字段
	 * @param in
	 * @param out
	 * @param sridHolder
	 * @throws Exception
	 */
	private static void copyGeometry(ByteBuffer in, ByteArrayOutputStream out, int[] sridHolder) throws Exception {
		byte byteOrder = in.get();
		boolean littleEndian = (byteOrder == 1);
		in.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
		out.write(byteOrder);
		int type = in.getInt();
		int base;
		boolean hasZ;
		boolean hasM;
		if ((type & EWKB_Z_FLAG) != 0 || (type & EWKB_M_FLAG) != 0 || (type & EWKB_SRID_FLAG) != 0) {
			// postgis EWKB:基准类型为低8位,Z/M为高位标志
			base = type & 0xFF;
			hasZ = (type & EWKB_Z_FLAG) != 0;
			hasM = (type & EWKB_M_FLAG) != 0;
		} else {
			// OGC偏移式:1001=POINT Z,2002=LINESTRING M,3003=POLYGON ZM
			base = type % 1000;
			int dims = type / 1000;
			hasZ = (dims == 1 || dims == 3);
			hasM = (dims >= 2);
		}
		boolean hasSrid = (type & EWKB_SRID_FLAG) != 0;
		int newType = base + (hasZ ? 1000 : 0) + (hasM ? 2000 : 0);
		writeInt(out, newType, littleEndian);
		if (hasSrid) {
			int srid = in.getInt();
			if (sridHolder[0] == 0) {
				sridHolder[0] = srid;
			}
		}
		int pointBytes = 8 * (2 + (hasZ ? 1 : 0) + (hasM ? 1 : 0));
		switch (base) {
		case 1: // POINT
			copyBytes(in, out, pointBytes);
			break;
		case 2: // LINESTRING
		{
			int count = in.getInt();
			writeInt(out, count, littleEndian);
			copyBytes(in, out, count * pointBytes);
			break;
		}
		case 3: // POLYGON
		{
			int rings = in.getInt();
			writeInt(out, rings, littleEndian);
			for (int i = 0; i < rings; i++) {
				int count = in.getInt();
				writeInt(out, count, littleEndian);
				copyBytes(in, out, count * pointBytes);
			}
			break;
		}
		default: // 4~7 MULTIPOINT/MULTILINESTRING/MULTIPOLYGON/GEOMETRYCOLLECTION
		{
			int count = in.getInt();
			writeInt(out, count, littleEndian);
			for (int i = 0; i < count; i++) {
				copyGeometry(in, out, sridHolder);
			}
			break;
		}
		}
	}

	/**
	 * @todo 判断字符串是否为EWKB hex形式
	 * @param str
	 * @return
	 */
	private static boolean looksLikeHex(String str) {
		if (str.length() < 10 || (str.length() % 2) != 0) {
			return false;
		}
		// WKB/EWKB首字节为字节序标识(00大端/01小端)
		if (str.charAt(0) != '0' || (str.charAt(1) != '0' && str.charAt(1) != '1')) {
			return false;
		}
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isByteOrderByte(byte b) {
		return b == 0 || b == 1;
	}

	private static int readInt(byte[] bytes, int offset, boolean bigEndian) {
		if (bigEndian) {
			return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
					| ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
		}
		return ((bytes[offset + 3] & 0xFF) << 24) | ((bytes[offset + 2] & 0xFF) << 16)
				| ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
	}

	private static void writeInt(ByteArrayOutputStream out, int value, boolean littleEndian) {
		if (littleEndian) {
			out.write(value & 0xFF);
			out.write((value >> 8) & 0xFF);
			out.write((value >> 16) & 0xFF);
			out.write((value >> 24) & 0xFF);
		} else {
			out.write((value >> 24) & 0xFF);
			out.write((value >> 16) & 0xFF);
			out.write((value >> 8) & 0xFF);
			out.write(value & 0xFF);
		}
	}

	private static void copyBytes(ByteBuffer in, ByteArrayOutputStream out, int length) {
		byte[] buffer = new byte[length];
		in.get(buffer);
		out.write(buffer, 0, length);
	}
}
