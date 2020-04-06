package ai.libs.sqlrest;

import ai.libs.jaicore.basic.kvstore.KVStore;
import ai.libs.sqlrest.model.SQLQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.aeonbits.owner.ConfigCache;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms4G", "-Xmx8G"})
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 4, time = 15)
public class HttpClientPerformance extends AbstractServiceBenchmark {


    private static final Logger logger = LoggerFactory.getLogger(IterativeSelect.class);

    private static final ParameterizedTypeReference<List<KVStore>> keyStoreListType = new ParameterizedTypeReference<List<KVStore>>() {};

    private static final IBenchmarkConfig BENCHMARK_CONFIG = ConfigCache.getOrCreate(IBenchmarkConfig.class);

    private static final IServerConfig SERVER_CONFIG = ConfigCache.getOrCreate(IServerConfig.class);

    @Param({
            "select-1",
            "select-100",
            "select-10000",
    }) // each entry is 3.5 kByte
    private String query;

    private SQLQuery queryObj;

    private CloseableHttpClient httpclient;
    ObjectMapper mapper = new ObjectMapper();
    CollectionType collectionType;

    private void createQuery() {
        String tableName = BENCHMARK_CONFIG.getBenchmarkTable();
        String sqlQuery = BenchmarkQueryRegistry.createQuery(query, tableName);
        queryObj = new SQLQuery(BENCHMARK_CONFIG.getBenchmarkToken(), sqlQuery);
    }

    @Setup
    public void setup() throws SQLException {
        createQuery();
        httpclient = HttpClients.createDefault();
        collectionType = mapper.getTypeFactory().constructCollectionType(List.class, KVStore.class);
    }

    @Benchmark
    public void springWebClient(SQLClientState state, Blackhole bh) throws IOException {
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
    public void apacheHttpClient(SQLClientState state, Blackhole bh) throws IOException {
        HttpPost httpPost = new HttpPost(BENCHMARK_CONFIG.getServiceHost() + "/query");
        String postBody = mapper.writeValueAsString(queryObj);
        httpPost.setEntity(new  StringEntity(postBody));
        httpPost.setHeader("Content-Type", "application/json");
        CloseableHttpResponse response2 = httpclient.execute(httpPost);

        try {
            assert response2.getStatusLine().getStatusCode() == 200;
            List<KVStore> o = mapper.readValue(response2.getEntity().getContent(), collectionType);
            assert o != null;
            bh.consume(o);
        } finally {
            response2.close();
        }
    }

}
