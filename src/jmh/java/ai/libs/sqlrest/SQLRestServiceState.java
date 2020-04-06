package ai.libs.sqlrest;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.web.reactive.function.client.WebClient;

@State(Scope.Benchmark)
public class SQLRestServiceState {

    SQLRestServiceHandler handler = SQLRestServiceHandler.INSTANCE;

    @Setup
    public void setup() {
        handler.startService();
    }

    public void wait(WebClient webClient) {
        handler.waitForServiceAvailability(webClient);
    }

    @TearDown
    public void teardown() {
        handler.stopService();
    }

}
