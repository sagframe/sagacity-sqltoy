/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.model.EntityQuery;
import org.sagacity.sqltoy.model.EntityUpdate;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.ComplexpkHeadVO;
import com.sagframe.sqltoy.showcase.vo.ComplexpkItemVO;
import com.sagframe.sqltoy.showcase.vo.StaffInfoVO;
import com.sagframe.sqltoy.utils.ShowCaseUtils;

/**
 * @project sqltoy-showcase
 * @description 普通的CRUD操作演示，无需额外写service方法，由sqltoy提供SqlToyCRUDServcie提供默认的操作即可
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:CrudCaseServiceTest.java,Revision:v1.0,Date:2019年7月12日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class CrudCaseServiceTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;
	
	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	/**
	 * 创建一条员工记录
	 */
	@Test
	public void saveStaffInfo() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715009");
		staffInfo.setStaffCode("S190715009");
		staffInfo.setStaffName("测试员工9");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test3@aliyun.com");
		staffInfo.setEntryDate(LocalDate.now());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		sqlToyCRUDService.save(staffInfo);
	}

	/**
	 * 修改员工记录信息
	 */
	// 演示sqltoy修改数据的策略
	// sqltoy 默认的update
	// 操作并不需要先将记录查询出来(普通hibernate则需要先取数据，然后对需要修改的地方进行重新设置值，确保其他字段值不会被覆盖)。
	// sqltoy 利用各种数据库自身特性,未null的字段会被忽略掉不参与更新操作(如果需要强制更新参见下一个范例:forceUpdate)
	@Test
	public void updateStaffInfo() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715001");
		// 只修改所在机构,其他字段不会被影响(避免先查询后修改提升了效率,同时避免高并发场景下先查询再修改数据冲突)
		staffInfo.setOrganId("C0002");
		sqlToyCRUDService.update(staffInfo);
	}

	/**
	 * 对员工信息特定字段进行强制修改
	 */
	@Test
	public void forceUpdate() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715001");
		// 只修改所在机构,其他字段不会被影响(避免先查询后修改提升了效率,同时避免高并发场景下先查询再修改数据冲突)
		staffInfo.setOrganId("C0002");
		staffInfo.setAddress("测试地址");

		// 第二个数组参数设置需要强制修改的字端,如果该字段的值为null，数据库中的值将被null覆盖
		sqlToyCRUDService.update(staffInfo, new String[] { "address" });
	}

	@Test
	public void saveOrUpdate() {
		StaffInfoVO staffInfo = new StaffInfoVO();
		staffInfo.setStaffId("S190715003");
		staffInfo.setStaffCode("S190715003");
		staffInfo.setStaffName("测试员工3");
		staffInfo.setSexType("M");
		staffInfo.setEmail("test3@aliyun.com");
		staffInfo.setEntryDate(LocalDate.now());
		staffInfo.setStatus(1);
		staffInfo.setOrganId("C0001");
		staffInfo.setPhoto(ShowCaseUtils.getBytes(ShowCaseUtils.getFileInputStream("classpath:/mock/staff_photo.jpg")));
		staffInfo.setCountry("86");
		sqlToyCRUDService.saveOrUpdate(staffInfo);
	}

	/**
	 * 通过where设置条件，对单表对象进行查询
	 */
	@Test
	public void findEntityByWhere() {
		List<StaffInfoVO> staffVOs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
				EntityQuery.create().where("#[`status` in (?)]#[and name like ?]").values(new Object[] { 1, 2 }, null));
		System.err.println(JSON.toJSONString(staffVOs));
	}

	/**
	 * 单表查询通过对象传参数据且不设置where 场景，自动根据对象属性值组织sql(非推荐功能)
	 */
	@Test
	public void findEntityByNoWhere() {
		List<StaffInfoVO> staffVOs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
				EntityQuery.create().values(new StaffInfoVO().setStatus(1).setEmail("test3@aliyun.com")));
		System.err.println(JSON.toJSONString(staffVOs));
	}
	
	/**
	 * findEntity 模式,简化sql编写模式,面向接口服务层提供快捷数据查询和处理
	 * 1、通过where指定条件
	 * 2、支持lock
	 * 3、支持order by (order by 在接口服务 层意义不大)
	 * 4、自动将对象属性映射成表字段
	 */
	@Test
	public void findEntity() {
		//条件利用sqltoy特有的#[]充当动态条件判断,#[]是支持嵌套的
		List<StaffInfoVO> staffVOs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
				EntityQuery.create().where("#[staffName like ?] #[ and status=?]").values("陈", 1)
				.lock(LockMode.UPGRADE).orderBy("staffName").orderByDesc("createTime"));
		System.err.println(JSON.toJSONString(staffVOs));
	}

	/**
	 * 指定where 并提供对象传参
	 */
	@Test
	public void findEntityByVO() {
		List<StaffInfoVO> staffVOs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
				EntityQuery.create().where("#[staffName like :staffName] #[ and status=:status]")
						.values(new StaffInfoVO().setStatus(1).setEmail("test3@aliyun.com")));
		System.err.println(JSON.toJSONString(staffVOs));
	}

	
	/**
	 * 通过参数传值进行删除，where必须有值(后端会校验)，delete操作属于危险操作
	 */
	@Test
	public void deleteEntity() {
		Long deleteCount = sqlToyLazyDao.deleteByQuery(StaffInfoVO.class,
				EntityQuery.create().where("status=:status").values(new StaffInfoVO().setStatus(1)));
		System.err.println(deleteCount);
	}

	/**
	 * update 操作where也必须有值，以防危险操作
	 */
	@Test
	public void updateEntity() {
		Long updateCount = sqlToyLazyDao.updateByQuery(StaffInfoVO.class,
				EntityUpdate.create().set("staffName", "张三").where("staffName like ? and status=?").values("陈", 1));
		System.err.println(updateCount);
	}

	@Test
	public void updateEntityVO() {
		Long updateCount = sqlToyLazyDao.updateByQuery(StaffInfoVO.class,
				EntityUpdate.create().set("staffName", "张三").where("staffName like :staffName and status=:status")
						.values(new StaffInfoVO().setStaffName("陈").setStatus(1)));
		System.err.println(updateCount);
	}

	@Test
	public void load() {
		StaffInfoVO staff = sqlToyCRUDService.load(new StaffInfoVO("S190715009"));
		System.err.println(JSON.toJSONString(staff));
	}

	@Test
	public void loadAll() {
		// 组织批量数据
		List<StaffInfoVO> staffs = new ArrayList<StaffInfoVO>();
		String[] ids = { "S190715001", "S190715002" };
		for (String id : ids) {
			staffs.add(new StaffInfoVO(id));
		}
		sqlToyCRUDService.loadAll(staffs);
	}

	@Test
	public void delete() {
		sqlToyCRUDService.delete(new StaffInfoVO("S190715001"));
	}

	@Test
	public void deleteAll() {
		// 组织批量数据
		List<StaffInfoVO> staffs = new ArrayList<StaffInfoVO>();
		String[] ids = { "S190715001", "S190715002" };
		for (String id : ids) {
			staffs.add(new StaffInfoVO(id));
		}
		sqlToyCRUDService.deleteAll(staffs);
	}

	/**
	 * 演示级联保存
	 */
	@Test
	public void cascadeSave() {
		// 主表记录
		ComplexpkHeadVO head = new ComplexpkHeadVO();
		head.setTransDate(LocalDate.now());
		head.setTransCode("S0001");
		head.setTotalCnt(BigDecimal.valueOf(10));
		head.setTotalAmt(BigDecimal.valueOf(10000));

		// 子表记录1
		ComplexpkItemVO item1 = new ComplexpkItemVO();
		item1.setProductId("P01");
		item1.setPrice(BigDecimal.valueOf(1000));
		item1.setAmt(BigDecimal.valueOf(5000));
		item1.setQuantity(BigDecimal.valueOf(5));
		head.getComplexpkItemVOs().add(item1);

		// 子表记录2
		ComplexpkItemVO item2 = new ComplexpkItemVO();
		item2.setProductId("P02");
		item2.setPrice(BigDecimal.valueOf(1000));
		item2.setAmt(BigDecimal.valueOf(5000));
		item2.setQuantity(BigDecimal.valueOf(5));
		head.getComplexpkItemVOs().add(item2);

		sqlToyCRUDService.save(head);
	}

	/**
	 * 演示级联加载
	 */
	@Test
	public void cascadeLoad() {
		ComplexpkHeadVO head = sqlToyCRUDService.loadCascade(new ComplexpkHeadVO(LocalDate.now(), "S0001"));
		// 打印级联加载的字表数据
		System.err.println(JSON.toJSONString(head.getComplexpkItemVOs()));
	}
}
