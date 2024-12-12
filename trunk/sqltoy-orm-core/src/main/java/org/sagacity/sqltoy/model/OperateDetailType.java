package org.sagacity.sqltoy.model;

/**
 * 所有操作类型的枚举
 */
public enum OperateDetailType {

	executeSql("executeSql"), executeSqlSingleTableDelete("executeSqlSingleTableDelete"),
	executeSqlSingleTableUpdate("executeSqlSingleTableUpdate"), isUnique("isUnique"),
	getRandomResult("getRandomResult"), getRandomResultSingleTable("getRandomResultSingleTable"),
	wrapTreeTableRoute("wrapTreeTableRoute"), findSkipTotalCountPage("findSkipTotalCountPage"),
	findSkipTotalCountPageSingleTable("findSkipTotalCountPageSingleTable"), findPage("findPage"),
	findPageSingleTable("findPageSingleTable"), findTop("findTop"), findTopSingleTable("findTopSingleTable"),
	findByQuery("findByQuery"), findByQuerySingleTable("findByQuerySingleTable"), getCount("getCount"),
	getCountSingleTable("getCountSingleTable"), saveOrUpdate("saveOrUpdate"), saveOrUpdateAll("saveOrUpdateAll"),
	saveAllIgnoreExist("saveAllIgnoreExist"), load("load"), loadAll("loadAll"), save("save"), saveAll("saveAll"),
	update("update"), updateSaveFetch("updateSaveFetch"), updateAll("updateAll"), delete("delete"),
	deleteAll("deleteAll"),

	updateFetch("updateFetch"), updateFetchSingleTable("updateFetchSingleTable"), executeStore("executeStore"),
	fetchStream("fetchStream"), fetchStreamSingleTable("fetchStreamSingleTable"),

	batchUpdate("batchUpdate");

	private final String value;

	private OperateDetailType(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}

	@Override
	public String toString() {
		return value;
	}
}
