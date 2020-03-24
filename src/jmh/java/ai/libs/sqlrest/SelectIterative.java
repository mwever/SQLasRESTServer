package ai.libs.sqlrest;

import ai.libs.jaicore.basic.kvstore.KVStore;
import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.SQLQuery;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/*
 * Assumes that the service is running
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 6, time = 5)
public class SelectIterative {

    private static final Logger logger = LoggerFactory.getLogger(SelectIterative.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Param({
            "1-random-time-null",
            "1000-random-time-null",
            "select-1",
            "select-1000",
            "select-100000"
    }) // each entry is 3.5 kByte
    private String query;

    private SQLQuery queryObj;

    private String userName, password, dbName;

    private SQLAdapter adapter;

    private void createQuery() {
        String sqlQuery;
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        if(query.equals("1-random-time-null")) {
            sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT, tableName, 1);
        } else if(query.equals("10-random-time-null")) {
            sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT, tableName, 10);
        } else if(query.equals("1000-random-time-null")) {
            sqlQuery = String.format(BenchmarkQueryRegistry.TIME_NULL_RANDOM_SELECT, tableName, 1000);
        }

        else if(query.equals("select-1")) {
            sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 1);
        }
        else if(query.equals("select-1000")) {
            sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 1000);
        } else if(query.equals("select-10000")) {
            sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 10000);
        } else if(query.equals("select-100000")) {
            sqlQuery = String.format(BenchmarkQueryRegistry.SELECT_N, tableName, 100000);
        }
        else {
            throw new IllegalStateException("Query not recognized: " + query);
        }
        queryObj = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), sqlQuery);
    }

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

    @Setup
    public void setup() throws SQLException {
        createQuery();
        retrieveDatabaseInfo();
        createAdapter();
    }

    @Benchmark
    public void singleQueryOverService(Blackhole bh) throws IOException {
        String requestUrl = "http://localhost:8080";
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
//                        .maxInMemorySize(500 * (1 << 20))) //
                        .maxInMemorySize(-1)) // -1 : unlimited memory for fetch buffer
                .build();

        WebClient webClient = WebClient.builder().exchangeStrategies(exchangeStrategies).baseUrl(requestUrl).build();
        WebClient.RequestHeadersSpec<?> request
                = webClient.post()
                .uri("query")
                .body(BodyInserters.fromValue(queryObj));
        List<KVStore> end = request.retrieve().bodyToMono(keyStoreListType).block();
        assert end != null;
        bh.consume(end);
    }

    @Benchmark
    public void singleQueryOverSingleAdapter(Blackhole bh) throws SQLException {
        List<IKVStore> end = adapter.getResultsOfQuery(queryObj.getQuery());
        assert end != null;
        bh.consume(end);
    }


    @Benchmark
    public void singleQueryOverFreshAdapters(Blackhole bh) throws SQLException {
        SQLAdapter freshAdapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
                userName, password, dbName, SERVER_CONFIG.getDBPropUseSsl());
        List<IKVStore> end = freshAdapter.getResultsOfQuery(queryObj.getQuery());
        assert end != null;
        bh.consume(end);
        freshAdapter.close();
    }

}
