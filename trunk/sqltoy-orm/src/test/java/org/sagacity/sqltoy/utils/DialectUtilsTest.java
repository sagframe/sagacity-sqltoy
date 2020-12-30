package org.sagacity.sqltoy.utils;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.config.model.SqlParamsModel;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;

/**
 * 增加对带问号查询转命名参数模式的校验测试
 * 
 * @author zhong
 *
 */
public class DialectUtilsTest {

	@Test
	public void testConvertQuesToNamed() {
		String sql = "select *,' and ? ' from table where a=' ? ' and b in(?,?,?) and c like ? and d>? and m<\"?\") or a>?";
		SqlParamsModel unifySqlParams = DialectUtils.convertParamsToNamed(sql, 0);
		System.err.println(unifySqlParams.getSql());
		System.err.println("paramCnt=" + unifySqlParams.getParamCnt());
		for (String s : unifySqlParams.getParamsName()) {
			System.err.println("param=" + s);
		}
		sql = "select *,' and ? ' from table where a=' ? ' ";
		unifySqlParams = DialectUtils.convertParamsToNamed(sql, 0);
		System.err.println(unifySqlParams.getSql());
		System.err.println("paramCnt=" + unifySqlParams.getParamCnt());
//		sql = "select * from table where a=' ? ' and b is ? and c like ? and m between ? and ?";
//		unifySqlParams = DialectUtils.convertParamsToNamed(sql, 0);
//		System.err.println(unifySqlParams.getSql());
//		System.err.println("paramCnt=" + unifySqlParams.getParamCnt());
//
//		sql = "update table set a.nnn=?,xxx=?_ where t.xxx=\\?";
//		unifySqlParams = DialectUtils.convertParamsToNamed(sql, 0);
//		System.err.println(unifySqlParams.getSql());
//		System.err.println("paramCnt=" + unifySqlParams.getParamCnt());
////
//		sql = "update table set a.nnn=?,xxx=? where t.xxx=?;";
//		unifySqlParams = DialectUtils.convertParamsToNamed(sql, 0);
//		System.err.println(unifySqlParams.getSql());
//		System.err.println("paramCnt=" + unifySqlParams.getParamCnt());
	}

}
