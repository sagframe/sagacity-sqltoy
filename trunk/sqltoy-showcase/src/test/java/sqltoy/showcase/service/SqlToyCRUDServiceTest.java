/**
 * 
 */
package sqltoy.showcase.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import sqltoy.showcase.CommonUtils;
import sqltoy.showcase.sagacity.vo.DictDetailVO;
import sqltoy.showcase.sagacity.vo.DictTypeVO;
import sqltoy.showcase.system.vo.BigLobVO;

/**
 * @project sqltoy-showcase
 * @description
 *              <p>
 *              请在此说明类的功能
 *              </p>
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlToyCRUDServiceTest.java,Revision:v1.0,Date:2015年10月29日
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring-context.xml" })
public class SqlToyCRUDServiceTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;

	@Autowired
	private SqlToyLazyDao sqlToyLazyDao;

	@Test
	public void testSaveCascade() {
		DictTypeVO dictTypeVO = new DictTypeVO();
		dictTypeVO.setDictTypeCode("POST_TYPE");
		dictTypeVO.setDictTypeName("岗位类别");
		dictTypeVO.setOperateDate(DateUtil.getNowTime());
		dictTypeVO.setOperator("admin");
		dictTypeVO.setDataSize(5);
		dictTypeVO.setDataType(1);
		dictTypeVO.setStatus("1");

		List dictDetailVOs = new ArrayList();
		DictDetailVO detailVO = new DictDetailVO();
		detailVO.setDictKey("1");
		detailVO.setDictName("staff");
		detailVO.setStatus("1");
		detailVO.setShowIndex(1);
		detailVO.setUpdateBy("0001");
		detailVO.setUpdateTime(DateUtil.getTimestamp(null));

		dictDetailVOs.add(detailVO);

		DictDetailVO detailVO1 = new DictDetailVO();
		detailVO1.setDictKey("2");
		detailVO1.setDictName("master");
		detailVO1.setStatus("1");
		detailVO1.setShowIndex(2);
		detailVO1.setUpdateTime(DateUtil.getTimestamp(null));
		detailVO1.setUpdateBy("0001");
		dictDetailVOs.add(detailVO1);

		dictTypeVO.setDictDetailVOs(dictDetailVOs);

		// 先删除
		sqlToyCRUDService.delete(dictTypeVO);

		sqlToyCRUDService.save(dictTypeVO);
	}

	@Test
	public void testSaveOrUpdate() {
		DictTypeVO dictTypeVO = new DictTypeVO();
		dictTypeVO.setDictTypeCode("POST_TYPE");
		// 换一个主键值,则变为保存
		// dictTypeVO.setDictTypeCode("POST_TYPE");
		dictTypeVO.setDictTypeName("岗位类别1");
		dictTypeVO.setOperateDate(DateUtil.getNowTime());
		dictTypeVO.setOperator("admin");
		dictTypeVO.setDataType(0);
		dictTypeVO.setStatus("1");
		sqlToyCRUDService.saveOrUpdate(dictTypeVO, null);
	}

	@Test
	public void testUpdate() {
		DictTypeVO dictTypeVO = new DictTypeVO();
		dictTypeVO.setDictTypeCode("POST_TYPE");
		dictTypeVO.setDictTypeName("岗位类别3");
		dictTypeVO.setOperateDate(DateUtil.getNowTime());
		dictTypeVO.setOperator("admin");
		dictTypeVO.setDataType(0);
		dictTypeVO.setStatus("1");
		sqlToyCRUDService.update(dictTypeVO, null);
	}

	@Test
	public void testSaveOrUpdateAll() {
		List entities = new ArrayList();
		DictTypeVO dictTypeVO = new DictTypeVO();
		dictTypeVO.setDictTypeCode("POST_TYPE");
		dictTypeVO.setDictTypeName("岗位类别更新");
		// dictTypeVO.setOperateDate(DateUtil.getNowTime());
		// dictTypeVO.setOperator("admin");
		dictTypeVO.setDataType(0);
		dictTypeVO.setStatus("1");
		entities.add(dictTypeVO);

		DictTypeVO dictTypeVO1 = new DictTypeVO();
		dictTypeVO1.setDictTypeCode("POST_TYPE2");
		dictTypeVO1.setDictTypeName("岗位类别2");
		// dictTypeVO1.setOperateDate(DateUtil.getNowTime());
		// dictTypeVO1.setOperator("admin");
		dictTypeVO1.setDataType(0);
		dictTypeVO1.setStatus("1");
		entities.add(dictTypeVO1);

		sqlToyCRUDService.saveOrUpdateAll(entities, null, new ReflectPropertyHandler() {

			@Override
			public void process() {
				this.setValue("status", "2");

			}
		});
	}

	@Test
	public void testLoad() {
		DictTypeVO dictTypeVO = new DictTypeVO();
		dictTypeVO.setDictTypeCode("POST_TYPE");
		DictTypeVO entity = (DictTypeVO) sqlToyCRUDService.load(dictTypeVO);
		Assert.assertEquals("验证岗位字段", entity.getDictTypeName(), "岗位类别");
	}

	/**
	 * 测试通过主表的主键级联查询加载字表明细记录(子表是通过一次查询,不是根据每个主键做一次子表查询,因此效率非常高)
	 * 
	 * @throws Exception
	 */
	@Test
	public void testLoadAllCascade() throws Exception {
		List entities = new ArrayList();
		entities.add(new DictTypeVO("POST_TYPE"));
		entities.add(new DictTypeVO("POST_TYPE1"));

		List<DictTypeVO> result = sqlToyLazyDao.loadAllCascade(entities);

		System.err.println(result.get(0).getDictDetailVOs().size());
	}

	// 单个对象删除
	@Test
	public void testDelete() {
		DictTypeVO dictTypeVO = new DictTypeVO();
		dictTypeVO.setDictTypeCode("POST_TYPE1");
		sqlToyCRUDService.delete(dictTypeVO);
	}

	// 展示批量删除操作
	@Test
	public void testDeleteAll() {
		List<Serializable> entities = new ArrayList<Serializable>();
		for (int i = 0; i < 5; i++) {
			BigLobVO toyLobVO = new BigLobVO(i);
			entities.add(toyLobVO);
		}
		sqlToyCRUDService.deleteAll(entities);
	}

	// 展示blob和clob类型数据的存储
	@Test
	public void testSaveByIdentity() throws Exception {
		BigLobVO toyLobVO = new BigLobVO();
		toyLobVO.setComments("测试");
		toyLobVO.setStaffName("张三");
		toyLobVO.setCreateDate(new Date());
		toyLobVO.setStaffPhoto(CommonUtils
				.getBytes(CommonUtils.getFileInputStream("classpath:sqltoy/showcase/service/blob_image.png")));
		// identity主键，存储之后返回主键值
		System.err.println(sqlToyCRUDService.save(toyLobVO));
	}

	// 验证批量identity主键(entities中的主键值在保存后返回)
	@Test
	public void testSaveAllByIdentity() throws Exception {
		List<BigLobVO> entities = new ArrayList<BigLobVO>();
		for (int i = 0; i < 10; i++) {
			BigLobVO sequenceVO = new BigLobVO();
			sequenceVO.setCreateDate(new Date());
			sequenceVO.setComments("注释" + i);
			sequenceVO.setStaffName("name" + i);
			sequenceVO.setStaffPhoto(CommonUtils
					.getBytes(CommonUtils.getFileInputStream("classpath:sqltoy/showcase/service/blob_image.png")));
			entities.add(sequenceVO);
		}
		sqlToyCRUDService.saveAll(entities, null);
	}
}
