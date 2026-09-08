package org.sagacity.sqltoy.utils;

/**
 * @project sagacity-sqltoy
 * @description geometry空间类型处理门面,jts-core为可选依赖
 *              update 2026-9-8 JTS类型引用全部剥离至JtsGeometryCodec,本类字节码不含任何
 *              org.locationtech引用(仅Class.forName字符串探测),任意环境下加载/验证本类
 *              均不依赖jts-core;JtsGeometryCodec仅在hasJts()通过后的转发调用中才被类加载。
 *              此前JTS引用与字符串工具同放在本类,无jts环境调用isGeometryTypeName等纯字符串
 *              方法时,类验证阶段解析同类中instanceof/数组创建等JTS符号引用直接抛
 *              NoClassDefFoundError,hasJts()执行期判断无法保护类验证期
 *              支持的值形式:WKT/EWKT字符串、postgis EWKB hex字符串、postgis EWKB二进制、
 *              mysql的4字节SRID前缀+WKB二进制、JTS Geometry对象
 * @author zhongxuchen
 * @date 2026-8-28
 */
public class GeometryTypeUtil {

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

	private GeometryTypeUtil() {
	}

	/**
	 * jts-core是否在classpath中
	 * 
	 * @return
	 */
	public static boolean hasJts() {
		return HAS_JTS;
	}

	/**
	 * 判断数据库元数据返回的列类型名是否属于geometry空间类型(传入大写形式)
	 * 
	 * @param columnTypeName
	 * @return
	 */
	public static boolean isGeometryTypeName(String columnTypeName) {
		if (columnTypeName == null) {
			return false;
		}
		// db2元数据对UDT列返回带引号的模式限定名(如"DB2GSE"."ST_GEOMETRY"),剥除引号统一判定
		columnTypeName = columnTypeName.replace("\"", "");
		switch (columnTypeName) {
		// mysql元数据对空间列可能返回具体子类型名,统一映射为GEOMETRY
		case "GEOMETRY":
		case "GEOGRAPHY":
		case "SDO_GEOMETRY":
		case "ST_GEOMETRY":
		case "POINT":
		case "LINESTRING":
		case "POLYGON":
		case "MULTIPOINT":
		case "MULTILINESTRING":
		case "MULTIPOLYGON":
		case "GEOMETRYCOLLECTION":
			return true;
		default:
			// dm(sysgeo/DMGEO.ST_GEOMETRY)、db2gse(DB2GSE.ST_GEOMETRY)等模式限定名
			return columnTypeName.endsWith(".ST_GEOMETRY");
		}
	}

	/**
	 * 将JTS Geometry对象转为WKT字符串,非JTS对象或JTS不可用时返回null
	 * 
	 * @param value
	 * @return
	 */
	public static String toWKT(Object value) {
		return HAS_JTS ? JtsGeometryCodec.toWKT(value) : null;
	}

	/**
	 * 将数据库返回的空间值(WKT/EWKT字符串、EWKB hex、二进制WKB/EWKB、PGobject、 oracle SDO_GEOMETRY
	 * Struct等)解析为JTS Geometry
	 * 
	 * @param jdbcValue
	 * @return 解析失败返回null,交回框架按常规类型处理
	 */
	public static Object parse(Object jdbcValue) {
		return HAS_JTS ? JtsGeometryCodec.parse(jdbcValue) : null;
	}

	/**
	 * 将mysql系(geometry列)读回的内部格式byte[]解码为WKT:mysql内部格式为
	 * 4字节SRID(小端)+标准WKB,列元数据已确证为geometry列故无条件剥离前缀
	 * (parseBytes的嗅探在SRID=0时前4字节全0会与大端WKB误判,不能用于本场景)
	 * 
	 * @param bytes mysql geometry内部格式字节
	 * @return WKT文本,jts不可用或解码失败返回null
	 */
	public static String mysqlGeometryBytesToWKT(byte[] bytes) {
		return HAS_JTS ? JtsGeometryCodec.mysqlGeometryBytesToWKT(bytes) : null;
	}

	/**
	 * 将数据库返回的空间值统一转为WKT字符串(String目标类型场景,如PG的EWKB hex转WKT)
	 * 
	 * @param jdbcValue
	 * @return 解析失败返回null
	 */
	public static String toWKTString(Object jdbcValue) {
		return HAS_JTS ? JtsGeometryCodec.toWKTString(jdbcValue) : null;
	}

	/**
	 * 将空间值(JTS Geometry或WKT/EWKT字符串)解析为oracle SDO_GEOMETRY的属性四元组,
	 * 供OracleSdoUtil构造驱动STRUCT完成绑定(save/update/saveOrUpdate共用)
	 * 
	 * @param value 空间值,支持JTS Geometry、WKT/EWKT字符串等parse()可识别形式
	 * @return Object[]{gtype,srid,elemInfo[],ordinates[]},无法识别返回null
	 */
	public static Object[] toSdoAttributes(Object value) {
		return HAS_JTS ? JtsGeometryCodec.toSdoAttributes(value) : null;
	}

	/**
	 * 解析oracle读回的SDO_GEOMETRY Struct为JTS Geometry
	 * 
	 * @param struct java.sql.Struct实例(属性:gtype,srid,point,elem_info,ordinates)
	 * @return 解析失败或类型不支持返回null
	 */
	public static Object parseSdoStruct(java.sql.Struct struct) {
		return HAS_JTS ? JtsGeometryCodec.parseSdoStruct(struct) : null;
	}

	/**
	 * 解析WKB属性布局的Struct为JTS Geometry(达梦DMGEO/sysgeo.ST_GEOMETRY,读回
	 * dm.jdbc.driver.DmdbStruct,属性布局:(srid,标准OGC WKB
	 * blob,版本),已实库验证POINT/LINESTRING)
	 * 
	 * @param struct java.sql.Struct实例(达梦ST_Geometry)
	 * @return 解析失败返回null,交回框架按原值处理
	 */
	public static Object parseWkbStruct(java.sql.Struct struct) {
		return HAS_JTS ? JtsGeometryCodec.parseWkbStruct(struct) : null;
	}

	/**
	 * 将WKB属性布局的Struct(达梦ST_Geometry)转为WKT字符串
	 * 
	 * @param struct java.sql.Struct实例
	 * @return WKT文本,jts不可用或解析失败返回null
	 */
	public static String wkbStructToWKT(java.sql.Struct struct) {
		return HAS_JTS ? JtsGeometryCodec.wkbStructToWKT(struct) : null;
	}
}
