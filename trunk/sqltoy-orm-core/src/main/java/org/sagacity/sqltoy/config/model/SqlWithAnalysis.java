package org.sagacity.sqltoy.config.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description Sql语句中存在with的分析,只支持单个with 但支持多个as
 * @author zhongxuchen
 * @version v1.0,Date:2013-06-20
 * @modify Date:2019-07-22 优化with as 语法解析格式 ,其格式包含:with xx as (); with xx as xx
 *         () ; with xxx(p1,p2) as () 三种形态
 * @modify Date:2020-01-16 支持mysql8.0.19 开始的with recursive cte as
 */
public class SqlWithAnalysis implements Serializable {
	private static final long serialVersionUID = -5841684922722930298L;

	private final Pattern asPattern = Pattern.compile("\\Was");

	/**
	 * update 2026-10-18 CTE物化hint定位:匹配后随'('的"as [not] materialized",组1承载"as"+空白
	 * 原形态,供adaptCteMaterialized在不支持库上剔除[not] materialized关键字而保留"as ("
	 */
	private static final Pattern CTE_MATERIALIZED_PATTERN = Pattern
			.compile("(?i)(\\bas\\s+)(?:not\\s+)?materialized\\s*(?=\\()");

	private String sql;

	private String withSql = "";

	/**
	 * 排除with之后的语句
	 */
	private String rejectWithSql;

	/**
	 * with sql前部分
	 */
	private String preSql;

	/**
	 * with sql尾部sql
	 */
	private String footSql;

	/**
	 * 是否有with as
	 */
	private boolean hasWith = false;

	/**
	 * 多个with存放的集合，正向排序(第一版采用逆向排序){asTableName,ext,sql script}
	 */
	private List<String[]> withSqlSet = null;

	public SqlWithAnalysis(String sql) {
		this.sql = sql;
		this.parse();
	}

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @return the withSql
	 */
	public String getWithSql() {
		return withSql;
	}

	/**
	 * @return the rejectWithSql
	 */
	public String getRejectWithSql() {
		return rejectWithSql;
	}

	/**
	 * @return the withSqlSet
	 */
	public List<String[]> getWithSqlSet() {
		return withSqlSet;
	}

	/**
	 * @return the preSql
	 */
	public String getPreSql() {
		return preSql;
	}

	/**
	 * @return the lastSql
	 */
	public String getFootSql() {
		return footSql;
	}

