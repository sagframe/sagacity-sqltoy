package org.sagacity.sqltoy.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.annotation.Column;
import org.sagacity.sqltoy.config.annotation.Entity;
import org.sagacity.sqltoy.config.annotation.Id;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.impl.DefaultLightDaoImpl;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.lambda.join.JoinCol;
import org.sagacity.sqltoy.model.Page;

/**
 * @description lambda编程式缓存翻译H2端到端测试:覆盖单表find/findPage/同列/多列/cacheType/select裁剪、
 *              join通道原地翻译、注解式@Translate共存,以及findColumns/聚合/union分支/fromFast内层/
 *              join通道key映射等误用场景的快速失败
 * @author zhongxuchen
 */
@DisplayName("lambda缓存翻译H2执行测试")
public class LambdaCacheTranslateH2Test {

	private static final String URL = "jdbc:h2:mem:lambdatrans;DB_CLOSE_DELAY=-1";
	private static JdbcDataSource dataSource;
	private static SqlToyContext context;
	private static LightDao lightDao;

	@Entity(tableName = "lambda_trans_staff")
	public static class Staff implements Serializable {

		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "ID")
		private Integer id;

		@Column(name = "STAFF_NAME")
		private String staffName;

		@Column(name = "STATUS")
		private Integer status;

		@Column(name = "STAFF_LEVEL")
		private String staffLevel;

		@Column(name = "ORGAN_ID")
		private Integer organId;

		/**
		 * 非表字段:仅承接缓存翻译结果
		 */
		private String statusName;

		private String statusNameEn;

		private String organName;

		private String levelName;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getStaffName() {
			return staffName;
		}

		public void setStaffName(String staffName) {
			this.staffName = staffName;
		}

		public Integer getStatus() {
			return status;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}

		public String getStaffLevel() {
			return staffLevel;
		}

		public void setStaffLevel(String staffLevel) {
			this.staffLevel = staffLevel;
		}

		public Integer getOrganId() {
			return organId;
		}

		public void setOrganId(Integer organId) {
			this.organId = organId;
		}

		public String getStatusName() {
			return statusName;
		}

		public void setStatusName(String statusName) {
			this.statusName = statusName;
		}

		public String getStatusNameEn() {
			return statusNameEn;
		}

		public void setStatusNameEn(String statusNameEn) {
			this.statusNameEn = statusNameEn;
		}

		public String getOrganName() {
			return organName;
		}

		public void setOrganName(String organName) {
			this.organName = organName;
		}

		public String getLevelName() {
			return levelName;
		}

