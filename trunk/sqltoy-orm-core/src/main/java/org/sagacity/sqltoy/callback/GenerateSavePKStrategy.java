package org.sagacity.sqltoy.callback;

import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.dialect.model.SavePKStrategy;

/**
 * @project sagacity-sqltoy
 * @description 定义产生主键策略的抽象类,用于反调方式进行自定义灵活实现
 * @author zhongxuchen
 * @version v1.0,Date:2015-03-19
 */
public abstract class GenerateSavePKStrategy {
	/**
	 * 提供不同数据库方言insert数据时主键产生策略
	 * 
	 * @param entityMeta
	 * @return
	 */
	public abstract SavePKStrategy generate(EntityMeta entityMeta);
}
