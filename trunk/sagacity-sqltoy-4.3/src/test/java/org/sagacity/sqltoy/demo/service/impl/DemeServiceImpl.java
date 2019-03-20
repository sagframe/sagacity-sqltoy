/**
 * 
 */
package org.sagacity.sqltoy.demo.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.demo.service.DemoService;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @project sqltoy-orm
 * @description <p>
 *              请在此说明类的功能
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:DemeServiceImpl.java,Revision:v1.0,Date:2015年4月9日
 */
@Service("demoService")
public class DemeServiceImpl implements DemoService {
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.demo.service.DemoService#updateFetch()
	 */
	@Override
	public List updateFetch() throws Exception {
		StringBuilder query = new StringBuilder(
				"SELECT STAFF_ID, STAFF_CODE, ORGAN_ID, STAFF_NAME, ENGLISH_NAME, SEX_TYPE,");
		query.append("LINK_PHONE, BIRTHDAY, DUTY_DATE, OUT_DUTY_DATE, POST, PHOTO, EMAIL, IS_VIRTUAL, OPERATOR, OPERATE_DATE, ENABLED ");
		query.append(" FROM SYS_STAFF_INFO where SEX_TYPE=:sexType");
		// String sql =
		// "select t.TRIGGER_ID, t.MANUAL_END, t.STATUS from cron_trigger t where t.STATUS in (:status) ";
		QueryExecutor queryExecutor = new QueryExecutor(query.toString(),
				new String[] { "sexType" }, new Object[] { "F" });
		List result = sqlToyLazyDao.updateFetch(queryExecutor,
				new UpdateRowHandler() {
					@Override
					public void updateRow(ResultSet rs, int index)
							throws SQLException {
						rs.updateString("LINK_PHONE", "13918799460");
						rs.updateString("ENGLISH_NAME", rs.getString("STAFF_NAME")+"EN");
					}
				});
		for (int i = 0; i < result.size(); i++) {
			System.err.println(((List) result.get(i)).get(0));
			System.err.println(((List) result.get(i)).get(3));
		}
		// List result = sqlToyLazyDao.updateFetchTop(queryExecutor, 1,null
		// ,
		// new UpdateRowHandler() {
		// @Override
		// public void updateRow(ResultSet rs, int index)
		// throws SQLException {
		// rs.updateInt("STATUS", 6);
		// }
		// }

		// );
		return result;
	}

}
