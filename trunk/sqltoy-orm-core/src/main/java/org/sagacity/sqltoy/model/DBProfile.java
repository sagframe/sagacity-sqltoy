package org.sagacity.sqltoy.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

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

	/**
	 * update 2026-9-15 连接探测的真实数据库类型:与dbType分离——dbType/dialect可被
	 * sqltoyContext.getDialect()配置覆盖(OB配mysql方言→dbType=MYSQL),
	 * 而realDBType/realDialect始终保存连接探测的真实库类型,供like转义等
	 * 需要感知真实库能力的场景使用(与productName/isOceanBase等事实字段同层)
	 */
	private int realDBType;

	/** 连接探测的真实方言识别名(与dialect分离,语义同realDBType) */
	private String realDialect;

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
		this.realDBType = dbType;
		this.realDialect = dialect;
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

	/** 连接探测的真实数据库类型(不被配置dialect覆盖) */
	public int getRealDBType() {
		return realDBType;
	}

	/** 连接探测的真实方言识别名(不被配置dialect覆盖) */
	public String getRealDialect() {
		return realDialect;
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

	// ==================== update 2026-9-12 优化步骤1扩容:族判定与声明型事实 ====================
	// 设计原则:DBProfile只承载"可探测/可声明的数据库事实",行为策略仍归Dialect实现;
	// 全部为getter派生(零新存储字段、零构造签名改动、零行为变化),供操作上下文重构
	// (步骤3)时作为唯一权威参数消费,替代执行链上(Integer dbType,String dialect)双轨
	// 与DialectExtUtils.isOceanBaseAsMysql()的ThreadLocal actuallyDBType隐式全局态。

	/**
	 * 是否为OceanBase系列数据库:按产品名特征判定(mysql租户模式驱动上报 "MySQL
	 * 5.7.25-OceanBase_CE...",oracle租户模式产品名同样携带OceanBase字样),
	 * 独立于dbType——OB按mysql方言配置时执行链dbType解析为MYSQL,但产品名事实不变。 update 2026-9-14
	 * 已完成收编:DialectExtUtils.isOceanBaseAsMysql()(ThreadLocal的
	 * actuallyDBType隐式全局态)已删除,vector包装、saveOrUpdateAll计数豁免、geometry wrap、
	 * getTables元数据绕行等分派点统一改用本方法(经profile参数显式传递,并行场景不依赖线程态)。
	 */
	public boolean isOceanBase() {
		return productName != null && productName.toUpperCase(Locale.ROOT).contains("OCEANBASE");
	}

	/**
	 * mysql协议族(语法谱系):反引号引用、string_to_vector、date_format、ifnull等分派组。
	 * 与ReservedWordsUtil/FunctionUtils等处的mysql组一致(不含tdengine——时序库仅引号策略同组)。
	 */
	public boolean isMysqlFamily() {
		return dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB || dbType == DBType.DORIS
				|| dbType == DBType.STARROCKS;
	}

	/**
	 * oracle谱系:merge into/sequence.nextval/nvl、NCLOB null绑定等分派组
	 * (与OracleDialect/DMDialect/OceanBaseDialect的NVL_FUNCTION=nvl组一致)。
	 * 注意:OB按mysql方言配置时实际策略由isOceanBase()+配置方言共同决定。
	 */
	public boolean isOracleFamily() {
		return dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM
				|| dbType == DBType.OCEANBASE;
	}

	/**
	 * PG系(json/vector/geometry经同源驱动PGobject绑定):与SqlUtil.isPGFamily定义
	 * 逐字一致(不含oscar——神通为老pgjdbc深度魔改衍生,PGobject绑定实测不适用)。
	 */
	public boolean isPGFamily() {
		return isPGFamily(dbType);
	}

	/**
	 * update 2026-9-16 PG系dbType白名单的静态单一事实源:供无profile实例的场景
	 * (DataSourceUtils.resolvePGobjectHolder构建档案前的探测守卫)与实例方法共用,
	 * 消除此前SqlUtil/JSONTypeUtil/DBProfile三处逐字拷贝的漂移风险。
	 * 不含oscar——神通为老pgjdbc深度魔改衍生,无org.postgresql包路径同构类,
	 * PGobject绑定实测不适用(跨驱动setObject必败),其json/vector以setString绑定为正确形态
	 *
	 * @param dbType 数据库类型,参见DataSourceUtils.DBType
	 * @return true表示PG系内核(PGobject绑定适用范围)
	 */
	public static boolean isPGFamily(int dbType) {
		return dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.OPENGAUSS
				|| dbType == DBType.MOGDB || dbType == DBType.GAUSSDB || dbType == DBType.STARDB
				|| dbType == DBType.VASTBASE || dbType == DBType.KINGBASE;
	}

	/**
	 * openGauss内核族(继承OpenGaussDialect):merge using select的wrapSelectFields、 cast(?
	 * as json/vector)分派组,与DialectUtils.getSaveOrUpdateSql的og组一致(含oscar)。
	 */
	public boolean isGaussFamily() {
		return dbType == DBType.GAUSSDB || dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB
				|| dbType == DBType.VASTBASE || dbType == DBType.STARDB || dbType == DBType.OSCAR;
	}

	/**
	 * update 2026-9-12 声明型事实:null判定函数名(弹性修改的nvl(?,col)包裹)。
	 * 与各方言NVL_FUNCTION常量同源(逐方言核实:OracleDialect/DB2Dialect/DMDialect/
	 * OceanBaseDialect=nvl,MySqlDialect/TidbDialect/DorisDialect/ClickHouseDialect/
	 * SqliteDialect/ImpalaDialect/HanaDialect=ifnull,PostgreSqlDialect(含H2继承)=COALESCE,
	 * OpenGaussDialect(含GaussDB/MogDB/Vastbase/StarDB/Oscar继承)/KingbaseDialect=NVL,
	 * SqlServerDialect=isnull;DefaultDialect兜底ifnull)。
	 * 预期在操作上下文重构(步骤3)中收编为唯一传递形态,替代nullFunction字符串参数 在save/update链路的手工逐层传递。
	 */
	/**
	 * update 2026-9-14 按SQL标准(ANSI COALESCE)统一空值判定函数:既有各库方言
	 * (nvl/ifnull/isnull/NVL)全部原生支持COALESCE,不再按库分派;短路求值更优
	 */
	public String getNullFunction() {
		// oracle族保留NVL(扩展类型VECTOR/SDO_GEOMETRY对COALESCE的绑定变量推断
		// 不兼容,实测ORA-00932);其余库统一ANSI标准COALESCE
		if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
			return "nvl";
		}
		return "COALESCE";
	}

	/**
	 * update 2026-9-12 声明型事实:驱动是否支持可更新结果集(CONCUR_UPDATABLE)——
	 * updateSaveFetch/updateFetch行级回写(rs.updateXXX/updateRow)的底层依赖。
	 * 实测clickhouse与hana(ngdbc)直接拒绝该游标形态,两者方言已显式抛
	 * UnsupportedOperationException;本事实为步骤3上下文收编后的统一判定依据, 并供上层应用在能力探测场景直取(替代试错)。
	 */
	public boolean supportsUpdatableResultSet() {
		return dbType != DBType.CLICKHOUSE && dbType != DBType.HANA;
	}

	/**
	 * update 2026-9-12 优化步骤3:构建有效执行视图——显式配置dialect时,执行链的
	 * dialect/dbType以配置为准(processDataSource既有语义:配置覆盖后getDBType(dialect)),
	 * 而productName/isOceanBase/pgobjectHolder/backslashEscape/hasGseSchema等
	 * 真实连接事实保留本档案值——使isOceanBase()等真实库事实在配置覆盖场景(OB按 mysql方言配置)依然可用,替代ThreadLocal
	 * getActuallyDBType()的独立判据。 本体字段全final,返回新实例;配置值与本体一致时返回自身(零分配)。
	 */
	public DBProfile asEffective(int effectiveDbType, String effectiveDialect) {
		if (effectiveDbType == dbType && dialect != null && dialect.equals(effectiveDialect)) {
			return this;
		}
		DBProfile effective = new DBProfile(url, effectiveDialect, effectiveDbType, productName, majorVersion,
				pgObjectHolder, hasGseSchema, backslashEscape);
		// update 2026-9-15 配置覆盖时保留连接探测的真实库类型(realDBType/realDialect)
		effective.realDBType = this.dbType;
		effective.realDialect = this.dialect;
		return effective;
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
