/**
 * 
 */
package org.sagacity.sqltoy.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.dialect.impl.MySqlDialect;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sqltoy-orm
 * @description 本处测试类仅仅是开发过程中验证不同数据库方言针对sqltoy后台对象操作具体语法实现,实际sqltoy
 *              相关增删改操作全部基于对象完成， 请勿以为实际使用sqltoy需要如此复杂的写sql
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:MySqlDialectTest.java,Revision:v1.0,Date:2015年2月13日
 */
public class MySqlDialectTest {
	@Test
	public void testInsertDefaultValue() {
		try {
			String saveOrUpdateSql = "insert into SAG_PK_IDENTITY (name,create_time,sallary) values(?,ifnull(?,'2015-01-01'),?)";
			Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_MYSQL,
					"jdbc:mysql://192.168.56.109:3306/sagacity?useUnicode=true&characterEncoding=utf-8", "root",
					"root");

			PreparedStatement pst = null;
			try {
				pst = conn.prepareStatement(saveOrUpdateSql);
				pst.setString(1, "chenrenfei");
				pst.setNull(2, java.sql.Types.DATE);
				pst.setLong(3, 1000);
				pst.executeUpdate();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (pst != null)
					pst.close();
				if (conn != null)
					conn.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testCTESearch() {
		StringBuilder query = new StringBuilder();
		query.append("with sag_dict_post as ");
		query.append("(");
		query.append("	select t0.DICT_TYPE_CODE,t0.DICT_KEY,t0.DICT_NAME,t0.ENABLED ");
		query.append("	from sag_dict_detail t0 where t0.enabled=? ");
		query.append("    and t0.DICT_TYPE_CODE=? ");
		query.append("),sag_tech_level as ");
		query.append("( ");
		query.append("	select t.DICT_TYPE_CODE,t.DICT_KEY,t.DICT_NAME,t.ENABLED  ");
		query.append("	from sag_dict_detail t where t.enabled=? ");
		query.append("    and t.DICT_TYPE_CODE=? ");
		query.append(") ");
		query.append("select t1.STAFF_ID,t1.STAFF_NAME,t2.DICT_NAME POST_NAME,t3.DICT_NAME TECH_NAME ");
		query.append(
				"from sys_staff_info t1 left join sag_dict_post t2 on t1.POST=t2.DICT_KEY left join sag_tech_level t3 ");
		query.append("on t1.TECH_LEVEL=t3.DICT_KEY ");
		query.append("where t1.STAFF_NAME like ?");
		query.append(" order by t1.STAFF_NAME");
		Object[] paramValues = { 1, "POST_TYPE", 1, "SEAT_SKILL_LEVEL", "王%" };
		Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_MYSQL,
				"jdbc:mysql://192.168.56.109:3306/vxiplatform?useUnicode=true&characterEncoding=utf-8", "root", "root");
		MySqlDialect dialect = new MySqlDialect();
		try {
			Long count = dialect.getCountBySql(null, null, query.toString(), paramValues, false, conn, DBType.MYSQL,
					"mysql");
			System.err.println(count);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testSaveOrUpdate() {
		String saveOrUpdateSql = "replace into SAG_TEST (STAFF_ID, NAME,CREATE_TIME) values(?,?,?)";
		Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_MYSQL,
				"jdbc:mysql://192.168.56.109:3306/vxiplatform?useUnicode=true&characterEncoding=utf-8", "root", "root");
		List datas = new ArrayList();
		for (int i = 0; i < 5; i++) {
			List row = new ArrayList();
			if ((i + 1) % 2 == 1) {
				row.add(i + 1);
				row.add(null);
			} else {
				row.add(null);
				row.add("chen" + i);
			}
			row.add(DateUtil.getNowTime());
			datas.add(row);
		}
		PreparedStatement pst = null;
		try {
			pst = conn.prepareStatement(saveOrUpdateSql);
			List row;
			for (int i = 0; i < datas.size(); i++) {
				row = (List) datas.get(i);
				if (row.get(0) != null)
					pst.setInt(1, (Integer) row.get(0));
				else
					pst.setNull(1, java.sql.Types.INTEGER);
				if (row.get(1) != null)
					pst.setString(2, row.get(1).toString());
				else
					pst.setNull(2, java.sql.Types.VARCHAR);
				pst.setDate(3, DateUtil.getSqlDate(row.get(2)));
				pst.addBatch();
			}
			pst.executeBatch();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testSqlServer() {
		try {
			String saveOrUpdateSql = "select * from SYS_BIG_LOB";
			Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_SQLSERVER,
					"jdbc:sqlserver://192.168.56.1:1433;databaseName=sqltoy", "sa", "sqltoy");

			PreparedStatement pst = null;
			try {
				pst = conn.prepareStatement(saveOrUpdateSql);
				ResultSet rs = pst.executeQuery();
				while (rs.next()) {
					System.err.println(rs.getString(1));
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (pst != null)
					pst.close();
				if (conn != null)
					conn.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
