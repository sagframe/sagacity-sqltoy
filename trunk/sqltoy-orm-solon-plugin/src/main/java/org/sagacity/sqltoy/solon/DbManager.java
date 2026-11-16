package org.sagacity.sqltoy.solon;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.noear.solon.core.AppContext;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.dao.LightDao;
import org.sagacity.sqltoy.dao.SqlToyLazyDao;
import org.sagacity.sqltoy.service.SqlToyCRUDService;
import org.sagacity.sqltoy.solon.dao.impl.LightDaoImpl;
import org.sagacity.sqltoy.solon.dao.impl.SqlToyLazyDaoImpl;
import org.sagacity.sqltoy.solon.service.impl.SqlToyCRUDServiceForSolon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solon的静态Db门面:内部持有JVM级单例SqlToyContext以及按DataSource缓存的DAO/Service实例。
 * <p>
 * update 2026-11-15 设计前提是"一个JVM一个SqlToyContext"(@Db注入、DbManager.xxx均走此全局入口);
 * 同一JVM存在多个Solon SqlToyContext时属于不支持的用法:后setContext的上下文生效,且切换时会清空
 * 按DataSource缓存的DAO/Service(避免继续复用绑定旧上下文的实例)并告警,但已经注入到业务字段中的
 * 旧DAO不会被替换——多上下文场景请直接从各自上下文获取dao,不要使用本全局门面。
 * </p>
 * 
 * @author 夜の孤城
 * @since 1.5
 */
public class DbManager {
	private static final Logger logger = LoggerFactory.getLogger(DbManager.class);

	private static final Map<DataSource, SqlToyLazyDao> daoMap = new ConcurrentHashMap<>();
	private static final Map<DataSource, LightDao> lightDaoMap = new ConcurrentHashMap<>();
	private static final Map<DataSource, SqlToyCRUDService> serviceMap = new ConcurrentHashMap<>();
	private static volatile SqlToyContext context;

	public static void setContext(SqlToyContext context) {
		SqlToyContext previous = DbManager.context;
		DbManager.context = context;
		if (previous != null && previous != context) {
			// update 2026-11-15 多上下文切换:清空按DataSource缓存的实例,避免新上下文继续复用旧上下文的DAO
			logger.warn(
					"DbManager is a JVM-level facade for a single SqlToyContext, but the context was switched ({} -> {}); cached dao/service instances will be rebuilt with the new context. Please get dao from each SqlToyContext directly if you use multiple contexts in one JVM!",
					previous, context);
			daoMap.clear();
			lightDaoMap.clear();
			serviceMap.clear();
		}
	}

	public static SqlToyLazyDao getDao(DataSource dataSource) {
		SqlToyLazyDao dao = daoMap.get(dataSource);
		if (dao == null) {
			SqlToyLazyDaoImpl sqlToyLazyDao = new SqlToyLazyDaoImpl();
			sqlToyLazyDao.setDataSource(dataSource);
			sqlToyLazyDao.setSqlToyContext(context);
			SqlToyLazyDao prev = daoMap.putIfAbsent(dataSource, sqlToyLazyDao);
			dao = (prev != null) ? prev : sqlToyLazyDao;
		}
		return dao;
	}

	public static LightDao getLightDao(DataSource dataSource) {
		LightDao dao = lightDaoMap.get(dataSource);
		if (dao == null) {
			LightDaoImpl lightDao = new LightDaoImpl();
			lightDao.setDataSource(dataSource);
			lightDao.setSqlToyContext(context);
			LightDao prev = lightDaoMap.putIfAbsent(dataSource, lightDao);
			dao = (prev != null) ? prev : lightDao;
		}
		return dao;
	}

	public static SqlToyCRUDService getService(AppContext context, DataSource dataSource) {
		SqlToyCRUDService service = serviceMap.get(dataSource);
		if (service == null) {
			SqlToyCRUDServiceForSolon crudService = context.beanMake(SqlToyCRUDServiceForSolon.class).get();
			// (jre8同步适配:本模块SqlToyCRUDServiceForSolon保持setSqlToyLazyDao形态,与6.0的setLightDao签名差异)
			crudService.setSqlToyLazyDao(getDao(dataSource));
			SqlToyCRUDService prev = serviceMap.putIfAbsent(dataSource, crudService);
			service = (prev != null) ? prev : crudService;
		}
		return service;
	}

	public static Map<DataSource, SqlToyCRUDService> getServiceMap() {
		return Collections.unmodifiableMap(serviceMap);
	}
}
