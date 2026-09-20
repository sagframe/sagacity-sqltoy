package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description MPP分析型数据库表引擎元数据模型(对应@MppTable注解)
 * @author zhongxuchen
 * @version v1.0,Date:2026-09-19
 * @modify Date:2026-09-19,修改说明
 */
public class MppTableMeta implements Serializable {

	private static final long serialVersionUID = 6283192745382901047L;

	/**
	 * 引擎:ClickHouse为MergeTree等;Doris/StarRocks为OLAP
	 */
	private String engine;

	/**
	 * 引擎参数,如ReplacingMergeTree的版本列
	 */
	private String engineArgs;

	/**
	 * 键模型(Doris/StarRocks):UNIQUE/PRIMARY/DUPLICATE/AGGREGATE
	 */
	private String keyModel;

	/**
	 * 排序键列
	 */
	private String[] orderBy;

	/**
	 * 分桶列
	 */
	private String[] distributedBy;

	/**
	 * 分桶数,-1表示未指定
	 */
	private int buckets = -1;

	/**
	 * 表属性("key=value"形式)
	 */
	private String[] properties;

	public MppTableMeta(String engine, String engineArgs, String keyModel, String[] orderBy, String[] distributedBy,
			int buckets, String[] properties) {
		this.engine = engine;
		this.engineArgs = engineArgs;
		this.keyModel = keyModel;
		this.orderBy = orderBy;
		this.distributedBy = distributedBy;
		this.buckets = buckets;
		this.properties = properties;
	}

	public String getEngine() {
		return engine;
	}

	public void setEngine(String engine) {
		this.engine = engine;
	}

	public String getEngineArgs() {
		return engineArgs;
	}

	public void setEngineArgs(String engineArgs) {
		this.engineArgs = engineArgs;
	}

	public String getKeyModel() {
		return keyModel;
	}

	public void setKeyModel(String keyModel) {
		this.keyModel = keyModel;
	}

	public String[] getOrderBy() {
		return orderBy;
	}

	public void setOrderBy(String[] orderBy) {
		this.orderBy = orderBy;
	}

	public String[] getDistributedBy() {
		return distributedBy;
	}

	public void setDistributedBy(String[] distributedBy) {
		this.distributedBy = distributedBy;
	}

	public int getBuckets() {
		return buckets;
	}

	public void setBuckets(int buckets) {
		this.buckets = buckets;
	}

	public String[] getProperties() {
		return properties;
	}

	public void setProperties(String[] properties) {
		this.properties = properties;
	}
}
