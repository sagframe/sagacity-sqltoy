/**
 * 
 */
package org.sagacity.sqltoy.dialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.utils.DateUtil;

/**
 * @project sqltoy-orm
 * @description 本处测试类仅仅是开发过程中验证不同数据库方言针对sqltoy后台对象操作具体语法实现,实际sqltoy
 *              相关增删改操作全部基于对象完成， 请勿以为实际使用sqltoy需要如此复杂的写sql
s * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:OracleDialectTest.java,Revision:v1.0,Date:2015年2月13日
 */
public class OracleDialectTest {
	@Test
	public void testInsertDefaultValue() {
		try {
			String saveOrUpdateSql = "insert into SAG_PK_SEQUENCE (id,name,create_time,sallary) values(SEQ_SAG_PK_ID.nextval,?,?,?)";
			Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_ORACLE,
					"jdbc:oracle:thin:@192.168.56.109:1521:lakalaCRM", "lakala", "lakala");

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
	public void testMergeInto() {
		StringBuilder saveOrUpdateSql = new StringBuilder();
		saveOrUpdateSql.append("merge into SAG_TEST t1 ");
		saveOrUpdateSql.append("using (select ? as STAFF_ID,? as NAME,? as CREATE_TIME from dual) t2 ");
		saveOrUpdateSql.append("   on (t1.STAFF_ID=t2.STAFF_ID) ");
		saveOrUpdateSql.append(" when matched then ");
		saveOrUpdateSql
				.append("  update set t1.NAME=nvl(t2.NAME,t1.NAME),t1.CREATE_TIME=nvl(t2.CREATE_TIME,t1.CREATE_TIME) ");
		saveOrUpdateSql.append(" when not matched then ");
		saveOrUpdateSql.append(
				"  insert (STAFF_ID,NAME,CREATE_TIME) VALUES (nvl(t2.STAFF_ID,SEQ_SAG_TEST.nextval),t2.NAME,t2.CREATE_TIME) ");
		Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_ORACLE,
				"jdbc:oracle:thin:@192.168.56.109:1521:lakalaCRM", "lakala", "lakala");
		List datas = new ArrayList();
		for (int i = 0; i < 5; i++) {
			List row = new ArrayList();
			if ((i + 1) % 2 == 1) {
				row.add(null);
				row.add("xxx" + i);
			} else {
				row.add(i + 1);
				row.add("nnn" + i);
			}
			row.add(DateUtil.getNowTime());
			datas.add(row);
		}

		PreparedStatement pst = null;
		try {
			System.err.println(saveOrUpdateSql.toString());
			pst = conn.prepareStatement(saveOrUpdateSql.toString());
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
}
