package ai.libs.sqlrest.supplier;

import ai.libs.jaicore.db.IDatabaseAdapter;
import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.IServerConfig;
import org.aeonbits.owner.ConfigCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DefaultConnectionSupplier {

    private final static Logger logger = LoggerFactory.getLogger(DefaultConnectionSupplier.class);

    public static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    public Connection get(String user, String password, String database) throws SQLException {
        String host = SERVER_CONFIG.getDBHost();
        boolean ssl = SERVER_CONFIG.getDBPropUseSsl();
        int tries = 0;
        do {
            try {
                Properties connectionProps = new Properties();
                connectionProps.put("user", user);
                connectionProps.put("password", password);
                String connectionString = "jdbc:mysql://" +
                        host + "/" + database +
                        ((ssl) ? "?verifyServerCertificate=false&requireSSL=true&useSSL=true" : "");
                Connection connect = DriverManager.getConnection(connectionString, connectionProps);
                return connect;
            } catch (SQLException e) {
                tries++;
                if(tries < 3)
                    logger.error("Connection to server {} failed with JDBC driver {} " +
                            "(attempt {} of 3)," +
                            " waiting 3 seconds before trying again.", host, "mysql", tries, e);
                else
                    throw e;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    logger.error(
                            "SQLAdapter got interrupted while trying to establish a connection to the database.", e1);
                    break;
                }
            }
        } while (tries < 3);
        logger.error("Quitting execution as no database connection could be established");
        return null;
    }


}
