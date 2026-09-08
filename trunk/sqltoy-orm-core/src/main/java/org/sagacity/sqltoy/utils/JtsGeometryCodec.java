package org.sagacity.sqltoy.utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description JTS几何编解码实现:update 2026-9-8 自GeometryTypeUtil剥离而来。
 *              本类字节码直接引用org.locationtech类型,仅允许被GeometryTypeUtil门面
 *              在hasJts()探测通过后调用,保证类加载/验证阶段jts-core必在classpath;
 *              公开方法签名只用Object/String/byte[]/Struct,不暴露JTS类型
 *              支持的值形式:WKT/EWKT字符串、postgis EWKB hex字符串、postgis EWKB二进制、
 *              mysql的4字节SRID前缀+WKB二进制、JTS Geometry对象
 * @author zhongxuchen
 * @date 2026-8-28
 */
public class JtsGeometryCodec {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(JtsGeometryCodec.class);

	// postgis EWKB扩展标志位(定义于liblwgeom)
	private static final int EWKB_SRID_FLAG = 0x20000000;
	private static final int EWKB_Z_FLAG = 0x80000000;
	private static final int EWKB_M_FLAG = 0x40000000;

	JtsGeometryCodec() {
	}

	/**
	 * 将JTS Geometry对象转为WKT字符串,非JTS对象返回null
	 *
	 * @param value
	 * @return
	 */
	static String toWKT(Object value) {
		if (!(value instanceof Geometry)) {
			return null;
		}
		return new WKTWriter(3).write((Geometry) value);
	}

