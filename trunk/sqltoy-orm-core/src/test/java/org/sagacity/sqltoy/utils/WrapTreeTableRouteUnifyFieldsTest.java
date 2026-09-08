package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.model.TreeTableModel;
import org.sagacity.sqltoy.plugins.IUnifyFieldsHandler;

/**
 * wrapTreeTableRoute公共字段验证:
 * 树形路由的update语句(层级/路径、叶子标志、主干标志)统一附加
 * IUnifyFieldsHandler.updateUnifyFields()中的公共更新字段(最后修改人、最后修改时间),
 * 与updateByQuery等更新流程的统一字段语义保持一致
 */
public class WrapTreeTableRouteUnifyFieldsTest {

	private static Connection conn;

	@BeforeAll
	public static void init() throws Exception {
		conn = DriverManager.getConnection("jdbc:h2:mem:treeroute;DB_CLOSE_DELAY=-1", "sa", "");
		createAndSeed();
	}

	private static void createAndSeed() throws Exception {
		try (Statement st = conn.createStatement()) {
			st.execute("drop table if exists t_tree_route");
			st.execute("create table t_tree_route (organ_id int primary key, organ_pid int, "
					+ "node_route varchar(50), node_level int, is_leaf int, "
					+ "last_modify_by varchar(50), last_modify_time timestamp)");
			st.execute("insert into t_tree_route values (1,null,'001',1,0,null,null)");
			st.execute("insert into t_tree_route values (2,1,'001',2,1,null,null)");
			st.execute("insert into t_tree_route values (3,2,'001',3,1,null,null)");
		}
	}

	private Object[] queryRow(int id) throws Exception {
		try (PreparedStatement pst = conn.prepareStatement(
				"select last_modify_by, last_modify_time, node_level from t_tree_route where organ_id=?")) {
			pst.setInt(1, id);
			ResultSet rs = pst.executeQuery();
			rs.next();
			return new Object[] { rs.getString(1), rs.getTimestamp(2), rs.getObject(3) };
		}
	}

	/**
	 * SqlUtil层:直接传入列名形式的公共更新字段,所有路由update语句(层级/路径、叶子标志、主干标志)均附带
	 */
	@Test
	public void unifyFieldsViaSqlUtilLayer() throws Exception {
		createAndSeed();
		TreeTableModel model = new TreeTableModel().table("t_tree_route").idField("organ_id").pidField("organ_pid")
				.nodeRouteField("node_route").nodeLevelField("node_level").isLeafField("is_leaf")
				.pidValue(1).idLength(3).idTypeIsChar(false);
		Map<String, Object> unify = new LinkedHashMap<>();
		unify.put("last_modify_by", "admin");
		unify.put("last_modify_time", Timestamp.valueOf("2026-09-04 10:00:00"));
		SqlUtil.wrapTreeTableRoute(null, model, conn, DataSourceUtils.DBType.H2, -1, unify);
		// 直属节点
		Object[] row2 = queryRow(2);
		assertEquals("admin", row2[0], "直属节点最后修改人应被更新");
		assertNotNull(row2[1], "直属节点最后修改时间应被更新");
		// 孙节点(递归层级)
		Object[] row3 = queryRow(3);
		assertEquals("admin", row3[0], "递归孙节点最后修改人应被更新");
		assertNotNull(row3[1], "递归孙节点最后修改时间应被更新");
		// 主干标志更新的节点
		Object[] row1 = queryRow(1);
		assertEquals("admin", row1[0], "主干标志更新的节点最后修改人应被更新");
	}

	/**
	 * DialectFactory实体层:updateUnifyFields的属性名称转化为列名,
	 * 通过实体+统一字段处理器完整走通wrapTreeTableRoute
	 */
	@Test
	public void unifyFieldsViaDialectFactoryEntityLayer() throws Exception {
		createAndSeed();
		SqlToyContext context = new SqlToyContext();
		context.setUnifyFieldsHandler(new IUnifyFieldsHandler() {
			@Override
			public Map<String, Object> updateUnifyFields() {
				Map<String, Object> fields = new LinkedHashMap<>();
				fields.put("lastModifyBy", "admin");
				fields.put("lastModifyTime", Timestamp.valueOf("2026-09-04 11:00:00"));
				return fields;
			}
		});
		context.setConnectionFactory(new org.sagacity.sqltoy.integration.ConnectionFactory() {
			@Override
			public java.sql.Connection getConnection(javax.sql.DataSource dataSource) {
				try {
					return dataSource.getConnection();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void releaseConnection(java.sql.Connection conn, javax.sql.DataSource dataSource) {
				try {
					if (conn != null) {
						conn.close();
					}
				} catch (Exception e) {
					// ignore
				}
			}
		});
		TreeRouteVO vo = new TreeRouteVO();
		vo.setOrganId(2L);
		vo.setOrganPid(1L);
		TreeTableModel model = new TreeTableModel(vo).pidField("organPid").nodeRouteField("nodeRoute")
				.nodeLevelField("nodeLevel").isLeafField("isLeaf").idLength(3).idTypeIsChar(false);
		org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:treeroute;DB_CLOSE_DELAY=-1");
		dataSource.setUser("sa");
		boolean result = DialectFactory.getInstance().wrapTreeTableRoute(context, model, dataSource);
		assertEquals(true, result, "路由处理应成功");
		// 属性名称lastModifyBy已转化为列名LAST_MODIFY_BY并生效
		Object[] row3 = queryRow(3);
		assertEquals("admin", row3[0], "实体层最后修改人应被更新");
		assertNotNull(row3[1], "实体层最后修改时间应被更新");
		Object[] row1 = queryRow(1);
		assertEquals("admin", row1[0], "主干节点最后修改人应被更新");
	}

	@Entity(tableName = "t_tree_route")
	public static class TreeRouteVO implements java.io.Serializable {
		@Id
		@Column(name = "organ_id")
		private Long organId;

		@Column(name = "organ_pid")
		private Long organPid;

		@Column(name = "node_route")
		private String nodeRoute;

		@Column(name = "node_level")
		private Integer nodeLevel;

		@Column(name = "is_leaf")
		private Integer isLeaf;

		@Column(name = "last_modify_by")
		private String lastModifyBy;

		@Column(name = "last_modify_time")
		private Timestamp lastModifyTime;

		public Long getOrganId() {
			return organId;
		}

		public void setOrganId(Long organId) {
			this.organId = organId;
		}

		public Long getOrganPid() {
			return organPid;
		}

		public void setOrganPid(Long organPid) {
			this.organPid = organPid;
		}

		public String getNodeRoute() {
			return nodeRoute;
		}

		public void setNodeRoute(String nodeRoute) {
			this.nodeRoute = nodeRoute;
		}

		public Integer getNodeLevel() {
			return nodeLevel;
		}

		public void setNodeLevel(Integer nodeLevel) {
			this.nodeLevel = nodeLevel;
		}

		public Integer getIsLeaf() {
			return isLeaf;
		}

		public void setIsLeaf(Integer isLeaf) {
			this.isLeaf = isLeaf;
		}

		public String getLastModifyBy() {
			return lastModifyBy;
		}

		public void setLastModifyBy(String lastModifyBy) {
			this.lastModifyBy = lastModifyBy;
		}

		public Timestamp getLastModifyTime() {
			return lastModifyTime;
		}

		public void setLastModifyTime(Timestamp lastModifyTime) {
			this.lastModifyTime = lastModifyTime;
		}
	}
}
