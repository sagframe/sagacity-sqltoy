package org.sagacity.sqltoy.plugins.ddl.impl;

/**
 * @project sagacity-sqltoy
 * @description Doris数据库通过POJO生成创建表结构的ddl语句
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 * @modify Date:2026-09-19,修改说明
 */
public class DorisDDLGenerator extends AbstractDorisStarRocksDDLGenerator {
	@Override
	protected char commentQuote() {
		return '\'';
	}
}
