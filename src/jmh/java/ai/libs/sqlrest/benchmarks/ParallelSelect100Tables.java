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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import static ai.libs.sqlrest.SQLBenchmarkUtil.getPerformanceProps;

/*
 * Assumes that the service is running
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(1)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class ParallelSelect100Tables {

    private static final Logger logger = LoggerFactory.getLogger(ParallelSelect100Tables.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Param({
        "64"
    })
    private String numServiceAdapters;

    @Param({
            "200"
    })
    private String numWorkers;

    @Param({
            "1000", "3000"
    })
    private String numJobs;

    @Param({
            "61"
    })
    private String seed;

    @Param({
            "0"
    })
    private String limitedAccessNum;

    @Param({
            "true", "false"
    })
    private String usePerfProperties;

    private List<SQLQuery> queries;

    private ExecutorService executor;

    private int numberOfJobs;

    private long resultFetchTimeout = TimeUnit.MINUTES.toMillis(30);

    private void createQuery() {
        numberOfJobs = Integer.parseInt(numJobs);
        RandomReadQueryBatch batch;
        batch = new RandomReadQueryBatch(numberOfJobs, Integer.parseInt(seed));
        queries = batch.queries();
    }

    private void setServerSettings() {
        SQLRestServiceHandler.INSTANCE.setNumAdapters(Integer.parseInt(numServiceAdapters));
        SQLRestServiceHandler.INSTANCE.setAccessLimit(Integer.parseInt(limitedAccessNum));
        if(usePerfProperties.equals("true")) {
            Properties perfProps = getPerformanceProps();
            SQLRestServiceHandler.INSTANCE.getServerProperties().putAll(perfProps);
        }
    }

    private void createExecutor() {
        executor = Executors.newFixedThreadPool(Integer.parseInt(numWorkers));
    }

    @Setup
    public void setup(SQLRestServiceState serviceState, SQLClientState state) {
        createQuery();
        createExecutor();
        setServerSettings();

        SQLBenchmarkUtil.startService(serviceState, state);
        SQLBenchmarkUtil.flushDB();
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Benchmark
    public void parallelQueryOverService(SQLClientState state, Blackhole bh) throws InterruptedException, TimeoutException, ExecutionException {
        WebClient webClient = state.getWebClient();
        List<Future<List<KVStore>>> futureList = new ArrayList<>(numberOfJobs);
        for (SQLQuery query : queries) {
            Callable<List<KVStore>> serviceCall = () -> {
                WebClient.RequestHeadersSpec<?> request
                        = webClient.post()
                        .uri("query")
                        .body(BodyInserters.fromValue(query));
                List<KVStore> end = request.retrieve().bodyToMono(keyStoreListType).block();
                return end;
            };
            futureList.add(executor.submit(serviceCall));
        }
        for (Future<List<KVStore>> future : futureList) {
            bh.consume(future.get(resultFetchTimeout, TimeUnit.MILLISECONDS));
        }
    }

    @Benchmark
    public void parallelQueryOverAdapters(SQLClientState state, Blackhole bh) throws InterruptedException, TimeoutException, ExecutionException {
        if(usePerfProperties.equals("false")) {
            throw new RuntimeException();
        }
        List<Future<List<IKVStore>>> futureList = new ArrayList<>(numberOfJobs);
        for (SQLQuery query : queries) {
            Callable<List<IKVStore>> serviceCall = () -> {
                SQLAdapter freshAdapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
                        state.getUserName(query.getToken()), state.getPassword(query.getToken()),
                        state.getDbName(query.getToken()),
                        SERVER_CONFIG.getDBPropUseSsl());
                List<IKVStore> end = freshAdapter.query(query.getQuery());
                freshAdapter.close();
                return end;
            };
            futureList.add(executor.submit(serviceCall));
        }
        for (Future<List<IKVStore>> future : futureList) {
            bh.consume(future.get(resultFetchTimeout, TimeUnit.MILLISECONDS));
        }
    }

}
