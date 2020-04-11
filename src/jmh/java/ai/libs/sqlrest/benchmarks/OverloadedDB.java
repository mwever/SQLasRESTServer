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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 15)
public class OverloadedDB  {

    private static final Logger logger = LoggerFactory.getLogger(OverloadedDB.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    @Param({
            "select-100",
    })
    private String query;

    @Param({
            "512", "1024"
    })
    private String numConnections;

    private SQLQuery queryObj;

    private List<SQLAdapter> openConnections;

    private void createQuery() {
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        String sqlQuery = BenchmarkQueryRegistry.createQuery(query, tableName);
        queryObj = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), sqlQuery);
    }

    private void openConnections(SQLClientState state) throws SQLException {
        int numberConnections = Integer.parseInt(numConnections);
        openConnections = new ArrayList<>(numberConnections);
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        String quickQuery = BenchmarkQueryRegistry.createQuery("select-1", tableName);
        SQLQuery quickSQLQuery = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), quickQuery);
        for (int i = 0; i < numberConnections; i++) {
            SQLAdapter adapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
                    state.getUserName(), state.getPassword(), state.getDbName(), SERVER_CONFIG.getDBPropUseSsl());
            List<IKVStore> resultsOfQuery = adapter.getResultsOfQuery(quickQuery);
            assert resultsOfQuery != null && !resultsOfQuery.isEmpty();
            openConnections.add(adapter);
        }
    }

    @Setup
    public void setup(SQLClientState clientState) throws SQLException {
        createQuery();
        openConnections(clientState);
        SQLBenchmarkUtil.flushDB();
    }

    @TearDown
    public void teardown() {
        openConnections.forEach(SQLAdapter::close);
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
