package org.sagacity.sqltoy.plugins.ddl;

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
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.JdbcTypes;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
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
	 * 因为存在外键关系，首先需要对表进行排序，被依赖的优先创建
	 * <p>
	 * update 2026-9-15 重写为Kahn拓扑排序:原"贪心单层前置"算法对链式依赖(A←B←C)在
	 * ConcurrentHashMap不利迭代顺序下产出[B,C,A](处理C时前置B但不考虑B自身的依赖,
	 * 处理B时A未入队被append到尾部),而外键约束输出对建表顺序硬依赖(mysql/pg内联 FOREIGN KEY于CREATE
	 * TABLE,oracle/h2/sqlserver的ALTER ADD CONSTRAINT紧跟本表
	 * CREATE之后),错序即建表失败;且原输出依赖hash序跨环境不可复现、swotTables全量
	 * 重建最坏O(n²)。新算法O(V+E):零入度集合按表名字典序出队保证脚本可复现,
	 * 自环(树表自引用外键,内联合法)不参与排序约束,多外键指向同表去重防入度虚增,
	 * 节点集外的外表引用忽略(与原实现一致),环状残余(互相外键,内联约束下本就无解) 按名序追加+warn提示,不吞表不死循环
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
			lowerKeyMap.put(key.toLowerCase(), key);
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
				if (entityMeta.getSchema() != null && !foreignTable.startsWith(entityMeta.getSchema().concat("."))) {
					foreignTable = entityMeta.getSchema().concat(".").concat(foreignTable);
				}
				// 大小写不敏感:@Foreign(table="QS_STAFF")与@Entity(tableName="qs_staff")等场景
				String realKey = lowerKeyMap.get(foreignTable.toLowerCase());
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
			columnMeta.setDefaultValue(fieldMeta.getDefaultValue());
			columnMeta.setNullable(fieldMeta.isNullable());
			columnMeta.setDataType(fieldMeta.getType());
			columnMeta.setTypeName(fieldMeta.getFieldType());
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
		if (colMeta.getNativeType() != null) {
			if (colMeta.getNativeType().equalsIgnoreCase("JSON")) {
				return jsonCarrierType(dbType);
			} else if (colMeta.getNativeType().equalsIgnoreCase("BSON")) {
				if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
						|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
						|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE) {
					return "BSON";
				} else {
					return jsonCarrierType(dbType);
				}
			}
		}
		boolean isBytes = false;
		String typeName = "VARCHAR";
		switch (colMeta.getDataType()) {
		case java.sql.Types.BIGINT:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
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
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "INTEGER";
			} else {
				typeName = "TINYINT";
				typeName = setLength(typeName, true, colMeta);
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
		case java.sql.Types.NVARCHAR:
			typeName = (dbType == DBType.HANA) ? "NVARCHAR" : "VARCHAR";
			typeName = setLength(typeName, false, colMeta);
			break;
		case java.sql.Types.LONGNVARCHAR:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.H2) {
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
					// update 2026-9-14 补KINGBASE(KingbaseES基于PG,类型映射归PG系)
					|| dbType == DBType.KINGBASE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				// 精度需跟在TIMESTAMP之后:TIMESTAMP(6) WITH TIME ZONE
				typeName = "TIMESTAMP";
				if (colMeta.getColumnSize() > 0) {
					typeName = typeName + "(" + colMeta.getColumnSize() + ")";
				}
				typeName = typeName + " WITH TIME ZONE";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = setTimePrecision("DATETIMEOFFSET", colMeta);
			} else {
				typeName = "TIMESTAMP";
			}
			break;
		case java.sql.Types.BLOB:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
					|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE) {
				typeName = "BYTEA";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "VARBINARY(MAX)";
			} else {
				typeName = "BLOB";
			}
			isBytes = true;
			break;
		case java.sql.Types.BINARY:
			if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
					|| dbType == DBType.OPENGAUSS || dbType == DBType.STARDB || dbType == DBType.OSCAR
					|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE) {
				typeName = "BYTEA";
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
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
					|| dbType == DBType.MOGDB || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE) {
				typeName = "BYTEA";
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
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
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM || dbType == DBType.H2
					|| dbType == DBType.DB2) {
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
			} else {
				typeName = "TIMESTAMP";
			}
			break;
		case java.sql.Types.DATE:
			// update 2026-9-5 修复dm/pg按生成DDL建表后时间部分被静默清零的缺陷:实测dm的DATE列类型
			// 只存日期(与oracle的DATE含时间不同),pg系date列同样只存日期(pg 18.6实测,timestamp
			// 写入date列静默截断时间);java.util.Date/LocalDateTime这类含时间字段(无论@Column未指定
			// type自动探测,还是quickvo生成实体显式声明type=DATE)此前均输出DATE列;
			// 依据字段Java类型(typeName,小写全类名)精化:dm输出DATETIME,pg系输出TIMESTAMP,
			// 纯日期类型(LocalDate/java.sql.Date)保持DATE语义不变;
			// update 2026-9-6 原"不得将探测归入Types.TIMESTAMP"的约束已解除:rowversion剔除
			// 改按目标库元数据校准(ensureRowVersionMeta),与实体JDBC类型解耦,LocalDateTime已归位TIMESTAMP
			if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.SQLSERVER
					|| dbType == DBType.DORIS || dbType == DBType.STARROCKS) {
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
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "INTEGER";
			} else if (dbType == DBType.SQLSERVER) {
				// sqlserver无boolean类型,bit为0/1
				typeName = "BIT";
			} else if (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.DORIS
					|| dbType == DBType.STARROCKS) {
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
			if (dbType == DBType.H2) {
				// h2无向量类型,测试场景按varchar存储'[1,2,3]'字符串形式
				typeName = "VARCHAR";
				typeName = setLength(typeName, false, colMeta);
			} else if (dbType == DBType.DB2) {
				// update 2026-9-10 实测db2 12.1.2.0/12.1.5.0:VECTOR须显式坐标类型双参形态
				// VECTOR(n, FLOAT32),单参VECTOR(n)报-104语法错误、裸VECTOR报-901
				// "Unknown vector coordinate type";维度必填(@Column(length=xxx)),
				// 其他坐标类型(FLOAT64等)经@Column(nativeType=...)覆盖
				typeName = (colMeta.getColumnSize() > 0) ? ("VECTOR(" + colMeta.getColumnSize() + ", FLOAT32)")
						: "VECTOR";
			} else if (dbType == DBType.DORIS) {
				// update 2026-9-10 实测doris 4.1.3:无VECTOR(n)列类型(解析器类型全集无VECTOR),
				// 向量以ARRAY<FLOAT>承载(近邻检索配VECTOR索引),字符串'[1,2,3]'绑定隐式转换、
				// 读回为'[1, 2, 3]'文本均实证可行;维度不进列定义(向量索引声明处约束)
				typeName = "ARRAY<FLOAT>";
			} else if (dbType == DBType.CLICKHOUSE) {
				// update 2026-9-11 实测clickhouse 26.8.2.7:无vector类型族(报Unknown data type
				// family: vector),向量以Array(Float32)承载(同doris思路):字符串'[1,2,3]'插入
				// 隐式解析、读回'[1,2,3]'文本、L2Distance(v,[..])距离检索均实证可行
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
			// 空间类型:oracle/dm为SDO_GEOMETRY,其余(postgis系/mysql/sqlserver/h2)为GEOMETRY
			// 类型精度修饰(如geometry(Point,4326)、geography)通过@Column(nativeType="...")指定;
			// nativeType未配置时注解默认为空串(非null),须按blank判断,否则生成空列类型导致DDL非法
			if (StringUtil.isNotBlank(colMeta.getNativeType())) {
				typeName = colMeta.getNativeType();
			} else if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "SDO_GEOMETRY";
			} else if (dbType == DBType.HANA) {
				// hana空间类型为ST_GEOMETRY(构造函数ST_GeomFromText与mysql系同名同参)
				typeName = "ST_GEOMETRY";
			} else {
				typeName = "GEOMETRY";
			}
			break;
		}
		case java.sql.Types.FLOAT:
			typeName = "FLOAT";
			break;
		case java.sql.Types.DOUBLE:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11) {
				typeName = "BINARY_DOUBLE";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
			// update 2026-9-14 补KINGBASE(KingbaseES基于PG,类型映射归PG系)
					|| dbType == DBType.KINGBASE || dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB
					|| dbType == DBType.STARDB || dbType == DBType.OSCAR || dbType == DBType.VASTBASE
					|| dbType == DBType.DM || dbType == DBType.H2) {
				typeName = "DOUBLE PRECISION";
			} else if (dbType == DBType.SQLSERVER) {
				typeName = "FLOAT";
			} else {
				typeName = "DOUBLE";
			}
			break;
		case java.sql.Types.DECIMAL:
		case java.sql.Types.NUMERIC:
			if (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM) {
				typeName = "NUMBER";
			} else if (dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.KINGBASE) {
				// update 2026-9-14 补KINGBASE(KingbaseES基于PG,numeric为任意精度,同vanilla PG)
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
		if ((dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14 || dbType == DBType.GAUSSDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.MOGDB || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR || dbType == DBType.VASTBASE || dbType == DBType.KINGBASE)
				&& colMeta.getDataType() != JdbcTypes.VECTOR && colMeta.getTypeName() != null
				&& colMeta.getTypeName().endsWith("[]") && !isBytes && !typeName.startsWith("_")) {
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
					boolean oracleFamily = dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM;
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
			return Double.compare(Double.parseDouble(unquoteBound(s1)), Double.parseDouble(unquoteBound(s2)));
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
	private static String quotePartitionValue(String value) {
		String v = value.trim();
		if (v.startsWith("'") || v.equalsIgnoreCase("MAXVALUE") || v.matches("\\d+") || v.contains("(")) {
			return v;
		}
		if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
			return "'" + v + "'";
		}
		return v;
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
		boolean isOracle = (dbType == DBType.ORACLE || dbType == DBType.ORACLE11 || dbType == DBType.DM);
		boolean isOracleStrict = (dbType == DBType.ORACLE || dbType == DBType.ORACLE11);
		boolean isMySqlFamily = (dbType == DBType.MYSQL || dbType == DBType.MYSQL57 || dbType == DBType.TIDB
				|| dbType == DBType.OCEANBASE || dbType == DBType.DORIS || dbType == DBType.STARROCKS);
		String splitSign = ";";
		for (ForeignModel foreign : tableMeta.getForeigns()) {
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
	 * 判断字段Java类型是否为日期+时间类型(update 2026-9-5 供Types.DATE列类型精化使用):
	 * java.time.LocalDateTime与java.util.Date携带时间部分,LocalDate/java.sql.Date为纯日期;
	 * typeName取自FieldMeta.fieldType(小写全类名),可能为null(空值按纯日期处理,尊重显式声明)
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
	 * 判断数据库的DATE列类型是否为纯日期语义(update 2026-9-5): dm的DATE列只存日期(dm
	 * 21c实测)、pg及衍生库的date只存日期(pg 18.6实测,timestamp写入静默截断时间);
	 * oracle的DATE含时间、mysql系无纯DATE列( Types.DATE已统一转DATETIME ),均不在精化范围;
	 * kingbase/openGauss/MogDB等PG衍生库的date在实例为Oracle兼容模式时含时间(此时TIMESTAMP为无损超型),
	 * PG模式时只存日期(此时TIMESTAMP修复丢时间),两种模式下输出TIMESTAMP均无损,按安全优先纳入
	 * 
	 * @param dbType
	 * @return
	 */
	private static boolean isDateOnlyDialect(int dbType) {
		return dbType == DBType.DM || dbType == DBType.POSTGRESQL || dbType == DBType.POSTGRESQL14
				|| dbType == DBType.GAUSSDB || dbType == DBType.KINGBASE || dbType == DBType.MOGDB
				|| dbType == DBType.OPENGAUSS || dbType == DBType.VASTBASE || dbType == DBType.STARDB
				|| dbType == DBType.OSCAR || dbType == DBType.HANA;
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
				|| defaultValue.equals("LOCALTIME") || defaultValue.equals("TODAY")) {
			return true;
		}
		// 带精度形态:CURRENT_TIMESTAMP(3)/NOW(6)等(mysql/mariadb合法时间函数默认值)
		return defaultValue.matches("(?i)(CURRENT_TIMESTAMP|NOW|LOCALTIMESTAMP)\\s*\\(\\s*\\d+\\s*\\)");
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