	/**
	 * 将带with 的sql解析成2部分:with as table () 和 select 部分
	 */
	private void parse() {
		rejectWithSql = this.sql;
		String headSql = "";
		String tailSql = " ".concat(this.sql);
		// 字面量掩码串与原串等长:with、逗号as的定位均在掩码串上进行,
		// 规避字面量内的with xx as (文本误触发解析导致字面量被截断;
		// 内容截取仍基于原串tailSql,掩码串位置可直接用于原串
		String maskedTailSql = SqlConfigParseUtils.maskLiterals(tailSql, false);
		String aliasTable;
		int endWith;
		int asIndex;
		String ext;
		StringBuilder withSqlBuffer = null;
		// 单个with
		Matcher withAsMatcher = SqlToyConstants.withPattern.matcher(maskedTailSql);
		String groupStr;
		String groupLow;
		String withAfter;
		String[] params;
		String withAsMiddle;
		String aliasParams;
		int bracketIndex;
		// WITH RECURSIVE search_graph(id, link, data, depth) AS
		if (withAsMatcher.find()) {
			withAfter = "";
			aliasParams = "";
			headSql = tailSql.substring(0, withAsMatcher.start() + 1);
			hasWith = true;
			withSqlBuffer = new StringBuilder();
			withSqlSet = new ArrayList<String[]>();
			groupStr = withAsMatcher.group();
			groupLow = groupStr.toLowerCase(Locale.ROOT);
			asIndex = StringUtil.matchIndex(groupLow, asPattern) + 1;
			withAsMiddle = groupStr.substring(groupLow.indexOf("with") + 4, asIndex).trim();
			bracketIndex = withAsMiddle.indexOf("(");
			if (bracketIndex > 0 && withAsMiddle.endsWith(")")) {
				aliasParams = withAsMiddle.substring(bracketIndex);
				withAsMiddle = withAsMiddle.substring(0, bracketIndex);
				if (withAsMiddle.endsWith(" ")) {
					aliasParams = " ".concat(aliasParams);
					withAsMiddle = withAsMiddle.trim();
				}
			}
			params = withAsMiddle.split("\\s+");
			// 剔除with
			aliasTable = params[params.length - 1];
			if (params.length > 1) {
				withAfter = params[0];
			}
			ext = groupStr.substring(asIndex + 2, groupStr.indexOf("(", asIndex));
			// update 2026-9-3 findBodyEnd替代
			// endWith = StringUtil.getSymMarkIndex("(", ")", tailSql, withAsMatcher.start()
			// + asIndex);
			endWith = findBodyEnd(tailSql, withAsMatcher.start() + asIndex);
			withSqlBuffer.append(tailSql.substring(withAsMatcher.start() + 1, endWith + 1));
			withSqlSet.add(new String[] { aliasTable, ext, tailSql.substring(withAsMatcher.end(), endWith), withAfter,
					aliasParams });
			tailSql = tailSql.substring(endWith + 1);
			maskedTailSql = maskedTailSql.substring(endWith + 1);
		} else {
			return;
		}
		// with 中包含多个 as
		Matcher otherMatcher = SqlToyConstants.otherWithPattern.matcher(maskedTailSql);
		while (otherMatcher.find()) {
			if (otherMatcher.start() != 0) {
				break;
			}
			withAfter = "";
			aliasParams = "";
			groupStr = otherMatcher.group();
			groupLow = groupStr.toLowerCase(Locale.ROOT);
			asIndex = StringUtil.matchIndex(groupLow, asPattern) + 1;
			withAsMiddle = groupStr.substring(groupStr.indexOf(",") + 1, asIndex).trim();
			bracketIndex = withAsMiddle.indexOf("(");
			if (bracketIndex > 0 && withAsMiddle.endsWith(")")) {
				aliasParams = withAsMiddle.substring(bracketIndex);
				withAsMiddle = withAsMiddle.substring(0, bracketIndex);
				if (withAsMiddle.endsWith(" ")) {
					aliasParams = " ".concat(aliasParams);
					withAsMiddle = withAsMiddle.trim();
				}
			}
			params = withAsMiddle.split("\\s+");
			aliasTable = params[params.length - 1];
			if (params.length > 1) {
				withAfter = params[0];
			}
			ext = groupStr.substring(asIndex + 2, groupStr.indexOf("(", asIndex));
			// update 2026-9-3 findBodyEnd替代
			// endWith = StringUtil.getSymMarkIndex("(", ")", tailSql, otherMatcher.start()
			// + asIndex);
			endWith = findBodyEnd(tailSql, otherMatcher.start() + asIndex);
			withSqlBuffer.append(tailSql.substring(0, endWith + 1));
			withSqlSet.add(new String[] { aliasTable, ext, tailSql.substring(otherMatcher.end(), endWith), withAfter,
					aliasParams });
			tailSql = tailSql.substring(endWith + 1);
			maskedTailSql = maskedTailSql.substring(endWith + 1);
			otherMatcher.reset(maskedTailSql);
		}
		this.preSql = headSql.trim();
		this.footSql = tailSql.trim();
		this.rejectWithSql = headSql.concat(" ").concat(tailSql);
		this.withSql = withSqlBuffer.append(" ").toString();
	}

