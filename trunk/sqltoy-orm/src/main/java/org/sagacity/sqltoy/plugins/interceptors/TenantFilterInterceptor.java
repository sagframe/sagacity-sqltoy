/**
 * 
 */
package org.sagacity.sqltoy.plugins.interceptors;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.plugins.SqlInterceptor;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 提供授权租户数据权限过滤
 * @author zhongxuchen
 * @version v1.0, Date:2022年9月21日
 * @modify 2022年9月21日,修改说明
 */
public class TenantFilterInterceptor implements SqlInterceptor {

	/**
	 * @TODO 对最终执行sql和sql参数进行处理
	 * @param sqlToyContext 支持getEntityMeta(tableName)获取表信息
	 * @param sqlToyConfig  传递原本的sql配置,可以通过获取paramNames判断是否sql中已经有相关参数
	 * @param operateType   search\page\top\random\count 等，
	 * @param sqlToyResult  存放了最终的sql 和paramValues
	 * @param entityClass   实体对象类型(只针对对象crud操作才有值、或者基于纯POJO的findEntity、findPageEntity、updateByQuery、deleteByQuery操作)
	 * @param dbType        当前数据库类型,通过DBType.xxx 进行对比
	 * @return
	 */
	@Override
	public SqlToyResult decorate(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, OperateType operateType,
			SqlToyResult sqlToyResult, Class entityClass, Integer dbType) {
		// 存在统一字段处理、且是对象实体操作
		if (sqlToyContext.getUnifyFieldsHandler() == null || entityClass == null) {
			return sqlToyResult;
		}
		// 根据表名获取pojo的信息
		// EntityMeta entityMeta = sqlToyContext.getEntityMeta(tableName);
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entityClass);
		// 不存在租户过滤控制
		if (entityMeta.getTenantField() == null) {
			return sqlToyResult;
		}
		// 你也可以判断表里面是否包含统一的租户字段，而非@Tenant注解模式
		// if (entityMeta.getColumnName("tenantId") != null) {
		// }

		// 授权租户信息为空不做过滤
		String[] tenants = sqlToyContext.getUnifyFieldsHandler().authTenants(entityClass, operateType);
		if (tenants == null || tenants.length == 0) {
			return sqlToyResult;
		}
		String sql = sqlToyResult.getSql();
		// 租户字段
		String tenantColumn = entityMeta.getColumnName(entityMeta.getTenantField());
		// 保留字处理(实际不会出现保留字用作租户)
		tenantColumn = ReservedWordsUtil.convertWord(tenantColumn, dbType);
		int whereIndex = StringUtil.matchIndex(sql, "(?i)\\Wwhere\\W");
		// sql 在where后面已经有租户条件过滤，无需做处理
		if (whereIndex > 0
				&& StringUtil.matches(sql.substring(whereIndex), "(?i)\\W" + tenantColumn + "(\\s*\\=|\\s+in)")) {
			return sqlToyResult;
		}
		String where = " where ";
		String sqlPart = where;
		if (tenants.length == 1) {
			sqlPart = sqlPart.concat(tenantColumn).concat("='").concat(tenants[0]).concat("' and ");
		} else {
			sqlPart = sqlPart.concat(tenantColumn).concat("in (")
					.concat(SqlUtil.combineQueryInStr(tenants, null, null, true).concat(") and "));
		}
		// 所有基于对象操作和查询、更新操作进行租户过滤
		if (operateType.equals(OperateType.load) || operateType.equals(OperateType.loadAll)
				|| operateType.equals(OperateType.update) || operateType.equals(OperateType.updateAll)
				|| operateType.equals(OperateType.delete) || operateType.equals(OperateType.deleteAll)
				|| operateType.equals(OperateType.unique) || operateType.equals(OperateType.saveOrUpdate)
				|| operateType.equals(OperateType.singleTable)) {
			// 从where开始替换，避免select a,b from table where id=? for update 场景拼接在最后面是有错误的
			// 对象操作sql由框架生成，where前后是空白
			if (operateType.equals(OperateType.saveOrUpdate) && sql.indexOf(SqlToyConstants.MERGE_UPDATE) > 0) {
				// 截取merge int xxxx (select ?,? from dual) as tv on (alias.field=tv.xxx)
				// 中的具体alias
				int onTenantIndex = sql.indexOf(SqlToyConstants.MERGE_ALIAS_ON);
				int end = onTenantIndex + SqlToyConstants.MERGE_ALIAS_ON.length();
				String aliasName = sql.substring(end, sql.indexOf(".", end)).trim();
				// 去除where、租户字段加上表别名，末尾补充and跟后续条件衔接
				sqlPart = sqlPart.replaceFirst(where, "").replaceFirst(tenantColumn, aliasName + "." + tenantColumn)
						.concat(" and ");
				sqlToyResult.setSql(sql.replaceFirst(SqlToyConstants.MERGE_ALIAS_ON,
						SqlToyConstants.MERGE_ALIAS_ON.concat(sqlPart)));
			} else {
				sqlToyResult.setSql(sql.replaceFirst("(?i)\\swhere\\s", sqlPart));
			}
		}
		// 通过表名获取entityMeta、并判断表里面是否有租户字段
		// EntityMeta entityMeta = sqlToyContext.getEntityMeta(tableName);
		// if (entityMeta.getColumnName("tenantId") != null)
		// 针对sql查询，你可以解析sql获得表名称，判断这个表是否有租户字段
		return sqlToyResult;
	}

}
