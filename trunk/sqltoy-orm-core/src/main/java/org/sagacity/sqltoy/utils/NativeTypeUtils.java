package org.sagacity.sqltoy.utils;

import java.util.Locale;

import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-11-21 @Column(nativeType=..)特殊类型族的解析期归一与消费点方言翻译。
 *
 * <p>背景:quickvo按<b>开发期源库</b>生成nativeType(源库物理类型名,如postgresql的jsonb/text[]),
 * 但消费点(merge into的using-select cast、DDL生成)运行在<b>目标方言</b>上下文——源方言类型名
 * 原样拼入目标SQL即产生非法cast/非法DDL(实测:jsonb在h2/dm、text[]在h2/dm等)。常规类型由
 * @Column(type=jdbc类型)跨库承载(nativeType不参与),nativeType实际只服务特殊类型族。
 *
 * <p>机制(解析期分类+消费点翻译):
 * <ul>
 * <li>解析期:{@link #specialFamily(String, Integer)}将nativeType别名(含jdbcType辅助)归一为
 * 逻辑族——json/jsonb/bson/vector/geometry/array,存FieldMeta供消费点取用</li>
 * <li>merge cast消费点:{@link #specialCastType(int, String, String)}按目标方言翻译cast目标,
 * 不适用返回null(数组回落组件类型推断链、其余回落裸?)</li>
 * <li>DDL消费点:convertType按族分派(pg内核系保留源精度原样,其余走jsonCarrierType等承载映射,
 * 数组族非pg目标回落组件链)</li>
 * </ul>
 *
 * <p>边界:族无法识别(如dm的具名VARRAY类型名、db2gse.st_geometry形态)时族为null,消费点
 * <b>原样透传nativeType</b>——用户显式声明的目标方言专属类型保持精确控制语义不变。
 *
 * @author zsy 2026-11-21
 */
public class NativeTypeUtils {

	/** json族(源json:pg/oracle 21c+/mysql等) */
	public static final String FAMILY_JSON = "json";

	/** jsonb族(pg系专属,精度高于json) */
	public static final String FAMILY_JSONB = "jsonb";

	/** bson族 */
	public static final String FAMILY_BSON = "bson";

	/** vector向量族(源别名:vector/floatvector) */
	public static final String FAMILY_VECTOR = "vector";

	/** geometry空间族(源别名:geometry/st_geometry/db2gse.st_geometry等) */
	public static final String FAMILY_GEOMETRY = "geometry";

	/** 数组族(pg形态:text[]/varchar[]/_varchar等) */
	public static final String FAMILY_ARRAY = "array";

	/** uuid族(pg源的uuid列,java承载String) */
	public static final String FAMILY_UUID = "uuid";

	private NativeTypeUtils() {
	}

	/**
	 * 判断是否pg内核系10库(postgresql/pg14/openGauss系6库/kingbase/highgo)——jsonb/bson/数组
	 * 的原生支持域,nativeType源形态在此域内原样有效
	 *
	 * @param dbType 目标方言
	 * @return true表示pg内核系
	 */
	public static boolean isPgKernel(int dbType) {
		return dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE
				|| dbType == DBType.HIGHGO;
	}

	/**
	 * 解析期归一:nativeType别名映射为特殊类型逻辑族(与产品开发库无关的中间形态)
	 *
	 * @param nativeType @Column(nativeType=..)原值,可null
	 * @param jdbcType   @Column(type=..),可null;jdbcType可独立判定族(JSON/JSONB/VECTOR/GEOMETRY)
	 * @return 逻辑族(json/jsonb/bson/vector/geometry/array),无法识别返回null(用户精确控制场景)
	 */
	public static String specialFamily(String nativeType, Integer jdbcType) {
		// jdbcType优先判定(JdbcTypes扩展常量与族一一对应,是正路)
		if (jdbcType != null) {
			if (jdbcType == org.sagacity.sqltoy.model.JdbcTypes.JSON) {
				return FAMILY_JSON;
			}
			if (jdbcType == org.sagacity.sqltoy.model.JdbcTypes.JSONB) {
				return FAMILY_JSONB;
			}
			if (jdbcType == org.sagacity.sqltoy.model.JdbcTypes.VECTOR) {
				return FAMILY_VECTOR;
			}
			if (jdbcType == org.sagacity.sqltoy.model.JdbcTypes.GEOMETRY) {
				return FAMILY_GEOMETRY;
			}
			// update 2026-11-22 type=JdbcTypes.UUID且未写nativeType的场景同样归族:此前family为null,
			// DDLUtils的uuid分派(metaType0==JdbcTypes.UUID)落在specialFamily非空块内不可达,pg目标
			// 回退switch输出VARCHAR(实体声明UUID列却建varchar,运行期cast/绑定错配)
			if (jdbcType == org.sagacity.sqltoy.model.JdbcTypes.UUID) {
				return FAMILY_UUID;
			}
		}
		if (nativeType == null) {
			return null;
		}
		String type = nativeType.trim().toLowerCase(Locale.ROOT);
		if (type.isEmpty()) {
			return null;
		}
		// pg数组两形态:标准elem[]与内部前缀_varchar(反向元数据形态)
		if (type.endsWith("[]") || type.startsWith("_")) {
			return FAMILY_ARRAY;
		}
		// update 2026-11-22 去掉尾部typmod参数后按基础类型名归族:vector(3)/floatvector(3)/
		// geometry(Point,4326)/numeric(10,2)等带参数形态此前只有"含geometry"的宽松判定能命中,
		// vector(3)等精确case全部漏判(quickvo按源库生成的向量类型即vector(3)形态,漏判导致
		// 无parse期jdbcType上调、无族cast,DDL按String字段落VARCHAR、merge裸?在h2报Unknown data type)
		int paramStart = type.indexOf('(');
		if (paramStart > 0 && type.endsWith(")")) {
			String baseType = type.substring(0, paramStart).trim();
			if (!baseType.isEmpty()) {
				type = baseType;
			}
		}
		switch (type) {
		case "json":
			return FAMILY_JSON;
		case "jsonb":
			return FAMILY_JSONB;
		case "bson":
			return FAMILY_BSON;
		case "vector":
		case "floatvector":
			return FAMILY_VECTOR;
		case "uuid":
			return FAMILY_UUID;
		case "geometry":
		case "st_geometry":
			return FAMILY_GEOMETRY;
		default:
			// db2gse.st_geometry等带schema形态含geometry判定
			if (type.contains("geometry")) {
				return FAMILY_GEOMETRY;
			}
			return null;
		}
	}

	/**
	 * merge cast消费点翻译:特殊族nativeType在目标方言下的cast目标
	 *
	 * @param dbType     目标方言
	 * @param family     解析期归一的逻辑族(specialFamily产物),null表示非特殊族
	 * @param nativeType 原值(族内保源精度形态用)
	 * @return cast(? as X)的X;返回null表示目标方言不适用该族——数组族回落组件类型推断链,
	 *         其余回落裸?(族无法识别时调用方不应调用本方法,原样透传)
	 */
	public static String specialCastType(int dbType, String family, String nativeType) {
		// update 2026-11-22 nativeType空白(含@Column默认空串)统一返回null:@Column的nativeType
		// 默认值是空串而非null,"族内保源形态return nativeType"路径曾返回空串,经"cast(? as "+x+")"
		// 拼出cast(? as )空类型——jdbcType判族(VECTOR/GEOMETRY/JSON)且未写nativeType的实体在pg上
		// merge即炸(ExtTypesMatrixTest真库实证);空源形态无"保"可言,统一回落原链(jdbcType分支
		// 正路cast(? as vector)等),消费点前置判空须用isNotBlank(null与空串双防)
		if (family == null) {
			// 非特殊族:用户显式声明的目标方言专属类型(如dm具名VARRAY),原样精确控制
			return (nativeType == null || nativeType.trim().isEmpty()) ? null : nativeType;
		}
		if (nativeType == null || nativeType.trim().isEmpty()) {
			return null;
		}
		// 族内归一判定的基础形态(小写去空白),仅用于判断nativeType是否已是目标方言的合法拼写
		String type = nativeType.trim().toLowerCase(Locale.ROOT);
		switch (family) {
		case FAMILY_ARRAY:
			// 数组族源形态(elem[]/_elem)仅pg内核系原样有效;h2回落组件链(VARCHAR ARRAY),
			// dm/db2无数组列语义回落裸?
			return isPgKernel(dbType) ? nativeType : null;
		case FAMILY_JSON:
		case FAMILY_JSONB:
			// update 2026-11-22 pg内核系不再原样透传nativeType:源库nativeType可能是目标库不存在
			// 的拼写(dm/oracle源以CLOB承载json、sqlserver源nvarchar等),原样拼cast(? as CLOB)在pg
			// 解析期即报 type "clob" does not exist(真库实证:merge into ... using (select cast(? as
			// CLOB) as data ...) 整个saveOrUpdate失败);此处按族归一为pg合法类型——nativeType本身
			// 是json/jsonb词面时保留源精度(与DDL同口径),否则族JSON→json、族JSONB→jsonb;
			// h2归一JSON;其余目标无对应cast回落jdbcType分支
			if (isPgKernel(dbType)) {
				// update 2026-11-22 OSCAR(传统Oscar驱动)无json类型,消费点jdbcType分支已显式排除cast
				// (裸?+setString为正路),族前置须同步返回null——否则族块先于jdbcType分支命中,重新引入
				// cast(? as json)必败
				if (dbType == DBType.OSCAR) {
					return null;
				}
				if ("jsonb".equals(type)) {
					return "jsonb";
				}
				if ("json".equals(type)) {
					return "json";
				}
				return FAMILY_JSONB.equals(family) ? "jsonb" : "json";
			}
			if (dbType == DBType.H2) {
				return "JSON";
			}
			return null;
		case FAMILY_BSON:
			// BSON为mongodb文档类型,实际不会与关系库混用(场景边缘);cast仍与DDL同口径按BSON列型定型
			// (nativeType=bson词面),保持DDL/merge cast一致;OSCAR无json/bson类型回落jdbcType分支
			if (isPgKernel(dbType)) {
				return dbType == DBType.OSCAR ? null : type;
			}
			if (dbType == DBType.H2) {
				return "JSON";
			}
			return null;
		case FAMILY_VECTOR:
			// update 2026-11-22 pg内核系原样透传仅限向量族自身拼写:外部拼写(如mysql的vector、
			// 源库自定义名)原样cast在目标库同样报类型不存在,按目标归一——gaussdb/vastbase只有
			// floatvector,其余pg内核为vector;vector(3)等typmod形态保留(合法cast目标)
			if (!isPgKernel(dbType)) {
				return null;
			}
			// OSCAR无vector类型(同OpenGaussDialectUtils的oscar例外),回落裸?
			if (dbType == DBType.OSCAR) {
				return null;
			}
			boolean floatVectorDb = (dbType == DBType.GAUSSDB || dbType == DBType.VASTBASE);
			if (type.startsWith("floatvector")) {
				return floatVectorDb ? nativeType.trim() : "vector";
			}
			if (type.startsWith("vector")) {
				return floatVectorDb ? "floatvector" : nativeType.trim();
			}
			return floatVectorDb ? "floatvector" : "vector";
		case FAMILY_UUID:
			// uuid:pg内核系与h2有原生uuid类型可cast;其余目标承载为varchar(36)族标量列,
			// 裸?+setString即正确,cast反而非法
			return (isPgKernel(dbType) || dbType == DBType.H2) ? "uuid" : null;
		case FAMILY_GEOMETRY:
			// geometry须各库专属函数包装而非通用cast:DM实测cast(? as ST_GEOMETRY)报-6801、
			// DB2内置形态cast(? as ST_GEOMETRY)报-408,正路DMGEO.ST_GeomFromText/db2GeomFromTextWrap
			// 才正确;DM/DB2回落null交由jdbcType=GEOMETRY分支(函数包装正路);h2归一GEOMETRY;
			// update 2026-11-22 pg内核系仅geometry/geography拼写原样(保geometry(Point,4326)精度),
			// ST_GEOMETRY/SDO_GEOMETRY/db2gse.st_geometry等外部拼写统一归一为geometry——原样透传
			// 会在pg解析期报type "st_geometry" does not exist(与JSON族同因)
			if (dbType == DBType.DM || dbType == DBType.DB2) {
				return null;
			}
			if (dbType == DBType.H2) {
				return "GEOMETRY";
			}
			if (isPgKernel(dbType)) {
				return (type.startsWith("geometry") || type.startsWith("geography")) ? nativeType.trim()
						: "geometry";
			}
			return null;
		default:
			return nativeType;
		}
	}
}
