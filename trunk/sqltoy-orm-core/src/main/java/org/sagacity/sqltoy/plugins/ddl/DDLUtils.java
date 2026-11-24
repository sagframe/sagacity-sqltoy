package org.sagacity.sqltoy.plugins.ddl;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.ForeignModel;
import org.sagacity.sqltoy.config.model.IndexModel;
import org.sagacity.sqltoy.config.model.ReferentialAction;
import org.sagacity.sqltoy.dialect.utils.H2DialectUtils;
import org.sagacity.sqltoy.dialect.utils.PostgreSqlDialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.NativeTypeUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 创建表语句的工具类，用于将EntityMeta依旧外键关系排序，转化封装为TableModel
 * @author zhongxuchen
 * @version v1.0,Date:2023-12-17
 * @modify Date:2023-12-17,修改说明
 */
public class DDLUtils {
	private static final Logger logger = LoggerFactory.getLogger(DDLUtils.class);

	public static String NEWLINE = "\r\n";
	public static String TAB = "   ";

	/**
	 * update 2026-9-15 因为存在外键关系，首先需要对表进行排序，被依赖的优先创建
	 * <p>
	 * 采用Kahn拓扑排序(O(V+E)):原"贪心单层前置"算法对链式依赖(A←B←C)在ConcurrentHashMap不利迭代
	 * 顺序下产出[B,C,A](处理C时前置B但不考虑B自身的依赖,处理B时A未入队被append到尾部),而外键约束
	 * 输出对建表顺序硬依赖(mysql/pg内联FOREIGN KEY于CREATE TABLE,oracle/h2/sqlserver的ALTER ADD
	 * CONSTRAINT紧跟本表CREATE之后),错序即建表失败;且原输出依赖hash序跨环境不可复现、swotTables
	 * 全量重建最坏O(n²)。新算法零入度集合按表名字典序出队保证脚本可复现,自环(树表自引用外键,内联合法)
	 * 不参与排序约束,多外键指向同表去重防入度虚增,节点集外的外表引用忽略,环状残余(互相外键,内联约束下 本就无解)按名序追加+warn提示,不吞表不死循环
	 *
	 * @param entitysMetaMap
	 * @return 被依赖表在前的表实体列表
	 */
	public static List<EntityMeta> sortTables(ConcurrentHashMap<String, EntityMeta> entitysMetaMap) {
		// 节点归一:schemaTable名->meta(与原实现口径一致)
		LinkedHashMap<String, EntityMeta> nodes = new LinkedHashMap<String, EntityMeta>();
		// 大小写不敏感索引(quickvo等工具@Foreign表名可与@Entity tableName大小写不一致)
		LinkedHashMap<String, String> lowerKeyMap = new LinkedHashMap<String, String>();
		for (EntityMeta entityMeta : entitysMetaMap.values()) {
			String key = entityMeta.getSchemaTable(null, null);
			nodes.put(key, entityMeta);
			lowerKeyMap.put(key.toLowerCase(Locale.ROOT), key);
		}
		// 建边:foreignTable->依赖表
		Map<String, Set<String>> successors = new HashMap<String, Set<String>>();
		Map<String, Integer> inDegree = new HashMap<String, Integer>();
		for (String name : nodes.keySet()) {
			inDegree.put(name, 0);
		}
		for (Map.Entry<String, EntityMeta> entry : nodes.entrySet()) {
			String tableName = entry.getKey();
			EntityMeta entityMeta = entry.getValue();
			if (entityMeta.getForeignFields() == null) {
				continue;
			}
			// 同表多外键指向同一外表时去重,避免入度虚增导致该表永不出队
			Set<String> deps = new HashSet<String>();
			for (Map.Entry<String, ForeignModel> iter : entityMeta.getForeignFields().entrySet()) {
				String foreignTable = iter.getValue().getForeignTable();
				if (entityMeta.getSchema() != null && !foreignTable.toLowerCase(Locale.ROOT)
						.startsWith(entityMeta.getSchema().toLowerCase(Locale.ROOT).concat("."))) {
					foreignTable = entityMeta.getSchema().concat(".").concat(foreignTable);
				}
				// 大小写不敏感:@Foreign(table="QS_STAFF")与@Entity(tableName="qs_staff")等场景
				String realKey = lowerKeyMap.get(foreignTable.toLowerCase(Locale.ROOT));
				if (realKey == null) {
					realKey = foreignTable;
				}
				// 自环排除:树表自引用外键内联合法,不约束建表顺序;节点集外的外表引用忽略
				if (!realKey.equals(tableName) && nodes.containsKey(realKey)) {
					deps.add(realKey);
				}
			}
			for (String dep : deps) {
				successors.computeIfAbsent(dep, k -> new LinkedHashSet<String>()).add(tableName);
				inDegree.put(tableName, inDegree.get(tableName) + 1);
			}
		}
		// Kahn:零入度集合按表名字典序出队,同层顺序确定,DDL脚本跨环境可复现
		TreeSet<String> ready = new TreeSet<String>();
		for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
			if (entry.getValue() == 0) {
				ready.add(entry.getKey());
			}
		}
		List<EntityMeta> result = new ArrayList<EntityMeta>(nodes.size());
		Set<String> emitted = new HashSet<String>();
		while (!ready.isEmpty()) {
			String name = ready.pollFirst();
			result.add(nodes.get(name));
			emitted.add(name);
			Set<String> nexts = successors.get(name);
			if (nexts != null) {
				for (String next : nexts) {
					int deg = inDegree.get(next) - 1;
					inDegree.put(next, deg);
					if (deg == 0) {
						ready.add(next);
					}
				}
			}
		}
		// 环状残余(互相外键):内联约束下任何顺序都无法建表,属schema设计问题;
		// 排序层职责是不吞表不死循环——按名序追加并warn定位
		if (result.size() < nodes.size()) {
			List<String> cyclic = new ArrayList<String>();
			for (String name : nodes.keySet()) {
				if (!emitted.contains(name)) {
					cyclic.add(name);
				}
			}
			Collections.sort(cyclic);
			logger.warn("foreign key cycle detected among tables:{}, appended in name order,"
					+ " inline FOREIGN KEY constraints may fail, consider ALTER-based constraints!", cyclic);
			for (String name : cyclic) {
				result.add(nodes.get(name));
			}
		}
		return result;
	}

	/**
	 * 将EntityMeta转化为TableMeta 便于输出表结构
	 * 
	 * @param entityMeta
	 * @param dbType
	 * @return
	 */
	public static TableMeta wrapTableMeta(EntityMeta entityMeta, Integer dbType) {
		TableMeta tableMeta = new TableMeta();
		tableMeta.setTableName(entityMeta.getTableName());
		tableMeta.setRemarks(escapeCommentForDdl(entityMeta.getTableComment()));
		tableMeta.setSchema(entityMeta.getSchema());
		tableMeta.setPkConstraint(entityMeta.getPkConstraint());
		// 分区元数据
		if (entityMeta.getPartitionMeta() != null) {
			tableMeta.setPartitionMeta(entityMeta.getPartitionMeta());
		}
		// MPP表引擎元数据
		if (entityMeta.getMppTableMeta() != null) {
			tableMeta.setMppTableMeta(entityMeta.getMppTableMeta());
		}
		// 索引信息
		if (entityMeta.getIndexModels() != null) {
			List<IndexModel> indexModels = new ArrayList<>();
			for (IndexModel indexModel : entityMeta.getIndexModels()) {
				indexModels.add(indexModel);
			}
			tableMeta.setIndexes(indexModels);
		}
		// 外键信息
		if (entityMeta.getForeignFields() != null) {
			List<ForeignModel> foreignModels = new ArrayList<>();
			for (Map.Entry<String, ForeignModel> entry : entityMeta.getForeignFields().entrySet()) {
				foreignModels.add(entry.getValue());
			}
			tableMeta.setForeigns(foreignModels);
		}
		// 列信息
		Map<String, FieldMeta> fieldMetaMap = entityMeta.getFieldsMeta();
		FieldMeta fieldMeta;
		List<ColumnMeta> columns = new ArrayList<>();
		for (Map.Entry<String, FieldMeta> entry : fieldMetaMap.entrySet()) {
			fieldMeta = entry.getValue();
			ColumnMeta columnMeta = new ColumnMeta();
			columnMeta.setColName(fieldMeta.getColumnName());
			columnMeta.setComments(escapeCommentForDdl(fieldMeta.getComments()));
			columnMeta.setAutoIncrement(fieldMeta.isAutoIncrement());
			columnMeta.setColumnSize(fieldMeta.getLength());
			columnMeta.setPartitionKey(fieldMeta.isPartitionKey());
			// update 2026-10-22
			// boolean默认值按目标库归一(数值承载方言的true/false转1/0),挂在本单一入口使全部generator的DEFAULT路径共享
			columnMeta.setDefaultValue(normalizeBooleanDefault(fieldMeta.getDefaultValue(), fieldMeta.getType(),
					dbType == null ? -1 : dbType));
			columnMeta.setNullable(fieldMeta.isNullable());
			columnMeta.setDataType(fieldMeta.getType());
			columnMeta.setTypeName(fieldMeta.getFieldType());
			// update 2026-11-21 数组/集合字段组件类型透传,DDL生成按方言转数组列类型
			columnMeta.setArrayComponentType(fieldMeta.getArrayComponentType());
			// update 2026-11-21 nativeType特殊族透传(json/jsonb/bson/vector/geometry/array)
			columnMeta.setSpecialFamily(fieldMeta.getSpecialFamily());
			columnMeta.setPK(fieldMeta.isPK() || fieldMeta.isDdlPk());
			columnMeta.setGeneratedType(fieldMeta.getGeneratedType());
			columnMeta.setDecimalDigits(fieldMeta.getPrecision());
			columnMeta.setNumPrecRadix(fieldMeta.getScale());
			columnMeta.setNativeType(fieldMeta.getNativeType());
			columns.add(columnMeta);
		}
		tableMeta.setColumns(columns);
		return tableMeta;
	}

	/**
	 * JSON文档承载类型:无原生JSON列类型的库降级承载,避免生成非法的"col JSON"。
	 * hana→NCLOB,sqlserver→NVARCHAR(MAX),db2/oracle11→CLOB;oracle21c+/mysql/pg/doris等原生支持→JSON
	 * (getBytes即UTF-8文本,getObject/getString该驱动不可用)框架级承接,无需降级承载
	 */
	private static String jsonCarrierType(int dbType) {
		if (dbType == DBType.HANA) {
			return "NCLOB";
		}
		if (dbType == DBType.SQLSERVER) {
			return "NVARCHAR(MAX)";
		}
		if (dbType == DBType.DB2 || dbType == DBType.ORACLE11) {
			return "CLOB";
		}
		return "JSON";
	}

	/**
	 * 设置类型
	 * 
	 * @param colMeta
	 * @param dbType
	 * @return
	 */
	public static String convertType(ColumnMeta colMeta, int dbType) {
		// update 2026-11-21 nativeType特殊族分派(替代原JSON/BSON词面特判):quickvo按源库生成
		// nativeType(源方言物理类型名,如pg的jsonb),跨库部署原样输出为目标列类型会非法;json族
		// pg内核系保源精度(json/jsonb),其余目标走jsonCarrierType承载映射;bson同构;数组族见下方
		// 组件分派;族不可识别(如dm具名VARRAY)不在此拦截,经default分支原样透传保持精确控制
		// update 2026-11-22 特殊族字段为空时现场兜底识别(幂等):实体链路由EntityManager解析期
		// 填充specialFamily,但手工构造/反向工程(getTableColumns)链路的ColumnMeta不携带该字段,
		// 仅依赖字段会使json/bson词面特判在这些链路失效(DDLConvertTypeMatrixTest真库回归暴露)
		String specialFamily = (colMeta.getSpecialFamily() != null) ? colMeta.getSpecialFamily()
				: NativeTypeUtils.specialFamily(colMeta.getNativeType(), colMeta.getDataType());
		if (specialFamily != null) {
			Integer metaType = colMeta.getDataType();
			if ((metaType != null && (metaType == JdbcTypes.JSON || metaType == JdbcTypes.JSONB))
					|| NativeTypeUtils.FAMILY_JSON.equals(specialFamily)
					|| NativeTypeUtils.FAMILY_JSONB.equals(specialFamily)) {
				// jdbcType=JSON/JSONB或nativeType为json/jsonb族:pg系保源精度,其余按目标承载
				// update 2026-11-22 源精度以nativeType词面为准:此前只按族/type判定,type=JSON与
				// nativeType=jsonb(或type=JSONB与nativeType=json)的实体被静默改写(jsonb→json属
				// schema降级:索引/操作符/比较语义不同),而改动前default分支原样输出nativeType;
				// 现nativeType为json/jsonb词面时优先,其余按族/type判定,与merge cast同口径
				String nativeJson = (colMeta.getNativeType() == null) ? "" : colMeta.getNativeType().trim();
				boolean jsonb;
				if ("jsonb".equalsIgnoreCase(nativeJson)) {
					jsonb = true;
				} else if ("json".equalsIgnoreCase(nativeJson)) {
					jsonb = false;
				} else {
					jsonb = NativeTypeUtils.FAMILY_JSONB.equals(specialFamily)
							|| (metaType != null && metaType == JdbcTypes.JSONB);
				}
				if (NativeTypeUtils.isPgKernel(dbType)) {
					return jsonb ? "jsonb" : "json";
				}
				return jsonCarrierType(dbType);
			}
			if (NativeTypeUtils.FAMILY_BSON.equals(specialFamily)) {
				if (NativeTypeUtils.isPgKernel(dbType)) {
					return "BSON";
				}
				return jsonCarrierType(dbType);
			}
			// update 2026-11-22 uuid族列类型:原生uuid方言(pg内核10库/h2的UUID/sqlserver的
			// uniqueidentifier)保原生,其余方言以36位变长字符承载(oracle VARCHAR2(36)/mysql系
			// VARCHAR(36)/db2 VARCHAR(36)/hana NVARCHAR(36)/sqlite TEXT/dm
			// VARCHAR(36));统一用变长
			// 而非CHAR——CHAR空白填充会给去连字符的32位值补尾空格,读回带空格(Java equals/JSON错位),
			// 迁到其它方言的VARCHAR后尾空格变成有效字符,跨库等值/唯一约束失真
			Integer metaType0 = colMeta.getDataType();
			if (NativeTypeUtils.FAMILY_UUID.equals(specialFamily)
					|| (metaType0 != null && metaType0 == JdbcTypes.UUID)) {
				// update 2026-11-22 显式nativeType优先(恢复"族不可识别精确控制"口径):uuid族/type=UUID
				// 且nativeType为方言专属物理类型(如BINARY(16)/CHAR(36)紧凑存储)时原样输出,此前
				// 一律落VARCHAR(36)静默改写既有实体列类型;uuid/uniqueidentifier拼写继续走下方原生映射
				String uuidNative = (colMeta.getNativeType() == null) ? "" : colMeta.getNativeType().trim();
				if (!uuidNative.isEmpty() && !"uuid".equalsIgnoreCase(uuidNative)
						&& !"uniqueidentifier".equalsIgnoreCase(uuidNative)) {
					return uuidNative;
				}
				if (NativeTypeUtils.isPgKernel(dbType)) {
					return "uuid";
				}
				if (dbType == DBType.H2) {
					return "UUID";
				}
				if (dbType == DBType.SQLSERVER) {
					return "UNIQUEIDENTIFIER";
				}
				if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
					return "VARCHAR2(36)";
				}
				if (dbType == DBType.HANA) {
					return "NVARCHAR(36)";
				}
				if (dbType == DBType.SQLITE) {
					return "TEXT";
				}
				return "VARCHAR(36)";
			}
		}
		boolean isBytes = false;
		String typeName = "VARCHAR";
		// update 2026-11-21 数组/集合字段按组件类型生成数组列类型(优先级在nativeType之后,
		// 与wrapSelectFields同优先链:nativeType显式声明最优先,配置了就不参与组件推断):
		// pg内核系9库生成elem[]标准数组建表语法(varchar[]/integer[]/bigint[]/numeric[]/float[]/
		// float8[]/bool[]/int2[],元素名与SqlUtil.setArray的createArrayOf及merge cast口径一致);
		// h2生成elem ARRAY。此前实体数组字段落default统一VARCHAR:pg系仅String[]靠typeName以[]
		// 结尾碰巧生成_VARCHAR,数值/布尔数组错型;h2直接丢数组维度成标量列,运行期数组绑定不匹配。
		// 无标准数组列的方言(mysql系/oracle/dm/sqlserver/db2/hana/ob等)维持switch现状,
		// 建议以@Column(nativeType=..)显式指定承载类型;未识别组件(日期时间等)同样回落switch
		// update 2026-11-22 二进制列优先于数组列分派:byte[]实体已由EntityManager排除组件提取,此处兜底
		// 反向工程/手工构造链路(ColumnMeta同时带BINARY/BLOB类型与组件类型)——byte[]在SQL侧无对应数组
		// 元素类型(int2为2字节整数,与byte语义不同),必须保BYTEA/BLOB/VARBINARY(MAX),不得降级为elem[]
		boolean binaryColumn = colMeta.getDataType() != null
				&& (colMeta.getDataType() == java.sql.Types.BINARY || colMeta.getDataType() == java.sql.Types.VARBINARY
						|| colMeta.getDataType() == java.sql.Types.LONGVARBINARY
						|| colMeta.getDataType() == java.sql.Types.BLOB);
		// update 2026-11-22 显式标量jdbcType优先于组件类型推断(与wrapSelectFields的jdbcType优先链
		// 统一):此前只要arrayComponentType非空且nativeType为空就生成数组列,导致①List<String>+
		// type=CLOB(应用以TypeHandler把集合序列化进CLOB,既有工程常见写法)的列被静默改成
		// varchar[]/VARCHAR ARRAY/JSON承载;②String[]+type=VARCHAR被改成数组列,而merge into的
		// using-select按jdbcType产出 cast(? as VARCHAR(n)),同一字段DDL与DML口径相反——h2实测
		// saveOrUpdate不报错但落库cardinality=1(数组元素被再次包裹),属静默错写数据。
		// 仅在jdbcType为中性(未声明/OTHER/ARRAY/JAVA_OBJECT/STRUCT)时才按组件类型生成数组列
		Integer declaredType = colMeta.getDataType();
		boolean neutralType = declaredType == null || declaredType == java.sql.Types.OTHER
				|| declaredType == java.sql.Types.ARRAY || declaredType == java.sql.Types.JAVA_OBJECT
				|| declaredType == java.sql.Types.STRUCT;
		if (!binaryColumn && neutralType && StringUtil.isNotBlank(colMeta.getArrayComponentType()) && (StringUtil
				.isBlank(colMeta.getNativeType())
				// 数组族nativeType(pg源text[]/varchar[]形态)仅pg内核系目标有效,其余目标视同未配置
				// 回落组件链(elem[]/elem ARRAY),避免源形态拼入h2/dm等目标产生非法DDL
				// update 2026-11-22 用现场兜底识别的specialFamily(与json/bson族同构),而非仅取字段——
				// 手工构造/反向工程链路ColumnMeta字段为空但nativeType=text[]时,非pg目标亦须回落JSON承载
				|| (NativeTypeUtils.FAMILY_ARRAY.equals(specialFamily) && !NativeTypeUtils.isPgKernel(dbType)))) {
			if (NativeTypeUtils.isPgKernel(dbType)) {
				String elem = PostgreSqlDialectUtils.pgArrayElementTypeName(colMeta.getArrayComponentType());
				if (elem != null) {
					return elem.concat("[]");
				}
			} else if (dbType == DBType.H2) {
				String elem = H2DialectUtils.h2ArrayElementTypeName(colMeta.getArrayComponentType());
				if (elem != null) {
					return elem.concat(" ARRAY");
				}
			} else {
				// update 2026-11-22 无数组列方言(mysql/oracle/hana/sqlserver/db2/dm等)以JSON/文本承载
				// (jsonCarrierType按目标分派:mysql/oracle 21c+原生JSON,oracle11/db2 CLOB,hana NCLOB,
				// sqlserver NVARCHAR(MAX))——此前落default将pg源text[]/integer[]等形态原样输出为非法DDL,
				// 或裸VARCHAR在mysql/oracle缺长度非法;数组运行期绑定在这些方言本就不可用(无数组列,
				// 序列化bytes绑定报错,见ArrayTypeEndToEndTest t11无假阳性),JSON承载亦是数组跨库
				// 迁移的正路,运行期由应用层以JSON字符串读写
				return jsonCarrierType(dbType);
			}
			// update 2026-11-22 未识别组件(java.time.*、String[][]等)此前静默回落下方switch生成
			// 标量列(如LocalDate[]→VARCHAR),而运行期仍按SQL数组绑定,DDL与DML必然不匹配且无提示;
			// 此处显式告警,需要数组列时由@Column(nativeType=..)指定承载类型
			logger.warn(
					"ddl convertType: array component type [{}] has no mapped element type on dbType [{}], fallback to a scalar column; declare @Column(nativeType=..) explicitly if an array column is required",
					colMeta.getArrayComponentType(), dbType);
		}
		switch (colMeta.getDataType()) {
		case java.sql.Types.BIGINT:
			if (isOracleFamily(dbType)) {
				typeName = "NUMBER";
				typeName = setLength(typeName, true, colMeta);
			} else {
				typeName = "BIGINT";
			}
			break;
		case java.sql.Types.INTEGER:
			typeName = "INTEGER";
			break;
		case java.sql.Types.TINYINT:
			// update 2026-11-22 tinyint方言分派:mysql源实体的tinyint(4)列部署目标库——此前
			// 非oracle系统一TINYINT,在pg/db2等无tinyint的库为非法DDL(真库实证pg报
			// type "tinyint" does not exist);最小整型方言:pg内核系/db2为SMALLINT,
			// oracle系INTEGER(现状),mysql系/sqlserver/sqlite/h2/hana原生TINYINT
			// update 2026-11-22 HANA移出SMALLINT名单:SAP HANA类型清单含TINYINT(见HanaDialectUtils
			// 类型分支),原先归入SMALLINT会把既有hana实体的tinyint列静默加宽为smallint
			if (isOracleFamily(dbType)) {
				typeName = "INTEGER";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE
					|| dbType == DBType.HIGHGO || dbType == DBType.DB2) {
				typeName = "SMALLINT";
			} else {
				typeName = "TINYINT";
				// update 2026-11-22 hana不接受显示宽度(TINYINT(n)非法),只有mysql系等既有方言沿用
				// setLength;hana自此分支承接TINYINT后须跳过长度后缀
				if (dbType != DBType.HANA) {
					typeName = setLength(typeName, true, colMeta);
				}
			}
			break;
		case java.sql.Types.SMALLINT:
			typeName = "SMALLINT";
			break;
		case java.sql.Types.CHAR:
		case java.sql.Types.NCHAR:
			// hana的CHAR/VARCHAR为ASCII字符集,unicode须NCHAR/NVARCHAR
			typeName = (dbType == DBType.HANA) ? "NCHAR" : "CHAR";
			typeName = setLength(typeName, false, colMeta);
			break;
		case java.sql.Types.VARCHAR:
		case java.sql.Types.NVARCHAR: {
			// update 2026-11-22
			// sqlserver默认NVARCHAR(unicode):VARCHAR为非unicode码页,中文经setString
			// (nvarchar参数)隐式转换丢失为"????"(真库A/B实证NVARCHAR中文往返MATCH);hana同因ASCII须NVARCHAR
			typeName = (dbType == DBType.HANA || dbType == DBType.SQLSERVER) ? "NVARCHAR" : "VARCHAR";
			// update 2026-11-22 sqlserver
			// NVARCHAR上限4000:varchar源列(length=8000等)直接拼NVARCHAR(8000)
			// 建表报"The size (8000) given to the parameter ... exceeds the maximum allowed
			// (4000)"
			// (真库实证),超出上限按NVARCHAR(MAX)承载;4000及以内沿用长度
			if (dbType == DBType.SQLSERVER && colMeta.getColumnSize() > 4000) {
				typeName = "NVARCHAR(MAX)";
			} else {
				typeName = setLength(typeName, false, colMeta);
			}
		}
			break;
		case java.sql.Types.LONGNVARCHAR:
			// update 2026-11-22 补db2→CLOB(无TEXT类型,真库实证-204);OCEANBASE归oracle族→CLOB
			// (ob mysql模式经dialect=mysql配置走MYSQL分支,不应在OCEANBASE分支强改TEXT破坏oracle模式)
			if (isOracleFamily(dbType) || dbType == DBType.H2 || dbType == DBType.DB2) {
				typeName = "CLOB";
			} else if (dbType == DBType.HANA) {
				typeName = "NCLOB";
			} else {
				typeName = "TEXT";
			}
			break;
		case java.sql.Types.TIMESTAMP_WITH_TIMEZONE:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.ORACLE
					// update 2026-9-14 KINGBASE(KingbaseES基于PG,类型映射归PG系)
					|| dbType == DBType.KINGBASE || dbType == DBType.HIGHGO || dbType == DBType.ORACLE11
					|| dbType == DBType.DM || dbType == DBType.OCEANBASE
					// update 2026-11-22 H2支持TIMESTAMP WITH TIME ZONE(真库实证:TIMESTAMP列插OffsetDateTime
					// 读回丢时区,TSTZ列保"+08");此前落else TIMESTAMP与merge cast TSTZ分歧且静默丢时区
					|| dbType == DBType.H2) {
				// 精度需跟在TIMESTAMP之后:TIMESTAMP(6) WITH TIME ZONE
				typeName = "TIMESTAMP";
				if (colMeta.getColumnSize() > 0) {
					typeName = typeName + "(" + colMeta.getColumnSize() + ")";
				}
				typeName = typeName + " WITH TIME ZONE";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = setTimePrecision("DATETIMEOFFSET", colMeta);
			} else if (dbType == DBType.DORIS) {
				// update 2026-11-10 Doris原生TIMESTAMPTZ(真库实测可建表),保留时区语义
				typeName = "TIMESTAMPTZ";
			} else if (dbType == DBType.STARROCKS) {
				// update 2026-11-10 StarRocks无TIMESTAMPTZ/TIMESTAMP(真库实测语法错),降级DATETIME
				typeName = "DATETIME";
			} else {
				typeName = "TIMESTAMP";
			}
			break;
		case java.sql.Types.BLOB:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE
					|| dbType == DBType.HIGHGO) {
				typeName = "BYTEA";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "VARBINARY(MAX)";
			} else if (dbType == DBType.DORIS) {
				// update 2026-11-22 doris类型集无BLOB且不支持VARBINARY建表(真库实证"do not support
				// varbinary create table, could use it by catalog"),二进制以STRING承载
				typeName = "STRING";
			} else if (dbType == DBType.STARROCKS) {
				// update 2026-11-22 starrocks类型集无BLOB(真库实证Unexpected input),以VARBINARY承载
				typeName = "VARBINARY";
			} else {
				typeName = "BLOB";
			}
			isBytes = true;
			break;
		case java.sql.Types.BINARY:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.STARDB || dbType == DBType.OSCAR
					|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE
					|| dbType == DBType.HIGHGO) {
				typeName = "BYTEA";
			} else if (isOracleFamily(dbType)) {
				typeName = "BLOB";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "VARBINARY(MAX)";
			} else {
				typeName = "BINARY";
				typeName = setLength(typeName, false, colMeta);
			}
			isBytes = true;
			break;
		case java.sql.Types.VARBINARY:
		case java.sql.Types.LONGVARBINARY:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.STARDB || dbType == DBType.OSCAR
					|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE
					|| dbType == DBType.HIGHGO) {
				typeName = "BYTEA";
			} else if (isOracleFamily(dbType)) {
				typeName = "BLOB";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "VARBINARY(MAX)";
			} else {
				typeName = "VARBINARY";
				typeName = setLength(typeName, false, colMeta);
			}
			isBytes = true;
			break;
		case java.sql.Types.CLOB:
		case java.sql.Types.NCLOB:
			// update 2026-11-22 LONGVARCHAR(mysql源text/longtext列的jdbcType)归并CLOB家族:此前无case
			// 落default按nativeType原样输出,在db2/hana等无TEXT类型库为非法DDL(真库实证db2报-204
			// SQLERRMC=TEXT)。但mysql系目标须保留源nativeType(longtext/mediumtext),否则longtext降级
			// TEXT(64KB上限)截断大文本——故仅mysql协议系(MYSQL/MYSQL57/TIDB)目标透传nativeType,
			// 其余目标翻译为CLOB/NCLOB/TEXT(DORIS/STARROCKS用STRING非longtext,亦走翻译)
		case java.sql.Types.LONGVARCHAR:
			if (colMeta.getDataType() == java.sql.Types.LONGVARCHAR
					&& (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB)
					&& StringUtil.isNotBlank(colMeta.getNativeType())) {
				typeName = colMeta.getNativeType();
			} else if (isOracleFamily(dbType) || dbType == DBType.H2 || dbType == DBType.DB2) {
				typeName = "CLOB";
			} else if (dbType == DBType.HANA) {
				// hana无TEXT类型,大文本为NCLOB(unicode)
				typeName = "NCLOB";
			} else {
				typeName = "TEXT";
			}
			break;
		case java.sql.Types.TIME:
			typeName = "TIME";
			break;
		case java.sql.Types.TIMESTAMP:
			// sqlserver的TIMESTAMP是行版本戳(rowversion),不是日期时间类型
			if (dbType == DBType.SQLSERVER) {
				typeName = "DATETIME2";
			} else if (dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
				// update 2026-11-10 Doris/StarRocks无TIMESTAMP类型:真库实测Doris解析器期望
				// DATETIME/TIMESTAMPTZ(mismatched input 'TIMESTAMP'),StarRocks报语法错;
				// LocalDateTime/localdatetime列统一落DATETIME
				typeName = "DATETIME";
			} else {
				typeName = "TIMESTAMP";
			}
			break;
		case java.sql.Types.DATE:
			// update 2026-10-13
			// Types.DATE按目标库与字段Java类型精化,修复按生成DDL建表后时间部分被静默清零:实测dm的DATE列只存日期
			// (与oracle的DATE含时间不同),pg系date列同样只存日期(pg 18.6实测,timestamp写入date列静默截断时间);
			// java.util.Date/LocalDateTime这类含时间字段(无论@Column未指定type自动探测,还是quickvo生成实体显式
			// 声明type=DATE)此前均输出DATE列,现按字段Java类型(小写全类名)精化:dm输出DATETIME,pg系输出TIMESTAMP,
			// 纯日期类型(LocalDate/java.sql.Date)保持DATE语义;rowversion已剔除并改按目标库元数据校准
			// (ensureRowVersionMeta),与实体JDBC类型解耦,LocalDateTime归位TIMESTAMP;TIDB为mysql协议同构,
			// Types.DATE与mysql一致转DATETIME,否则quickvo实体(type=DATE,nativeType=DATETIME,LocalDateTime)生成裸DATE列,
			// 与DEFAULT CURRENT_TIMESTAMP(3)冲突报"Invalid default value"(真库实测)
			if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.SQLSERVER
					|| dbType == DBType.DORIS || dbType == DBType.STARROCKS || dbType == DBType.TIDB) {
				typeName = "DATETIME";
			} else if (isTimeCarryingJavaType(colMeta.getTypeName()) && isDateOnlyDialect(dbType)) {
				typeName = (dbType == DBType.DM) ? "DATETIME" : "TIMESTAMP";
			} else {
				typeName = "DATE";
			}
			break;
		case java.sql.Types.BOOLEAN:
			if ("string".equals(colMeta.getTypeName())) {
				if (colMeta.getColumnSize() > 0) {
					typeName = "VARCHAR";
					typeName = setLength(typeName, false, colMeta);
				} else {
					typeName = "CHAR(1)";
				}
			} else if (isOracleFamily(dbType)) {
				typeName = "INTEGER";
			} else if (dbType == DBType.SQLSERVER) {
				// sqlserver无boolean类型,bit为0/1
				typeName = "BIT";
			} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS
					|| dbType == DBType.STARROCKS || dbType == DBType.TIDB) {
				typeName = "TINYINT(1)";
			} else {
				// postgresql/openGauss/H2等原生支持boolean
				typeName = "BOOLEAN";
			}
			break;
		case JdbcTypes.VECTOR: {
			// 向量类型:gaussdb企业版为floatvector,其余(pgvector/openGauss系/oracle 23ai/mysql
			// heatwave/sqlserver 2025/db2 12.1.2+)为vector
			// 维度通过@Column(length=xxx)指定,mysql heatwave和sqlserver 2025的维度为必填项
			// 内核)实测"Type (vector) not found",商业版V8.8提供向量能力,类型名以@Column
			// (nativeType)覆盖为准;HIGHGO同pgvector(需库内create extension vector,社区镜像
			// 未预装,可源码编译pgvector 0.7.4后安装,实测vector(3)建表/绑定/读回/近邻均可用)
			if (dbType == DBType.H2) {
				// h2无向量类型,按varchar承载'[1,2,3]'文本。update 2026-11-22 不复用@Column(length)作字符长度:
				// 该长度语义为向量维度(元素数),误作VARCHAR(n)会截断文本(维度3→VARCHAR(3)容不下'[1,2,3]'7字符,
				// 真库实证"Value too long");h2 VARCHAR不带长度为大上限,承载任意维度向量文本
				typeName = "VARCHAR";
			} else if (dbType == DBType.DB2) {
				// update 2026-9-10 db2 12.1.2.0/12.1.5.0实测:VECTOR须显式坐标类型双参形态VECTOR(n,
				// FLOAT32),单参VECTOR(n)
				// 报-104语法错误、裸VECTOR报-901 "Unknown vector coordinate
				// type";维度必填(@Column(length=xxx)),
				// 其他坐标类型(FLOAT64等)经@Column(nativeType=...)覆盖
				typeName = (colMeta.getColumnSize() > 0) ? ("VECTOR(" + colMeta.getColumnSize() + ", FLOAT32)")
						: "VECTOR";
			} else if (dbType == DBType.DORIS) {
				// update 2026-9-10 doris
				// 4.1.3实测:无VECTOR(n)列类型(解析器类型全集无VECTOR),向量以ARRAY<FLOAT>承载
				// (近邻检索配VECTOR索引),字符串'[1,2,3]'绑定隐式转换、读回为'[1, 2, 3]'文本均实证可行;维度不进列定义(向量索引声明处约束)
				typeName = "ARRAY<FLOAT>";
			} else if (dbType == DBType.CLICKHOUSE) {
				// update 2026-9-11 clickhouse 26.8.2.7实测:无vector类型族(报Unknown data type family:
				// vector),向量以Array(Float32)
				// 承载(同doris思路):字符串'[1,2,3]'插入隐式解析、读回'[1,2,3]'文本、L2Distance(v,[..])距离检索均实证可行
				typeName = "Array(Float32)";
			} else if (dbType == DBType.HANA) {
				// 2026-9-11 hana 2.0 SPS08起提供REAL_VECTOR类型(维度必填,上限65000)
				typeName = (colMeta.getColumnSize() > 0) ? ("REAL_VECTOR(" + colMeta.getColumnSize() + ")")
						: "REAL_VECTOR";
			} else {
				// update 2026-9-10 vastbase G100 3.0实测向量类型名为FLOATVECTOR(无VECTOR别名),同gaussdb企业版
				if (dbType == DBType.GAUSSDB || dbType == DBType.VASTBASE) {
					typeName = "FLOATVECTOR";
				} else {
					typeName = "VECTOR";
				}
				// 维度有效时才带(n):pgvector经JDBC取到的COLUMN_SIZE为Integer.MAX_VALUE(无界哨兵),
				// 直接渲染vector(2147483647)会超pgvector上限16000被拒,故无界时渲染裸vector(pgvector允许无界向量)
				int vecDim = colMeta.getColumnSize();
				if (vecDim > 0 && vecDim < Integer.MAX_VALUE) {
					typeName = typeName + "(" + vecDim + ")";
				}
			}
			break;
		}
		case JdbcTypes.GEOMETRY: {
			// 空间类型:oracle为SDO_GEOMETRY,dm为ST_GEOMETRY,其余(postgis系/mysql/sqlserver/h2)为GEOMETRY
			// 类型精度修饰(如geometry(Point,4326)、geography)通过@Column(nativeType="...")指定;
			// nativeType未配置时注解默认为空串(非null),须按blank判断,否则生成空列类型导致DDL非法
			// 扩展且依赖GEOS/PROJ无法容器内源码编译),nativeType覆盖为唯一路径
			// update 2026-11-22 dm空间类型为DMGEO.ST_GEOMETRY(须SP_INIT_GEO_SYS初始化),与绑定链
			// DialectExtUtils的DMGEO.ST_GeomFromText一致
			// update 2026-11-22 显式nativeType须判定DM是否支持,不支持的须转成DM支持的类型:真库实测
			// (DM8 20260710+DMGEO)DM列类型仅 ST_GEOMETRY 可建表——DMGEO.ST_GEOMETRY(带schema)/
			// SDO_GEOMETRY/GEOMETRY/GEOGRAPHY/POINT/MDSYS.SDO_GEOMETRY 全部报-3719非法基类名;
			// 受支持拼写只有 ST_GEOMETRY 一个且类型名大小写不敏感,故统一输出 ST_GEOMETRY
			// (pg源nativeType="geometry"直通dm即-3719,此处亦覆盖跨库部署场景)
			if (dbType == DBType.DM) {
				// DM白名单:仅ST_GEOMETRY可建表(真库实证DM8+DMGEO),无条件收敛(含显式nativeType外部拼写)
				typeName = "ST_GEOMETRY";
			} else {
				// update 2026-11-22 按目标库匹配转化(作者原则):nativeType匹配目标几何类型则用nativeType保精度,
				// 目标完全不支持则尽可能转化为目标默认几何类型,无法判断的未知拼写回落nativeType精确控制
				typeName = NativeTypeUtils.geometryDdlType(dbType, colMeta.getNativeType());
			}
			break;
		}
		case java.sql.Types.FLOAT:
			typeName = "FLOAT";
			break;
		case java.sql.Types.DOUBLE:
			if (isOracleStrictDialect(dbType)) {
				typeName = "BINARY_DOUBLE";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
			// update 2026-9-14 KINGBASE(KingbaseES基于PG,类型映射归PG系)
					|| dbType == DBType.KINGBASE || dbType == DBType.HIGHGO || dbType == DBType.OPENGAUSS
					|| dbType == DBType.MOGDB || dbType == DBType.STARDB || dbType == DBType.OSCAR
					|| dbType == DBType.VASTBASE || dbType == DBType.DM || dbType == DBType.H2) {
				typeName = "DOUBLE PRECISION";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "FLOAT";
			} else {
				typeName = "DOUBLE";
			}
			break;
		case java.sql.Types.DECIMAL:
		case java.sql.Types.NUMERIC:
			if (isOracleFamily(dbType)) {
				typeName = "NUMBER";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.KINGBASE
					|| dbType == DBType.HIGHGO) {
				// update 2026-9-14 KINGBASE(KingbaseES基于PG,numeric为任意精度,同vanilla PG)
				typeName = "NUMERIC";
			} else {
				typeName = "DECIMAL";
			}
			typeName = setLength(typeName, true, colMeta);
			break;
		default: {
			// nativeType未配置时注解默认为空串(非null),须按blank判断,否则未知类型会生成空列类型
			if (StringUtil.isNotBlank(colMeta.getNativeType())) {
				typeName = colMeta.getNativeType();
			} else {
				typeName = "VARCHAR";
				typeName = setLength(typeName, false, colMeta);
			}
		}
		}
		// 数组类型(pg系:typeName以[]结尾时渲染为下划线前缀的数组类型);
		// VECTOR除外:pgvector的vector本身即向量类型(非"向量的数组"),加_前缀会变成_vector数组类型导致建表失败
		// update 2026-11-21 typeName已是elem[]数组语法(nativeType显式声明如text[]经default透传)时
		// 不再加_前缀(此前会被包成"_text[]"非法类型,建表必败)
		if ((dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE
				|| dbType == DBType.HIGHGO) && colMeta.getDataType() != JdbcTypes.VECTOR
				&& colMeta.getTypeName() != null && colMeta.getTypeName().endsWith("[]") && !isBytes
				&& !typeName.startsWith("_") && !typeName.endsWith("[]")) {
			return "_".concat(typeName);
		}
		return typeName;
	}

	/**
	 * 设置类型长度
	 * 
	 * @param typeName
	 * @param isNumber
	 * @param colMeta
	 * @return
	 */
	public static String setLength(String typeName, boolean isNumber, ColumnMeta colMeta) {
		if (isNumber) {
			if (colMeta.getNumPrecRadix() > 0) {
				return typeName + "(" + colMeta.getColumnSize() + "," + colMeta.getNumPrecRadix() + ")";
			}
		}
		if (colMeta.getColumnSize() > 0) {
			if (typeName.equals("CHAR") || typeName.equals("VARCHAR")) {
				return typeName + "(" + (colMeta.getColumnSize() > 10485760 ? 10485760 : colMeta.getColumnSize()) + ")";
			}
			return typeName + "(" + colMeta.getColumnSize() + ")";
		}
		return typeName;
	}

	/**
	 * 设置时间类型的精度(只取一位精度,不能带scale)
	 * 
	 * @param typeName
	 * @param colMeta
	 * @return
	 */
	public static String setTimePrecision(String typeName, ColumnMeta colMeta) {
		if (colMeta.getColumnSize() > 0) {
			return typeName + "(" + colMeta.getColumnSize() + ")";
		}
		return typeName;
	}

	/**
	 * 包装主键信息
	 * 
	 * @param tableMeta
	 * @param toUpperOrLower
	 * @param dbType
	 * @param tableSql
	 */
	/**
	 * 生成表分区子句(依据@Partition元数据,闭括号后附加):
	 * expression为数据库原生分区表达式时直接使用(如postgresql的"RANGE (trade_date)"),
	 * 否则按strategy+columns(或expression)拼接;分区明细定义以注释形式附加
	 * 
	 * @param tableMeta
	 * @param upperOrLower
	 * @param tableSql
	 * @return 是否产生了分区子句
	 */
	public static boolean wrapTablePartition(TableMeta tableMeta, String upperOrLower, int dbType,
			StringBuilder tableSql) {
		org.sagacity.sqltoy.config.model.PartitionMeta partitionMeta = tableMeta.getPartitionMeta();
		if (partitionMeta == null || StringUtil.isBlank(partitionMeta.getStrategy())) {
			return false;
		}
		String strategy = partitionMeta.getStrategy().toUpperCase(Locale.ROOT);
		// 逻辑策略token转SQL语法:RANGE_COLUMNS/LIST_COLUMNS(下划线,见@Partition定义)渲染为"RANGE
		// COLUMNS"/"LIST COLUMNS"
		// (mysql/oceanbase等要求空格分隔,下划线形式非法)
		String sqlStrategy = strategy.replace("RANGE_COLUMNS", "RANGE COLUMNS").replace("LIST_COLUMNS", "LIST COLUMNS");
		// 反引号/双引号剔除:quickvo从SHOW CREATE提取的表达式可能带mysql引号,Doris/SR的PARTITION BY不接受
		String expression = (partitionMeta.getExpression() == null) ? null
				: partitionMeta.getExpression().replace("`", "").replace("\"", "");
		// 原生表达式已含策略(如"RANGE (trade_date)"):直接使用(列名跟随大小写策略)
		String upperExpression = (expression == null) ? null : expression.trim().toUpperCase(Locale.ROOT);
		if (StringUtil.isNotBlank(expression)
				&& (upperExpression.startsWith(strategy) || upperExpression.startsWith(sqlStrategy))) {
			tableSql.append(" PARTITION BY ").append(StringUtil.toLowerOrUpper(expression.trim(), upperOrLower));
		} else {
			// 按策略+分区键(或函数表达式)拼接
			String columns = StringUtil.isNotBlank(expression)
					? StringUtil.toLowerOrUpper(expression.trim(), upperOrLower)
					: joinPartitionColumns(partitionMeta.getColumns(), upperOrLower);
			tableSql.append(" PARTITION BY ").append(sqlStrategy).append(" (").append(columns).append(")");
		}
		wrapPartitionDefs(partitionMeta, strategy, dbType, tableSql);
		return true;
	}

	/**
	 * 分区明细渲染:mysql/doris/starrocks等要求RANGE/LIST显式给出分区清单才能建表, 有明细时渲染真实子句(PARTITION
	 * xx VALUES LESS THAN (v),..);HASH/KEY渲染PARTITIONS n;
	 * 无明细(pg/oracle等策略型分区,子分区独立建表)维持注释形式。 LIST语法按方言区分:oracle/DM为"VALUES
	 * (v)",mysql系为"VALUES IN (v)"
	 */
	private static void wrapPartitionDefs(org.sagacity.sqltoy.config.model.PartitionMeta partitionMeta, String strategy,
			int dbType, StringBuilder tableSql) {
		String[] names = partitionMeta.getPartitionNames();
		if (names == null || names.length == 0) {
			return;
		}
		String[] values = partitionMeta.getPartitionValues();
		boolean rangeStyle = strategy.startsWith("RANGE") || strategy.startsWith("LIST");
		if (!rangeStyle) {
			// HASH/KEY:分区数量
			tableSql.append(" PARTITIONS ").append(names.length);
			return;
		}
		// update 2026-10-13 DB2表分区为STARTING..ENDING双边界区间语法(非mysql系VALUES LESS
		// THAN):PARTITION <name>
		// STARTING (<low>) ENDING
		// (<high>)[EXCLUSIVE];MINVALUE/MAXVALUE为DB2关键字不带括号;上界缺失以
		// /* end value missing
		// */显式标记(建表会失败,提示补全,优于静默生成错误边界);明细按目录SEQNO序原样渲染(DB2无升序强制,不做值排序)
		if (dbType == DBType.DB2) {
			String[] endValues = partitionMeta.getPartitionEndValues();
			boolean[] endInclusives = partitionMeta.getPartitionEndInclusives();
			tableSql.append(NEWLINE).append("(");
			for (int i = 0; i < names.length; i++) {
				if (i > 0) {
					tableSql.append(",");
				}
				tableSql.append(NEWLINE).append(TAB).append("PARTITION ").append(names[i]);
				String low = (values != null && i < values.length) ? values[i] : null;
				String high = (endValues != null && i < endValues.length) ? endValues[i] : null;
				if (StringUtil.isBlank(low) || StringUtil.isBlank(high)) {
					tableSql.append(" /* bound value missing */");
					continue;
				}
				tableSql.append(" STARTING ").append(db2Bound(low, "MINVALUE"));
				tableSql.append(" ENDING ").append(db2Bound(high, "MAXVALUE"));
				// ENDING默认INCLUSIVE,显式EXCLUSIVE须带关键字(endInclusives缺省按true=INCLUSIVE)
				if (endInclusives != null && i < endInclusives.length && !endInclusives[i]) {
					tableSql.append(" EXCLUSIVE");
				}
			}
			tableSql.append(NEWLINE).append(")");
			return;
		}
		boolean lessThan = strategy.startsWith("RANGE");
		// RANGE分区明细必须按上界升序渲染(doris/mysql等拒绝非升序的VALUES LESS THAN);
		// 提取顺序可能非升序(如doris经SHOW PARTITIONS返回pmax在前),此处按值稳定排序,MAXVALUE/空值恒排最后
		if (lessThan && values != null) {
			Integer[] order = new Integer[names.length];
			for (int i = 0; i < order.length; i++) {
				order[i] = i;
			}
			final String[] vals = values;
			java.util.Arrays.sort(order, (x, y) -> comparePartitionBound((x < vals.length) ? vals[x] : null,
					(y < vals.length) ? vals[y] : null));
			String[] sortedNames = new String[names.length];
			String[] sortedValues = new String[names.length];
			for (int i = 0; i < names.length; i++) {
				sortedNames[i] = names[order[i]];
				sortedValues[i] = (order[i] < vals.length) ? vals[order[i]] : null;
			}
			names = sortedNames;
			values = sortedValues;
		}
		tableSql.append(NEWLINE).append("(");
		for (int i = 0; i < names.length; i++) {
			if (i > 0) {
				tableSql.append(",");
			}
			tableSql.append(NEWLINE).append(TAB).append("PARTITION ").append(names[i]);
			String value = (values != null && i < values.length) ? values[i] : null;
			if (lessThan) {
				// RANGE:MAXVALUE仅最后一个分区合法;中间分区缺值时无法合成合法上界,
				// 以/* value missing */显式标记(该语句建表会失败,提示补全分区值,优于静默生成错误边界)
				if (StringUtil.isBlank(value)) {
					if (i == names.length - 1) {
						tableSql.append(" VALUES LESS THAN (MAXVALUE)");
					} else {
						tableSql.append(" /* value missing */");
					}
				} else {
					tableSql.append(" VALUES LESS THAN (").append(quotePartitionValue(value)).append(")");
				}
			} else {
				// LIST:不支持MAXVALUE;缺值时以/* value missing */显式标记(建表会失败,提示补全);
				// oracle/DM语法为"VALUES (v)",mysql系为"VALUES IN (v)"
				if (StringUtil.isBlank(value)) {
					tableSql.append(" /* value missing */");
				} else {
					boolean oracleFamily = isOracleFamily(dbType);
					tableSql.append(oracleFamily ? " VALUES (" : " VALUES IN (").append(quotePartitionValue(value))
							.append(")");
				}
			}
		}
		tableSql.append(NEWLINE).append(")");
	}

	/**
	 * RANGE分区上界比较(用于按升序排序分区明细):MAXVALUE/空值恒排最后;
	 * 数值边界按数值大小(避免'10'<'9'的字典序错误),其余按字典序(同一表内格式一致,ISO日期字典序即时间序)
	 */
	private static int comparePartitionBound(String v1, String v2) {
		boolean m1 = isMaxOrBlankBound(v1);
		boolean m2 = isMaxOrBlankBound(v2);
		if (m1 != m2) {
			return m1 ? 1 : -1;
		}
		if (m1) {
			return 0;
		}
		String s1 = v1.trim();
		String s2 = v2.trim();
		try {
			// 用BigDecimal避免超过2^53的整数分区边界在Double下被判相等而错序
			return new BigDecimal(unquoteBound(s1)).compareTo(new BigDecimal(unquoteBound(s2)));
		} catch (NumberFormatException e) {
			return s1.compareTo(s2);
		}
	}

	private static boolean isMaxOrBlankBound(String v) {
		return StringUtil.isBlank(v) || v.trim().equalsIgnoreCase("MAXVALUE");
	}

	private static String unquoteBound(String v) {
		String s = v.trim();
		if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
			s = s.substring(1, s.length() - 1).trim();
		}
		return s;
	}

	/** 分区值:日期形态加引号(2026-02-01裸值会被解析为算术2023);数字/MAXVALUE/函数调用不加 */
	public static String quotePartitionValue(String value) {
		String v = value.trim();
		if (v.startsWith("'") || v.equalsIgnoreCase("MAXVALUE") || v.matches("\\d+") || v.contains("(")) {
			return v;
		}
		if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
			return "'" + v + "'";
		}
		return v;
	}

	/**
	 * update 2026-10-13
	 * DB2分区边界渲染:MINVALUE/MAXVALUE为DB2关键字不带括号,其余按字面量括号包裹(值引号规则同quotePartitionValue)
	 */
	private static String db2Bound(String value, String keyword) {
		String v = value.trim();
		if (v.equalsIgnoreCase(keyword)) {
			return keyword;
		}
		return "(" + quotePartitionValue(v) + ")";
	}

	/** 分区键列名按输出大小写策略转换后拼接 */
	private static String joinPartitionColumns(String[] columns, String upperOrLower) {
		if (columns == null || columns.length == 0) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < columns.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			result.append(StringUtil.toLowerOrUpper(columns[i], upperOrLower));
		}
		return result.toString();
	}

	public static void wrapTablePrimaryKeys(TableMeta tableMeta, String toUpperOrLower, int dbType,
			StringBuilder tableSql) {
		String primaryKeys = "";
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.isPK()) {
				if (primaryKeys.equals("")) {
					primaryKeys = colMeta.getColName();
				} else {
					primaryKeys = primaryKeys + "," + colMeta.getColName();
				}
			}
		}
		// 主键
		if (!primaryKeys.equals("")) {
			tableSql.append(",").append(NEWLINE);
			tableSql.append(TAB);
			tableSql.append("PRIMARY KEY (").append(StringUtil.toLowerOrUpper(primaryKeys, toUpperOrLower)).append(")");
		}
	}

	/**
	 * 组织索引信息
	 * 
	 * @param tableMeta
	 * @param dbType
	 * @param tableSql
	 * @param outerTable create table () 括号外还是内部
	 */
	public static void wrapTableIndexes(TableMeta tableMeta, String toUpperOrLower, int dbType, StringBuilder tableSql,
			boolean outerTable) {
		if (tableMeta.getIndexes() == null || tableMeta.getIndexes().isEmpty()) {
			return;
		}
		String splitSign = ";";
		String tableName = StringUtil.toLowerOrUpper(tableMeta.getTableName(), toUpperOrLower);
		String indexName;
		// 索引
		for (IndexModel indexModel : tableMeta.getIndexes()) {
			indexName = StringUtil.toLowerOrUpper(indexModel.getName(), toUpperOrLower);
			if (outerTable) {
				tableSql.append(splitSign).append(NEWLINE);
				tableSql.append("CREATE ");
				if (indexModel.isUnique()) {
					tableSql.append("UNIQUE ");
				}
				tableSql.append("INDEX ").append(indexName);
				tableSql.append(" ON ").append(tableName);
			} else {
				tableSql.append(",").append(NEWLINE);
				tableSql.append(TAB);
				if (indexModel.isUnique()) {
					tableSql.append("UNIQUE ");
				}
				tableSql.append("KEY ").append(indexName);
			}
			tableSql.append(" (");
			int meter = 0;
			String[] sortTypes = indexModel.getSortTypes();
			int typeLen = (sortTypes == null) ? 0 : sortTypes.length;
			for (String col : indexModel.getColumns()) {
				if (meter > 0) {
					tableSql.append(",");
				}
				tableSql.append(col);
				if (meter < typeLen && StringUtil.isNotBlank(sortTypes[meter])) {
					tableSql.append(" ").append(sortTypes[meter]);
				}
				meter++;
			}
			tableSql.append(")");
		}
	}

	/**
	 * 组织外键信息
	 * 
	 * @param tableMeta
	 * @param lowerOrUpper
	 * @param dbType
	 * @param tableSql
	 * @param outerTable   create table () 括号外还是内部
	 */
	public static void wrapForeignKeys(TableMeta tableMeta, String lowerOrUpper, int dbType, StringBuilder tableSql,
			boolean outerTable) {
		if (tableMeta.getForeigns() == null || tableMeta.getForeigns().isEmpty()) {
			return;
		}
		// oracle/DM不支持ON UPDATE子句;oracle(非DM)不接受NO ACTION关键字(ORA-02000),
		// oracle/DM(ORA-03001)与mysql系(InnoDB)均不支持SET DEFAULT,须跳过以免生成非法FK
		boolean isOracle = (isOracleFamily(dbType));
		boolean isOracleStrict = (isOracleStrictDialect(dbType));
		boolean isMySqlFamily = (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB
				|| dbType == DBType.OCEANBASE || dbType == DBType.DORIS || dbType == DBType.STARROCKS);
		String splitSign = ";";
		for (ForeignModel foreign : tableMeta.getForeigns()) {
			// ALTER形态整体括号包裹(ADD CONSTRAINT (FOREIGN KEY ... CONSTRAINT name)),
			// 不支持ON UPDATE子句,ON DELETE仅CASCADE/SET NULL(RESTRICT/NO ACTION为默认须省略,
			// 真库实测ON DELETE RESTRICT与命名前置的ADD CONSTRAINT name FOREIGN KEY均语法错)

			if (outerTable) {
				tableSql.append(splitSign).append(NEWLINE);
				tableSql.append("ALTER TABLE ")
						.append(StringUtil.toLowerOrUpper(tableMeta.getTableName(), lowerOrUpper));
				tableSql.append(" ADD ");
			} else {
				tableSql.append(",").append(NEWLINE);
				tableSql.append(TAB);
			}
			tableSql.append(" CONSTRAINT ").append(foreign.getConstraintName());
			tableSql.append(" FOREIGN KEY (").append(
					StringUtil.toLowerOrUpper(StringUtil.linkAry(",", true, foreign.getColumns()), lowerOrUpper))
					.append(")");
			tableSql.append(" REFERENCES ").append(StringUtil.toLowerOrUpper(foreign.getForeignTable(), lowerOrUpper))
					.append("(");
			tableSql.append(StringUtil.toLowerOrUpper(StringUtil.linkAry(",", true, foreign.getForeignColumns()),
					lowerOrUpper));
			tableSql.append(")");
			if (foreign.getDeleteRestict() == ReferentialAction.RESTRICT) {
				if (!isOracle) {
					tableSql.append(" ON DELETE RESTRICT");
				}
			} else if (foreign.getDeleteRestict() == ReferentialAction.CASCADE) {
				tableSql.append(" ON DELETE CASCADE");
			} else if (foreign.getDeleteRestict() == ReferentialAction.SET_NULL) {
				tableSql.append(" ON DELETE SET NULL");
			} else if (foreign.getDeleteRestict() == ReferentialAction.NO_ACTION) {
				// oracle不接受NO ACTION关键字(ORA-02000,其默认行为即NO ACTION),跳过;DM/mysql/pg等支持
				if (!isOracleStrict) {
					tableSql.append(" ON DELETE NO ACTION");
				}
			} else if (foreign.getDeleteRestict() == ReferentialAction.SET_DEFAULT) {
				// oracle/DM(ORA-03001)与mysql系(InnoDB)不支持SET DEFAULT,跳过(退化为默认NO ACTION行为)
				if (!isOracle && !isMySqlFamily) {
					tableSql.append(" ON DELETE SET DEFAULT");
				}
			}
			if (!isOracle) {
				if (foreign.getUpdateRestict() == ReferentialAction.RESTRICT) {
					tableSql.append(" ON UPDATE RESTRICT");
				} else if (foreign.getUpdateRestict() == ReferentialAction.CASCADE) {
					tableSql.append(" ON UPDATE CASCADE");
				} else if (foreign.getUpdateRestict() == ReferentialAction.SET_NULL) {
					tableSql.append(" ON UPDATE SET NULL");
				} else if (foreign.getUpdateRestict() == ReferentialAction.NO_ACTION) {
					tableSql.append(" ON UPDATE NO ACTION");
				} else if (foreign.getUpdateRestict() == ReferentialAction.SET_DEFAULT) {
					// mysql系(InnoDB)不支持ON UPDATE SET DEFAULT
					if (!isMySqlFamily) {
						tableSql.append(" ON UPDATE SET DEFAULT");
					}
				}
			}
		}
	}

	/**
	 * 统一处理表和字段的备注
	 * 
	 * @param tableMeta
	 * @param lowerOrUpper
	 * @param dbType
	 * @param tableSql
	 */
	public static void wrapTableAndColumnsComment(TableMeta tableMeta, String lowerOrUpper, int dbType,
			StringBuilder tableSql) {
		String splitSign = ";";
		// 表注释
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(splitSign);
			tableSql.append(NEWLINE);
			tableSql.append("COMMENT ON TABLE ")
					.append(StringUtil.toLowerOrUpper(tableMeta.getTableName(), lowerOrUpper)).append(" IS '")
					.append(tableMeta.getRemarks()).append("'");
		}
		// 字段注释
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(splitSign);
				tableSql.append(NEWLINE);
				tableSql.append("COMMENT ON COLUMN ")
						.append(StringUtil.toLowerOrUpper(tableMeta.getTableName(), lowerOrUpper)).append(".")
						.append(colMeta.getColName()).append(" IS '").append(colMeta.getComments()).append("'");
			}
		}
	}

	/**
	 * 判断类型默认值是否需要加单引号
	 * 
	 * @param dataType
	 * @return
	 */
	public static boolean isNotChar(int dataType) {
		if (dataType == Types.BIGINT || dataType == Types.INTEGER || dataType == Types.BOOLEAN
				|| dataType == Types.DECIMAL || dataType == Types.DOUBLE || dataType == Types.NUMERIC
				|| dataType == Types.FLOAT || dataType == Types.REAL || dataType == Types.SMALLINT
				|| dataType == Types.TINYINT || dataType == Types.BIT) {
			return true;
		}
		return false;
	}

	/**
	 * 判断是否是日期或时间类型
	 * 
	 * @param dataType
	 * @return
	 */
	public static boolean isDate(int dataType) {
		if (dataType == Types.DATE || dataType == Types.TIME || dataType == Types.TIMESTAMP
				|| dataType == Types.TIME_WITH_TIMEZONE || dataType == Types.TIMESTAMP_WITH_TIMEZONE) {
			return true;
		}
		return false;
	}

	/**
	 * update 2026-9-5
	 * 判断字段Java类型是否为日期+时间类型(供Types.DATE列类型精化使用):java.time.LocalDateTime与
	 * java.util.Date携带时间部分,LocalDate/java.sql.Date为纯日期;typeName取自FieldMeta.fieldType
	 * (小写全类名),可能为null(空值按纯日期处理,尊重显式声明)
	 *
	 * @param typeName
	 * @return
	 */
	private static boolean isTimeCarryingJavaType(String typeName) {
		if (typeName == null) {
			return false;
		}
		String tmp = typeName.toLowerCase(Locale.ROOT);
		return "java.time.localdatetime".equals(tmp) || "java.util.date".equals(tmp);
	}

	/**
	 * update 2026-9-5 判断数据库的DATE列类型是否为纯日期语义:dm的DATE列只存日期(dm 21c实测)、pg及衍生库的date只存日期
	 * (pg 18.6实测,timestamp写入静默截断时间);oracle的DATE含时间、mysql系无纯DATE列(Types.DATE已统一
	 * 转DATETIME),均不在精化范围;kingbase/openGauss/MogDB等PG衍生库的date在实例为Oracle兼容模式时含时间
	 * (此时TIMESTAMP为无损超型),PG模式时只存日期(此时TIMESTAMP修复丢时间),两种模式下输出TIMESTAMP均无损, 按安全优先纳入
	 *
	 * @param dbType
	 * @return
	 */
	/**
	 * Oracle 兼容家族(含 OceanBase 的 oceanbase 方言):类型名/标识符/DDL 语法按 Oracle 处理
	 */
	private static boolean isOracleFamily(int dbType) {
		return dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM
				|| dbType == DBType.OCEANBASE;
	}

	/**
	 * Oracle 原生语义(不含 DM 的差异化类型;OceanBase 按 Oracle 兼容处理)
	 */
	private static boolean isOracleStrictDialect(int dbType) {
		return dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.OCEANBASE;
	}

	private static boolean isDateOnlyDialect(int dbType) {
		return dbType == DBType.DM || dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14
				|| dbType == DBType.GAUSSDB || dbType == DBType.KINGBASE || dbType == DBType.HIGHGO
				|| dbType == DBType.MOGDB || dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE
				|| dbType == DBType.STARDB || dbType == DBType.OSCAR || dbType == DBType.HANA;
	}

	/**
	 * update 2026-10-22
	 * boolean默认值按目标库归一:oracle系(BOOLEAN列映射INTEGER)与sqlserver(BIT)无true/false字面量,
	 * 裸DEFAULT
	 * true建表报ORA-00904/无效列名(响亮失败),true/false统一转1/0;mysql系(TINYINT(1))/pg系/H2/
	 * sqlite等TRUE/FALSE为原生字面量原样保留。仅精确匹配true/false(忽略大小写),生成列表达式等其余形态不受影响
	 *
	 * @param defaultValue 原始默认值
	 * @param dataType     字段JDBC类型
	 * @param dbType       目标库类型(未知-1原样返回)
	 * @return 归一后的默认值
	 */
	public static String normalizeBooleanDefault(String defaultValue, int dataType, int dbType) {
		if (defaultValue == null || dataType != Types.BOOLEAN) {
			return defaultValue;
		}
		String tmp = defaultValue.trim();
		if (!"true".equalsIgnoreCase(tmp) && !"false".equalsIgnoreCase(tmp)) {
			return defaultValue;
		}
		if (isOracleFamily(dbType) || dbType == DBType.SQLSERVER) {
			return "true".equalsIgnoreCase(tmp) ? "1" : "0";
		}
		return defaultValue;
	}

	/**
	 * 判断是否是日期函数，对默认值处理时不需要加单引号
	 *
	 * @param defaultValue
	 * @return
	 */
	public static boolean isDateFunction(String defaultValue) {
		if (defaultValue.equals("SYSDATE()") || defaultValue.equals("SYSDATE") || defaultValue.equals("NOW()")
				|| defaultValue.equals("GETDATE()") || defaultValue.equals("CURRENT_TIMESTAMP()")
				|| defaultValue.equals("CURRENT_TIMESTAMP") || defaultValue.equals("CURRENT_DATE")
				|| defaultValue.equals("CURDATE()") || defaultValue.equals("CURTIME()")
				|| defaultValue.equals("LOCALTIMESTAMP") || defaultValue.equals("SYSTIMESTAMP")
				|| defaultValue.equals("SYSDATETIME()") || defaultValue.equals("sysdatetime()")
				|| defaultValue.equals("LOCALTIME")) {
			return true;
		}
		// 带精度形态:CURRENT_TIMESTAMP(3)/NOW(6)等(mysql/mariadb合法时间函数默认值)
		if (defaultValue.matches("(?i)(CURRENT_TIMESTAMP|NOW|LOCALTIMESTAMP)\\s*\\(\\s*\\d+\\s*\\)")) {
			return true;
		}
		return false;
	}

	/**
	 * 将注释中的单引号转义为标准SQL的''形式(Oracle/PostgreSQL/MySQL等主流库的字符串字面量均支持),
	 * 表和字段注释统一使用本方法保证转义一致;注释处于单引号字面量内,双引号无需转义,
	 * 反斜杠在标准SQL中是普通字符(MySQL特有转义由MySqlDDLGenerator输出时单独处理)
	 * 
	 * @param str
	 * @return
	 */
	private static String escapeCommentForDdl(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.replace("'", "''");
	}
}
