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
	 * update 2026-9-10 db2的GSE空间扩展schema(DB2GSE)是否存在:12.1起内置空间引擎(非限定
	 * SYSIBM函数)与GSE扩展可并存(实测12.1.5容器GSE仍启用),且内置ST_GEOMETRY与
	 * db2gse.ST_GEOMETRY为不同UDT(函数产物与列类型错配报-408),geometry参数化包装须按此
	 * 探测分派db2gse前缀形态或内置非限定形态;非db2为null,db2探测异常为TRUE(保持既有db2gse形态)
	 */
	private final Boolean hasGseSchema;

	/**
	 * update 2026-9-10 字符串字面量内反斜杠是否为转义字符(mysql系为true):由DataSourceUtils
	 * 在构建档案解析出dbType时一并判定,运行期直取,统一供字面量掩码与like ESCAPE子句形态判定;
	 * 运行期可变的backslashEscaping全局开关不入本档案(实时语义由SqlConfigParseUtils.isBackslashEscapeDialect分层叠加)
	 */
	private final boolean backslashEscape;

	public DBProfile(String url, String dialect, int dbType, String productName, int majorVersion,
			PGobjectHolder pgObjectHolder, Boolean hasGseSchema, boolean backslashEscape) {
		this.url = url;
		this.dialect = dialect;
		this.dbType = dbType;
		this.productName = productName;
		this.majorVersion = majorVersion;
		this.pgObjectHolder = pgObjectHolder;
		this.hasGseSchema = hasGseSchema;
		this.backslashEscape = backslashEscape;
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

	public Boolean getHasGseSchema() {
		return hasGseSchema;
	}

	/**
	 * update 2026-9-10 本库字符串字面量内反斜杠是否为转义字符(mysql系为true,构建档案时已按dbType判定)
	 * 
	 * @return true表示字面量内\'不终结字面量(mysql系)
	 */
	public boolean isBackslashEscape() {
		return backslashEscape;
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
