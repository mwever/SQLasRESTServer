package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.SQLQuery;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@State(Scope.Thread)
public class SQLClientState {
    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    private String userName, password, dbName;

    private SQLAdapter adapter;

    private WebClient webClient;

    private void retrieveDatabaseInfo() throws SQLException {
        SQLAdapter adminAdapter = SERVER_CONFIG.createAdminAdapter();
        List<IKVStore> res = adminAdapter.getResultsOfQuery("SELECT * " +
                "FROM experiments " +
                "WHERE experiment_token='"
                + Objects.requireNonNull(BENCHMARK_CONFIG.getBenchmarkToken())
                + "'");
        if (res.isEmpty()) {
            throw new IllegalStateException("No experiment known for the given token!");
        }
        if (res.size() > 1) {
            throw new IllegalStateException("Multiple experiments for the same token. A token must be unique!");
        }
        IKVStore connectionDescription = res.get(0);
        userName = connectionDescription.getAsString("db_user");
        password = connectionDescription.getAsString("db_passwd");
        dbName = connectionDescription.getAsString("db_name");
        adminAdapter.close();
    }

    private void createAdapter() {
        Objects.requireNonNull(userName);
        Objects.requireNonNull(password);
        Objects.requireNonNull(dbName);

        adapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
                userName, password, dbName, SERVER_CONFIG.getDBPropUseSsl());
    }

    private void createWebClient() {

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
//                        .maxInMemorySize(500 * (1 << 20))) //
                        .maxInMemorySize(-1)) // -1 : unlimited memory for fetch buffer
                .build();

        webClient = WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .baseUrl(BENCHMARK_CONFIG.getServiceHost())
                .build();
    }

    @Setup
    public void setup() throws SQLException {
        retrieveDatabaseInfo();
        createAdapter();
        createWebClient();
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getDbName() {
        return dbName;
    }

    public SQLAdapter getAdapter() {
        return adapter;
    }

    public WebClient getWebClient() {
        return webClient;
    }
}
