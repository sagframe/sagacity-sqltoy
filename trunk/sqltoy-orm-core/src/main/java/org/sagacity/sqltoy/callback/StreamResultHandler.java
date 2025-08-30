package org.sagacity.sqltoy.callback;

/**
 * @project sagacity-sqltoy
 * @description 定义基于流模式获取查询结果的反调
 * @author zhongxuchen
 * @version v1.0, Date:2022-7-23
 */
public interface StreamResultHandler {

	/**
	 * @TODO 开始
	 * @param columnsLabels 查询结果列标题
	 * @param columnsTypes  查询结果列对应的数据类型
	 */
	public default void start(String[] columnsLabels, String[] columnsTypes) {
	}

	/**
	 * @TODO 对行数据进行消费
	 * @param row
	 * @param rowIndex
	 */
	public default void consume(Object row, int rowIndex) {

	}

	/**
	 * 有条件存在终止行为的消费
	 * @param row
	 * @param rowIndex
	 * @return
	 */
	public default boolean doNextConsume(Object row, int rowIndex) {
		return true;
	}

	/**
	 * @TODO 流数据提取完成
	 */
	public default void end() {

	}
}
