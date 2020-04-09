/**
 * 
 */
package com.sagframe.sqltoy.showcase;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.utils.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.alibaba.fastjson.JSON;
import com.sagframe.sqltoy.SqlToyApplication;
import com.sagframe.sqltoy.showcase.vo.BigintTableVO;
import com.sagframe.sqltoy.showcase.vo.IdentityTableVO;
import com.sagframe.sqltoy.showcase.vo.SequenceTableVO;

/**
 * @project sqltoy-showcase
 * @description 请在此说明类的功能
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:PrimaryKeyTest.java,Revision:v1.0,Date:2020年2月6日
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SqlToyApplication.class)
public class PrimaryKeyTest {
	@Autowired
	private SqlToyCRUDService sqlToyCRUDService;
	@Resource(name = "sqlToyLazyDao")
	private SqlToyLazyDao sqlToyLazyDao;

	/**
	 * 数据库identity模式
	 */
	@Test
	public void testIdentity() {
		IdentityTableVO vo = new IdentityTableVO();
		vo.setName("测试");
		vo.setCreateTime(DateUtil.getTimestamp(null));
		System.err.println(sqlToyCRUDService.save(vo));
	}

	/**
	 * 数据库sequence模式
	 */
	@Test
	public void testSequence() {
		SequenceTableVO vo = new SequenceTableVO();
		vo.setName("测试");
		vo.setCreateTime(DateUtil.getTimestamp(null));
		System.err.println(sqlToyCRUDService.save(vo));
	}

	@Test
	public void testLoadSequence() {
		SequenceTableVO vo = sqlToyCRUDService.load(new SequenceTableVO(BigDecimal.ONE));

		System.err.println(JSON.toJSONString(vo));
	}

	@Test
	public void testSaveOrUpdate() {
		List<SequenceTableVO> entities = new ArrayList<SequenceTableVO>();
		SequenceTableVO vo = new SequenceTableVO();
		vo.setId(BigDecimal.ONE);
		vo.setName("测试变更");
		vo.setCreateTime(DateUtil.getTimestamp(null));
		entities.add(vo);

		SequenceTableVO vo1 = new SequenceTableVO();
		vo1.setName("新建测试");
		vo1.setCreateTime(DateUtil.getTimestamp(null));
		entities.add(vo1);

		System.err.println(sqlToyCRUDService.saveOrUpdateAll(entities));
	}

	@Test
	public void testSaveIgnoreExist() {
		List<SequenceTableVO> entities = new ArrayList<SequenceTableVO>();
		SequenceTableVO vo = new SequenceTableVO();
		vo.setId(BigDecimal.ONE);
		vo.setName("测试变更");
		vo.setCreateTime(DateUtil.getTimestamp(null));
		entities.add(vo);

		SequenceTableVO vo1 = new SequenceTableVO();
		vo1.setName("新建测试2");
		vo1.setCreateTime(DateUtil.getTimestamp(null));
		entities.add(vo1);

		System.err.println(sqlToyCRUDService.saveAllIgnoreExist(entities));
	}

	@Test
	public void testBigIntSnowflake() {
		BigintTableVO vo = new BigintTableVO();
		vo.setName("测试");
		sqlToyCRUDService.save(vo);
	}

	@Test
	public void testBigIntSnowflake1() {
		BigintTableVO vo = sqlToyCRUDService.load(new BigintTableVO(new BigInteger("697757119726620672")));
		System.err.println(JSON.toJSONString(vo));
	}
}
