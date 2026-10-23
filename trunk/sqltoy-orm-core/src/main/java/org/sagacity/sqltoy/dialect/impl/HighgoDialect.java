package org.sagacity.sqltoy.dialect.impl;

/**
 * update 2026-10-11 @project sagacity-sqltoy
 * @description 瀚高数据库(HighGo,PG12内核+oracle兼容层)方言实现:SQL生成层复用PG系。
 *              真库实测:PG wire协议/方言行为与vanilla PG一致,
 *              差异集中在函数面——uuid()与gen_random_uuid()均无但有sys_guid()、
 *              last_day/add_months/trunc原生、listagg无而string_agg原生,由函数适配器的HIGHGO分支承担
 * @author zhongxuchen
 * @version v1.0,Date:2026-10-11
 */
public class HighgoDialect extends PostgreSqlDialect {
}