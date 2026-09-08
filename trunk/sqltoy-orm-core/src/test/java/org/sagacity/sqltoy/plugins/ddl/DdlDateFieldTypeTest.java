package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.impl.OracleDDLGenerator;
import org.sagacity.sqltoy.plugins.ddl.impl.PostgreSqlDDLGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * DDL字段日期类型映射回归:update 2026-9-5 修复dm/pg按生成DDL建表后时间部分被静默清零的缺陷
 * (实测dm的DATE列类型只存日期;pg系date同样只存日期,pg 18.6实测timestamp写入静默截断时间)。
 * 修复采用DDL层依据字段Java类型(typeName)精化列类型:dm上含时间类型(LocalDateTime/java.util.Date)
 * 输出DATETIME、pg系输出TIMESTAMP,纯日期类型(LocalDate/java.sql.Date)保持DATE。
 * 同时守卫字段JDBC类型探测不被调整:sqlserver运行时将Types.TIMESTAMP视作rowversion列,
 * 从merge/insert/update中整体剔除(见DialectUtils.getSaveOrUpdateSql,按dbType=sqlserver门控),
 * 时间字段探测若误归TIMESTAMP会导致普通时间字段被剔除。
 * oracle生成器即dm所用生成器(DDLFactory.getGenerator)。
 */
public class DdlDateFieldTypeTest {

	@Entity(tableName = "ddl_date_probe")
	public static class DdlDateProbeEntity {

		@Id
		@Column(name = "ID", length = 22L, type = java.sql.Types.VARCHAR, nullable = false)
		private String id;

		/** 纯日期类型 */
		@Column(name = "BIZ_DATE", nullable = true)
		private LocalDate bizDate;

		/** 日期+时间类型 */
		@Column(name = "CREATE_TIME", nullable = true)
		private LocalDateTime createTime;

		/** java.util.Date同为日期+时间类型 */
		@Column(name = "UPDATE_TIME", nullable = true)
		private Date updateTime;

		/**
		 * oracle项目迁移形态:quickvo在oracle侧为TIMESTAMP列生成显式type=TIMESTAMP,
		 * sqlserver建表必须输出DATETIME2(输出TIMESTAMP会建成rowversion列,插入报错)
		 */
		@Column(name = "MIG_TIME", nullable = true, type = java.sql.Types.TIMESTAMP)
		private LocalDateTime migTime;

		public String getId() {
			return id;
		}

		public LocalDate getBizDate() {
			return bizDate;
		}

		public LocalDateTime getCreateTime() {
			return createTime;
		}

		public Date getUpdateTime() {
			return updateTime;
		}

		public LocalDateTime getMigTime() {
			return migTime;
		}
	}

	private EntityMeta scanProbeEntity() {
		EntityManager entityManager = new EntityManager();
		return entityManager.parseEntityMeta(new SqlToyContext(), DdlDateProbeEntity.class, true, false);
	}

	@Test
	public void autoDetectDateFieldTypes() {
		EntityMeta entityMeta = scanProbeEntity();
		assertNotNull(entityMeta, "实体解析失败");
		Map<String, FieldMeta> fieldsMeta = entityMeta.getFieldsMeta();
		// fieldsMeta键为属性名小写;update 2026-9-6 LocalDateTime归位TIMESTAMP:
		// 此前防御性映射DATE的唯一动机(旧rowversion判据按type==TIMESTAMP剔除)已消除,
		// rowversion现按目标库元数据校准(ensureRowVersionMeta),实体侧TIMESTAMP不再触发剔除
		assertEquals(Types.TIMESTAMP, fieldsMeta.get("createtime").getType(), "LocalDateTime探测为TIMESTAMP(语义归位)");
		assertEquals(Types.DATE, fieldsMeta.get("updatetime").getType(), "java.util.Date保持DATE(遗留类型保守不动)");
		assertEquals(Types.DATE, fieldsMeta.get("bizdate").getType(), "LocalDate探测为DATE(纯日期)");
	}

