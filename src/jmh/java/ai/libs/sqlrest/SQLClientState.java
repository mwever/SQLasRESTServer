package ai.libs.sqlrest;

import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.SQLQuery;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class SQLClientState {

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    private String userName, password, dbName;

    private SQLAdapter adapter;

    private Map<String, String[]> tokenAccessMap = new ConcurrentHashMap<>();

    private WebClient webClient;

    private void retrieveDatabaseInfo() throws SQLException {
        userName = getUserName(BENCHMARK_CONFIG.getBenchmarkToken());
        password = getPassword(BENCHMARK_CONFIG.getBenchmarkToken());
        dbName = getDbName(BENCHMARK_CONFIG.getBenchmarkToken());
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
        TcpClient tcpClient = TcpClient.create(ConnectionProvider.create("fixed-pool-", 1000))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(1, TimeUnit.MINUTES));
                    connection.addHandlerLast(new WriteTimeoutHandler(1, TimeUnit.MINUTES));
                });

        webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.from(tcpClient)))
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

    public String[] getAccessInfo(final String token) {
        return tokenAccessMap.computeIfAbsent(token, t -> {
            String[] access = new String[3];
            SQLAdapter adminAdapter = SERVER_CONFIG.createAdminAdapter();
            List<IKVStore> res = null;
            try {
                res = adminAdapter.getResultsOfQuery("SELECT * " +
                        "FROM experiments " +
                        "WHERE experiment_token='"
                        + Objects.requireNonNull(token)
                        + "'");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (res.isEmpty()) {
                throw new IllegalStateException("No experiment known for the given token!");
            }
            if (res.size() > 1) {
                throw new IllegalStateException("Multiple experiments for the same token. A token must be unique!");
            }
            IKVStore connectionDescription = res.get(0);
            access[0] = connectionDescription.getAsString("db_user");
            access[1] = connectionDescription.getAsString("db_passwd");
            access[2] = connectionDescription.getAsString("db_name");
            adminAdapter.close();
            return access;
        });
    }

    public String getUserName(String token) {
        return getAccessInfo(token)[0];
    }
    public String getPassword(String token) {
        return getAccessInfo(token)[1];

    }
    public String getDbName(String token) {
        return getAccessInfo(token)[2];
    }

    public SQLAdapter getAdapter() {
        return adapter;
    }

    public WebClient getWebClient() {
        return webClient;
    }
}
