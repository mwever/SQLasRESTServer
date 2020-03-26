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
import reactor.core.Disposable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 4, time = 15)
public class OverloadedService {

    private static final Logger logger = LoggerFactory.getLogger(OverloadedService.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    @Param({
            "select-100",
    })
    private String query;

    @Param({
            "512", "1024", "2048"
    })
    private String numConnections;

    private SQLQuery queryObj;

    private List<Disposable> openConnections;


    private void createQuery() {
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        String sqlQuery = BenchmarkQueryRegistry.createQuery(query, tableName);
        queryObj = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), sqlQuery);
    }

    private void openConnections(WebClient webClient) {
        int numberConnections = Integer.parseInt(numConnections);
        openConnections = new ArrayList<>(numberConnections);
        for (int i = 0; i < numberConnections; i++) {
            Disposable liveConnection = webClient.get().uri("block").retrieve().toBodilessEntity().subscribe();
            openConnections.add(liveConnection);
        }
//        logger.warn("Finished opening {} connections.", numberConnections);
    }

    @Setup
    public void setup(SQLClientState clientState) {
        createQuery();
        openConnections(clientState.getWebClient());
    }

    @TearDown
    public void teardown() {
        openConnections.forEach(Disposable::dispose);
//        logger.warn("Closed {} connections.", openConnections.size());
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

}
