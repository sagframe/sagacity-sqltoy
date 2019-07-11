/**
 * 
 */
package sqltoy.showcase.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import sqltoy.showcase.system.service.OrganInfoService;
import sqltoy.showcase.system.vo.OrganInfoVO;

/**
 * @project sqltoy-showcase
 * @description
 * 				<p>
 *              机构信息维护测试
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:OrganInfoServiceTest.java,Revision:v1.0,Date:2015年11月3日
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring-context.xml" })
public class OrganInfoServiceTest {
	@Autowired
	private OrganInfoService organInfoService;

	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	/**
	 * Test method for
	 * {@link sqltoy.showcase.system.service.impl.OrganInfoServiceImpl#add(sqltoy.showcase.system.vo.OrganInfoVO)}
	 * .
	 */
	@Test
	public void testAdd() {
		OrganInfoVO organInfoVO = new OrganInfoVO();
		organInfoVO.setOrganId("1");
		//先删除
		sqlToyCRUDService.delete(organInfoVO);
		organInfoVO.setOrganName("睿智科技有限公司");
		organInfoVO.setAliasName("睿智科技");
		organInfoVO.setStatus("1");
		organInfoVO.setOperateDate(new Date());
		organInfoVO.setOrganCode("S000001");
		organInfoVO.setOperator("admin");
		organInfoVO.setOrganPid("-1");
		organInfoService.add(organInfoVO);
	}

	/**
	 * 测试对象属性值修改
	 */
	@Test
	public void testUpdate() {
		OrganInfoVO organInfoVO = new OrganInfoVO();
		organInfoVO.setOrganId("1");
		organInfoVO.setOrganName("未来科技有限公司");
		organInfoVO.setAliasName("未来科技");
		// 第二个参数表示强制修改的属性,当该属性值为null,将被强制设置为null，否则不被修改
		sqlToyCRUDService.update(organInfoVO, new String[] { "aliasName" });
	}

	@Test
	public void testSaveOrUpdateAll() {
		String[][] organInfos = new String[][] { { "1", "未来科技有限公司", "未来科技", "S000001", "-1" },
				{ "2", "智能设备研发部", "智能设备研发部", "S000002", "1" }, { "3", "生命科学研发部", "生命科学研发部", "S000003", "1" },
				{ "4", "新能源研发部", "新能源研发部", "S000004", "1" }, { "5", "宇航探索研发部", "宇航探索", "S000005", "1" },
				{ "6", "软件信息科技部", "软件信息科技部", "S000006", "1" }, { "7", "深度娱乐研发公司", "深度娱乐", "S000007", "1" },
				{ "8", "智能穿戴研发部", "智能穿戴", "S000008", "2" }, { "9", "智能机器人研发部", "智能机器人", "S000009", "2" },
				{ "10", "信息系统室", "信息系统室", "S000010", "6" }, { "11", "平台架构室", "平台架构室", "S000011", "6" },
				{ "12", "ERP系统组", "ERP系统组", "S000012", "10" }, { "13", "OA系统组", "OA系统组", "S000013", "10" } };
		List<OrganInfoVO> entities = new ArrayList<OrganInfoVO>();
		String[] rowOrgan;
		for (int i = 0; i < organInfos.length; i++) {
			rowOrgan = organInfos[i];
			OrganInfoVO organInfoVO = new OrganInfoVO();
			organInfoVO.setOrganId(rowOrgan[0]);
			organInfoVO.setOrganName(rowOrgan[1]);
			organInfoVO.setAliasName(rowOrgan[2]);
			organInfoVO.setOperateDate(new Date());
			organInfoVO.setOrganCode(rowOrgan[3]);
			organInfoVO.setOperator("admin");
			organInfoVO.setOrganPid(rowOrgan[4]);
			organInfoVO.setStatus("1");
			entities.add(organInfoVO);
		}
		sqlToyCRUDService.saveOrUpdateAll(entities);
		// 使用此简单方法，前提是nodeRoute\nodeLevel\isLeaf 等字段
		// 参数:{对象实体提供主键值,父节点字段名称}
		OrganInfoVO entity = new OrganInfoVO();
		// 设置主键值,表示只对organId=1记录以及以下节点进行节点路径补全
		entity.setOrganId("1");
		entity.setOrganPid("-1");
		sqlToyCRUDService.wrapTreeTableRoute(entity, "ORGAN_PID",-1);
	}
}
