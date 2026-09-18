package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;

/**
 * SqlUtil.convertFieldsToColumns 边界场景测试(update 2026-9-15)
 * <p>
 * 覆盖三处近期改动的回归面:缓存key改\u0000分隔、maskLiterals字面量掩码、掩码串平行拼接;
 * 场景编号A~G对应测试计划矩阵。
 * <p>
 * 静态状态规约:convertSqlMap与ReservedWordsUtil均为全局静态——每用例开头clear()保留字,
 * put保留字的用例finally中clear()恢复;表名统一staff_cfe_*前缀且每用例唯一(缓存key=表名
 * +\u0000+sql,防跨类串扰);依赖缓存写入的用例先反射清空convertSqlMap(套件运行时
 * PerfRegressionBatchTest会将其填满至2000上限,不清空则put被跳过导致断言顺序依赖)。
 * <p>
 * backslashEscape上下文:SqlToyConstants.backslashEscaping全仓库仅SqlToyContext.initialize
 * 赋值(JVM级一次),测试目录零赋值,裸上下文isBackslashEscape(null)恒为false(标准SQL语义,
 * 反斜杠不转义引号),字面量用例按此语义断言。
 */
public class SqlUtilConvertFieldsEdgeTest {

	// ======================== A. 字面量掩码 ========================

