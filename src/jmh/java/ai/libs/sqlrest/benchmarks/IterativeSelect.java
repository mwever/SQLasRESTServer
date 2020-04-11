package ai.libs.sqlrest.benchmarks;

import ai.libs.jaicore.basic.kvstore.KVStore;
import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.*;
import ai.libs.sqlrest.model.SQLQuery;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/*
 * Assumes that the service is running
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 10, time = 8)
public class IterativeSelect  {

    private static final Logger logger = LoggerFactory.getLogger(IterativeSelect.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Param({
//            "1-random-time-null",
//            "100-random-time-null",
            "1-random",
            "100-random",
            "1000-random",
//            "1-time-null",
//            "100-time-null",
            "select-1",
            "select-100",
            "select-10000",
//            "1-random-time-null-join",
//            "100-random-time-null-join",
//            "1-random-time-null-subquery",
//            "100-random-time-null-subquery"
    }) // each entry is 3.5 kByte
    private String query;

    private SQLQuery queryObj;

    private void createQuery() {
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        String sqlQuery = BenchmarkQueryRegistry.createQuery(query, tableName);
        queryObj = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), sqlQuery);
    }

    @Setup
    public void setup(SQLRestServiceState serviceState, SQLClientState state) {
        createQuery();
        SQLBenchmarkUtil.startService(serviceState, state);
        SQLBenchmarkUtil.flushDB();
    }


    @Benchmark
    public void singleQueryOverService(SQLClientState state, Blackhole bh) throws IOException {
        WebClient webClient = state.getWebClient();
        WebClient.RequestHeadersSpec<?> request
                = webClient.post()
                .uri("query")
                .body(BodyInserters.fromValue(queryObj));
        List<KVStore> end = request.retrieve().bodyToMono(keyStoreListType).block();
        assert end != null;
        bh.consume(end);
    }

    @Benchmark
    public void singleQueryOverSingleAdapter(SQLClientState state, Blackhole bh) throws SQLException {
        List<IKVStore> end = state.getAdapter().getResultsOfQuery(queryObj.getQuery());
        assert end != null;
        bh.consume(end);
    }


    @Benchmark
    public void singleQueryOverFreshAdapters(SQLClientState state, Blackhole bh) throws SQLException {
        SQLAdapter freshAdapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
                state.getUserName(), state.getPassword(), state.getDbName(), SERVER_CONFIG.getDBPropUseSsl());
        List<IKVStore> end = freshAdapter.getResultsOfQuery(queryObj.getQuery());
        assert end != null;
        bh.consume(end);
        freshAdapter.close();
    }

}