	@Test
	public void dmConvertDateColumnTypes() {
		// dm依据字段Java类型精化:含时间类型(LocalDateTime/java.util.Date)→DATETIME,纯日期→DATE
		EntityMeta entityMeta = scanProbeEntity();
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.DM);
		String createTimeType = null;
		String bizDateType = null;
		String updateTimeType = null;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.getColName().equalsIgnoreCase("CREATE_TIME")) {
				createTimeType = DDLUtils.convertType(colMeta, DBType.DM);
			} else if (colMeta.getColName().equalsIgnoreCase("UPDATE_TIME")) {
				updateTimeType = DDLUtils.convertType(colMeta, DBType.DM);
			} else if (colMeta.getColName().equalsIgnoreCase("BIZ_DATE")) {
				bizDateType = DDLUtils.convertType(colMeta, DBType.DM);
			}
		}
		// update 2026-9-6 LocalDateTime归位TIMESTAMP后,CREATE_TIME列走case TIMESTAMP分支直接输出TIMESTAMP
		// (dm的TIMESTAMP含时间语义等价DATETIME,合法且精度更高;java.util.Date仍走DATE+精化→DATETIME)
		assertEquals("TIMESTAMP", createTimeType, "dm的LocalDateTime列输出TIMESTAMP(类型语义归位)");
		assertEquals("DATETIME", updateTimeType, "dm的java.util.Date列精化为DATETIME");
		assertEquals("DATE", bizDateType, "dm的LocalDate列保持DATE(纯日期语义)");
		// 生成的DDL语句
		String ddl = new OracleDDLGenerator().createTableSql(tableMeta, null, "upper", DBType.DM);
		assertTrue(ddl.contains("CREATE_TIME TIMESTAMP"), "dm DDL应包含CREATE_TIME TIMESTAMP: " + ddl);
		assertTrue(ddl.contains("UPDATE_TIME DATETIME"), "dm DDL应包含UPDATE_TIME DATETIME: " + ddl);
		assertTrue(ddl.contains("BIZ_DATE DATE"), "dm DDL应包含BIZ_DATE DATE: " + ddl);
	}

	@Test
	public void pgConvertDateColumnTypes() {
		// pg系的date列只存日期(pg 18.6实测,timestamp写入静默截断时间),含时间字段应输出TIMESTAMP
		EntityMeta entityMeta = scanProbeEntity();
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.POSTGRESQL);
		String createTimeType = null;
		String bizDateType = null;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.getColName().equalsIgnoreCase("CREATE_TIME")) {
				createTimeType = DDLUtils.convertType(colMeta, DBType.POSTGRESQL);
			} else if (colMeta.getColName().equalsIgnoreCase("BIZ_DATE")) {
				bizDateType = DDLUtils.convertType(colMeta, DBType.POSTGRESQL);
			}
		}
		assertEquals("TIMESTAMP", createTimeType, "pg的LocalDateTime列应为TIMESTAMP(pg date只存日期)");
		assertEquals("DATE", bizDateType, "pg的LocalDate列保持DATE(纯日期语义)");
		String ddl = new PostgreSqlDDLGenerator().createTableSql(tableMeta, null, "upper", DBType.POSTGRESQL);
		assertTrue(ddl.contains("CREATE_TIME TIMESTAMP"), "pg DDL应包含CREATE_TIME TIMESTAMP: " + ddl);
		assertTrue(ddl.contains("BIZ_DATE DATE"), "pg DDL应包含BIZ_DATE DATE: " + ddl);
	}

	@Test
	public void explicitDateColumnOnDm() {
		// 显式声明路径:quickvo生成的实体常显式@Column(type=DATE)声明LocalDateTime字段,
		// 依赖convertType按字段Java类型(typeName)精化兜底
		ColumnMeta createTimeCol = new ColumnMeta();
		createTimeCol.setColName("CREATE_TIME");
		createTimeCol.setDataType(Types.DATE);
		createTimeCol.setTypeName("java.time.localdatetime");
		assertEquals("DATETIME", DDLUtils.convertType(createTimeCol, DBType.DM), "dm显式DATE+LocalDateTime应转DATETIME");
		// 纯日期类型显式声明DATE保持DATE
		ColumnMeta bizDateCol = new ColumnMeta();
		bizDateCol.setColName("BIZ_DATE");
		bizDateCol.setDataType(Types.DATE);
		bizDateCol.setTypeName("java.time.localdate");
		assertEquals("DATE", DDLUtils.convertType(bizDateCol, DBType.DM), "dm显式DATE+LocalDate保持DATE");
	}

	@Test
	public void oracleColumnTypesUnchanged() {
		// update 2026-9-6 LocalDateTime归位TIMESTAMP后,CREATE_TIME输出oracle TIMESTAMP(含时间且精度到小数秒);
		// BIZ_DATE(LocalDate)保持DATE,java.util.Date走DATE分支保持DATE(oracle DATE含时间)
		EntityMeta entityMeta = scanProbeEntity();
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.ORACLE);
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.getColName().equalsIgnoreCase("CREATE_TIME")) {
				assertEquals("TIMESTAMP", DDLUtils.convertType(colMeta, DBType.ORACLE),
						"oracle的LocalDateTime列输出TIMESTAMP(类型语义归位)");
			} else if (colMeta.getColName().equalsIgnoreCase("BIZ_DATE")) {
				assertEquals("DATE", DDLUtils.convertType(colMeta, DBType.ORACLE), "oracle的LocalDate列保持DATE");
			}
		}
		String ddl = new OracleDDLGenerator().createTableSql(tableMeta, null, "upper", DBType.ORACLE);
		assertTrue(ddl.contains("CREATE_TIME TIMESTAMP"), "oracle DDL应包含CREATE_TIME TIMESTAMP: " + ddl);
		assertTrue(ddl.contains("BIZ_DATE DATE"), "oracle DDL应包含BIZ_DATE DATE: " + ddl);
	}

	@Test
	public void sqlserverSaveOrUpdateKeepsTimeFields() {
		// update 2026-9-6 守卫语义更新:LocalDateTime探测归位TIMESTAMP,本用例验证"校准后TIMESTAMP
		// 不触发rowversion剔除"(rowversion按目标库元数据校准,与实体JDBC类型解耦);
		// 直调静态生成方法未经dialect入口ensure时,rowVersionColumns为null回退旧type判据(保守兜底,
		// 服务绕过框架入口的场景),故此处模拟校准完成(空集合=无rowversion列)再断言
		EntityMeta entityMeta = scanProbeEntity();
		entityMeta.setRowVersionColumns(new org.sagacity.sqltoy.model.IgnoreCaseSet());
		// update 2026-9-7 统一由DialectUtils.getSaveOrUpdateSql生成(rowversion排除按dbType=sqlserver门控)
		String sql = DialectUtils.getSaveOrUpdateSql(null, null, DBType.SQLSERVER, entityMeta, PKStrategy.ASSIGN,
				null, null, "isnull", null, true, "ddl_date_probe");
		assertTrue(sql.contains("CREATE_TIME"), "sqlserver merge语句应包含CREATE_TIME: " + sql);
		assertTrue(sql.contains("UPDATE_TIME"), "sqlserver merge语句应包含UPDATE_TIME: " + sql);
		assertTrue(sql.contains("BIZ_DATE"), "sqlserver merge语句应包含BIZ_DATE: " + sql);
	}

	/**
	 * oracle迁移形态的DDL守卫:显式type=TIMESTAMP的业务时间列在sqlserver建表必须输出DATETIME2,
	 * 绝不能输出TIMESTAMP(sqlserver的TIMESTAMP即rowversion行版本戳,建出的列不可写,save/insert直接报错)
	 */
	@Test
	public void sqlserverExplicitTimestampColumnDdl() {
		EntityMeta entityMeta = scanProbeEntity();
		TableMeta tableMeta = DDLUtils.wrapTableMeta(entityMeta, DBType.SQLSERVER);
		boolean found = false;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (colMeta.getColName().equalsIgnoreCase("MIG_TIME")) {
				found = true;
				assertEquals("DATETIME2", DDLUtils.convertType(colMeta, DBType.SQLSERVER),
						"显式type=TIMESTAMP列在sqlserver应输出DATETIME2(oracle迁移场景)");
			}
		}
		assertTrue(found, "MIG_TIME列应存在");
		String ddl = new org.sagacity.sqltoy.plugins.ddl.impl.SqlServerDDLGenerator().createTableSql(tableMeta, null,
				"upper", DBType.SQLSERVER);
		assertTrue(ddl.contains("MIG_TIME DATETIME2"), "sqlserver DDL应含MIG_TIME DATETIME2: " + ddl);
		// 整条DDL不得出现裸TIMESTAMP类型(即rowversion语义)
		assertFalse(ddl.matches("(?is).*[^_A-Z]TIMESTAMP[^_A-Z(].*"),
				"sqlserver DDL不得出现TIMESTAMP类型(会建成rowversion列): " + ddl);
	}
}
