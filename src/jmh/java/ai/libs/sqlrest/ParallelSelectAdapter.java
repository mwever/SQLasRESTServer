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
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/*
 * Assumes that the service is running
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(1)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
public class ParallelSelectAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ParallelSelectAdapter.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Param({
//            "1-random-time-null",
//            "100-random-time-null",
            "select-1",
            "select-100"
    }) // each entry is 3.5 kByte
    private String query;

    @Param({
            "1", "16"
    })
    private String numServiceAdapters;

    @Param({
            "25", "99"
    })
    private String numWorkers;

    @Param({
            "2000"
    })
    private String numJobs;

    private SQLQuery queryObj;

    private ExecutorService executor;

    private int numberOfJobs;

    private long resultFetchTimeout = TimeUnit.MINUTES.toMillis(30);

    private void createQuery() {
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        String sqlQuery = BenchmarkQueryRegistry.createQuery(query, tableName);
        queryObj = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), sqlQuery);
    }

    private void createExecutor() {
        executor = Executors.newFixedThreadPool(Integer.parseInt(numWorkers));
    }

    @Setup
    public void setup(SQLClientState state) {
        createQuery();
        createExecutor();
        numberOfJobs = Integer.parseInt(numJobs);
    }

//    @Setup
//    public void flushDBHosts(SQLClientState state) throws IOException, SQLException {
//        SQLAdapter adapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
//                state.getUserName(), state.getPassword(), state.getDbName(), SERVER_CONFIG.getDBPropUseSsl());
//        adapter.query("FLUSH TABLES; FLUSH HOSTS;");
//    }

    @TearDown
    public void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }


    @Benchmark
    public void parallelQueryOverFreshAdapters(SQLClientState state, Blackhole bh) throws InterruptedException, TimeoutException, ExecutionException {
        Callable<List<IKVStore>> serviceCall = () -> {
            SQLAdapter freshAdapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
                    state.getUserName(), state.getPassword(), state.getDbName(), SERVER_CONFIG.getDBPropUseSsl());
            List<IKVStore> end = freshAdapter.getResultsOfQuery(queryObj.getQuery());
            freshAdapter.close();
            return end;
        };
        List<Future<List<IKVStore>>> futureList = new ArrayList<>(numberOfJobs);
        for (int i = 0; i < numberOfJobs; i++) {
            futureList.add(executor.submit(serviceCall));
        }
        for (Future<List<IKVStore>> future : futureList) {
            bh.consume(future.get(resultFetchTimeout, TimeUnit.MILLISECONDS));
        }
    }

    @Benchmark
    public void parallelQueryOverASingleAdapter(SQLClientState state, Blackhole bh) throws InterruptedException, TimeoutException, ExecutionException {
        Callable<List<IKVStore>> serviceCall = () -> {
            SQLAdapter adapter = state.getAdapter();
            List<IKVStore> end = adapter.getResultsOfQuery(queryObj.getQuery());
            return end;
        };
        List<Future<List<IKVStore>>> futureList = new ArrayList<>(numberOfJobs);
        for (int i = 0; i < numberOfJobs; i++) {
            futureList.add(executor.submit(serviceCall));
        }
        for (Future<List<IKVStore>> future : futureList) {
            bh.consume(future.get(resultFetchTimeout, TimeUnit.MILLISECONDS));
        }
    }

}