	/**
	 * 将数据库返回的空间值(WKT/EWKT字符串、EWKB hex、二进制WKB/EWKB、PGobject、 oracle SDO_GEOMETRY
	 * Struct等)解析为JTS Geometry
	 *
	 * @param jdbcValue
	 * @return 解析失败返回null,交回框架按常规类型处理
	 */
	static Object parse(Object jdbcValue) {
		if (jdbcValue == null) {
			return null;
		}
		try {
			if (jdbcValue instanceof Geometry) {
				return jdbcValue;
			}
			// oracle SDO_GEOMETRY读回为java.sql.Struct(ojdbc的oracle.sql.STRUCT实现了该接口)
			if (jdbcValue instanceof java.sql.Struct) {
				return parseSdoStruct((java.sql.Struct) jdbcValue);
			}
			if (jdbcValue instanceof byte[]) {
				return parseBytes((byte[]) jdbcValue);
			}
			return parseString(jdbcValue.toString());
		} catch (Exception e) {
			logger.debug("failed to parse geometry value:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 将mysql系(geometry列)读回的内部格式byte[]解码为WKT:mysql内部格式为
	 * 4字节SRID(小端)+标准WKB,列元数据已确证为geometry列故无条件剥离前缀
	 * (parseBytes的嗅探在SRID=0时前4字节全0会与大端WKB误判,不能用于本场景)
	 *
	 * @param bytes mysql geometry内部格式字节
	 * @return WKT文本,解码失败返回null
	 */
	static String mysqlGeometryBytesToWKT(byte[] bytes) {
		if (bytes == null || bytes.length < 9) {
			return null;
		}
		try {
			// 剥离4字节小端SRID前缀,剩余为标准WKB
			int srid = ((bytes[3] & 0xFF) << 24) | ((bytes[2] & 0xFF) << 16) | ((bytes[1] & 0xFF) << 8)
					| (bytes[0] & 0xFF);
			byte[] wkb = new byte[bytes.length - 4];
			System.arraycopy(bytes, 4, wkb, 0, wkb.length);
			Geometry geometry = new WKBReader().read(wkb);
			if (srid != 0) {
				geometry.setSRID(srid);
			}
			return new WKTWriter(3).write(geometry);
		} catch (Exception e) {
			logger.debug("failed to parse mysql geometry bytes:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 将数据库返回的空间值统一转为WKT字符串(String目标类型场景,如PG的EWKB hex转WKT)
	 *
	 * @param jdbcValue
	 * @return 解析失败返回null
	 */
	static String toWKTString(Object jdbcValue) {
		Object geometry = parse(jdbcValue);
		if (geometry == null) {
			return null;
		}
		return new WKTWriter(3).write((Geometry) geometry);
	}

	/**
	 * 解析字符串形式:postgis EWKB hex或WKT/EWKT
	 *
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
	 * 解析二进制形式:mysql(4字节SRID前缀+WKB)或postgis EWKB
	 *
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
	 * 将空间值(JTS Geometry或WKT/EWKT字符串)解析为oracle SDO_GEOMETRY的属性四元组,
	 * 供OracleSdoUtil构造驱动STRUCT完成绑定(save/update/saveOrUpdate共用)
	 *
	 * @param value 空间值,支持JTS Geometry、WKT/EWKT字符串等parse()可识别形式
	 * @return Object[]{gtype,srid,elemInfo[],ordinates[]},无法识别返回null
	 */
	static Object[] toSdoAttributes(Object value) {
		if (value == null) {
			return null;
		}
		Object parsed = (value instanceof Geometry) ? value : parse(value);
		if (!(parsed instanceof Geometry)) {
			return null;
		}
		Geometry geometry = (Geometry) parsed;
		int srid = geometry.getSRID();
		try {
			if (geometry instanceof org.locationtech.jts.geom.Point) {
				Coordinate c = ((org.locationtech.jts.geom.Point) geometry).getCoordinate();
				int dims = Double.isNaN(c.getZ()) ? 2 : 3;
				return new Object[] { dims * 1000 + 1, srid, new int[] { 1, 1, 1 }, coordToAry(c, dims) };
			}
			if (geometry instanceof org.locationtech.jts.geom.LineString) {
				Coordinate[] cs = geometry.getCoordinates();
				int dims = coordDims(cs);
				return new Object[] { dims * 1000 + 2, srid, new int[] { 1, 2, 1 }, coordsToAry(cs, dims) };
			}
			if (geometry instanceof org.locationtech.jts.geom.Polygon) {
				return buildPolygonSdo((org.locationtech.jts.geom.Polygon) geometry, srid);
			}
			if (geometry instanceof org.locationtech.jts.geom.MultiPoint) {
				Coordinate[] cs = geometry.getCoordinates();
				int dims = coordDims(cs);
				return new Object[] { dims * 1000 + 5, srid, new int[] { 1, 1, cs.length }, coordsToAry(cs, dims) };
			}
			if (geometry instanceof org.locationtech.jts.geom.MultiLineString) {
				return buildMultiLineSdo((org.locationtech.jts.geom.MultiLineString) geometry, srid);
			}
			if (geometry instanceof org.locationtech.jts.geom.MultiPolygon) {
				return buildMultiPolygonSdo((org.locationtech.jts.geom.MultiPolygon) geometry, srid);
			}
		} catch (Exception e) {
			logger.debug("failed to encode sdo attributes:{}", e.getMessage());
		}
		// GeometryCollection等复杂类型不支持编码
		return null;
	}

	/**
	 * 解析oracle读回的SDO_GEOMETRY Struct为JTS Geometry
	 *
	 * @param struct java.sql.Struct实例(属性:gtype,srid,point,elem_info,ordinates)
	 * @return 解析失败或类型不支持返回null
	 */
	static Object parseSdoStruct(java.sql.Struct struct) {
		try {
			Object[] attrs = struct.getAttributes();
			int gtype = (int) ((Number) attrs[0]).longValue();
			int srid = (attrs[1] instanceof Number) ? ((Number) attrs[1]).intValue() : 0;
			Object pointAttr = attrs.length > 2 ? attrs[2] : null;
			int[] elemInfo = sdoArrayToInts(attrs.length > 3 ? attrs[3] : null);
			double[] ordinates = sdoArrayToDoubles(attrs.length > 4 ? attrs[4] : null);
			int dims = gtype / 1000;
			if (dims == 0) {
				dims = 2;
			}
			int baseType = gtype % 1000;
			org.locationtech.jts.geom.GeometryFactory factory = new org.locationtech.jts.geom.GeometryFactory();
			// 单点可走SDO_POINT属性形式(不占ordinates)
			if (pointAttr != null && baseType == 1 && pointAttr instanceof java.sql.Struct) {
				Object[] pointAttrs = ((java.sql.Struct) pointAttr).getAttributes();
				Coordinate c = new Coordinate(asDouble(pointAttrs[0]), asDouble(pointAttrs[1]),
						(pointAttrs.length > 2 && pointAttrs[2] != null) ? asDouble(pointAttrs[2]) : Double.NaN);
				org.locationtech.jts.geom.Point point = factory.createPoint(c);
				point.setSRID(srid);
				return point;
			}
			if (ordinates == null || ordinates.length < dims) {
				return null;
			}
			Geometry geometry = null;
			switch (baseType) {
			case 1: // POINT
				geometry = factory.createPoint(
						new Coordinate(ordinates[0], ordinates[1], (dims > 2) ? ordinates[2] : Double.NaN));
				break;
			case 2: // LINESTRING
				geometry = factory.createLineString(readCoords(ordinates, 0, ordinates.length / dims, dims));
				break;
			case 3: // POLYGON,按elem_info三元组切分外环(1003)与内环(2003)
				geometry = readPolygon(ordinates, elemInfo, dims, factory);
				break;
			case 5: // MULTIPOINT,(offset,1,N)或每点一个三元组
				geometry = readMultiPoint(ordinates, elemInfo, dims, factory);
				break;
			case 6: // MULTILINESTRING
				geometry = readMultiLine(ordinates, elemInfo, dims, factory);
				break;
			case 7: // MULTIPOLYGON,按外环(1003)分组
				geometry = readMultiPolygon(ordinates, elemInfo, dims, factory);
				break;
			default:
				return null;
			}
			if (geometry != null) {
				geometry.setSRID(srid);
			}
			return geometry;
		} catch (Exception e) {
			logger.debug("failed to parse sdo geometry struct:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 解析WKB属性布局的Struct为JTS Geometry(达梦DMGEO/sysgeo.ST_GEOMETRY,读回
	 * dm.jdbc.driver.DmdbStruct,属性布局:(srid,标准OGC WKB
	 * blob,版本),已实库验证POINT/LINESTRING)
	 *
	 * @param struct java.sql.Struct实例(达梦ST_Geometry)
	 * @return 解析失败返回null,交回框架按原值处理
	 */
	static Object parseWkbStruct(java.sql.Struct struct) {
		try {
			Object[] attrs = struct.getAttributes();
			if (attrs == null || attrs.length < 2 || attrs[1] == null) {
				return null;
			}
			byte[] wkb;
			if (attrs[1] instanceof java.sql.Blob) {
				java.sql.Blob blob = (java.sql.Blob) attrs[1];
				wkb = blob.getBytes(1, (int) blob.length());
			} else if (attrs[1] instanceof byte[]) {
				wkb = (byte[]) attrs[1];
			} else {
				return null;
			}
			Object geometry = parseBytes(wkb);
			if (geometry instanceof Geometry && attrs[0] instanceof Number) {
				((Geometry) geometry).setSRID(((Number) attrs[0]).intValue());
			}
			return geometry;
		} catch (Exception e) {
			logger.debug("failed to parse wkb struct:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 将WKB属性布局的Struct(达梦ST_Geometry)转为WKT字符串
	 *
	 * @param struct java.sql.Struct实例
	 * @return WKT文本,解析失败返回null
	 */
	static String wkbStructToWKT(java.sql.Struct struct) {
		Object geometry = parseWkbStruct(struct);
		if (!(geometry instanceof Geometry)) {
			return null;
		}
		return new WKTWriter(3).write((Geometry) geometry);
	}

	/**
	 * 构造多边形的SDO属性:外环etype=1003,内环etype=2003,interpretation=1(直线边)
	 *
	 * @param polygon JTS多边形
	 * @param srid    坐标系ID
	 * @return SDO属性四元组
	 */
	private static Object[] buildPolygonSdo(org.locationtech.jts.geom.Polygon polygon, int srid) {
		int dims = coordDims(polygon.getExteriorRing().getCoordinates());
		int ringCnt = 1 + polygon.getNumInteriorRing();
		int[] elemInfo = new int[ringCnt * 3];
		double[] ordinates = new double[0];
		int offset = 1;
		for (int i = 0; i < ringCnt; i++) {
			org.locationtech.jts.geom.LineString ring = (i == 0) ? polygon.getExteriorRing()
					: polygon.getInteriorRingN(i - 1);
			Coordinate[] cs = ring.getCoordinates();
			elemInfo[i * 3] = offset;
			elemInfo[i * 3 + 1] = (i == 0) ? 1003 : 2003;
			elemInfo[i * 3 + 2] = 1;
			double[] ringOrd = coordsToAry(cs, dims);
			double[] tmp = new double[ordinates.length + ringOrd.length];
			System.arraycopy(ordinates, 0, tmp, 0, ordinates.length);
			System.arraycopy(ringOrd, 0, tmp, ordinates.length, ringOrd.length);
			ordinates = tmp;
			offset += ringOrd.length / dims;
		}
		return new Object[] { dims * 1000 + 3, srid, elemInfo, ordinates };
	}

	/**
	 * 构造多线串的SDO属性:每条线一个(offset,2,1)三元组
	 *
	 * @param multiLine JTS多线串
	 * @param srid      坐标系ID
	 * @return SDO属性四元组
	 */
	private static Object[] buildMultiLineSdo(org.locationtech.jts.geom.MultiLineString multiLine, int srid) {
		int size = multiLine.getNumGeometries();
		int[] elemInfo = new int[size * 3];
		double[] ordinates = new double[0];
		int dims = 2;
		int offset = 1;
		for (int i = 0; i < size; i++) {
			Coordinate[] cs = multiLine.getGeometryN(i).getCoordinates();
			dims = coordDims(cs);
			double[] lineOrd = coordsToAry(cs, dims);
			elemInfo[i * 3] = offset;
			elemInfo[i * 3 + 1] = 2;
			elemInfo[i * 3 + 2] = 1;
			double[] tmp = new double[ordinates.length + lineOrd.length];
			System.arraycopy(ordinates, 0, tmp, 0, ordinates.length);
			System.arraycopy(lineOrd, 0, tmp, ordinates.length, lineOrd.length);
			ordinates = tmp;
			offset += lineOrd.length / dims;
		}
		return new Object[] { dims * 1000 + 6, srid, elemInfo, ordinates };
	}

	/**
	 * 构造多多边形的SDO属性:每个环一个(1003/2003)三元组,外环开启新的子多边形
	 *
	 * @param multiPolygon JTS多多边形
	 * @param srid         坐标系ID
	 * @return SDO属性四元组
	 */
	private static Object[] buildMultiPolygonSdo(org.locationtech.jts.geom.MultiPolygon multiPolygon, int srid) {
		int polyCnt = multiPolygon.getNumGeometries();
		// 先统计环数预分配elem_info
		int ringCnt = 0;
		for (int i = 0; i < polyCnt; i++) {
			ringCnt += 1 + ((org.locationtech.jts.geom.Polygon) multiPolygon.getGeometryN(i)).getNumInteriorRing();
		}
		int[] elemInfo = new int[ringCnt * 3];
		double[] ordinates = new double[0];
		int dims = 2;
		int offset = 1;
		int elemIdx = 0;
		for (int i = 0; i < polyCnt; i++) {
			org.locationtech.jts.geom.Polygon polygon = (org.locationtech.jts.geom.Polygon) multiPolygon
					.getGeometryN(i);
			int subRings = 1 + polygon.getNumInteriorRing();
			for (int r = 0; r < subRings; r++) {
				org.locationtech.jts.geom.LineString ring = (r == 0) ? polygon.getExteriorRing()
						: polygon.getInteriorRingN(r - 1);
				Coordinate[] cs = ring.getCoordinates();
				dims = coordDims(cs);
				double[] ringOrd = coordsToAry(cs, dims);
				elemInfo[elemIdx] = offset;
				elemInfo[elemIdx + 1] = (r == 0) ? 1003 : 2003;
				elemInfo[elemIdx + 2] = 1;
				elemIdx += 3;
				double[] tmp = new double[ordinates.length + ringOrd.length];
				System.arraycopy(ordinates, 0, tmp, 0, ordinates.length);
				System.arraycopy(ringOrd, 0, tmp, ordinates.length, ringOrd.length);
				ordinates = tmp;
				offset += ringOrd.length / dims;
			}
		}
		return new Object[] { dims * 1000 + 7, srid, elemInfo, ordinates };
	}

	/**
	 * 按elem_info三元组读取多边形(外环1003/内环2003,环结束位置以下一个三元组offset为界)
	 */
	private static Geometry readPolygon(double[] ordinates, int[] elemInfo, int dims,
			org.locationtech.jts.geom.GeometryFactory factory) {
		java.util.List<double[]> ringOrdinates = splitRingsByElemInfo(ordinates, elemInfo, dims);
		if (ringOrdinates.isEmpty()) {
			// 无elem_info时整体视作单个外环
			ringOrdinates.add(ordinates);
		}
		org.locationtech.jts.geom.LinearRing shell = null;
		java.util.List<org.locationtech.jts.geom.LinearRing> holes = new java.util.ArrayList<>();
		for (int i = 0, n = ringOrdinates.size(); i < n; i++) {
			int etype = (elemInfo != null && elemInfo.length > i * 3 + 1) ? elemInfo[i * 3 + 1] : 1003;
			org.locationtech.jts.geom.LinearRing ring = toRing(ringOrdinates.get(i), dims, factory);
			if (ring == null) {
				continue;
			}
			if (etype == 2003 && shell != null) {
				holes.add(ring);
			} else if (shell == null) {
				shell = ring;
			} else {
				// 出现第二个外环,超出单多边形语义
				return null;
			}
		}
		if (shell == null) {
			return null;
		}
		return factory.createPolygon(shell, holes.toArray(new org.locationtech.jts.geom.LinearRing[0]));
	}

	/**
	 * 按elem_info三元组读取多点
	 */
	private static Geometry readMultiPoint(double[] ordinates, int[] elemInfo, int dims,
			org.locationtech.jts.geom.GeometryFactory factory) {
		java.util.List<Coordinate> points = new java.util.ArrayList<>();
		if (elemInfo == null || elemInfo.length < 3) {
			for (int i = 0; i + dims <= ordinates.length; i += dims) {
				points.add(readCoord(ordinates, i, dims));
			}
		} else {
			for (int i = 0; i + 2 < elemInfo.length; i += 3) {
				int start = (elemInfo[i] - 1) * dims;
				int count = elemInfo[i + 2];
				for (int p = 0; p < count; p++) {
					int idx = start + p * dims;
					if (idx + dims <= ordinates.length) {
						points.add(readCoord(ordinates, idx, dims));
					}
				}
			}
		}
		if (points.isEmpty()) {
			return null;
		}
		return factory.createMultiPointFromCoords(points.toArray(new Coordinate[0]));
	}

	/**
	 * 按elem_info三元组(etype=2)读取多线串
	 */
	private static Geometry readMultiLine(double[] ordinates, int[] elemInfo, int dims,
			org.locationtech.jts.geom.GeometryFactory factory) {
		java.util.List<double[]> lines = splitRingsByElemInfo(ordinates, elemInfo, dims);
		if (lines.isEmpty()) {
			lines.add(ordinates);
		}
		org.locationtech.jts.geom.LineString[] lineStrings = new org.locationtech.jts.geom.LineString[lines.size()];
		for (int i = 0; i < lines.size(); i++) {
			lineStrings[i] = factory.createLineString(readCoords(lines.get(i), 0, lines.get(i).length / dims, dims));
		}
		return factory.createMultiLineString(lineStrings);
	}

	/**
	 * 按外环(1003)分组读取多多边形
	 */
	private static Geometry readMultiPolygon(double[] ordinates, int[] elemInfo, int dims,
			org.locationtech.jts.geom.GeometryFactory factory) {
		java.util.List<double[]> rings = splitRingsByElemInfo(ordinates, elemInfo, dims);
		if (rings.isEmpty()) {
			return null;
		}
		java.util.List<org.locationtech.jts.geom.Polygon> polygons = new java.util.ArrayList<>();
		org.locationtech.jts.geom.LinearRing shell = null;
		java.util.List<org.locationtech.jts.geom.LinearRing> holes = new java.util.ArrayList<>();
		for (int i = 0; i < rings.size(); i++) {
			int etype = (elemInfo != null && elemInfo.length > i * 3 + 1) ? elemInfo[i * 3 + 1] : 1003;
			org.locationtech.jts.geom.LinearRing ring = toRing(rings.get(i), dims, factory);
			if (ring == null) {
				continue;
			}
			if (etype == 1003) {
				if (shell != null) {
					polygons.add(factory.createPolygon(shell,
							holes.toArray(new org.locationtech.jts.geom.LinearRing[0])));
				}
				shell = ring;
				holes = new java.util.ArrayList<>();
			} else if (shell != null) {
				holes.add(ring);
			}
		}
		if (shell != null) {
			polygons.add(factory.createPolygon(shell, holes.toArray(new org.locationtech.jts.geom.LinearRing[0])));
		}
		if (polygons.isEmpty()) {
			return null;
		}
		return factory.createMultiPolygon(polygons.toArray(new org.locationtech.jts.geom.Polygon[0]));
	}

	/**
	 * 按elem_info三元组将ordinates切分为各元素(线/环)的坐标段
	 */
	private static java.util.List<double[]> splitRingsByElemInfo(double[] ordinates, int[] elemInfo, int dims) {
		java.util.List<double[]> segments = new java.util.ArrayList<>();
		if (elemInfo == null || elemInfo.length < 3) {
			return segments;
		}
		for (int i = 0; i + 2 < elemInfo.length; i += 3) {
			int start = (elemInfo[i] - 1) * dims;
			int end = (i + 3 < elemInfo.length) ? (elemInfo[i + 3] - 1) * dims : ordinates.length;
			if (start < 0 || end > ordinates.length || start >= end) {
				continue;
			}
			double[] seg = new double[end - start];
			System.arraycopy(ordinates, start, seg, 0, seg.length);
			segments.add(seg);
		}
		return segments;
	}

	/**
	 * 坐标段转闭合LinearRing(JTS要求首尾重合且至少4个点)
	 */
	private static org.locationtech.jts.geom.LinearRing toRing(double[] ordinates, int dims,
			org.locationtech.jts.geom.GeometryFactory factory) {
		int pointCnt = ordinates.length / dims;
		if (pointCnt < 3) {
			return null;
		}
		Coordinate[] cs = readCoords(ordinates, 0, pointCnt, dims);
		Coordinate first = cs[0];
		Coordinate last = cs[cs.length - 1];
		if (first.getX() != last.getX() || first.getY() != last.getY()) {
			Coordinate[] closed = new Coordinate[cs.length + 1];
			System.arraycopy(cs, 0, closed, 0, cs.length);
			closed[cs.length] = new Coordinate(first.getX(), first.getY(),
					Double.isNaN(first.getZ()) ? Double.NaN : first.getZ());
			cs = closed;
		}
		return factory.createLinearRing(cs);
	}

	/**
	 * 坐标平铺数组按dims步长读取
	 */
	private static Coordinate[] readCoords(double[] ordinates, int offset, int pointCnt, int dims) {
		Coordinate[] cs = new Coordinate[pointCnt];
		for (int i = 0; i < pointCnt; i++) {
			int idx = offset + i * dims;
			cs[i] = readCoord(ordinates, idx, dims);
		}
		return cs;
	}

	private static Coordinate readCoord(double[] ordinates, int idx, int dims) {
		return new Coordinate(ordinates[idx], ordinates[idx + 1],
				(dims > 2 && idx + 2 < ordinates.length) ? ordinates[idx + 2] : Double.NaN);
	}

	private static double[] coordToAry(Coordinate c, int dims) {
		double[] ary = (dims > 2) ? new double[3] : new double[2];
		ary[0] = c.getX();
		ary[1] = c.getY();
		if (dims > 2) {
			ary[2] = c.getZ();
		}
		return ary;
	}

	private static double[] coordsToAry(Coordinate[] cs, int dims) {
		double[] ary = new double[cs.length * dims];
		for (int i = 0; i < cs.length; i++) {
			ary[i * dims] = cs[i].getX();
			ary[i * dims + 1] = cs[i].getY();
			if (dims > 2) {
				ary[i * dims + 2] = cs[i].getZ();
			}
		}
		return ary;
	}

	/**
	 * 以首个坐标是否存在有效Z判定维度
	 */
	private static int coordDims(Coordinate[] cs) {
		if (cs != null && cs.length > 0 && !Double.isNaN(cs[0].getZ())) {
			return 3;
		}
		return 2;
	}

	private static double asDouble(Object value) {
		return (value instanceof Number) ? ((Number) value).doubleValue() : Double.NaN;
	}

	/**
	 * 数据库返回的NUMBER数组(SDO_ELEM_INFO)转int数组,兼容BigDecimal[]、Number[]等形态
	 */
	private static int[] sdoArrayToInts(Object arrayValue) {
		if (arrayValue == null) {
			return null;
		}
		try {
			Object array = (arrayValue instanceof java.sql.Array) ? ((java.sql.Array) arrayValue).getArray()
					: arrayValue;
			int size = java.lang.reflect.Array.getLength(array);
			int[] result = new int[size];
			for (int i = 0; i < size; i++) {
				Object item = java.lang.reflect.Array.get(array, i);
				result[i] = (item instanceof Number) ? ((Number) item).intValue() : 0;
			}
			return result;
		} catch (Exception e) {
			logger.debug("failed to read sdo elem info:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 数据库返回的NUMBER数组(SDO_ORDINATE)转double数组
	 */
	private static double[] sdoArrayToDoubles(Object arrayValue) {
		if (arrayValue == null) {
			return null;
		}
		try {
			Object array = (arrayValue instanceof java.sql.Array) ? ((java.sql.Array) arrayValue).getArray()
					: arrayValue;
			int size = java.lang.reflect.Array.getLength(array);
			double[] result = new double[size];
			for (int i = 0; i < size; i++) {
				Object item = java.lang.reflect.Array.get(array, i);
				result[i] = (item instanceof Number) ? ((Number) item).doubleValue() : Double.NaN;
			}
			return result;
		} catch (Exception e) {
			logger.debug("failed to read sdo ordinates:{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 将EWKB归一化为标准OGC WKB:JTS的WKBReader不识别EWKB的类型字高位标志位和SRID字段,
	 * 递归重写类型字(高位标志转OGC的1000/2000偏移),坐标数据原样拷贝,同时提取顶层SRID
	 *
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
	 * 递归拷贝单个geometry,归一化类型字并剔除SRID字段
	 *
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
	 * 判断字符串是否为EWKB hex形式
	 *
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
