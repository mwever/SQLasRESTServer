package ai.libs.sqlrest;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class AbstractServiceBenchmark {

    @Setup
    public void waitForService(SQLRestServiceState serviceState, SQLClientState clientState) {
        serviceState.wait(clientState.getWebClient());
    }

}
