/**
 * 
 */
package com.sqltoy.quickstart.dao;

import org.sagacity.sqltoy.support.SqlToyDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 演示dao的编写,正常情况下sqltoy并不需要写dao(service层调用SqlToyLazyDao即可完成)
 * 
 * @author zhongxuchen
 * @version 1.0.0,Date:2020-07-16
 */
@Repository("staffInfoDao")
public class StaffInfoDao extends SqlToyDaoSupport {

}
