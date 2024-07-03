/**
 *
 */
package org.sagacity.sqltoy.plugins.ddl;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.config.EntityManager;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.impl.*;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.sagacity.sqltoy.utils.FileUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @project sagacity-sqltoy
 * @description 数据库表脚本创建、更新等操作
 * @author zhongxuchen
 * @version v1.0, Date:2023年7月13日
 * @modify 2023年7月13日, 修改说明
 */
public class DDLFactory {
    /**
     * 定义日志
     */
    private final static Logger logger = LoggerFactory.getLogger(DDLFactory.class);

    private static String NEWLINE = "\r\n";

    private static DialectDDLGenerator getGenerator(Integer dbType) {
        DialectDDLGenerator generator = null;
        switch (dbType) {
            case DBType.MYSQL:
            case DBType.MYSQL57: {
                generator = new MySqlDDLGenerator();
                break;
            }
            case DBType.GAUSSDB:
            case DBType.MOGDB:
            case DBType.POSTGRESQL:
            case DBType.POSTGRESQL15: {
                generator = new PostgreSqlDDLGenerator();
                break;
            }
            case DBType.ORACLE:
            case DBType.ORACLE11:
            case DBType.DM: {
                generator = new OracleDDLGenerator();
                break;
            }
            case DBType.SQLSERVER: {
                generator = new SqlServerDDLGenerator();
                break;
            }
            case DBType.H2: {
                generator = new H2DDLGenerator();
                break;
            }
            default:
                generator = new DefaultDDLGenerator();
        }
        return generator;
    }

    /**
     * @TODO 提供动态根据POJO产生数据库表创建的脚本文件
     * @param scanPackages
     * @param saveFile
     * @param dbType
     * @param schema
     * @param dialectDDLGenerator 自己指定ddl创建器
     * @throws Exception
     */
    public static void createSqlFile(String[] scanPackages, String saveFile, Integer dbType, String schema,
                                     DialectDDLGenerator dialectDDLGenerator) throws Exception {
        EntityManager entityManager = new EntityManager();
        entityManager.setPackagesToScan(scanPackages);
        entityManager.initialize(null);
        ConcurrentHashMap<String, EntityMeta> entitysMetaMap = entityManager.getAllEntities();
        if (entitysMetaMap == null || entitysMetaMap.isEmpty()) {
            logger.warn("没有扫描到具体的实体对象,请检查scanPackages是否正确!");
            return;
        }
        List<EntityMeta> allTableEntities = DDLUtils.sortTables(entitysMetaMap);
        List<TableMeta> tableMetas = new ArrayList<>();
        for (EntityMeta entityMeta : allTableEntities) {
            tableMetas.add(DDLUtils.wrapTableMeta(entityMeta));
        }
        // 写文件
        if (!tableMetas.isEmpty()) {
            DialectDDLGenerator generator = (dialectDDLGenerator == null) ? getGenerator(dbType) : dialectDDLGenerator;
            // 先删除文件
            FileUtil.delFile(saveFile);
            // sqlserver从2017开始支持;符号
            String splitSign = ";";
            int index = 0;
            String tableSql;
            logger.debug("一共有:[" + tableMetas.size() + "]个实体对象需生成建表语句!");
            for (TableMeta tableMeta : tableMetas) {
                logger.debug("begin generate table:[" + tableMeta.getTableName() + "] ddl sql!");
                tableSql = generator.createTableSql(tableMeta, schema, dbType);
                if (tableSql != null && !tableSql.equals("")) {
                    if (index > 0) {
                        tableSql = splitSign.concat(NEWLINE).concat(NEWLINE).concat(tableSql);
                    }
                    FileUtil.appendFileByStream(saveFile, tableSql);
                    index++;
                }
            }
        }
    }

    /**
     * @TODO 动态向数据库创建表结构
     * @param sqlToyContext
     * @param entitysMetaMap
     * @param dataSource
     */
    public static void createDDL(SqlToyContext sqlToyContext, ConcurrentHashMap<String, EntityMeta> entitysMetaMap,
                                 DataSource dataSource) {
        if (entitysMetaMap == null || entitysMetaMap.isEmpty()) {
            return;
        }
        try {
            List<EntityMeta> allTableEntities = DDLUtils.sortTables(entitysMetaMap);
            for (EntityMeta entityMeta : allTableEntities) {
                DataSourceUtils.processDataSource(sqlToyContext, dataSource, new DataSourceCallbackHandler() {
                    @Override
                    public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
                        // 判断表是否已经存在
                        ResultSet rs = null;
                        String tableName = null;
                        try {
                            rs = conn.getMetaData().getTables(conn.getCatalog(),
                                    (entityMeta.getSchema() == null) ? conn.getSchema() : entityMeta.getSchema(),
                                    entityMeta.getTableName(), new String[]{"TABLE"});
                            while (rs.next()) {
                                tableName = rs.getString("TABLE_NAME");
                                break;
                            }
                        } catch (Exception e) {

                        } finally {
                            if (rs != null) {
                                rs.close();
                            }
                        }
                        // 数据库不存在当前表，则进行创建
                        if (tableName == null) {
                            TableMeta tableMeta = DDLUtils.wrapTableMeta(entityMeta);
                            logger.debug("开始创建表:[" + tableMeta.getTableName() + "]的表结构!");
                            DialectDDLGenerator dialectDDLGenerator = (sqlToyContext.getDialectDDLGenerator() == null)
                                    ? getGenerator(dbType)
                                    : sqlToyContext.getDialectDDLGenerator();
                            String createSql = dialectDDLGenerator.createTableSql(tableMeta, conn.getSchema(), dbType);
                            try {
                                if (createSql != null && !createSql.equals("")) {
                                    SqlUtil.executeSql(null, createSql, null, null, conn, dbType, null, true);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            logger.debug("表:[" + tableName + "]在数据库中已经存在!");
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
