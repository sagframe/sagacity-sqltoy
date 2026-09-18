package org.sagacity.sqltoy.utils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description oracle SDO_GEOMETRY空间类型的驱动层绑定工具,oracle.sql.ARRAY/STRUCT等
 *              ojdbc内部类通过反射隔离(ojdbc为可选依赖),jts解析由GeometryTypeUtil承担;
 *              设计动机:oracle的SDO_GEOMETRY列不接受VARCHAR隐式转换(21c实测ORA-00932),
 *              SQL层SDO_UTIL.FROM_WKTGEOMETRY包装在参数为null时报ORA-29532且无法覆盖
 *              insert/update语句,故统一在驱动层构造SDO STRUCT绑定,
 *              save/saveAll/update/saveOrUpdate/mergeIgnore共用同一套绑定逻辑
 * @author zhongxuchen
 * @date 2026-9-6
 */
public class OracleSdoUtil {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(OracleSdoUtil.class);

	/**
	 * oracle SDO_GEOMETRY的UDT类型名(带schema全名,unset时驱动按同名非限定解析)
	 */
	public static final String SDO_TYPE_NAME = "MDSYS.SDO_GEOMETRY";

	private static final String ARRAY_DESCRIPTOR_CLASS = "oracle.sql.ArrayDescriptor";
	private static final String ARRAY_CLASS = "oracle.sql.ARRAY";
	private static final String STRUCT_DESCRIPTOR_CLASS = "oracle.sql.StructDescriptor";
	private static final String STRUCT_CLASS = "oracle.sql.STRUCT";
	private static final String ELEM_INFO_ARRAY_TYPE = "MDSYS.SDO_ELEM_INFO_ARRAY";
	private static final String ORDINATE_ARRAY_TYPE = "MDSYS.SDO_ORDINATE_ARRAY";

	/**
	 * ojdbc反射句柄,静态一次性探测(线程安全:不可变对象,volatile保证可见性)
	 */
	private static volatile ReflectHandles handles;

	private OracleSdoUtil() {
	}

	/**
	 * ojdbc是否可用(ARRAY/STRUCT反射句柄齐备)
	 * 
	 * @return
	 */
	public static boolean isAvailable() {
		return resolveHandles() != null;
	}

	/**
	 * 将SDO属性四元组构造为oracle.sql.STRUCT实例
	 * 
	 * @param conn      数据库连接(描述符与连接绑定)
	 * @param gtype     SDO_GTYPE(dims*1000+几何类型,如2003为二维多边形)
	 * @param srid      坐标系ID,0或负数表示未指定
	 * @param elemInfo  SDO_ELEM_INFO三元组数组,可传null
	 * @param ordinates SDO_ORDINATE坐标平铺数组
	 * @return STRUCT实例,ojdbc不可用或构造失败返回null(调用方回退setString)
	 */
	public static Object buildSdoGeometry(Connection conn, int gtype, int srid, int[] elemInfo, double[] ordinates) {
		ReflectHandles hs = resolveHandles();
		if (hs == null || conn == null || ordinates == null || ordinates.length == 0) {
			return null;
		}
		try {
			// update 2026-9-8 实测Hikari等连接池的代理连接传入ArrayDescriptor/STRUCT构造器
			// 时UDT类型解析失败("Unable to resolve type",catch后静默null回退setString报
			// ORA-00932),须unwrap为物理OracleConnection再构造
			Connection realConn = unwrap(conn);
			if (realConn == null) {
				return null;
			}
			Object elemDesc = hs.arrayDescCtor.newInstance(ELEM_INFO_ARRAY_TYPE, realConn);
			Object ordDesc = hs.arrayDescCtor.newInstance(ORDINATE_ARRAY_TYPE, realConn);
			Object elemArr = null;
			if (elemInfo != null && elemInfo.length > 0) {
				BigDecimal[] elems = new BigDecimal[elemInfo.length];
				for (int i = 0; i < elemInfo.length; i++) {
					elems[i] = BigDecimal.valueOf(elemInfo[i]);
				}
				elemArr = hs.arrayCtor.newInstance(elemDesc, realConn, elems);
			}
			Object ordArr = hs.arrayCtor.newInstance(ordDesc, realConn, ordinates);
			Object sdoDesc = hs.structDescCtor.newInstance(SDO_TYPE_NAME, realConn);
			Object[] attrs = new Object[] { BigDecimal.valueOf(gtype), (srid > 0) ? BigDecimal.valueOf(srid) : null,
					null, elemArr, ordArr };
			return hs.structCtor.newInstance(sdoDesc, realConn, attrs);
		} catch (Throwable e) {
			logger.warn("failed to build oracle SDO_GEOMETRY struct: {}", String.valueOf(e.getMessage()));
			return null;
		}
	}

