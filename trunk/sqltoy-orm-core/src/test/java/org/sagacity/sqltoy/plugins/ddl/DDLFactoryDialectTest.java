package org.sagacity.sqltoy.plugins.ddl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.ddl.impl.OracleDDLGenerator;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * DDLFactory 方言生成器选择:OceanBase 的 oceanbase 方言继承 OracleDialect,应走 Oracle DDL
 */
public class DDLFactoryDialectTest {

	@Test
	public void oceanBaseUsesOracleDdlGenerator() {
		assertTrue(DDLFactory.getGenerator(DBType.OCEANBASE) instanceof OracleDDLGenerator,
				"oceanbase should use OracleDDLGenerator");
	}
}
