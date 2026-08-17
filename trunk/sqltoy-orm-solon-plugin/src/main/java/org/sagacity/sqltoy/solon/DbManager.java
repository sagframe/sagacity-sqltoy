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
/**
 * @author 夜の孤城
 * @since 1.5
 * */
public class DbManager {
    private static final Map<DataSource, SqlToyLazyDao> daoMap = new ConcurrentHashMap<>();
    private static final Map<DataSource, LightDao> lightDaoMap = new ConcurrentHashMap<>();
    private static final Map<DataSource, SqlToyCRUDService> serviceMap = new ConcurrentHashMap<>();
    private static volatile SqlToyContext context;

    public static void setContext(SqlToyContext context) {
        DbManager.context = context;
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
