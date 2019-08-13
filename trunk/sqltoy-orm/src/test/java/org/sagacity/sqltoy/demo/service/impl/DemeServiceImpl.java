/**
 * 
 */
package org.sagacity.sqltoy.demo.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.demo.service.DemoService;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.plugin.TranslateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @project sqltoy-orm
 * @description
 *              <p>
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
		query.append(
				"LINK_PHONE, BIRTHDAY, DUTY_DATE, OUT_DUTY_DATE, POST, PHOTO, EMAIL, IS_VIRTUAL, OPERATOR, OPERATE_DATE, ENABLED ");
		query.append(" FROM SYS_STAFF_INFO where SEX_TYPE=:sexType");
		// String sql =
		// "select t.TRIGGER_ID, t.MANUAL_END, t.STATUS from cron_trigger t where
		// t.STATUS in (:status) ";
		QueryExecutor queryExecutor = new QueryExecutor(query.toString(), new String[] { "sexType" },
				new Object[] { "F" });
		List result = sqlToyLazyDao.updateFetch(queryExecutor, new UpdateRowHandler() {
			@Override
			public void updateRow(ResultSet rs, int index) throws SQLException {
				rs.updateString("LINK_PHONE", "13918799460");
				rs.updateString("ENGLISH_NAME", rs.getString("STAFF_NAME") + "EN");
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

	/**
	 * @todo 直接调用sqltoy缓存进行集合翻译
	 * @throws Exception
	 */
	public void testTranslate() throws Exception {
		List myDataSet = new ArrayList();
		myDataSet.add(new Object[] { "001", "" });
		myDataSet.add(new Object[] { "002", "" });
		
		//通过一个反调,实现自主取需要翻译的代码
		/*
		 * 参数说明
		 * 1、集合;
		 * 2、缓存名称
		 * 3、缓存类型(一般针对数据字典,非数据字典传null)
		 * 4、cacheNameIndex,如果传null 则默认赋值为1
		 */
		sqlToyLazyDao.translate(myDataSet, "dictKeyCache", "TAX_RATE", 1, new TranslateHandler() {
			//获取需要翻译的key
			public Object getKey(Object row) {
				return ((Object[]) row)[0];
			}
			
			//提供给框架实现翻译结果名称设置到结合列或属性上去
			public void setName(Object row, String name) {
				((Object[]) row)[1] = name;
			}
			
		});
	}
}
