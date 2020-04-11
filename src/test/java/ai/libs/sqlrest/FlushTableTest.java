package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.junit.Test;

import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.Properties;

public class FlushTableTest {

    @Test
    public void testFlushCommandsThroughConnection() throws Exception {
        Connection connection = null;
        IServerConfig conf = ConfigCache.getOrCreate(IServerConfig.class);
        String connectionString = "jdbc:mysql" + "://" + conf.getDBHost() + "/" + conf.getAdminDBName();
        Properties props = new Properties();
        props.put("user", conf.getAdminDBUser());
        props.put("password", conf.getAdminDBPassword());
        try {
            connection = DriverManager.getConnection(connectionString, props);
            CallableStatement callableStatement = connection.prepareCall("FLUSH HOSTS");
            boolean execute = callableStatement.execute();
            System.out.println(String.valueOf(callableStatement.getUpdateCount()));
            ResultSet resultSet = callableStatement.getResultSet();
            if(resultSet.getWarnings().getMessage() != null) {
                throw new Exception(resultSet.getWarnings().getMessage());
            }
        } catch(Exception ex) {
            connection.close();
            throw ex;
        }
    }


    @Test
    public void testFlushCommands() throws IOException, SQLException {
        IServerConfig conf = ConfigCache.getOrCreate(IServerConfig.class);
        SQLAdapter sqlAdapter = conf.createAdminAdapter();
        List<IKVStore> query = sqlAdapter.query("FLUSH HOSTS; FLUSH TABLES;");
        assert query.isEmpty();

    }
}
