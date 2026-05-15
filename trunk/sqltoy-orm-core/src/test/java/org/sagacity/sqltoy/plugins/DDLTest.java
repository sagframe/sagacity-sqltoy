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
		// 指定POJO所在的包路径
		String[] scanPackages = new String[] { "org.sagacity.sqltoy.demo.domain" };
		try {
			/**
			 * @param scanPackages
			 * @param saveFile            脚本存放文件
			 * @param upperOrLower        upper|lower 脚本表名、字段名是否统一转大写或小写
			 * @param dbType              数据库类型，用DBType.xx 提供
			 * @param schema              针对sqlserver需要提供(其他数据库可为null)，项目启动时会根据connection获取schema
			 * @param dialectDDLGenerator 自己指定ddl创建器,如果是：mysql、oracle、pg、sqlserver等数据库无需扩展，传递null即可
			 */
			DDLFactory.createSqlFile(scanPackages, "D://sqltoy.sql", "upper", DBType.MYSQL, null, null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
