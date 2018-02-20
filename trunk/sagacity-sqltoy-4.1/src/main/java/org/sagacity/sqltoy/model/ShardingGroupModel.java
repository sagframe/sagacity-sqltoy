/**
 * 
 */
package org.sagacity.sqltoy.model;

import java.io.Serializable;
import java.util.List;

/**
 * @project sagacity-sqltoy4.0
 * @description 批量数据操作分组分库分表模型
 * @author chenrenfei <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:ShardingGroupModel.java,Revision:v1.0,Date:2017年11月3日
 */
public class ShardingGroupModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1188114638866564391L;

	private String key;

	private ShardingModel shardingModel;

	private List entities;

	/**
	 * @return the key
	 */
	public String getKey() {
		return key;
	}

	/**
	 * @param key
	 *            the key to set
	 */
	public void setKey(String key) {
		this.key = key;
	}

	/**
	 * @return the shardingModel
	 */
	public ShardingModel getShardingModel() {
		return shardingModel;
	}

	/**
	 * @param shardingModel
	 *            the shardingModel to set
	 */
	public void setShardingModel(ShardingModel shardingModel) {
		this.shardingModel = shardingModel;
	}

	/**
	 * @return the entities
	 */
	public List getEntities() {
		return entities;
	}

	/**
	 * @param entities
	 *            the entities to set
	 */
	public void setEntities(List entities) {
		this.entities = entities;
	}

}
