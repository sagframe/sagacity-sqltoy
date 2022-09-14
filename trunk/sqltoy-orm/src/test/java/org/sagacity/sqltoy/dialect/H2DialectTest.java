/**
 *
 */
package org.sagacity.sqltoy.dialect;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * @Author junhua
 * @Description 增加对h2数据库的支持，h2与pg类似
 * @Date 2022/08/29 下午17:32
 **/
public class H2DialectTest {
    @Test
    public void testValidateQuery() {
        try {
            String validateQuerySql = "SELECT 1";
            Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_H2,
                    "jdbc:h2:file:/Users/hujun/Documents/temp/jztools/db/jztools", "root",
                    "root");

            PreparedStatement pst = null;
            try {
                pst = conn.prepareStatement(validateQuerySql);
                pst.executeQuery();
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
    public void testLock() {
        try {
            String query = "SELECT * FROM table1 FOR UPDATE nowait";
            Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_H2,
                    "jdbc:h2:file:/Users/hujun/Documents/temp/jztools/db/jztools", "root",
                    "root");

            PreparedStatement pst = null;
            try {
                pst = conn.prepareStatement(query);
                pst.executeQuery();
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
    public void testRandom() {
        try {
            String random = "SELECT * FROM TABLE1 ORDER BY random() LIMIT 2";
            Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_H2,
                    "jdbc:h2:file:/Users/hujun/Documents/temp/jztools/db/jztools", "root",
                    "root");

            PreparedStatement pst = null;
            try {
                pst = conn.prepareStatement(random);
                pst.executeQuery();
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
    public void testSeq() {
        try {
            String seq = "INSERT INTO TABLE1 VALUES ( nextval('test') )";
            Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_H2,
                    "jdbc:h2:file:/Users/hujun/Documents/temp/jztools/db/jztools", "root",
                    "root");

            PreparedStatement pst = null;
            try {
                pst = conn.prepareStatement(seq);
                pst.execute();
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
    public void testPage() {
        try {
            String pageSql = "SELECT * FROM TABLE1  LIMIT 1 OFFSET 1";
            Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_H2,
                    "jdbc:h2:file:/Users/hujun/Documents/temp/jztools/db/jztools", "root",
                    "root");

            PreparedStatement pst = null;
            try {
                pst = conn.prepareStatement(pageSql);
                pst.executeQuery();
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
    public void testNvl() {
        try {
            String nvlsql = "SELECT COALESCE(id,100) FROM TABLE1";
            Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_H2,
                    "jdbc:h2:file:/Users/hujun/Documents/temp/jztools/db/jztools", "root",
                    "root");

            PreparedStatement pst = null;
            try {
                pst = conn.prepareStatement(nvlsql);
                pst.executeQuery();
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
    public void testMerge() {
        try {
            String nvlsql = "MERGE INTO TABLE1 AS T USING TABLE12 AS S"+
                    "    ON T.ID = S.ID"+
                    "    WHEN MATCHED AND T.COL2 <> 'FINAL' THEN"+
                    "        UPDATE SET T.COL1 = S.COL1"+
                    "    WHEN MATCHED AND T.COL2 = 'FINAL' THEN"+
                    "        DELETE"+
                    "    WHEN NOT MATCHED THEN"+
                    "        INSERT (ID, COL1, COL2) VALUES(S.ID, S.COL1, S.COL2)";
            Connection conn = DBUtilsTest.getConnection(DBUtilsTest.DRIVER_H2,
                    "jdbc:h2:file:/Users/hujun/Documents/temp/jztools/db/jztools", "root",
                    "root");

            PreparedStatement pst = null;
            try {
                pst = conn.prepareStatement(nvlsql);
                pst.execute();
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