		public void setLevelName(String levelName) {
			this.levelName = levelName;
		}
	}

	/**
	 * 注解式@Translate对照类:证明显式translate不与注解式冲突
	 */
	@Entity(tableName = "lambda_trans_staff")
	public static class AnnotatedStaff implements Serializable {

		private static final long serialVersionUID = 1L;

		@Id
		@Column(name = "ID")
		private Integer id;

		@Column(name = "STATUS")
		private Integer status;

		@org.sagacity.sqltoy.config.annotation.Translate(cacheName = "dict_staff_status", keyField = "status")
		private String statusName;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Integer getStatus() {
			return status;
		}

		public void setStatus(Integer status) {
			this.status = status;
		}

		public String getStatusName() {
			return statusName;
		}

		public void setStatusName(String statusName) {
			this.statusName = statusName;
		}
	}

	@BeforeAll
	public static void init() throws Exception {
		try (Connection conn = DriverManager.getConnection(URL, "sa", ""); Statement st = conn.createStatement()) {
			st.execute("drop table if exists lambda_trans_staff");
			st.execute(
					"create table lambda_trans_staff (id int primary key, staff_name varchar(50), status int, staff_level varchar(10), organ_id int)");
			st.execute("insert into lambda_trans_staff values (1,'张三',1,'A',10)");
			st.execute("insert into lambda_trans_staff values (2,'李四',0,'B',20)");
			st.execute("insert into lambda_trans_staff values (3,'王五',1,'A',10)");
			st.execute("insert into lambda_trans_staff values (4,'赵六',0,'B',20)");
		}
		dataSource = new JdbcDataSource();
		dataSource.setURL(URL);
		dataSource.setUser("sa");
		context = new SqlToyContext();
		context.setDefaultDataSource(dataSource);
		context.setTranslateConfig("classpath:translate/lambda-cache-translate.xml");
		context.initialize();
		HashMap<String, Object[]> status = new HashMap<>();
		status.put("1", new Object[] { "1", "在职" });
		status.put("0", new Object[] { "0", "离职" });
		context.getTranslateManager().putCacheData("dict_staff_status", null, status);
		HashMap<String, Object[]> statusEn = new HashMap<>();
		statusEn.put("1", new Object[] { "1", "active" });
		statusEn.put("0", new Object[] { "0", "inactive" });
		context.getTranslateManager().putCacheData("dict_staff_status", "en", statusEn);
		HashMap<String, Object[]> organ = new HashMap<>();
		organ.put("10", new Object[] { "10", "总部" });
		organ.put("20", new Object[] { "20", "分部" });
		context.getTranslateManager().putCacheData("dict_organ", null, organ);
		HashMap<String, Object[]> level = new HashMap<>();
		level.put("A", new Object[] { "A", "高级" });
		level.put("B", new Object[] { "B", "初级" });
		context.getTranslateManager().putCacheData("dict_level", null, level);
		lightDao = new DefaultLightDaoImpl(context);
	}

	@Test
	@DisplayName("单表find:key属性值翻译到target属性")
	public void singleTableFindTranslate() {
		List<Staff> rows = lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName)
				.orderByAsc(Staff::getId).find();
		assertEquals(4, rows.size());
		assertEquals("在职", rows.get(0).getStatusName());
		assertEquals("离职", rows.get(1).getStatusName());
		assertEquals("在职", rows.get(2).getStatusName());
	}

	@Test
	@DisplayName("单表findPage:当页数据翻译,count不带翻译")
	public void singleTablePageTranslate() {
		Page<Staff> page = lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName)
				.orderByAsc(Staff::getId).findPage(new Page(2, 2));
		assertEquals(4L, page.getRecordCount());
		assertEquals(2, page.getRows().size());
		Staff first = (Staff) page.getRows().get(0);
		assertEquals(3, first.getId().intValue());
		assertEquals("在职", first.getStatusName());
		assertEquals("离职", ((Staff) page.getRows().get(1)).getStatusName());
	}

	@Test
	@DisplayName("同列翻译:原列值原地替换,不新增重复列")
	public void sameColumnTranslateInPlace() {
		List<Staff> rows = lightDao.lambdaQuery(Staff.class)
				.translate("dict_level", Staff::getStaffLevel)
				.orderByAsc(Staff::getId).find();
		assertEquals("高级", rows.get(0).getStaffLevel());
		assertEquals("初级", rows.get(1).getStaffLevel());
	}

	@Test
	@DisplayName("distinct+同列翻译目标未投影:anti-no-op补列改变DISTINCT身份,须快速失败")
	public void distinctSameColumnTranslateNotProjectedRejected() {
		org.junit.jupiter.api.Assertions.assertThrows(org.sagacity.sqltoy.exception.DataAccessException.class,
				() -> lightDao.lambdaQuery(Staff.class).select(Staff::getId).distinct()
						.translate("dict_level", Staff::getStaffLevel).find());
	}

	@Test
	@DisplayName("groupBy+同列翻译目标未投影:补列非分组列,须快速失败")
	public void groupBySameColumnTranslateNotProjectedRejected() {
		org.junit.jupiter.api.Assertions.assertThrows(org.sagacity.sqltoy.exception.DataAccessException.class,
				() -> lightDao.lambdaQuery(Staff.class).select(Staff::getId).groupBy(Staff::getId)
						.translate("dict_level", Staff::getStaffLevel).find());
	}

	@Test
	@DisplayName("distinct+同列翻译目标已投影:原地翻译不补列,放行")
	public void distinctSameColumnTranslateProjectedAllowed() {
		List<Staff> rows = lightDao.lambdaQuery(Staff.class).select(Staff::getId, Staff::getStaffLevel).distinct()
				.translate("dict_level", Staff::getStaffLevel).orderByAsc(Staff::getId).find();
		org.junit.jupiter.api.Assertions.assertFalse(rows.isEmpty());
	}

	@Test
	@DisplayName("多列翻译:多条translate并存")
	public void multiColumnTranslate() {
		List<Staff> rows = lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName)
				.translate("dict_organ", Staff::getOrganId, Staff::getOrganName)
				.orderByAsc(Staff::getId).find();
		assertEquals("在职", rows.get(0).getStatusName());
		assertEquals("总部", rows.get(0).getOrganName());
		assertEquals("分部", rows.get(1).getOrganName());
	}

	@Test
	@DisplayName("cacheType:同一cacheName按类型区分缓存")
	public void cacheTypeTranslate() {
		List<Staff> rows = lightDao.lambdaQuery(Staff.class)
				.translates(new Translate("dict_staff_status").setCacheType("en").setKeyColumn("status")
						.setColumn("statusNameEn"))
				.orderByAsc(Staff::getId).find();
		assertEquals("active", rows.get(0).getStatusNameEn());
		assertEquals("inactive", rows.get(1).getStatusNameEn());
	}

	@Test
	@DisplayName("select裁剪:翻译列仍被补充查询")
	public void selectTrimStillTranslate() {
		List<Staff> rows = lightDao.lambdaQuery(Staff.class)
				.select(Staff::getId, Staff::getStatus)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName)
				.orderByAsc(Staff::getId).find();
		assertEquals(4, rows.size());
		assertEquals("在职", rows.get(0).getStatusName());
		assertNull(rows.get(0).getStaffLevel(), "select未包含的列应保持null");
	}

	@Test
	@DisplayName("join通道:目标列已在select结果中,原地翻译")
	public void joinChannelSameColumnTranslate() {
		List<Staff> rows = lightDao.lambdaQuery(Staff.class)
				.select(JoinCol.of(Staff::getStaffLevel).as("staffLevel"))
				.translate("dict_level", Staff::getStaffLevel)
				.eq(Staff::getId, 1)
				.find();
		assertEquals(1, rows.size());
		assertEquals("高级", rows.get(0).getStaffLevel());
	}

	@Test
	@DisplayName("join通道:key与target不同列时快速失败")
	public void joinChannelKeyMappingRejected() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(Staff.class)
				.select(JoinCol.of(Staff::getStatus).as("statusName"))
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName)
				.find());
	}

	@Test
	@DisplayName("findColumns投影不支持翻译")
	public void findColumnsRejected() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName)
				.findColumns(Staff::getId));
	}

	@Test
	@DisplayName("聚合终端不支持翻译")
	public void aggregateRejected() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName)
				.max(Staff::getId));
	}

	@Test
	@DisplayName("union分支不允许设置翻译")
	public void unionBranchTranslateRejected() {
		LambdaQuery<Staff> branch = lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName);
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(Staff.class).union(branch));
	}

	@Test
	@DisplayName("fromFast内层不允许设置翻译")
	public void fromFastInnerTranslateRejected() {
		LambdaQuery<Staff> inner = lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName);
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(Staff.class).fromFast(inner, "t"));
	}

	@Test
	@DisplayName("注解式@Translate与编程式translate共存")
	public void annotationTranslateStillWorks() {
		List<AnnotatedStaff> rows = lightDao.lambdaQuery(AnnotatedStaff.class)
				.eq(AnnotatedStaff::getId, 1).find();
		assertEquals(1, rows.size());
		assertEquals("在职", rows.get(0).getStatusName());
	}

	@Test
	@DisplayName("同列翻译:投影裁剪掉该列时须补列并翻译,不得静默no-op")
	public void sameColumnTranslateWithTrimmedProjection() {
		// update 2026-11-22 复查修复回归锁死:同列翻译分支此前无条件跳过补列,投影未包含该列时
		// 结果集里没有该列,翻译静默no-op且属性为null(实测)
		List<Staff> rows = lightDao.lambdaQuery(Staff.class).select(Staff::getId)
				.translate("dict_level", Staff::getStaffLevel).orderByAsc(Staff::getId).find();
		assertEquals(4, rows.size());
		assertEquals("高级", rows.get(0).getStaffLevel(), "投影缺列时须补 key as column 后翻译");
	}

	@Test
	@DisplayName("distinct+补列式翻译:执行期快速失败(会改变去重行口径)")
	public void distinctAppendTranslateRejected() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(Staff.class).select(Staff::getStatus)
				.distinct().translate("dict_staff_status", Staff::getStatus, Staff::getStatusName).find());
	}

	@Test
	@DisplayName("组合之后再给分支设置翻译:执行期拒绝(声明期校验覆盖不到)")
	public void translateAfterCompositionRejected() {
		LambdaQuery<Staff> branch = lightDao.lambdaQuery(Staff.class).eq(Staff::getStatus, 1);
		LambdaQuery<Staff> root = lightDao.lambdaQuery(Staff.class).eq(Staff::getStatus, 0).union(branch);
		// 声明期校验发生在union()时,此处追加的翻译只能由执行期拦截,不得静默丢弃
		branch.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName);
		assertThrows(DataAccessException.class, root::find);
	}

	@Test
	@DisplayName("count不携带翻译且计数正确")
	public void countIgnoresTranslate() {
		assertEquals(4L, lightDao.lambdaQuery(Staff.class)
				.translate("dict_staff_status", Staff::getStatus, Staff::getStatusName).count());
	}

	@Test
	@DisplayName("join通道:翻译目标列未select时快速失败(此前静默no-op)")
	public void joinChannelTranslateColumnNotSelectedRejected() {
		assertThrows(DataAccessException.class, () -> lightDao.lambdaQuery(Staff.class)
				.select(JoinCol.of(Staff::getId).as("id"))
				.translate("dict_level", Staff::getStaffLevel)
				.eq(Staff::getId, 1).find());
	}
}
