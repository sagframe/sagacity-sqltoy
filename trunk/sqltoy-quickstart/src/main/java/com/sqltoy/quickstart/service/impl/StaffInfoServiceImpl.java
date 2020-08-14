/**
 * 
 */
package com.sqltoy.quickstart.service.impl;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.PaginationModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sqltoy.quickstart.dao.StaffInfoDao;
import com.sqltoy.quickstart.service.StaffInfoService;
import com.sqltoy.quickstart.vo.StaffInfoVO;

/**
 * 
 * @project sqltoy-quickstart
 * @description 演示
 * @author zhongxuchen
 * @version v1.0, Date:2020年7月20日
 * @modify 2020年7月20日,修改说明
 */
@Service("staffInfoService")
public class StaffInfoServiceImpl implements StaffInfoService {
	// 这里是演示用，常规情況下无需写dao
	@Autowired
	StaffInfoDao staffInfoDao;

	@Autowired
	SqlToyLazyDao sqlToyLazyDao;

	public PaginationModel<StaffInfoVO> queryStaff(PaginationModel<StaffInfoVO> pageModel, StaffInfoVO staffInfoVO) {
		return staffInfoDao.findStaff(pageModel, staffInfoVO);
	}

	// updateFetch 应用场景一般用于诸如库存台账、资金台账这类需要查询并锁住记录，进行部分逻辑处理后修改记录
	/**
	 * @TODO 演示updateFetch用法(这里仅仅演示用法,场景并不贴切)
	 * @return
	 */
	@Transactional
	public List<StaffInfoVO> updateFetch() {
		String sql = "select t.STAFF_ID,t.STAFF_NAME,t.ADDRESS,t.ENTRY_DATE,t.ORGAN_ID,t.ORGAN_ID ORGAN_NAME,t.UPDATE_TIME"
				+ " from sqltoy_staff_info t " + " where #[t.CREATE_TIME >=?] #[and t.CREATE_TIME<=?]";
		return sqlToyLazyDao
				.updateFetch(
						new QueryExecutor(sql).values(LocalDate.parse("2019-01-01"), null).resultType(StaffInfoVO.class)
								.translates(new Translate("organIdName").setColumn("ORGAN_NAME")),
						new UpdateRowHandler() {
							@Override
							public void updateRow(ResultSet rs, int index) throws Exception {
								String staffName = rs.getString("STAFF_NAME");
								// 一般updateFetch会依托表中的现有值做一些逻辑处理,否则可以直接update
								if (staffName.contains("陈")) {
									rs.updateString("ADDRESS", rs.getString("ADDRESS") + "更新!");
								}
								rs.updateObject("UPDATE_TIME", LocalDateTime.now());
							}
						});
	}

	@Transactional
	public void updateLockStaff(String id, String address) {
		StaffInfoVO staffInfo = sqlToyLazyDao.load(new StaffInfoVO(id));
		staffInfo.setAddress(address);
		sqlToyLazyDao.update(staffInfo);
	}

	@Transactional
	public List<StaffInfoVO> callStore() {
		return sqlToyLazyDao.executeStore("{call sp_showcase(?,?)}", new Object[] { 1,null }, null, StaffInfoVO.class)
				.getRows();
	}

}
