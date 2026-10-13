package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.CaseType;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * update 2026-10-12 生成主键列提示大小写分派锁定(15库真库矩阵核实):
 * pg折叠内核(opengauss/og7/vastbase/highgo全大写与混合大小写报column does not exist、
 * kingbase混合大小写报错,转小写全家族安全)归LOWER;mysql系/tidb/ob/sqlserver/db2/dm/
 * docker环境,按opengauss同内核系谱与原注释声明一并纳入LOWER
 */
public class ReturnPrimaryKeyColumnCaseTest {

	@Test
	public void pgFoldFamilyLower() {
		int[] lowerFamily = { DBType.POSTGRESQL, DBType.POSTGRESQL14, DBType.OPENGAUSS, DBType.GAUSSDB,
				DBType.MOGDB, DBType.VASTBASE, DBType.STARDB, DBType.OSCAR, DBType.KINGBASE, DBType.HIGHGO };
		for (int dbType : lowerFamily) {
			assertEquals(CaseType.LOWER, DataSourceUtils.getReturnPrimaryKeyColumnCase(dbType),
					"dbType=" + dbType + "应转小写");
		}
	}

	@Test
	public void insensitiveFamilyDefault() {
		// 真库矩阵:id/ID/Id三形态getGeneratedKeys全过,提示保持原样
		int[] defaultFamily = { DBType.MYSQL, DBType.MYSQL57, DBType.TIDB, DBType.OCEANBASE, DBType.SQLSERVER,
				DBType.DB2, DBType.DM, DBType.ORACLE, DBType.ORACLE11, DBType.HANA };
		for (int dbType : defaultFamily) {
			assertEquals(CaseType.DEFAULT, DataSourceUtils.getReturnPrimaryKeyColumnCase(dbType),
					"dbType=" + dbType + "应维持DEFAULT");
		}
	}

	@Test
	public void columnConvertedToLowerForFamily() {
		assertEquals("orderid", DataSourceUtils.getReturnPrimaryKeyColumn("OrderId", DBType.OPENGAUSS),
				"opengauss列提示应转小写");
		assertEquals("orderid", DataSourceUtils.getReturnPrimaryKeyColumn("ORDERID", DBType.KINGBASE),
				"kingbase列提示应转小写(混合大小写实测报错,小写兜底)");
		assertEquals("OrderId", DataSourceUtils.getReturnPrimaryKeyColumn("OrderId", DBType.MYSQL),
				"mysql列提示保持原样");
	}
}
