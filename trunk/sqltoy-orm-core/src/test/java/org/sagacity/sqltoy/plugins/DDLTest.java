/**
 * 
 */
package org.sagacity.sqltoy.plugins;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.plugins.ddl.DDLFactory;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sagacity-sqltoy
 * @description 请在此说明类的功能
 * @author zhong
 * @version v1.0, Date:2023年12月20日
 * @modify 2023年12月20日,修改说明
 */
public class DDLTest {
	@Test
	public void testCreateSqlFile() {
		String[] scanPackages = new String[] { "org.sagacity.sqltoy.demo.domain" };
		try {
			DDLFactory.createSqlFile(scanPackages, "D://sqltoy.sql", "upper", DBType.MYSQL, null, null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
