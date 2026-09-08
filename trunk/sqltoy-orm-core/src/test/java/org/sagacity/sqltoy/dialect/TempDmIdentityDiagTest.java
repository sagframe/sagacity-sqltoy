package org.sagacity.sqltoy.dialect;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.dialect.impl.DMDialect;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DMDialectUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/** 临时诊断:DM identity的insert生成链逐环节输出 */
public class TempDmIdentityDiagTest {

	@Entity(tableName = "diag_identity_t")
	public static class DiagVO implements java.io.Serializable {
		@Id(strategy = "identity")
		@Column(name = "id")
		private Long id;

		@Column(name = "name")
		private String name;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Test
	public void diag() throws Exception {
		EntityManager em = new EntityManager();
		EntityMeta meta = em.parseEntityMeta(new SqlToyContext(), DiagVO.class, true, false);
		System.out.println("[Diag] idStrategy=" + meta.getIdStrategy()
				+ " sequence=[" + meta.getSequence() + "]");
		PKStrategy pk = org.sagacity.sqltoy.dialect.utils.DialectUtils.getSavePKStrategy(meta, new DiagVO(),
				DBType.DM);
		boolean isAssign = DMDialectUtils.allowAssignPKValue(pk);
		System.out.println("[Diag] afterGetSavePKStrategy=" + pk + " isAssignPK=" + isAssign);
		String seq = meta.getSequence() + ".nextval";
		// 与DMDialect.save完全同参的生成调用
		String insertSql = DialectExtUtils.generateInsertSql(null, DBType.DM, meta, pk, "nvl", seq, isAssign, null);
		System.out.println("[Diag] generateInsertSql=" + insertSql.replaceAll("\\s+", " "));
		// 真实save链路
		Class.forName("dm.jdbc.driver.DmDriver");
		try (Connection conn = DriverManager.getConnection("jdbc:dm://localhost:5236?schema=SYSDBA", "SYSDBA",
				"SYSDBA001"); java.sql.Statement st = conn.createStatement()) {
			st.execute("drop table if exists diag_identity_t");
			st.execute("create table diag_identity_t (id bigint identity(1,1) primary key, name varchar(50))");
			DiagVO vo = new DiagVO();
			vo.setName("diag");
			DMDialect dm = new DMDialect();
			try {
				Object r = dm.save(new SqlToyContext(), vo, conn, DBType.DM, "dm", null);
				System.out.println("[Diag] save OK pk=" + r + " voId=" + vo.getId());
			} catch (Exception e) {
				System.out.println("[Diag] save FAIL: " + e.getMessage());
			}
			st.execute("drop table if exists diag_identity_t");
		}
	}
}
