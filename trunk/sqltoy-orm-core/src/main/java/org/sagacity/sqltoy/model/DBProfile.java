package org.sagacity.sqltoy.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * update 2026-9-6 连接维度的数据库特征档案:以JDBC URL为缓存key(同一URL必然指向同一数据库实例)
 * 一次性统筹解析并缓存dialect/dbType/productName/majorVersion及PG系扩展类型绑定句柄,
 * 取代此前DataSourceUtils(dialect/dbType)与SqlUtil(PGobjectHolder)各自独立解析与缓存的分散形态。
 * 由DataSourceUtils.getDBProfile(connection)创建,进程级缓存;不缓存运行期可变的属性
 * (如dialectMap自定义映射、backslashEscaping全局开关)以保持既有实时语义。
 */
public class DBProfile {

	/** JDBC连接URL(缓存key) */
	private final String url;

	/** 方言识别名(如postgresql/opengauss/starrocks) */
	private final String dialect;

	/** 数据库类型,参见DataSourceUtils.DBType */
	private final int dbType;

	/** 驱动上报的产品名 */
	private final String productName;

	/** 数据库主版本号 */
	private final int majorVersion;

	/** PG系扩展类型(json/vector/geometry)的PGobject绑定句柄,非PG系或解析失败为null */
	private final PGobjectHolder pgObjectHolder;

	/**
	 * update 2026-9-8 sqlserver是否存在原生json类型(2025 GA/17.x正式版引入):merge的
	 * using子查询对原生json列须convert(json,?)定型,对nvarchar承载列convert报 "Type json is not a
	 * defined system type"(本机17.0.4075预览版实测无json类型);
	 * 以convert探针实测判定,非sqlserver或探测失败为Boolean.FALSE
	 */
	private final Boolean hasJsonType;

	public DBProfile(String url, String dialect, int dbType, String productName, int majorVersion,
			PGobjectHolder pgObjectHolder, Boolean hasJsonType) {
		this.url = url;
		this.dialect = dialect;
		this.dbType = dbType;
		this.productName = productName;
		this.majorVersion = majorVersion;
		this.pgObjectHolder = pgObjectHolder;
		this.hasJsonType = hasJsonType;
	}

	public String getUrl() {
		return url;
	}

	public String getDialect() {
		return dialect;
	}

	public int getDbType() {
		return dbType;
	}

	public String getProductName() {
		return productName;
	}

	public int getMajorVersion() {
		return majorVersion;
	}

	public PGobjectHolder getPgObjectHolder() {
		return pgObjectHolder;
	}

	public Boolean getHasJsonType() {
		return hasJsonType;
	}

	/**
	 * PG系扩展类型(json/jsonb/vector/geometry等)的PGobject反射句柄: 按URL
	 * scheme选择同源驱动的PGobject类构造(实测PGobject不能跨驱动setObject, 报Can't infer the SQL
	 * type);解析失败为NULL哨兵(holder内constructor为null)。
	 * PGobject实例不可跨参数复用(驱动持有引用至execute,批量addBatch复用会串值),每参数新建。
	 */
	public static class PGobjectHolder {
		final Constructor<?> constructor;
		final Method setType;
		final Method setValue;

		public PGobjectHolder(Constructor<?> constructor, Method setType, Method setValue) {
			this.constructor = constructor;
			this.setType = setType;
			this.setValue = setValue;
		}

		/** 反射构造指定类型名的PGobject实例,失败返回null由调用方回退 */
		public Object create(String typeName, String value) {
			try {
				if (constructor == null) {
					return null;
				}
				Object pgObject = constructor.newInstance();
				setType.invoke(pgObject, typeName);
				setValue.invoke(pgObject, value);
				return pgObject;
			} catch (Throwable e) {
				return null;
			}
		}
	}
}
