package ai.libs.sqlrest.supplier;

import ai.libs.sqlrest.IServerConfig;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultConnectionSupplier {

    private final static Logger logger = LoggerFactory.getLogger(DefaultConnectionSupplier.class);

    public static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    private Map<String,MysqlDataSource> dataSourceMap = new ConcurrentHashMap<>();

    public DefaultConnectionSupplier() {
    }

    private DataSource getDataSource(final String user, final String password, final String database) {
        return dataSourceMap.computeIfAbsent(database, d -> {
            String host = SERVER_CONFIG.getDBHost();
            boolean ssl = SERVER_CONFIG.getDBPropUseSsl();
            String url = "jdbc:mysql://" +
                    host + "/" + database +
                    ((ssl) ? "?verifyServerCertificate=false&requireSSL=true&useSSL=true" : "");
            MysqlDataSource ds = new MysqlDataSource();
            ds.setURL(url);
            ds.setUser(user);
            ds.setPassword(password);
            try {
                ds.setRollbackOnPooledClose(false);
                ds.setAllowMultiQueries(false);
                ds.setUseCompression(SERVER_CONFIG.getDBPropUseCompression());
                ds.setDefaultFetchSize(SERVER_CONFIG.getDBPropFetchSize());
                ds.setUseServerPrepStmts(SERVER_CONFIG.getDBPropUseServerSidePrepStmts());
                ds.setCachePrepStmts(SERVER_CONFIG.getDBPropCachePrepStmts());
                ds.setPrepStmtCacheSize(SERVER_CONFIG.getDBPropCachePrepStmtsSize());
                ds.setPrepStmtCacheSqlLimit(SERVER_CONFIG.getDBPropCachePrepStmtsSqlLimit());
                ds.setCacheServerConfiguration(SERVER_CONFIG.getDBPropCacheServerConf());
                ds.setUseReadAheadInput(SERVER_CONFIG.getDBPropReadAheadInput());
                ds.setUseUnbufferedInput(SERVER_CONFIG.getDPPropUseUnbufferedInput());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return ds;
        });
    }

    public Connection get(String user, String password, String database) throws SQLException {
        int tries = 0;
        while(tries < 3) {
            try {
                DataSource dataSource = getDataSource(user, password, database);
                Connection connect = dataSource.getConnection();
                return connect;
            } catch (Exception e) {
                tries++;
                if(tries < 3)
                    logger.error("Connection to server {} failed with JDBC driver {} " +
                            "(attempt {} of 3)," +
                            " waiting 3 seconds before trying again.", SERVER_CONFIG.getDBHost(), "mysql", tries, e);
                else
                    throw new SQLException(e);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    logger.error(
                            "SQLAdapter got interrupted while trying to establish a connection to the database.", e1);
                    break;
                }
            }
        }
        logger.error("Quitting execution as no database connection could be established");
        return null;
    }


}
