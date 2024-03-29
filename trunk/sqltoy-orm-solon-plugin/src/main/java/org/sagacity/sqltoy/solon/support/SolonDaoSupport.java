package org.sagacity.sqltoy.solon.support;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.support.SqlToyDaoSupport;

/**
 * @author limliu
 * @version v1.0, Date:2024年3月21日
 * @project sagacity-sqltoy
 * @description 提供solon框架下写dao的基类，如StaffInfoDao extends SpringDaoSupport
 * @modify 2024年3月21日, 修改说明
 */
public class SolonDaoSupport extends SqlToyDaoSupport {
    
    /**
     * 针对solon提供sqlToyContext注入(差异点)
     */
    @Override
    public void setSqlToyContext(SqlToyContext sqlToyContext) {
        super.setSqlToyContext(sqlToyContext);
    }
}
