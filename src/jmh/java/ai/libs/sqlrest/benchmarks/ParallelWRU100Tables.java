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

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static ai.libs.sqlrest.SQLBenchmarkUtil.getPerformanceProps;

/*
 * Assumes that the service is running
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(1)
@Fork(value = 1, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 50, time = 1)
@Measurement(iterations = 50, time = 1)
@State(Scope.Benchmark)
public class ParallelWRU100Tables {

    private static final Logger logger = LoggerFactory.getLogger(ParallelWRU100Tables.class);

    private static final ParameterizedTypeReference<int[]> intArrType = new ParameterizedTypeReference<int[]>() {};
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
            "1000"
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

    private List<RandomWRUQueryBatch.WRUQueries> queries;

    private ExecutorService executor;

    private int numberOfJobs;

    private long resultFetchTimeout = TimeUnit.MINUTES.toMillis(30);

    private void createQuery() {
        numberOfJobs = Integer.parseInt(numJobs);
        RandomWRUQueryBatch batch;
        batch = new RandomWRUQueryBatch(numberOfJobs, Integer.parseInt(seed));
        File dumQueriesFile = new File("benchmark-workingdir/WRU-querries.txt");
        try (FileOutputStream fos = new FileOutputStream(dumQueriesFile)){
            BufferedOutputStream out = new BufferedOutputStream(fos);
            out.write(batch.dumpQueries().getBytes());
            out.flush();
        } catch (IOException e) {
            logger.error("Error trying to dump querries: " + dumQueriesFile);
        }
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
        for (RandomWRUQueryBatch.WRUQueries query : queries) {
            Callable<List<KVStore>> serviceCall = () -> {
                WebClient.RequestHeadersSpec<?> request
                        = webClient.post()
                        .uri("insert")
                        .body(BodyInserters.fromValue(query.getWrite()));
                int[] indices = request.retrieve().bodyToMono(intArrType).block();
                if(indices.length != 1) {
                    throw new RuntimeException("There are " + indices.length + " many generated indices: " + Arrays.toString(indices));
                }
                int generatedIndex = indices[0];
                query.setIndex(generatedIndex);
                Thread.sleep(100);
                request
                        = webClient.post()
                        .uri("query")
                        .body(BodyInserters.fromValue(query.getRead()));
                List<KVStore> end = request.retrieve().bodyToMono(keyStoreListType).block();

                Thread.sleep(100);
                request
                        = webClient.post()
                        .uri("update")
                        .body(BodyInserters.fromValue(query.getUpdate()));
                Integer nrOfChangedRows = request.retrieve().bodyToMono(Integer.class).block();

                if(nrOfChangedRows == null || nrOfChangedRows == 0) {
                    logger.warn("No rows changed");
                }
                return end;
            };
            futureList.add(executor.submit(serviceCall));
        }
        for (Future<List<KVStore>> future : futureList) {
            bh.consume(future.get(resultFetchTimeout, TimeUnit.MILLISECONDS));
        }
    }
//    @Benchmark
    public void parallelQueryOverAdapter(SQLClientState state, Blackhole bh) throws InterruptedException, TimeoutException, ExecutionException {
        if(usePerfProperties.equals("false")) {
            throw new IllegalStateException();
        }
        List<Future<List<IKVStore>>> futureList = new ArrayList<>(numberOfJobs);
        for (RandomWRUQueryBatch.WRUQueries query : queries) {
            Callable<List<IKVStore>> serviceCall = () -> {
                SQLAdapter freshAdapter = new SQLAdapter(SERVER_CONFIG.getDBHost(),
                        state.getUserName(query.getToken()), state.getPassword(query.getToken()),
                        state.getDbName(query.getToken()),
                        SERVER_CONFIG.getDBPropUseSsl());
                int[] indices = freshAdapter.insert(query.getWrite().getQuery(), Collections.emptyList());
                if(indices.length != 1) {
                    throw new RuntimeException("There are " + indices.length + " many generated indices: " + Arrays.toString(indices));
                }
                int generatedIndex = indices[0];
                query.setIndex(generatedIndex);
                Thread.sleep(100);
                List<IKVStore> end = freshAdapter.query(query.getRead().getQuery());
                Thread.sleep(100);
                Integer nrOfChangedRows = freshAdapter.update(query.getUpdate().getQuery());

                if(nrOfChangedRows == null || nrOfChangedRows == 0) {
                    logger.warn("No rows changed");
                }
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