	/**
	 * A1/A2: 字面量内容恰为字段名(含大小写变体)不转换
	 */
	@Test
	public void testA1_literalContentIsFieldName() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a1");
		assertEquals("remark='staffName'", SqlUtil.convertFieldsToColumns(meta, "remark='staffName'").trim());
		assertEquals("remark='STAFFNAME'", SqlUtil.convertFieldsToColumns(meta, "remark='STAFFNAME'").trim());
		assertEquals("remark='StaffName'", SqlUtil.convertFieldsToColumns(meta, "remark='StaffName'").trim());
	}

	/**
	 * A3: 字面量内外同字段,仅外部转换
	 */
	@Test
	public void testA3_sameFieldInsideAndOutsideLiteral() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a3");
		assertEquals("STAFF_NAME='staffName'", SqlUtil.convertFieldsToColumns(meta, "staffName='staffName'").trim());
	}

	/**
	 * A4/A5: ''成对转义(单段与多段),转义段与字面量整体均受掩码保护
	 */
	@Test
	public void testA4_escapedQuotePairs() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a4");
		assertEquals("remark='it''s staffName' and STAFF_NAME=1",
				SqlUtil.convertFieldsToColumns(meta, "remark='it''s staffName' and staffName=1").trim());
		assertEquals("remark='a''b''staffName'",
				SqlUtil.convertFieldsToColumns(meta, "remark='a''b''staffName'").trim());
	}

	/**
	 * A6: 空字面量''不影响相邻字段转换
	 */
	@Test
	public void testA6_emptyLiteral() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a6");
		assertEquals("STAFF_NAME='' and SEX_TYPE=1",
				SqlUtil.convertFieldsToColumns(meta, "staffName='' and sexType=1").trim());
	}

	/**
	 * A7: like通配字面量内的字段名不转换
	 */
	@Test
	public void testA7_likeWildcardLiteral() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a7");
		assertEquals("STAFF_NAME like '%staffName%'",
				SqlUtil.convertFieldsToColumns(meta, "staffName like '%staffName%'").trim());
	}

	/**
	 * A8: 多字面量+多字段交错——三轮字段替换下掩码串与原串平行拼接保持偏移对齐(核心用例)
	 */
	@Test
	public void testA8_multiLiteralMultiFieldParallelMask() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a8");
		assertEquals("STAFF_NAME='l1' and SEX_TYPE='l2' and BIZ_STAFF_NAME=1",
				SqlUtil.convertFieldsToColumns(meta, "staffName='l1' and sexType='l2' and bizStaffName=1").trim());
		// 字面量在前、字段夹在中间与末尾的形态
		assertEquals("remark='r' and STAFF_NAME='l' and SEX_TYPE=2",
				SqlUtil.convertFieldsToColumns(meta, "remark='r' and staffName='l' and sexType=2").trim());
	}

	/**
	 * A9: 字面量位于串首/串尾,裸字段正常转换
	 */
	@Test
	public void testA9_literalAtStartAndEnd() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a9");
		assertEquals("'staffName' STAFF_NAME",
				SqlUtil.convertFieldsToColumns(meta, "'staffName' staffName").trim());
		assertEquals("STAFF_NAME 'x'", SqlUtil.convertFieldsToColumns(meta, "staffName 'x'").trim());
	}

	/**
	 * A10: 未闭合引号fail-safe——引号后全部视为字面量内容,不转换不抛异常
	 */
	@Test
	public void testA10_unterminatedLiteralFailSafe() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a10");
		assertEquals("remark='staffName and sexType=1",
				SqlUtil.convertFieldsToColumns(meta, "remark='staffName and sexType=1").trim());
	}

	/**
	 * A11: 字面量含中文/通配符等特殊字符,掩码按字符处理不受影响
	 */
	@Test
	public void testA11_chineseAndSpecialCharsInLiteral() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a11");
		assertEquals("remark='张三staffName%' and STAFF_NAME=1",
				SqlUtil.convertFieldsToColumns(meta, "remark='张三staffName%' and staffName=1").trim());
	}

	/**
	 * A12: 标准SQL语义(测试上下文backslashEscape=false)——反斜杠不转义引号,'C:\'为完整字面量
	 */
	@Test
	public void testA12_backslashStandardSemantics() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a12");
		assertEquals("path='C:\\' and STAFF_NAME=1",
				SqlUtil.convertFieldsToColumns(meta, "path='C:\\' and staffName=1").trim());
	}

	/**
	 * A13: #[...]动态条件段内的字面量同样受保护,段外字段正常转换
	 */
	@Test
	public void testA13_dynamicSegmentWithLiteral() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a13");
		assertEquals("#[and remark='staffName'] and STAFF_NAME=1",
				SqlUtil.convertFieldsToColumns(meta, "#[and remark='staffName'] and staffName=1").trim());
	}

	/**
	 * A14: 命名参数、字面量、裸字段三形态并存
	 */
	@Test
	public void testA14_paramAndLiteralCoexist() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_a14");
		assertEquals("STAFF_NAME=:staffName and remark='staffName'",
				SqlUtil.convertFieldsToColumns(meta, "staffName=:staffName and remark='staffName'").trim());
	}

	// ======================== B. 词边界 ========================

	/**
	 * B1/B2/B3: 字母、数字、下划线粘连(前后)均不转换
	 */
	@Test
	public void testB1_alnumUnderscoreGlue() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b1");
		String sql = "staffNameX=1,xstaffName=2,staffName2=3,2staffName=4,_staffName=5,staffName_=6";
		assertEquals(sql, SqlUtil.convertFieldsToColumns(meta, sql).trim());
	}

	/**
	 * B4: 点前缀(表别名限定)正常转换
	 */
	@Test
	public void testB4_dotPrefix() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b4");
		assertEquals("t.STAFF_NAME=1", SqlUtil.convertFieldsToColumns(meta, "t.staffName=1").trim());
	}

	/**
	 * B6: 函数形态field(不转换;函数参数位正常转换
	 */
	@Test
	public void testB6_functionForms() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b6");
		assertEquals("count(STAFF_NAME)+nvl(STAFF_NAME,0)+staffName(x)",
				SqlUtil.convertFieldsToColumns(meta, "count(staffName)+nvl(staffName,0)+staffName(x)").trim());
	}

	/**
	 * B7: 运算符边界(>=、<>、!=、=、>、<)均正常转换
	 */
	@Test
	public void testB7_operatorBoundaries() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b7");
		assertEquals("STAFF_NAME>=1 and STAFF_NAME<>2 or STAFF_NAME!=3 and STAFF_NAME>4",
				SqlUtil.convertFieldsToColumns(meta, "staffName>=1 and staffName<>2 or staffName!=3 and staffName>4")
						.trim());
	}

	/**
	 * B8: 行首/行尾字段——返回值含末尾补齐空白(精确断言,调用方trim为既有约定)
	 */
	@Test
	public void testB8_startAndEndOfString() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b8");
		assertEquals("where STAFF_NAME ", SqlUtil.convertFieldsToColumns(meta, "where staffName"));
		assertEquals("STAFF_NAME=1 ", SqlUtil.convertFieldsToColumns(meta, "staffName=1"));
	}

	/**
	 * B10: 包含关系字段(name/staffName/bizStaffName)——name同名非保留字跳过,长字段不被短字段误切
	 */
	@Test
	public void testB10_inclusionFields() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b10");
		assertEquals("name and STAFF_NAME and BIZ_STAFF_NAME",
				SqlUtil.convertFieldsToColumns(meta, "name and staffName and bizStaffName").trim());
	}

	/**
	 * B11a: 换行/tab空白边界正常转换
	 */
	@Test
	public void testB11_newlineTabBoundary() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b11a");
		assertEquals("STAFF_NAME\nand\tSEX_TYPE",
				SqlUtil.convertFieldsToColumns(meta, "staffName\nand\tsexType").trim());
	}

	/**
	 * B11b: 列名含另一字段名(SEX_TYPE含type)——fieldsArray两种顺序均安全:
	 * type先处理时sexType内的type被字母前界拒绝,sexType先处理时SEX_TYPE内的TYPE被下划线前界拒绝。
	 * 注意type列名须真正不同于字段名(如TYPE_ID):若列名与字段名仅大小写差异(TYPE vs type),
	 * 命中"属性名与列名一致且非保留字则跳过"规则,该字段整体不参与转换(B10的name/NAME同款)
	 */
	@Test
	public void testB11_columnNameContainsOtherField() {
		ReservedWordsUtil.clear();
		// 顺序1: sexType在前——SEX_TYPE内的TYPE片段须被下划线前界拒绝
		EntityMeta meta1 = buildMeta("staff_cfe_b11b1", "sexType", "SEX_TYPE", "type", "TYPE_ID");
		assertEquals("SEX_TYPE=1 and TYPE_ID=2",
				SqlUtil.convertFieldsToColumns(meta1, "sexType=1 and type=2").trim());
		// 顺序2: type在前——sexType内的type片段须被字母前界拒绝
		EntityMeta meta2 = buildMeta("staff_cfe_b11b2", "type", "TYPE_ID", "sexType", "SEX_TYPE");
		assertEquals("SEX_TYPE=1 and TYPE_ID=2",
				SqlUtil.convertFieldsToColumns(meta2, "sexType=1 and type=2").trim());
		// 列名与字段名仅大小写差异时整体跳过(规则文档化)
		EntityMeta meta3 = buildMeta("staff_cfe_b11b3", "sexType", "SEX_TYPE", "type", "TYPE");
		assertEquals("SEX_TYPE=1 and type=2",
				SqlUtil.convertFieldsToColumns(meta3, "sexType=1 and type=2").trim());
	}

	/**
	 * B补充: 调用方真实片段形态(where/orderBy/groupBy/having/updateByQuery的set右值)
	 */
	@Test
	public void testB12_callerShapes() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_b12");
		// where(EntityQuery/deleteByQuery/updateByQuery)
		assertEquals("STAFF_NAME like :staffName and SEX_TYPE in (:sexType)",
				SqlUtil.convertFieldsToColumns(meta, "staffName like :staffName and sexType in (:sexType)").trim());
		// orderBy(含级联@OneToMany.orderBy)
		assertEquals("STAFF_NAME desc,SEX_TYPE asc",
				SqlUtil.convertFieldsToColumns(meta, "staffName desc,sexType asc").trim());
		// groupBy
		assertEquals("SEX_TYPE,count(STAFF_NAME)",
				SqlUtil.convertFieldsToColumns(meta, "sexType,count(staffName)").trim());
		// having
		assertEquals("count(STAFF_NAME)>5", SqlUtil.convertFieldsToColumns(meta, "count(staffName)>5").trim());
		// updateByQuery的set右值表达式(field=field+?)
		assertEquals("SEX_TYPE=SEX_TYPE+1", SqlUtil.convertFieldsToColumns(meta, "sexType=sexType+1").trim());
	}

	// ======================== C. 保留字/引号标识符 ========================

	/**
	 * C1/C2/C3: 引号类标识符前缀([、`、")绕过convertWord——即使列名为保留字也不额外包裹
	 */
	@Test
	public void testC123_quotedIdentifiersBypassKeyword() {
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("staff_name");
		try {
			EntityMeta meta = buildStaffMeta("staff_cfe_c123");
			assertEquals("[STAFF_NAME]=1", SqlUtil.convertFieldsToColumns(meta, "[staffName]=1").trim());
			assertEquals("`STAFF_NAME`=2", SqlUtil.convertFieldsToColumns(meta, "`staffName`=2").trim());
			assertEquals("\"STAFF_NAME\"=3", SqlUtil.convertFieldsToColumns(meta, "\"staffName\"=3").trim());
		} finally {
			ReservedWordsUtil.clear();
		}
	}

	/**
	 * C5变体: 字段名=列名且为保留字,多处出现均转换并包裹
	 */
	@Test
	public void testC5_keywordSameNameMultiOccurrence() {
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("name");
		try {
			EntityMeta meta = buildStaffMeta("staff_cfe_c5");
			assertEquals("[NAME]=1 or [NAME]=2", SqlUtil.convertFieldsToColumns(meta, "name=1 or name=2").trim());
		} finally {
			ReservedWordsUtil.clear();
		}
	}

	/**
	 * C6: 引号标识符+保留字包裹+字面量保护三机制组合
	 */
	@Test
	public void testC6_keywordLiteralQuotedCombo() {
		ReservedWordsUtil.clear();
		ReservedWordsUtil.put("staff_name");
		try {
			EntityMeta meta = buildStaffMeta("staff_cfe_c6");
			assertEquals("[STAFF_NAME]='staffName' and [STAFF_NAME]=1",
					SqlUtil.convertFieldsToColumns(meta, "[staffName]='staffName' and staffName=1").trim());
		} finally {
			ReservedWordsUtil.clear();
		}
	}

	// ======================== D. 缓存(白盒反射) ========================

	/**
	 * D2: 同表同sql二次调用命中缓存,结果一致且key为"表名\u0000sql"形态
	 */
	@Test
	public void testD2_cacheHitConsistency() throws Exception {
		ReservedWordsUtil.clear();
		ConcurrentHashMap<String, String> cache = getConvertCache();
		cache.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_d2");
		String r1 = SqlUtil.convertFieldsToColumns(meta, "staffName=1");
		assertTrue(cache.containsKey("staff_cfe_d2\u0000staffName=1"), "缓存key应为表名+\\u0000+sql形态");
		String r2 = SqlUtil.convertFieldsToColumns(meta, "staffName=1");
		assertEquals(r1, r2);
		assertEquals("STAFF_NAME=1", r2.trim());
	}

	/**
	 * D3: 同sql不同表(列名映射不同)——缓存互不串扰,交替调用结果稳定
	 */
	@Test
	public void testD3_differentTablesSameSql() throws Exception {
		ReservedWordsUtil.clear();
		getConvertCache().clear();
		EntityMeta metaA = buildMeta("staff_cfe_d3a", "staffName", "AAA");
		EntityMeta metaB = buildMeta("staff_cfe_d3b", "staffName", "BBB");
		assertEquals("AAA=1", SqlUtil.convertFieldsToColumns(metaA, "staffName=1").trim());
		assertEquals("BBB=1", SqlUtil.convertFieldsToColumns(metaB, "staffName=1").trim());
		// 二次调用均命中各自缓存
		assertEquals("AAA=1", SqlUtil.convertFieldsToColumns(metaA, "staffName=1").trim());
		assertEquals("BBB=1", SqlUtil.convertFieldsToColumns(metaB, "staffName=1").trim());
	}

	/**
	 * D4: 容量上限——缓存满后put跳过,转换结果仍正确且size不增;finally清空恢复
	 * (避免像PerfRegressionBatchTest那样留满缓存影响后续依赖写入的用例)
	 */
	@Test
	public void testD4_cacheCapacityLimit() throws Exception {
		ReservedWordsUtil.clear();
		ConcurrentHashMap<String, String> cache = getConvertCache();
		try {
			cache.clear();
			for (int i = 0; i < 2000; i++) {
				cache.put("cfe-dummy-" + i, "x");
			}
			EntityMeta meta = buildStaffMeta("staff_cfe_d4");
			assertEquals("STAFF_NAME=1", SqlUtil.convertFieldsToColumns(meta, "staffName=1").trim());
			assertEquals(2000, cache.size(), "超限后不应再写入缓存");
		} finally {
			cache.clear();
		}
	}

	/**
	 * D5: 已知限制文档化——缓存不感知保留字配置变化:同表同sql先以无保留字状态入缓存,
	 * 后配置保留字再调用仍返回旧缓存结果(无[]包裹)。生产上保留字随方言配置启动期加载
	 * 后不变,该限制可接受;如引入运行期动态保留字,需同步失效convertSqlMap
	 */
	@Test
	public void testD5_cacheIgnoresKeywordChange() throws Exception {
		ReservedWordsUtil.clear();
		getConvertCache().clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_d5");
		assertEquals("STAFF_NAME=1", SqlUtil.convertFieldsToColumns(meta, "staffName=1").trim());
		ReservedWordsUtil.put("staff_name");
		try {
			// 命中前次缓存,不带保留字包裹(现状记录)
			assertEquals("STAFF_NAME=1", SqlUtil.convertFieldsToColumns(meta, "staffName=1").trim());
		} finally {
			ReservedWordsUtil.clear();
		}
	}

	// ======================== E. 输入异常/防御 ========================

	/**
	 * E2: 纯字面量sql原样返回
	 */
	@Test
	public void testE2_pureLiteralSql() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_e2");
		assertEquals("'staffName'", SqlUtil.convertFieldsToColumns(meta, "'staffName'").trim());
	}

	/**
	 * E3: fieldsArray含fieldsMeta缺失的字段、或FieldMeta的columnName为null——均跳过不抛异常
	 * (getColumnName对缺失键null安全,方法内columnName!=null守卫)
	 */
	@Test
	public void testE3_ghostFieldSkipped() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_e3");
		// ghost不在fieldsMeta中;nullCol在fieldsMeta中但columnName为null
		FieldMeta nullColMeta = new FieldMeta();
		nullColMeta.setFieldName("nullCol");
		nullColMeta.setColumnName(null);
		meta.getFieldsMeta().put("nullcol", nullColMeta);
		meta.setFieldsArray(new String[] { "ghost", "nullCol", "staffName" });
		assertEquals("ghost=1 and nullCol=2 and STAFF_NAME=3",
				SqlUtil.convertFieldsToColumns(meta, "ghost=1 and nullCol=2 and staffName=3").trim());
	}

	/**
	 * E4: 超长sql(约5KB,50字段×每字段3形态:裸字段/命名参数/字面量)——
	 * 50轮替换+平行掩码压力,逐段构造期望值全量比对
	 */
	@Test
	public void testE4_longSqlManyFieldsLiterals() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildWideMeta("staff_cfe_e4", 50);
		StringBuilder sql = new StringBuilder();
		StringBuilder expected = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			sql.append("f").append(i).append("=:f").append(i).append(" and f").append(i).append("!='lit")
					.append(i).append("xxxxxxxxxxxxxxxxxxxxxx' and ");
			expected.append("F_").append(i).append("=:f").append(i).append(" and F_").append(i).append("!='lit")
					.append(i).append("xxxxxxxxxxxxxxxxxxxxxx' and ");
		}
		String result = SqlUtil.convertFieldsToColumns(meta, sql.toString());
		assertEquals(expected.toString().trim(), result.trim());
	}

	/**
	 * E5: 宽实体50字段无字面量——maskedSql==realSql快速通道下50轮重建的引用同步
	 * (else分支maskedSql=realSql,防陈旧引用回归)
	 */
	@Test
	public void testE5_wideEntityNoLiteral() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildWideMeta("staff_cfe_e5", 50);
		StringBuilder sql = new StringBuilder();
		StringBuilder expected = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			sql.append("f").append(i).append("=").append(i).append(" and ");
			expected.append("F_").append(i).append("=").append(i).append(" and ");
		}
		String result = SqlUtil.convertFieldsToColumns(meta, sql.toString());
		assertEquals(expected.toString().trim(), result.trim());
	}

	// ======================== F. 并发 ========================

	/**
	 * F1: 8线程×100次并发转换互不相同的sql——并发计算+并发写ConcurrentHashMap缓存,
	 * 全部结果精确匹配(仿ConcurrencyBatch3Test的latch+AtomicInteger汇总模式)
	 */
	@Test
	public void testF1_concurrentDistinctSqls() throws Exception {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_f1");
		int threads = 8;
		int loops = 100;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threads);
		AtomicInteger failures = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			final int tid = t;
			new Thread(() -> {
				try {
					startGate.await();
					for (int i = 0; i < loops; i++) {
						String sql = "staffName=t" + tid + "i" + i;
						String expected = "STAFF_NAME=t" + tid + "i" + i;
						if (!expected.equals(SqlUtil.convertFieldsToColumns(meta, sql).trim())) {
							failures.incrementAndGet();
						}
					}
				} catch (Exception e) {
					failures.incrementAndGet();
				} finally {
					doneLatch.countDown();
				}
			}).start();
		}
		startGate.countDown();
		doneLatch.await();
		assertEquals(0, failures.get(), "并发转换不同sql应全部正确,失败数:" + failures.get());
	}

	/**
	 * F2: 8线程并发同一sql(首次计算竞态,可能多线程同时计算后覆盖写缓存)——结果幂等一致
	 */
	@Test
	public void testF2_concurrentSameSql() throws Exception {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildStaffMeta("staff_cfe_f2");
		int threads = 8;
		CountDownLatch startGate = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threads);
		AtomicInteger failures = new AtomicInteger();
		for (int t = 0; t < threads; t++) {
			new Thread(() -> {
				try {
					startGate.await();
					for (int i = 0; i < 50; i++) {
						if (!"STAFF_NAME=concurrent"
								.equals(SqlUtil.convertFieldsToColumns(meta, "staffName=concurrent").trim())) {
							failures.incrementAndGet();
						}
					}
				} catch (Exception e) {
					failures.incrementAndGet();
				} finally {
					doneLatch.countDown();
				}
			}).start();
		}
		startGate.countDown();
		doneLatch.await();
		assertEquals(0, failures.get(), "并发转换同一sql结果应幂等一致,失败数:" + failures.get());
	}

	// ======================== G. 性能 smoke ========================

	/**
	 * G1: 50字段+5字面量sql——首次转换(50轮平行掩码重建)与1万次缓存命中耗时smoke,
	 * 宽松上限防数量级劣化(不做严格断言避免CI抖动)
	 */
	@Test
	public void testG1_perfSmoke() {
		ReservedWordsUtil.clear();
		EntityMeta meta = buildWideMeta("staff_cfe_g1", 50);
		StringBuilder sql = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			sql.append("f").append(i).append("=").append(i);
			// 每10个字段插入一个字面量段
			if (i % 10 == 0) {
				sql.append(" and remark='literal-value-").append(i).append("'");
			}
			sql.append(" and ");
		}
		String sqlStr = sql.toString();
		long t0 = System.nanoTime();
		String result = SqlUtil.convertFieldsToColumns(meta, sqlStr);
		long firstMs = (System.nanoTime() - t0) / 1000000;
		assertTrue(result.contains("F_0=") && result.contains("F_49=") && result.contains("remark='literal-value-0'"),
				"转换结果抽查应正确:" + result.substring(0, Math.min(120, result.length())));
		long t1 = System.nanoTime();
		for (int i = 0; i < 10000; i++) {
			SqlUtil.convertFieldsToColumns(meta, sqlStr);
		}
		long cachedMs = (System.nanoTime() - t1) / 1000000;
		System.err.println("convertFieldsToColumns perf smoke: first=" + firstMs + "ms, 10000 cached hits="
				+ cachedMs + "ms, sqlLen=" + sqlStr.length());
		assertTrue(firstMs < 500, "首次转换耗时应<500ms,实际:" + firstMs);
		assertTrue(cachedMs < 5000, "1万次缓存命中耗时应<5000ms,实际:" + cachedMs);
	}

	// ======================== helpers ========================

	private EntityMeta buildStaffMeta(String tableName) {
		return buildMeta(tableName, "name", "NAME", "staffName", "STAFF_NAME", "bizStaffName", "BIZ_STAFF_NAME",
				"sexType", "SEX_TYPE");
	}

	private EntityMeta buildWideMeta(String tableName, int fieldCnt) {
		String[] pairs = new String[fieldCnt * 2];
		for (int i = 0; i < fieldCnt; i++) {
			pairs[i * 2] = "f" + i;
			pairs[i * 2 + 1] = "F_" + i;
		}
		return buildMeta(tableName, pairs);
	}

	private EntityMeta buildMeta(String tableName, String... fieldColumnPairs) {
		EntityMeta meta = new EntityMeta();
		meta.setEntityClass(getClass());
		meta.setTableName(tableName);
		HashMap<String, FieldMeta> fieldsMeta = new HashMap<String, FieldMeta>();
		String[] fields = new String[fieldColumnPairs.length / 2];
		for (int i = 0; i < fieldColumnPairs.length; i += 2) {
			FieldMeta fieldMeta = new FieldMeta();
			fieldMeta.setFieldName(fieldColumnPairs[i]);
			fieldMeta.setColumnName(fieldColumnPairs[i + 1]);
			fieldsMeta.put(fieldColumnPairs[i].toLowerCase(Locale.ROOT), fieldMeta);
			fields[i / 2] = fieldColumnPairs[i];
		}
		meta.setFieldsMeta(fieldsMeta);
		meta.setFieldsArray(fields);
		return meta;
	}

	@SuppressWarnings("unchecked")
	private ConcurrentHashMap<String, String> getConvertCache() throws Exception {
		Field field = SqlUtil.class.getDeclaredField("convertSqlMap");
		field.setAccessible(true);
		return (ConcurrentHashMap<String, String>) field.get(null);
	}
}