	/**
	 * 查找with体的配对')'位置:跳过'...'字符串字面量(''成对转义)并支持括号嵌套,
	 * 规避字面量内的)被误当with体终结导致提取截断(如select ')' as mark场景)
	 * 
	 * @param sql
	 * @param fromIndex 从该位置开始查找with体的'('
	 * @return 配对')'的位置,未找到返回-1
	 */
	private static int findBodyEnd(String sql, int fromIndex) {
		// 委托StringUtil的字面量感知括号配对(语义一致,统一维护)
		return StringUtil.getSymMarkIndexSkipQuoted("(", ")", sql, fromIndex);
	}

	/**
	 * @return the hasWith
	 */
	public boolean isHasWith() {
		return hasWith;
	}

	/**
	 * @param hasWith the hasWith to set
	 */
	public void setHasWith(boolean hasWith) {
		this.hasWith = hasWith;
	}

	/**
	 * update 2026-10-18 目标库是否支持CTE物化hint(AS [NOT] MATERIALIZED)。该语法为PostgreSQL
	 * 12+引入,同内核衍生库openGauss/金仓/Vastbase/瀚高经本次真库实测均支持;其余库(mysql/tidb/
	 * sqlserver/oracle/db2/dm/hana/clickhouse等,实测mysql9.3/tidb8.5/mssql2025混入即报语法错误)
	 * 不支持。白名单保守只收录已实测支持的库,未收录库一律剔除hint(仅失去物化优化、不改变查询
	 * 语义,绝不会因关键字报错);GAUSSDB/MOGDB/STARDB等openGauss/PG衍生库待真库实测后可补入
	 *
	 * @param dbType DBType常量
	 * @return true表示支持,应保留hint
	 */
	public static boolean supportsCteMaterialized(int dbType) {
		switch (dbType) {
		case DBType.POSTGRESQL:
		case DBType.POSTGRESQL14:
		case DBType.OPENGAUSS:
		case DBType.KINGBASE:
		case DBType.VASTBASE:
		case DBType.GAUSSDB:
		case DBType.HIGHGO:
			return true;
		default:
			return false;
		}
	}

	/**
	 * update 2026-10-18 按目标库类型保留/剔除CTE物化hint。支持库(PG系)原样返回;不支持库将 "AS [NOT]
	 * MATERIALIZED ("改写为"AS (",规避混入PG专有关键字导致的执行报错。定位在字面量
	 * 掩码串上进行(掩码串与原串等长、非字面量字符原样保留,匹配位置可直接用于原串截取),规避 字面量内的"as materialized
	 * ("文本被误剔;不含materialized关键字时走零开销快速通道。
	 * 剔除只移除优化hint、不改变CTE查询语义,对不支持库本就无物化概念,行为等价于用户未写hint
	 *
	 * @param sql    原始sql(可为整条语句或with片段)
	 * @param dbType 目标库类型(DBType常量)
	 * @return dbType支持或无匹配时原样返回,否则返回剔除MATERIALIZED后的sql
	 */
	public static String adaptCteMaterialized(String sql, int dbType) {
		if (StringUtil.isBlank(sql) || supportsCteMaterialized(dbType)) {
			return sql;
		}
		// 快速通道:常规sql不含materialized关键字,零处理原样返回
		if (StringUtil.indexOfIgnoreCase(sql, "materialized") == -1) {
			return sql;
		}
		String masked = SqlConfigParseUtils.maskLiterals(sql, SqlConfigParseUtils.isBackslashEscapeDialect(dbType));
		Matcher matcher = CTE_MATERIALIZED_PATTERN.matcher(masked);
		if (!matcher.find()) {
			return sql;
		}
		StringBuilder result = new StringBuilder(sql.length());
		int last = 0;
		do {
			// 保留原"as"+空白(组1),跳过[not] materialized关键字直至'('前(lookahead不消费'(')
			result.append(sql, last, matcher.end(1));
			last = matcher.end();
		} while (matcher.find());
		result.append(sql, last, sql.length());
		return result.toString();
	}

}
