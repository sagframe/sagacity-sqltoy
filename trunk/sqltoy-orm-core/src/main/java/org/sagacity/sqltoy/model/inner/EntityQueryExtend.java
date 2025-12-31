/**
 * 
 */
package org.sagacity.sqltoy.model.inner;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.IgnoreCaseSet;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.ParamsFilter;

/**
 * @project sqltoy-orm
 * @description 针对EntityQuery构造一个存放参数的内部类，避免EntityQuery使用时带出大量的get方法
 * @author zhongxuchen
 * @version v1.0,Date:2020-8-1
 */
public class EntityQueryExtend implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5446320176141577000L;

	/**
	 * jdbc 查询时默认加载到内存中的记录数量 -1表示不设置，采用数据库默认的值
	 */
	public int fetchSize = -1;

	/**
	 * jdbc查询最大返回记录数量
	 */
	public int maxRows = -1;

	/**
	 * 条件语句
	 */
	public String where;

	/**
	 * 参数名称
	 */
	public String[] names;

	/**
	 * 参数值
	 */
	public Object[] values;

	/**
	 * 查询哪些字段
	 */
	public String[] fields;

	public String groupBy;

	public String having;

	public boolean distinct = false;

	/**
	 * 不参与查询的字段
	 */
	public Set<String> notSelectFields;

	/**
	 * 数据源
	 */
	public DataSource dataSource;

	/**
	 * 锁类型
	 */
	public LockMode lockMode;

	/**
	 * 空白字符转为null，默认为true
	 */
	public boolean blankToNull = true;

	/**
	 * 动态增加缓存翻译配置
	 */
	public List<Translate> translates = new ArrayList<Translate>();

	/**
	 * 动态组织的order by 排序
	 */
	public LinkedHashMap<String, String> orderBy = new LinkedHashMap<String, String>();

	/**
	 * 动态设置filters
	 */
	public List<ParamsFilter> paramFilters = new ArrayList<ParamsFilter>();

	/**
	 * 对字段进行安全脱敏
	 */
	public LinkedHashMap<String, SecureMask> secureMask = new LinkedHashMap<String, SecureMask>();
	
	/**
	 * 解密的列
	 */
	public IgnoreCaseSet decryptColumns=new IgnoreCaseSet();

	// 分库策略配置
	public ShardingStrategyConfig dbSharding;

	// 分表策略配置
	public ShardingStrategyConfig tableSharding;

	/**
	 * 分页优化模型
	 */
	public PageOptimize pageOptimize;

	/**
	 * -1:普通查询; 0:top;1:取随机记录
	 */
	public int pickType = -1;

	/**
	 * 取记录数量
	 */
	public double pickSize;

	/**
	 * 执行时是否输出sql 日志
	 */
	public Boolean showSql;

}