	/**
	 * update 2026-9-8 连接池代理连接(实现JDBC4.0 Wrapper)解包为物理OracleConnection:
	 * oracle.sql描述符类须以物理连接解析UDT类型名,代理连接报Unable to resolve type
	 * 
	 * @param conn 池化或物理连接
	 * @return 物理连接,解包失败返回原连接
	 */
	private static Connection unwrap(Connection conn) {
		try {
			if (conn.isWrapperFor(oracle.jdbc.OracleConnection.class)) {
				return conn.unwrap(oracle.jdbc.OracleConnection.class);
			}
		} catch (Throwable e) {
			logger.debug("unwrap oracle connection failed: {}", String.valueOf(e.getMessage()));
		}
		return conn;
	}

	/**
	 * SDO_GEOMETRY列的null值绑定:UDT类型二参setNull被驱动拒绝(ORA-17068要求带类型名), 须使用三参setNull(idx,
	 * STRUCT, typeName)
	 * 
	 * @param pst        预编译语句
	 * @param paramIndex 参数位置下标(从1开始)
	 * @throws SQLException 带类型名的setNull均失败时抛出
	 */
	public static void setNull(PreparedStatement pst, int paramIndex) throws SQLException {
		try {
			pst.setNull(paramIndex, java.sql.Types.STRUCT, SDO_TYPE_NAME);
		} catch (SQLException e) {
			try {
				// 部分驱动/库对非限定名或大小写敏感,去schema前缀重试
				pst.setNull(paramIndex, java.sql.Types.STRUCT, "SDO_GEOMETRY");
			} catch (SQLException e2) {
				throw e;
			}
		}
	}

	/**
	 * 反射句柄解析(进程内缓存)
	 * 
	 * @return
	 */
	private static ReflectHandles resolveHandles() {
		ReflectHandles hs = handles;
		if (hs != null) {
			return hs.available ? hs : null;
		}
		synchronized (OracleSdoUtil.class) {
			if (handles == null) {
				handles = createHandles();
			}
			hs = handles;
		}
		return hs.available ? hs : null;
	}

	/**
	 * 探测并缓存oracle.sql各类的构造反射句柄
	 * 
	 * @return
	 */
	private static ReflectHandles createHandles() {
		ReflectHandles hs = new ReflectHandles();
		try {
			Class<?> arrayDescClass = Class.forName(ARRAY_DESCRIPTOR_CLASS);
			Class<?> arrayClass = Class.forName(ARRAY_CLASS);
			Class<?> structDescClass = Class.forName(STRUCT_DESCRIPTOR_CLASS);
			Class<?> structClass = Class.forName(STRUCT_CLASS);
			hs.arrayDescCtor = findCtor(arrayDescClass, String.class, Connection.class);
			hs.arrayCtor = findCtor(arrayClass, null, Connection.class, Object.class);
			hs.structDescCtor = findCtor(structDescClass, String.class, Connection.class);
			hs.structCtor = findCtor(structClass, null, Connection.class, Object[].class);
			hs.available = (hs.arrayDescCtor != null && hs.arrayCtor != null && hs.structDescCtor != null
					&& hs.structCtor != null);
		} catch (Throwable e) {
			hs.available = false;
		}
		return hs;
	}

	/**
	 * 按参数类型精确匹配构造器(firstType为null时匹配任意首位类型,用于区分byte[]形态)
	 * 
	 * @param clazz     目标类
	 * @param firstType 首参类型,null表示不限定
	 * @param restTypes 其余参数类型
	 * @return
	 */
	private static Constructor<?> findCtor(Class<?> clazz, Class<?> firstType, Class<?>... restTypes) {
		for (Constructor<?> c : clazz.getDeclaredConstructors()) {
			Class<?>[] pts = c.getParameterTypes();
			if (pts.length != restTypes.length + 1) {
				continue;
			}
			if (firstType != null && pts[0] != firstType) {
				continue;
			}
			boolean matched = true;
			for (int i = 0; i < restTypes.length; i++) {
				if (pts[i + 1] != restTypes[i]) {
					matched = false;
					break;
				}
			}
			if (matched) {
				return c;
			}
		}
		return null;
	}

	/**
	 * ojdbc反射句柄容器
	 */
	private static class ReflectHandles {
		Constructor<?> arrayDescCtor;
		Constructor<?> arrayCtor;
		Constructor<?> structDescCtor;
		Constructor<?> structCtor;
		boolean available;
	}
}
