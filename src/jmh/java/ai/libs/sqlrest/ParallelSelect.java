package ai.libs.sqlrest;

import ai.libs.jaicore.basic.kvstore.KVStore;
import ai.libs.jaicore.db.sql.SQLAdapter;
import ai.libs.sqlrest.model.SQLQuery;
import org.aeonbits.owner.ConfigCache;
import org.api4.java.datastructure.kvstore.IKVStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.Control;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.function.ServerRequest;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/*
 * Assumes that the service is running
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(1)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
public class ParallelSelect {

    private static final Logger logger = LoggerFactory.getLogger(ParallelSelect.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Param({
            "1-random-time-null",
            "100-random-time-null",
            "select-1",
            "select-100"
    }) // each entry is 3.5 kByte
    private String query;

    @Param({
        "1", "2", "8", "16"
    })
    private String numServiceAdapters;

    @Param({
            "4", "16", "32"
    })
    private String numWorkers;

    @Param({
            "256"
    })
    private String numJobs;

    private SQLQuery queryObj;

    private ExecutorService executor;

    private int numberOfJobs = 256;

    private long resultFetchTimeout = TimeUnit.SECONDS.toMillis(30);

    private void createQuery() {
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        String sqlQuery = BenchmarkQueryRegistry.createQuery(query, tableName);
        queryObj = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), sqlQuery);
    }

    private void setServiceAdapters(WebClient webClient) {
        WebClient.RequestHeadersSpec<?> request = webClient.post()
                .uri("numadapter")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(numServiceAdapters));
        ResponseEntity<Void> response = request.retrieve().toBodilessEntity().block();
        if(response == null || response.getStatusCode().isError()) {
            throw new RuntimeException("Couldn't set service adapters");
        }
    }

    private void createExecutor() {
        executor = Executors.newFixedThreadPool(Integer.parseInt(numWorkers));
    }


    @Setup
    public void setup(SQLClientState state) {
        createQuery();
        setServiceAdapters(state.getWebClient());
        createExecutor();
        numberOfJobs = Integer.parseInt(numJobs);
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Benchmark
    public void parallelQueryOverService(SQLClientState state, Blackhole bh) throws InterruptedException, TimeoutException, ExecutionException {
        WebClient webClient = state.getWebClient();
        Callable<List<KVStore>> serviceCall = () -> {
            WebClient.RequestHeadersSpec<?> request
                    = webClient.post()
                    .uri("query")
                    .body(BodyInserters.fromValue(queryObj));
            List<KVStore> end = request.retrieve().bodyToMono(keyStoreListType).block();
            return end;
        };
        List<Future<List<KVStore>>> futureList = new ArrayList<>(numberOfJobs);
        for (int i = 0; i < numberOfJobs; i++) {
            futureList.add(executor.submit(serviceCall));
        }
        for (Future<List<KVStore>> future : futureList) {
            bh.consume(future.get(resultFetchTimeout, TimeUnit.MILLISECONDS));
        }
    }

    @Benchmark
    public void parallelQueryOverFreshAdapters(SQLClientState state, Blackhole bh) throws InterruptedException, TimeoutException, ExecutionException {
        if(Integer.parseInt(numServiceAdapters) > 1) {
            throw new IllegalStateException("Parameter `numServiceAdapter` doesn't affect this adapter benchmarks.");
        }
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

}
