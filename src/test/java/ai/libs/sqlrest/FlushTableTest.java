package ai.libs.sqlrest;

import ai.libs.jaicore.basic.kvstore.KVStore;
import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.SQLQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aeonbits.owner.ConfigCache;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Test
    public void killOtherService() {
        try {
            Process getPidOtherService = Runtime.getRuntime().exec("lsof -t -i :8080");
            getPidOtherService.waitFor();
            if(getPidOtherService.exitValue() != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(getPidOtherService.getErrorStream()));
                String errLines = reader.lines().collect(Collectors.joining("\n"));
                throw new RuntimeException("Couldn't get pid of the other service: " + errLines);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(getPidOtherService.getInputStream()));
            String otherProcessIDs = reader.lines().collect(Collectors.joining(" "));
            Process killOtherService = Runtime.getRuntime().exec("kill " + otherProcessIDs);
            killOtherService.waitFor();
            if(killOtherService.exitValue() != 0) {
                BufferedReader errReader = new BufferedReader(new InputStreamReader(killOtherService.getErrorStream()));
                String errLines = errReader.lines().collect(Collectors.joining("\n"));
                BufferedReader outReader = new BufferedReader(new InputStreamReader(killOtherService.getInputStream()));
                String outLines = outReader.lines().collect(Collectors.joining("\n"));
                System.out.println(String.format("Out: %s\nErr: %s", outLines, errLines));
                throw new RuntimeException("Cannot destroy other process listening on port 8080");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error trying to destroy other process listening on port 8080.", e);
        }
    }

    @Test
    public void slowRequest() throws IOException {
        HttpPost httpPost = new HttpPost("http://localhost:8080/query");
        ObjectMapper mapper = new ObjectMapper();
        SQLQuery queryObj = new SQLQuery("token_d0", String.format("SELECT * " +
                "FROM %s " +
                "ORDER BY RAND() LIMIT %d", "t0", 1000));
        String postBody = mapper.writeValueAsString(queryObj);
        httpPost.setEntity(new StringEntity(postBody));
        httpPost.setHeader("Content-Type", "application/json");
        try(CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response2 = httpClient.execute(httpPost)) {
                assert response2.getStatusLine().getStatusCode() == 200;
                List<KVStore> o = mapper.readValue(response2.getEntity().getContent(), mapper.getTypeFactory().constructCollectionType(List.class, KVStore.class));
                assert o != null;
            }
        }
    }
}
