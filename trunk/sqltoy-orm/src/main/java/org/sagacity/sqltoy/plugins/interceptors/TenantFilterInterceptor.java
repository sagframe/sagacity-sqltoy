/**
 * 
 */
package org.sagacity.sqltoy.plugins.interceptors;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.OperateType;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.plugins.SqlInterceptor;
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

	@Override
	public SqlToyResult decorate(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, OperateType operateType,
			SqlToyResult sqlToyResult, Class entityClass, Integer dbType) {
		// 存在统一字段处理、且是对象实体操作
		if (sqlToyContext.getUnifyFieldsHandler() == null || entityClass == null) {
			return sqlToyResult;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entityClass);
		// 不存在租户过滤控制
		if (entityMeta.getTenantField() == null) {
			return sqlToyResult;
		}
		// 授权租户信息为空不做过滤
		String[] tenants = sqlToyContext.getUnifyFieldsHandler().authTenants(entityClass, operateType);
		if (tenants == null || tenants.length == 0) {
			return sqlToyResult;
		}
		String sql = sqlToyResult.getSql();
		// 租户字段
		String tenantColumn = entityMeta.getColumnName(entityMeta.getTenantField());
		
		// sql 在where后面已经有租户条件过滤，无需做处理
		if (StringUtil.matches(sql.substring(StringUtil.matchIndex(sql, "(?i)\\Wwhere\\W")), "(?i)\\W" + tenantColumn + "(\\s*\\=|\\s+in)")) {
			return sqlToyResult;
		}
		String sqlPart = " where ";
		if (tenants.length == 1) {
			sqlPart = sqlPart.concat(tenantColumn).concat("='").concat(tenants[0]).concat("' and ");
		} else {
			sqlPart = sqlPart.concat(tenantColumn).concat("in (")
					.concat(SqlUtil.combineQueryInStr(tenants, null, null, true).concat(") and "));
		}
		if (operateType.equals(OperateType.load) || operateType.equals(OperateType.loadAll)
				|| operateType.equals(OperateType.update) || operateType.equals(OperateType.updateAll)
				|| operateType.equals(OperateType.delete) || operateType.equals(OperateType.deleteAll)
				|| operateType.equals(OperateType.unique) || operateType.equals(OperateType.saveOrUpdate)) {
			// 从where开始替换，避免select a,b from table where id=? for update 场景拼接在最后面是有错误的
			// 对象操作sql由框架生成，where前后是空白
			sqlToyResult.setSql(sql.replaceFirst("(?i)\\swhere\\s", sqlPart));
		}
		return sqlToyResult;
	}

}
