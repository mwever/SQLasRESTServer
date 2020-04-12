package ai.libs.sqlrest.supplier;

import ai.libs.jaicore.db.sql.SQLAdapter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

class BaseConnectionHandler {

    private Connection connection;

    private DefaultConnectionSupplier supplier;

    private String user, passwd, databaseName;

    private long timestampOfLastAction = Long.MIN_VALUE;

    private long connectionTimeout = TimeUnit.MINUTES.toMillis(5);

    public BaseConnectionHandler(DefaultConnectionSupplier supplier, String user1, String passwd1, String databaseName1) {
        this.supplier = supplier;
        this.user = user1;
        this.passwd = passwd1;
        this.databaseName = databaseName1;
    }

    public synchronized Connection getConnection() throws SQLException {
        /*
         * Close connection if the last access to the connection was 5 minutes ago  or earlier.
         */
        long currentTime = System.currentTimeMillis();
        if(timestampOfLastAction + connectionTimeout < currentTime) {
            closeConnection();
        }

        /*
         * Create a connection when first accessed or in case of a timeout.
         */
        if(connection == null) {
            createConnection();
        }

        timestampOfLastAction = currentTime;
        return connection;
    }

    private void createConnection() throws SQLException {
        connection = supplier.get(user, passwd, databaseName);
        if(connection == null) {
            throw new SQLException("Connection supplier returned null.");
        }
    }

    public void closeConnection() throws SQLException {
        if(connection != null) {
            connection.close();
            connection = null;
        }
    }

}
